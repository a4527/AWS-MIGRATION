# ARCH-01: before/after measurements for the S3 gateway endpoint and the
# target group deregistration delay (DEC-026).
#   python3 scripts/drills/arch01_measure.py <out.log> s3      # upload+download, NAT bytes
#   python3 scripts/drills/arch01_measure.py <out.log> idle    # NAT bytes with no workload
#   python3 scripts/drills/arch01_measure.py <out.log> deploy  # force new deployment: duration + NAT bytes
#   python3 scripts/drills/arch01_measure.py <out.log> stop    # stop the production task: outage
# Needs the lab layer (terraform/environments/dev) and AWS_PROFILE=bootcamp-tf.
import datetime, hashlib, json, os, subprocess, sys, time, urllib.error, urllib.request, uuid

env = dict(os.environ, AWS_PROFILE=os.environ.get("AWS_PROFILE", "bootcamp-tf"))
CL, SV, NAT_NAME, ALB_NAME = "fileshare-dev-ecs", "fileshare-dev-backend", "fileshare-dev-nat", "fileshare-dev-alb"
OUT, MODE = sys.argv[1], sys.argv[2]
FILES, SIZE = int(os.environ.get("ARCH01_FILES", "20")), int(os.environ.get("ARCH01_SIZE", str(5 * 1024 * 1024)))


def aws(*a):
    r = subprocess.run(["aws", *a, "--output", "json"], env=env, capture_output=True, text=True)
    if r.returncode:
        raise RuntimeError(r.stderr[:400])
    return json.loads(r.stdout) if r.stdout.strip() else {}


def now():
    return datetime.datetime.now(datetime.timezone.utc)


def iso(t):
    return t.strftime("%Y-%m-%dT%H:%M:%SZ")


def log(*m):
    line = now().strftime("%H:%M:%SZ") + " " + " ".join(str(x) for x in m)
    print(line, flush=True)
    with open(OUT, "a") as f:
        f.write(line + "\n")


BASE = "http://" + aws("elbv2", "describe-load-balancers", "--names", ALB_NAME)["LoadBalancers"][0]["DNSName"]


def http(method, path, token=None, data=None, headers=None, timeout=30):
    h = dict(headers or {})
    if token:
        h["Authorization"] = "Bearer " + token
    req = urllib.request.Request(BASE + path, data=data, method=method, headers=h)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.status, r.read()
    except urllib.error.HTTPError as e:
        return e.code, e.read()
    except Exception as e:  # connection reset, timeout
        return 0, str(e).encode()


def nat_bytes(start, end):
    """Sum of NAT processed bytes (both directions) per metric over [start, end]."""
    nat = aws("ec2", "describe-nat-gateways", "--filter", f"Name=tag:Name,Values={NAT_NAME}", "Name=state,Values=available")["NatGateways"][0]["NatGatewayId"]
    out = {}
    for metric in ("BytesOutToDestination", "BytesInFromDestination", "BytesOutToSource", "BytesInFromSource"):
        pts = aws("cloudwatch", "get-metric-statistics", "--namespace", "AWS/NATGateway", "--metric-name", metric,
                  "--dimensions", f"Name=NatGatewayId,Value={nat}", "--start-time", iso(start), "--end-time", iso(end),
                  "--period", "60", "--statistics", "Sum")["Datapoints"]
        out[metric] = int(sum(p["Sum"] for p in pts))
    return out


def report_nat(label, start, end):
    log(f"waiting 180s for NAT metrics ({label})")
    time.sleep(180)
    b = nat_bytes(start, end)
    mins = max((end - start).total_seconds() / 60, 1)
    log(f"NAT {label} window {iso(start)}..{iso(end)} ({mins:.1f} min):",
        " ".join(f"{k}={v / 1e6:.1f}MB" for k, v in b.items()),
        f"processed(InFromSource+InFromDestination)={(b['BytesInFromSource'] + b['BytesInFromDestination']) / 1e6:.1f}MB")


def user():
    email = f"arch01-{int(time.time())}@example.com"
    js = {"Content-Type": "application/json"}
    http("POST", "/api/auth/signup", data=json.dumps({"email": email, "password": "Password123!", "name": "arch01"}).encode(), headers=js)
    return json.loads(http("POST", "/api/auth/login", data=json.dumps({"email": email, "password": "Password123!"}).encode(), headers=js)[1])["data"]["accessToken"]


