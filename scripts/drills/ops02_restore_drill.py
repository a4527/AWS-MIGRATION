# OPS-02: Aurora PITR restore to a separate cluster, verify DB rows against S3
# objects and hashes, measure RTO/RPO, then delete the restored cluster.
import json, subprocess, time, datetime, os, sys, hashlib
env = dict(os.environ, AWS_PROFILE="bootcamp-tf")
OUT = sys.argv[1]
def aws(*a, check=True):
    r = subprocess.run(["aws", *a, "--output", "json"], env=env, capture_output=True, text=True)
    if check and r.returncode: raise RuntimeError(r.stderr[:500])
    return json.loads(r.stdout) if r.stdout.strip() else {}
def now(): return datetime.datetime.now(datetime.timezone.utc)
def log(*m):
    line = now().strftime("%H:%M:%SZ") + " " + " ".join(str(x) for x in m)
    print(line, flush=True); open(OUT, "a").write(line + "\n")
SRC, DST = "fileshare-dev-aurora", "fileshare-dev-aurora-restore"
OWNER = [{"Key": "Owner", "Value": sys.argv[2]}, {"Key": "Project", "Value": "fileshare"}, {"Key": "Purpose", "Value": "OPS-02 restore drill"}]
ECS_TAGS = [{"key": t["Key"], "value": t["Value"]} for t in OWNER]
RESUME = "--resume" in sys.argv
src = aws("rds", "describe-db-clusters", "--db-cluster-identifier", SRC)["DBClusters"][0]
if RESUME:
    # Continue verification for a restore cluster that already exists; RTO then
    # starts at the cluster creation time.
    t0 = datetime.datetime.fromisoformat(aws("rds", "describe-db-clusters", "--db-cluster-identifier", DST)["DBClusters"][0]["ClusterCreateTime"]).timestamp()
if not RESUME:
    latest = src["LatestRestorableTime"]; t_inc = now()
    log("incident time (assumed now):", t_inc.isoformat()[:19], "latest restorable:", latest[:19])
    rpo = (t_inc - datetime.datetime.fromisoformat(latest)).total_seconds()
    log(f"RPO at incident: {rpo:.0f}s (data newer than LatestRestorableTime would be lost)")
    t0 = time.time()
    aws("rds", "restore-db-cluster-to-point-in-time", "--source-db-cluster-identifier", SRC, "--db-cluster-identifier", DST,
    "--use-latest-restorable-time", "--db-subnet-group-name", src["DBSubnetGroup"],
    "--vpc-security-group-ids", *[g["VpcSecurityGroupId"] for g in src["VpcSecurityGroups"]],
    "--serverless-v2-scaling-configuration", "MinCapacity=0.5,MaxCapacity=2",
    "--no-deletion-protection", "--tags", json.dumps(OWNER))
    log("restore-db-cluster-to-point-in-time started")
    while aws("rds", "describe-db-clusters", "--db-cluster-identifier", DST)["DBClusters"][0]["Status"] != "available": time.sleep(20)
    log(f"cluster available after {time.time()-t0:.0f}s")
    aws("rds", "create-db-instance", "--db-instance-identifier", DST + "-1", "--db-cluster-identifier", DST,
    "--db-instance-class", "db.serverless", "--engine", "aurora-postgresql", "--tags", json.dumps(OWNER))
    while aws("rds", "describe-db-instances", "--db-instance-identifier", DST + "-1")["DBInstances"][0]["DBInstanceStatus"] != "available": time.sleep(20)
endpoint = aws("rds", "describe-db-clusters", "--db-cluster-identifier", DST)["DBClusters"][0]["Endpoint"]
if not RESUME: log(f"instance available after {time.time()-t0:.0f}s; endpoint ready")
# one-off psql task in the app subnets/SG, password from the same SSM parameter
svc = aws("ecs", "describe-services", "--cluster", "fileshare-dev-ecs", "--services", "fileshare-dev-backend")["services"][0]
td = aws("ecs", "describe-task-definition", "--task-definition", svc["taskDefinition"], "--include", "TAGS")
c = td["taskDefinition"]["containerDefinitions"][0]
pw_arn = [s["valueFrom"] for s in c["secrets"] if s["name"] == "DB_PASSWORD"][0]
user = [e["value"] for e in c["environment"] if e["name"] == "DB_USERNAME"][0]
dbname = [e["value"] for e in c["environment"] if e["name"] == "DB_URL"][0].rsplit("/", 1)[1]
live_host = [e["value"] for e in c["environment"] if e["name"] == "DB_URL"][0].split("//")[1].split(":")[0]
sql = ("select 'COUNT', count(*), count(*) filter (where status<>'DELETED') from files; "
       "select 'ROW', id, storage_path, status, scan_status, size from files order by id;")
