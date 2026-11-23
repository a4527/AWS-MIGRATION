#!/usr/bin/env bash
# BACKUP-01: run the one-off pg_dump task and wait for the dump in S3.
#   AWS_PROFILE=bootcamp-tf scripts/db-backup.sh
# Needs the lab layer (terraform/environments/dev) to be up. The dump lands in
# the file bucket under backups/db/ and replication copies it to the personal
# account. Exits non-zero if either container fails or the object is missing.
set -euo pipefail

TF_DIR=$(cd "$(dirname "$0")/../terraform/environments/dev" && pwd)
REGION=${AWS_REGION:-us-east-1}

out() { terraform -chdir="$TF_DIR" output -raw "$1"; }
CLUSTER=$(out ecs_cluster_name)
FAMILY=$(out db_backup_task_definition_family)
SG=$(out db_backup_security_group_id)
PREFIX=$(out db_backup_object_prefix)
BUCKET=$(out file_bucket_name)
SUBNETS=$(terraform -chdir="$TF_DIR" output -json private_subnet_ids | jq -r 'join(",")')

KEY="${PREFIX}$(date -u +%Y%m%dT%H%M%SZ).dump"
OVERRIDES=$(jq -nc --arg key "$KEY" \
  '{containerOverrides: [{name: "upload", environment: [{name: "DUMP_KEY", value: $key}]}]}')

START=$(date +%s)
TASK=$(aws ecs run-task --region "$REGION" --cluster "$CLUSTER" --task-definition "$FAMILY" \
  --launch-type FARGATE --count 1 --propagate-tags TASK_DEFINITION \
  --network-configuration "awsvpcConfiguration={subnets=[$SUBNETS],securityGroups=[$SG],assignPublicIp=DISABLED}" \
  --overrides "$OVERRIDES" --query 'tasks[0].taskArn' --output text)
echo "task: ${TASK##*/}  key: $KEY"

aws ecs wait tasks-stopped --region "$REGION" --cluster "$CLUSTER" --tasks "$TASK"
RESULT=$(aws ecs describe-tasks --region "$REGION" --cluster "$CLUSTER" --tasks "$TASK" \
  --query 'tasks[0].{reason: stoppedReason, containers: containers[].{name: name, exit: exitCode, reason: reason}}' --output json)
echo "$RESULT" | jq -c .
echo "elapsed: $(( $(date +%s) - START ))s"

if ! echo "$RESULT" | jq -e '[.containers[].exit] | all(. == 0)' > /dev/null; then
  echo "BACKUP FAIL: a container did not exit 0. Logs: /ecs/${FAMILY}" >&2
  exit 1
fi

aws s3api head-object --region "$REGION" --bucket "$BUCKET" --key "$KEY" \
  --query '{bytes: ContentLength, versionId: VersionId, replication: ReplicationStatus}' --output json
echo "BACKUP OK s3://$BUCKET/$KEY"