def s3_workload():
    token = user()
    start = now()
    log(f"=== s3: {FILES} uploads x {SIZE} bytes, then downloads")
    sent = {}
    for i in range(FILES):
        content = os.urandom(SIZE)
        b = uuid.uuid4().hex
        body = (f"--{b}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"arch01-{i}.txt\"\r\n"
                "Content-Type: text/plain\r\n\r\n").encode() + content + f"\r\n--{b}--\r\n".encode()
        st, resp = http("POST", "/api/files", token, body, {"Content-Type": f"multipart/form-data; boundary={b}"}, timeout=120)
        if st != 200:
            log("upload failed", st, resp[:200])
            continue
        sent[json.loads(resp)["data"]["fileId"]] = hashlib.sha256(content).hexdigest()
    log(f"uploaded {len(sent)}/{FILES}")
    deadline = time.time() + 300  # GuardDuty scan must finish before download (FILE-03)
    pending = set(sent)
    while pending and time.time() < deadline:
        for fid in list(pending):
            st, resp = http("GET", f"/api/files/{fid}", token)
            if st == 200 and json.loads(resp)["data"]["status"] != "scanning":
                pending.discard(fid)
        if pending:
            time.sleep(10)
    ok = 0
    for fid, digest in sent.items():
        st, data = http("GET", f"/api/files/{fid}/download", token, timeout=120)
        ok += st == 200 and hashlib.sha256(data).hexdigest() == digest
    end = now()
    log(f"downloaded with matching sha256 {ok}/{len(sent)}; still scanning {len(pending)}")
    report_nat("s3", start, end)


def idle():
    start = now()
    log("=== idle: 10 min without workload")
    time.sleep(600)
    report_nat("idle", start, now())


def deploy():
    start = now()
    dep = aws("ecs", "update-service", "--cluster", CL, "--service", SV, "--force-new-deployment")["service"]["deployments"][0]["id"]
    log(f"=== deploy: force new deployment {dep}")
    while True:
        d = [x for x in aws("ecs", "describe-services", "--cluster", CL, "--services", SV)["services"][0]["deployments"] if x["id"] == dep][0]
        if d["rolloutState"] in ("COMPLETED", "FAILED"):
            break
        time.sleep(5)
    end = now()
    log(f"rollout {d['rolloutState']} in {(end - start).total_seconds():.0f}s")
    for e in reversed(aws("ecs", "describe-services", "--cluster", CL, "--services", SV)["services"][0]["events"][:8]):
        if datetime.datetime.fromisoformat(e["createdAt"]) >= start:
            log("  event", e["createdAt"][11:19], e["message"][:160])
    report_nat("deploy", start, end)


def stop():
    task = aws("ecs", "list-tasks", "--cluster", CL, "--service-name", SV, "--desired-status", "RUNNING")["taskArns"][0]
    tg = aws("elbv2", "describe-target-groups", "--names", "fileshare-dev-backend")["TargetGroups"][0]["TargetGroupArn"]
    delay = [a["Value"] for a in aws("elbv2", "describe-target-group-attributes", "--target-group-arn", tg)["Attributes"]
             if a["Key"] == "deregistration_delay.timeout_seconds"][0]
    log(f"=== stop: deregistration delay {delay}s, stopping {task.rsplit('/', 1)[1]}")
    t0 = now()
    aws("ecs", "stop-task", "--cluster", CL, "--task", task, "--reason", "ARCH-01 deregistration delay drill")
    first_bad = last_bad = None
    good_streak = 0
    while good_streak < 30 and (now() - t0).total_seconds() < 1200:
        st, _ = http("GET", "/api/health", timeout=5)
        if st == 200:
            good_streak += 1
        else:
            good_streak = 0
            last_bad = now()
            if first_bad is None:
                first_bad = last_bad
                log(f"first non-200 ({st})")
        time.sleep(1)
    if first_bad:
        log(f"outage {first_bad.strftime('%H:%M:%SZ')}..{last_bad.strftime('%H:%M:%SZ')} = {(last_bad - first_bad).total_seconds():.0f}s"
            f" (from StopTask {(last_bad - t0).total_seconds():.0f}s)")
    else:
        log("no outage observed")
    for e in reversed(aws("ecs", "describe-services", "--cluster", CL, "--services", SV)["services"][0]["events"][:8]):
        if datetime.datetime.fromisoformat(e["createdAt"]) >= t0:
            log("  event", e["createdAt"][11:19], e["message"][:160])


{"s3": s3_workload, "idle": idle, "deploy": deploy, "stop": stop}[MODE]()
