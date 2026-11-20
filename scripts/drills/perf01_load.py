# PERF-01: run k6 in an ECS task; record k6 summary, task count timeline, scaling activities.
import json, subprocess, time, datetime, os, sys, base64
env = dict(os.environ, AWS_PROFILE="bootcamp-tf")
OUT, MODE, OWNER = sys.argv[1], sys.argv[2], sys.argv[3]   # MODE: baseline | scaling
def aws(*a, check=True):
    r = subprocess.run(["aws", *a, "--output", "json"], env=env, capture_output=True, text=True)
    if check and r.returncode: raise RuntimeError(r.stderr[:400])
    return json.loads(r.stdout) if r.stdout.strip() else {}
def now(): return datetime.datetime.now(datetime.timezone.utc)
def log(*m):
    line = now().strftime("%H:%M:%SZ") + " " + " ".join(str(x) for x in m); print(line, flush=True); open(OUT, "a").write(line + "\n")
CL, SV, RID = "fileshare-dev-ecs", "fileshare-dev-backend", "service/fileshare-dev-ecs/fileshare-dev-backend"
maxcap = "1" if MODE == "baseline" else "4"
aws("application-autoscaling", "register-scalable-target", "--service-namespace", "ecs", "--resource-id", RID,
    "--scalable-dimension", "ecs:service:DesiredCount", "--min-capacity", "1", "--max-capacity", maxcap)
log(f"=== PERF-01 {MODE}: scalable target min 1 max {maxcap}")
svc = aws("ecs", "describe-services", "--cluster", CL, "--services", SV)["services"][0]
td = aws("ecs", "describe-task-definition", "--task-definition", svc["taskDefinition"])["taskDefinition"]
alb = aws("elbv2", "describe-load-balancers", "--names", "fileshare-dev-alb")["LoadBalancers"][0]["DNSName"]
script = base64.b64encode(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "perf01_load.js"), "rb").read()).decode()
tags = [{"key": "Owner", "value": OWNER}, {"key": "Project", "value": "fileshare"}, {"key": "Purpose", "value": "PERF-01"}]
spec = {"family": "fileshare-dev-k6", "networkMode": "awsvpc", "requiresCompatibilities": ["FARGATE"], "cpu": "1024", "memory": "2048",
        "executionRoleArn": td["executionRoleArn"], "tags": tags,
        "containerDefinitions": [{"name": "k6", "image": "grafana/k6:0.54.0", "essential": True, "entryPoint": ["sh", "-c"],
            "command": ["echo $SCRIPT | base64 -d > /tmp/load.js && k6 run --quiet /tmp/load.js"],
            "environment": [{"name": "SCRIPT", "value": script}, {"name": "BASE_URL", "value": f"http://{alb}"}, {"name": "VUS", "value": "40"}],
            "logConfiguration": {"logDriver": "awslogs", "options": {"awslogs-group": "/ecs/fileshare-dev-backend", "awslogs-region": "us-east-1", "awslogs-stream-prefix": "k6"}}}]}
open(OUT + ".td.json", "w").write(json.dumps(spec))
tdarn = aws("ecs", "register-task-definition", "--cli-input-json", f"file://{OUT}.td.json")["taskDefinition"]["taskDefinitionArn"]
net = svc["networkConfiguration"]["awsvpcConfiguration"]
start = now()
task = aws("ecs", "run-task", "--cluster", CL, "--launch-type", "FARGATE", "--task-definition", tdarn, "--tags", json.dumps(tags),
           "--network-configuration", json.dumps({"awsvpcConfiguration": {"subnets": net["subnets"], "securityGroups": net["securityGroups"], "assignPublicIp": "DISABLED"}}))["tasks"][0]["taskArn"]
log("k6 task started")
prev = None
while True:
    t = aws("ecs", "describe-tasks", "--cluster", CL, "--tasks", task)["tasks"][0]
    s = aws("ecs", "describe-services", "--cluster", CL, "--services", SV)["services"][0]
    cur = (s["desiredCount"], s["runningCount"])
    if cur != prev: log(f"  backend desired={cur[0]} running={cur[1]}"); prev = cur
    if t["lastStatus"] == "STOPPED": break
    time.sleep(15)
end = now()
log("k6 exit", t["containers"][0].get("exitCode"))
time.sleep(10)
ev = aws("logs", "get-log-events", "--log-group-name", "/ecs/fileshare-dev-backend", "--log-stream-name", "k6/k6/" + task.split("/")[-1], "--start-from-head")["events"]
for e in ev:
    if "PERF_SUMMARY" in e["message"]: log("  " + e["message"].strip())
for a in aws("application-autoscaling", "describe-scaling-activities", "--service-namespace", "ecs", "--resource-id", RID)["ScalingActivities"]:
    if a["StartTime"] >= start.isoformat():
        log(f"  scaling {a['StartTime'][11:19]} -> {a.get('EndTime','')[11:19]} {a['StatusCode']} {a['Description'][:90]}")
for metric, ns, dims in [("CPUUtilization", "AWS/ECS", [f"Name=ClusterName,Value={CL}", f"Name=ServiceName,Value={SV}"]),
                         ("RunningTaskCount", "ECS/ContainerInsights", [f"Name=ClusterName,Value={CL}", f"Name=ServiceName,Value={SV}"])]:
    dp = aws("cloudwatch", "get-metric-statistics", "--namespace", ns, "--metric-name", metric, "--dimensions", *dims,
             "--start-time", start.isoformat(), "--end-time", end.isoformat(), "--period", "60", "--statistics", "Maximum")["Datapoints"]
    dp.sort(key=lambda x: x["Timestamp"])
    log(f"  {metric} max/min: " + " ".join(f"{d['Timestamp'][11:16]}={d['Maximum']:.0f}" for d in dp))
aws("ecs", "deregister-task-definition", "--task-definition", tdarn)
log("window", start.strftime("%H:%M:%SZ"), "->", end.strftime("%H:%M:%SZ"))
