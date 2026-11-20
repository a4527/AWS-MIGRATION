#!/usr/bin/env bash
# Deploy a task definition revision to an ECS service and wait for the rollout
# (CICD-02/03). Fails when the circuit breaker rolls the deployment back or the
# rollout does not finish in time.
#   scripts/ecs-deploy.sh <service> <task-definition-arn>
set -euo pipefail

SERVICE=${1:?service}
REVISION=${2:?task definition arn}
CLUSTER=${ECS_CLUSTER:?ECS_CLUSTER}
TIMEOUT=${DEPLOY_TIMEOUT_SECONDS:-900}

# shellcheck disable=SC2016  # JMESPath literal, not a shell expansion
DEPLOYMENT=$(aws ecs update-service --cluster "$CLUSTER" --service "$SERVICE" \
  --task-definition "$REVISION" --query 'service.deployments[?status==`PRIMARY`].id|[0]' --output text)
echo "deploying ${REVISION##*/} to $SERVICE (deployment $DEPLOYMENT)"

start=$(date +%s)
while :; do
  STATE=$(aws ecs describe-services --cluster "$CLUSTER" --services "$SERVICE" \
    --query "services[0].deployments[?id=='$DEPLOYMENT'].rolloutState|[0]" --output text)
  case "$STATE" in
    COMPLETED)
      echo "rollout completed in $(( $(date +%s) - start ))s"; exit 0 ;;
    FAILED|None)
      # The circuit breaker marks the deployment FAILED and replaces it with a
      # rollback deployment, so the original id may disappear (None).
      echo "::error::$SERVICE deployment $DEPLOYMENT failed ($STATE); circuit breaker rolled back."
      aws ecs describe-services --cluster "$CLUSTER" --services "$SERVICE" \
        --query 'services[0].events[:5].message' --output text || true
      exit 1 ;;
  esac
  if [ $(( $(date +%s) - start )) -ge "$TIMEOUT" ]; then
    echo "::error::$SERVICE rollout did not complete in ${TIMEOUT}s (state $STATE)."; exit 1
  fi
  sleep 15
done
