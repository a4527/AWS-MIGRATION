import json, subprocess, time, datetime, os, threading, urllib.request, urllib.error, uuid, sys
env = dict(os.environ, AWS_PROFILE="bootcamp-tf")
CL, SV = "fileshare-dev-ecs", "fileshare-dev-backend"
OUT = sys.argv[1]
def aws(*a, check=True):
    r = subprocess.run(["aws", *a, "--output", "json"], env=env, capture_output=True, text=True)
    if check and r.returncode: raise RuntimeError(r.stderr)
    return json.loads(r.stdout) if r.stdout.strip() else {}
def ts(): return datetime.datetime.now(datetime.timezone.utc).strftime("%H:%M:%S")
def log(*m):
    line = ts() + "Z " + " ".join(str(x) for x in m)
    print(line, flush=True); open(OUT, "a").write(line + "\n")
BASE = subprocess.run(["terraform", "output", "-raw", "application_url"], cwd="/home/taemin/AWS-MIGRATION/terraform/environments/dev", env=env, capture_output=True, text=True).stdout
def http(method, path, token=None, data=None, headers=None, timeout=10):
    h = dict(headers or {}); 
    if token: h["Authorization"] = "Bearer " + token
    req = urllib.request.Request(BASE + path, data=data, method=method, headers=h)
    t0 = time.time()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.status, r.read(), time.time() - t0, r.headers.get("X-Request-Id")
    except urllib.error.HTTPError as e:
        return e.code, e.read(), time.time() - t0, e.headers.get("X-Request-Id")
    except Exception as e:
        return 0, str(e).encode(), time.time() - t0, None
def multipart(name, content):
    b = uuid.uuid4().hex
    body = (f"--{b}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"{name}\"\r\nContent-Type: text/plain\r\n\r\n").encode() + content + f"\r\n--{b}--\r\n".encode()
    return body, {"Content-Type": f"multipart/form-data; boundary={b}"}
# user + file
email = f"ops01-{int(time.time())}@example.com"
http("POST", "/api/auth/signup", data=json.dumps({"email": email, "password": "Password123!", "name": "ops01"}).encode(), headers={"Content-Type": "application/json"})
TOKEN = json.loads(http("POST", "/api/auth/login", data=json.dumps({"email": email, "password": "Password123!"}).encode(), headers={"Content-Type": "application/json"})[1])["data"]["accessToken"]
body, hd = multipart("ops01.txt", b"ops01 baseline\n")
FID = json.loads(http("POST", "/api/files", TOKEN, body, hd)[1])["data"]["fileId"]
class Probe:
    def __init__(self, name, fn, every=2):
        self.name, self.fn, self.every, self.stop, self.res = name, fn, every, False, []
        threading.Thread(target=self.run, daemon=True).start()
    def run(self):
        while not self.stop:
            st, _, dt, _ = self.fn(); self.res.append((ts(), st, dt)); time.sleep(self.every)
    def end(self):
        self.stop = True; time.sleep(self.every + 1)
        bad = [r for r in self.res if r[1] != 200]
        counts = {}
        for r in self.res: counts[r[1]] = counts.get(r[1], 0) + 1
        lat = sorted(r[2] for r in self.res if r[1] == 200)
        p95 = lat[int(len(lat) * 0.95) - 1] if lat else None
        log(f"  probe[{self.name}] n={len(self.res)} status={counts} non200_window={bad[0][0]+'~'+bad[-1][0] if bad else '-'} p95={p95 and round(p95,3)}s")
def svc(): return aws("ecs", "describe-services", "--cluster", CL, "--services", SV)["services"][0]
def stable_wait(limit=900):
    t0 = time.time()
    while time.time() - t0 < limit:
        s = svc()
        if len(s["deployments"]) == 1 and s["runningCount"] == s["desiredCount"] and s["deployments"][0].get("rolloutState") == "COMPLETED":
            return time.time() - t0
        time.sleep(10)
    return None