spec = {"family": "fileshare-dev-dbcheck", "networkMode": "awsvpc", "requiresCompatibilities": ["FARGATE"], "cpu": "256", "memory": "512",
        "executionRoleArn": td["taskDefinition"]["executionRoleArn"], "tags": ECS_TAGS,
        "containerDefinitions": [{"name": "psql", "image": "public.ecr.aws/docker/library/postgres:16-alpine", "essential": True,
            "command": ["sh", "-c", f"for H in $RESTORE_HOST $LIVE_HOST; do echo \"== $H\"; psql -h $H -U {user} -d {dbname} -At -F '|' -c \"{sql}\"; done"],
            "environment": [{"name": "RESTORE_HOST", "value": endpoint}, {"name": "LIVE_HOST", "value": live_host}, {"name": "PGSSLMODE", "value": "require"}],
            "secrets": [{"name": "PGPASSWORD", "valueFrom": pw_arn}],
            "logConfiguration": {"logDriver": "awslogs", "options": {"awslogs-group": "/ecs/fileshare-dev-backend", "awslogs-region": "us-east-1", "awslogs-stream-prefix": "dbcheck"}}}]}
open(OUT + ".td.json", "w").write(json.dumps(spec))
tdarn = aws("ecs", "register-task-definition", "--cli-input-json", f"file://{OUT}.td.json")["taskDefinition"]["taskDefinitionArn"]
net = svc["networkConfiguration"]["awsvpcConfiguration"]
task = aws("ecs", "run-task", "--cluster", "fileshare-dev-ecs", "--launch-type", "FARGATE", "--task-definition", tdarn,
           "--network-configuration", json.dumps({"awsvpcConfiguration": {"subnets": net["subnets"], "securityGroups": net["securityGroups"], "assignPublicIp": "DISABLED"}}),
           "--tags", json.dumps(ECS_TAGS))["tasks"][0]["taskArn"]
while True:
    t = aws("ecs", "describe-tasks", "--cluster", "fileshare-dev-ecs", "--tasks", task)["tasks"][0]
    if t["lastStatus"] == "STOPPED": break
    time.sleep(10)
log("psql task exit", t["containers"][0].get("exitCode"), t.get("stoppedReason", "")[:80])
time.sleep(10)
stream = "dbcheck/psql/" + task.split("/")[-1]
lines = [e["message"] for e in aws("logs", "get-log-events", "--log-group-name", "/ecs/fileshare-dev-backend", "--log-stream-name", stream, "--start-from-head")["events"]]
rto = time.time() - t0
sections, cur = {}, None
for l in lines:
    if l.startswith("== "): cur = "restore" if l[3:] == endpoint else "live"; sections[cur] = []
    elif cur: sections[cur].append(l)
for k in ("restore", "live"):
    cnt = [l for l in sections.get(k, []) if l.startswith("COUNT")]
    log(f"{k}: {cnt[0] if cnt else 'no result'}")
rows = [l.split("|") for l in sections.get("restore", []) if l.startswith("ROW")]
live_rows = {l.split("|")[1]: l.split("|") for l in sections.get("live", []) if l.startswith("ROW")}
bucket = [e["value"] for e in c["environment"] if e["name"] == "S3_BUCKET"][0]
missing, size_mismatch, checked = 0, 0, 0
for r in rows:
    _, fid, path, status, scan, size = r
    h = aws("s3api", "head-object", "--bucket", bucket, "--key", path, check=False)
    checked += 1
    if not h: missing += 1; log("  missing object", fid, path, status); continue
    if str(h.get("ContentLength")) != size: size_mismatch += 1; log("  size mismatch", fid, path, size, h.get("ContentLength"))
log(f"restored rows={len(rows)} checked objects={checked} missing={missing} size_mismatch={size_mismatch}")
newer = [fid for fid in live_rows if fid not in {r[1] for r in rows}]
log(f"rows in live but not in restore (lost if this were a real incident): {newer}")
# sample hash: download first non-deleted object twice is meaningless; compare against the object's own ETag (single-part MD5)
sample = [r for r in rows if r[3] != "DELETED"][:3]
for r in sample:
    p = OUT + ".obj"
    subprocess.run(["aws", "s3", "cp", f"s3://{bucket}/{r[2]}", p, "--quiet"], env=env, check=True)
    md5 = hashlib.md5(open(p, "rb").read()).hexdigest(); sha = hashlib.sha256(open(p, "rb").read()).hexdigest()[:16]
    etag = aws("s3api", "head-object", "--bucket", bucket, "--key", r[2])["ETag"].strip('"')
    log(f"  hash id={r[1]} md5==etag {md5 == etag} sha256={sha}...")
log(f"RTO (restore start -> verified): {rto:.0f}s ({rto/60:.1f} min)")
aws("ecs", "deregister-task-definition", "--task-definition", tdarn)
if "--keep" not in sys.argv:
    aws("rds", "delete-db-instance", "--db-instance-identifier", DST + "-1", "--skip-final-snapshot")
    while aws("rds", "describe-db-instances", "--db-instance-identifier", DST + "-1", check=False): time.sleep(20)
    aws("rds", "delete-db-cluster", "--db-cluster-identifier", DST, "--skip-final-snapshot")
    log("restored cluster deletion requested")
log("done")
