import json, subprocess, time, datetime, os, sys, urllib.request, urllib.error, uuid
env = dict(os.environ, AWS_PROFILE="bootcamp-tf")
OUT = sys.argv[1]
def aws(*a, check=True):
    r = subprocess.run(["aws", *a, "--output", "json"], env=env, capture_output=True, text=True)
    if check and r.returncode: raise RuntimeError(r.stderr[:300])
    return json.loads(r.stdout) if r.stdout.strip() else {}
def ts(): return datetime.datetime.now(datetime.timezone.utc).strftime("%H:%M:%SZ")
def log(*m):
    line = ts() + " " + " ".join(str(x) for x in m); print(line, flush=True); open(OUT, "a").write(line + "\n")
tf = lambda k: subprocess.run(["terraform", "output", "-raw", k], cwd="/home/taemin/AWS-MIGRATION/terraform/environments/dev", env=env, capture_output=True, text=True).stdout
BASE, BUCKET = tf("application_url"), tf("file_bucket_name")
QURL = aws("sqs", "get-queue-url", "--queue-name", "fileshare-dev-scan-results")["QueueUrl"]
DLQ = aws("sqs", "get-queue-url", "--queue-name", "fileshare-dev-scan-results-dlq")["QueueUrl"]
def http(method, path, token=None, data=None, headers=None):
    h = dict(headers or {})
    if token: h["Authorization"] = "Bearer " + token
    req = urllib.request.Request(BASE + path, data=data, method=method, headers=h)
    try:
        with urllib.request.urlopen(req, timeout=20) as r: return r.status, r.read()
    except urllib.error.HTTPError as e:
        try: body = e.read()
        except Exception: body = b""
        return e.code, body
def ec(b):
    try: return ec(b)
    except Exception: return f"(non-json {b[:40]!r})"
def up(token, name, content):
    b = uuid.uuid4().hex
    body = f"--{b}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"{name}\"\r\nContent-Type: text/plain\r\n\r\n".encode() + content + f"\r\n--{b}--\r\n".encode()
    st, r = http("POST", "/api/files", token, body, {"Content-Type": f"multipart/form-data; boundary={b}"})
    return json.loads(r)["data"]
def info(token, fid):
    st, r = http("GET", f"/api/files/{fid}", token); d = json.loads(r)
    return st, (d.get("data") or {}).get("status"), (d.get("data") or {}).get("scanStatus"), d.get("errorCode")
def wait_final(token, fid, limit=240):
    t0 = time.time()
    while time.time() - t0 < limit:
        st, s, scan, err = info(token, fid)
        if scan not in ("PENDING", None): return scan, s, time.time() - t0
        time.sleep(3)
    return "TIMEOUT", None, time.time() - t0
email = f"file03-{int(time.time())}@example.com"
http("POST", "/api/auth/signup", data=json.dumps({"email": email, "password": "Password123!", "name": "file03"}).encode(), headers={"Content-Type": "application/json"})
T = json.loads(http("POST", "/api/auth/login", data=json.dumps({"email": email, "password": "Password123!"}).encode(), headers={"Content-Type": "application/json"})[1])["data"]["accessToken"]

log("=== F1 clean file: PENDING -> CLEAN, download blocked while pending")
d = up(T, "clean.txt", b"hello clean file\n"); fid = d["fileId"]
log("  upload response status/scan:", d["status"], d["scanStatus"])
st, b = http("GET", f"/api/files/{fid}/download", T); log("  download while pending:", st, ec(b))
scan, s, dt = wait_final(T, fid); log(f"  final {s}/{scan} after {dt:.0f}s")
st, b = http("GET", f"/api/files/{fid}/download", T); log("  download after clean:", st, b[:20])
tags = aws("s3api", "get-object-tagging", "--bucket", BUCKET, "--key", d["storedFileName"])["TagSet"]
log("  object tags:", {t["Key"]: t["Value"] for t in tags})
CLEAN_KEY = d["storedFileName"]

log("=== F2 EICAR test file: INFECTED -> quarantined, download 403")
eicar = b"X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*"
d = up(T, "eicar.txt", eicar); fid2 = d["fileId"]
scan, s, dt = wait_final(T, fid2); log(f"  final {s}/{scan} after {dt:.0f}s")
st, b = http("GET", f"/api/files/{fid2}/download", T); log("  download:", st, ec(b))
tags = aws("s3api", "get-object-tagging", "--bucket", BUCKET, "--key", d["storedFileName"])["TagSet"]
log("  object tags:", {t["Key"]: t["Value"] for t in tags})
EICAR_KEY = d["storedFileName"]

def send(key, status):
    ev = {"source": "aws.guardduty", "detail-type": "GuardDuty Malware Protection Object Scan Result",
          "detail": {"scanStatus": "COMPLETED", "resourceType": "S3_OBJECT",
                     "s3ObjectDetails": {"bucketName": BUCKET, "objectKey": key},
                     "scanResultDetails": {"scanResultStatus": status, "threats": None}}}
    aws("sqs", "send-message", "--queue-url", QURL, "--message-body", json.dumps(ev))

log("=== F3 duplicate and late results do not change a final verdict")
send(EICAR_KEY, "NO_THREATS_FOUND"); send(CLEAN_KEY, "THREATS_FOUND"); time.sleep(20)
log("  eicar after fake CLEAN:", info(T, fid2)[1:3], " clean after fake THREATS:", info(T, fid)[1:3])

log("=== F4 delete right after upload; scan result must not restore it")
d = up(T, "race.txt", b"delete race\n"); fid4 = d["fileId"]
log("  delete immediately:", http("DELETE", f"/api/files/{fid4}", T)[0])
time.sleep(60)
log("  get after scan window:", info(T, fid4)[0], info(T, fid4)[3], " list contains:", any(f["fileId"] == fid4 for f in json.loads(http("GET", "/api/files", T)[1])["data"]))
tags = aws("s3api", "get-object-tagging", "--bucket", BUCKET, "--key", d["storedFileName"])["TagSet"]
log("  object scanned anyway (tag):", {t["Key"]: t["Value"] for t in tags}.get("GuardDutyMalwareScanStatus"))

log("=== F5 result for an object with no DB row -> retried, then DLQ")
ghost = f"files/ghost-{uuid.uuid4()}.txt"; send(ghost, "NO_THREATS_FOUND"); t0 = time.time()
while time.time() - t0 < 600:
    n = int(aws("sqs", "get-queue-attributes", "--queue-url", DLQ, "--attribute-names", "ApproximateNumberOfMessages")["Attributes"]["ApproximateNumberOfMessages"])
    if n > 0: break
    time.sleep(15)
log(f"  DLQ messages={n} after {time.time()-t0:.0f}s")
for i in range(16):
    st = aws("cloudwatch", "describe-alarms", "--alarm-names", "fileshare-dev-scan-results-dlq")["MetricAlarms"][0]["StateValue"]
    if st == "ALARM": break
    time.sleep(15)
log("  DLQ alarm:", st)
log("done")