def stopped_reasons(since):
    arns = aws("ecs", "list-tasks", "--cluster", CL, "--desired-status", "STOPPED")["taskArns"]
    if not arns: return []
    out = []
    for t in aws("ecs", "describe-tasks", "--cluster", CL, "--tasks", *arns[:20])["tasks"]:
        if t.get("stoppedAt", "") and t["stoppedAt"] >= since:
            out.append((t["stoppedAt"][11:19], t.get("stopCode"), t.get("stoppedReason", "")[:120], [c.get("exitCode") for c in t["containers"]]))
    return out
def register_variant(base_td, mutate):
    td = aws("ecs", "describe-task-definition", "--task-definition", base_td, "--include", "TAGS")
    d = td["taskDefinition"]
    keep = ["family","taskRoleArn","executionRoleArn","networkMode","containerDefinitions","volumes","placementConstraints","requiresCompatibilities","cpu","memory"]
    spec = {k: d[k] for k in keep if k in d}; spec["tags"] = td.get("tags", [])
    mutate(spec["containerDefinitions"][0])
    p = OUT + ".td.json"; open(p, "w").write(json.dumps(spec))
    return aws("ecs", "register-task-definition", "--cli-input-json", f"file://{p}")["taskDefinition"]["taskDefinitionArn"]
def alarm_states(names):
    r = aws("cloudwatch", "describe-alarms", "--alarm-names", *names)["MetricAlarms"]
    return {a["AlarmName"].replace("fileshare-dev-", ""): a["StateValue"] for a in r}
def bad_deploy(title, mutate, observe=240):
    log(f"=== {title}")
    base = svc()["taskDefinition"]; log("  base revision", base.split("/")[-1])
    bad = register_variant(base, mutate); log("  bad revision", bad.split("/")[-1])
    pr = Probe("GET /api/files/{id}", lambda: http("GET", f"/api/files/{FID}", TOKEN))
    since = datetime.datetime.now(datetime.timezone.utc).isoformat()
    log("  update-service -> bad"); aws("ecs", "update-service", "--cluster", CL, "--service", SV, "--task-definition", bad)
    time.sleep(observe)
    s = svc(); d = [x for x in s["deployments"] if x["taskDefinition"] == bad]
    log("  after", observe, "s: running", s["runningCount"], "deployments", len(s["deployments"]), "bad failedTasks", d[0].get("failedTasks") if d else None, "rollout", d[0].get("rolloutState") if d else None)
    for e in s["events"][:4]: log("  event", e["createdAt"][11:19], e["message"][:140])
    for r in stopped_reasons(since[:19]): log("  stopped", r)
    log("  rollback update-service -> base"); aws("ecs", "update-service", "--cluster", CL, "--service", SV, "--task-definition", base)
    w = stable_wait(); log("  stable after rollback in", w and round(w), "s")
    pr.end()
    aws("ecs", "deregister-task-definition", "--task-definition", bad); log("  bad revision deregistered")
    log("  alarms", alarm_states(["fileshare-dev-alb-elb-5xx","fileshare-dev-alb-target-5xx","fileshare-dev-alb-no-healthy-target"]))

tests = sys.argv[2].split(",")
if "pull" in tests:
    bad_deploy("T1 image pull failure (nonexistent tag)", lambda c: c.__setitem__("image", c["image"].rsplit(":",1)[0] + ":does-not-exist"))
if "db" in tests:
    def m(c):
        for e in c["environment"]:
            if e["name"] == "DB_USERNAME": e["value"] = "wrong_user"
    bad_deploy("T2 DB authentication failure (wrong DB_USERNAME)", m)
if "s3" in tests:
    log("=== T3 S3 AccessDenied (task role policy detached)")
    role = "fileshare-dev-ecs-task"
    pol = [p["PolicyArn"] for p in aws("iam", "list-attached-role-policies", "--role-name", role)["AttachedPolicies"] if p["PolicyName"].endswith("application-files")][0]
    aws("iam", "detach-role-policy", "--role-name", role, "--policy-arn", pol); log("  detached", pol.split("/")[-1])
    time.sleep(20)
    for i in range(6):
        body, hd = multipart(f"t3-{i}.txt", b"t3\n")
        st, b, dt, rid = http("POST", "/api/files", TOKEN, body, hd)
        st2, b2, _, rid2 = http("GET", f"/api/files/{FID}/download", TOKEN)
        log(f"  upload {st} {b[:90]!r} rid={rid} | download {st2} rid={rid2}")
        if st != 200: last_rid = rid
        time.sleep(10)
    aws("iam", "attach-role-policy", "--role-name", role, "--policy-arn", pol); log("  reattached")
    time.sleep(20)
    body, hd = multipart("t3-after.txt", b"t3 after\n")
    log("  upload after restore", http("POST", "/api/files", TOKEN, body, hd)[0])
    log("  last failing request id", locals().get("last_rid"))
if "redis" in tests:
    log("=== T4 Redis node reboot")
    pr = Probe("GET /api/files/{id}", lambda: http("GET", f"/api/files/{FID}", TOKEN), every=1)
    pr2 = Probe("download", lambda: http("GET", f"/api/files/{FID}/download", TOKEN), every=2)
    aws("elasticache", "reboot-cache-cluster", "--cache-cluster-id", "fileshare-dev-redis-001", "--cache-node-ids-to-reboot", "0001"); log("  reboot requested")
    deleted = False
    for i in range(60):
        st = aws("elasticache", "describe-cache-clusters", "--cache-cluster-id", "fileshare-dev-redis-001", "--show-cache-node-info")["CacheClusters"][0]
        ns = st["CacheNodes"][0]["CacheNodeStatus"]
        if i % 3 == 0: log("  node", ns)
        if ns == "rebooting cache cluster nodes" and not deleted:
            body, hd = multipart("t4-del.txt", b"t4\n")
            did = json.loads(http("POST", "/api/files", TOKEN, body, hd)[1])["data"]["fileId"]
            http("GET", f"/api/files/{did}", TOKEN)
            log("  delete during reboot", http("DELETE", f"/api/files/{did}", TOKEN)[0], "then get", http("GET", f"/api/files/{did}", TOKEN)[0]); deleted = True
        if ns == "available" and i > 6: break
        time.sleep(10)
    time.sleep(20); pr.end(); pr2.end()
    if deleted: log("  deleted file get after reboot", http("GET", f"/api/files/{did}", TOKEN)[0])
if "outage" in tests:
    log("=== T5 total outage (desired 0 for 3.5 min)")
    rid = f"service/{CL}/{SV}"
    aws("application-autoscaling", "register-scalable-target", "--service-namespace", "ecs", "--resource-id", rid, "--scalable-dimension", "ecs:service:DesiredCount", "--min-capacity", "0", "--max-capacity", "4")
    pr = Probe("GET /api/health", lambda: http("GET", "/api/health"), every=3)
    aws("ecs", "update-service", "--cluster", CL, "--service", SV, "--desired-count", "0"); log("  desired 0")
    names = ["fileshare-dev-alb-no-healthy-target","fileshare-dev-alb-elb-5xx"]; prev = {}
    for i in range(14):
        s = alarm_states(names)
        if s != prev: log("  alarms", s); prev = s
        time.sleep(15)
    aws("application-autoscaling", "register-scalable-target", "--service-namespace", "ecs", "--resource-id", rid, "--scalable-dimension", "ecs:service:DesiredCount", "--min-capacity", "1", "--max-capacity", "4")
    aws("ecs", "update-service", "--cluster", CL, "--service", SV, "--desired-count", "1"); log("  restored min 1 / desired 1")
    w = stable_wait(); log("  stable in", w and round(w), "s")
    for i in range(40):
        s = alarm_states(names)
        if s != prev: log("  alarms", s); prev = s
        if all(v == "OK" for v in s.values()): break
        time.sleep(15)
    pr.end()
log("done")
