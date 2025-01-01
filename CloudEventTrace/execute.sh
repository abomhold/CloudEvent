#!/bin/bash
# Invoke AWS Lambda URL 5 times in parallel and collect CloudWatch logs
# Copyright 2025 austin

set -eo pipefail

#######################################
# Defaults / Configurable
#######################################
NODE_JSON="node.json" # Generated by your deployment script
REGION="us-east-1"    # Default region
STACK_NAME="CloudEventTrace"
PAYLOAD=$(cat ./events/payload.json)

#######################################
# Reads node.json to retrieve:
#   - The Lambda function ARN -> used to parse the function name
#   - The Lambda function URL -> invoked 5 times in parallel
# Globals:
#   NODE_JSON
# Arguments:
#  None
#######################################
function load_node_info() {
  echo "Reading Lambda info from $NODE_JSON ..."
  LAMBDA_ARN=$(jq -r '.id' "$NODE_JSON")
  LAMBDA_URL=$(jq -r '.host' "$NODE_JSON")
  echo "  ARN: $LAMBDA_ARN"
  echo "  URL: $LAMBDA_URL"

  # Extract function name from the ARN (the portion after "function:")
  # Example ARN: arn:aws:lambda:us-east-1:123456789012:function:MyLambdaFunction
  FUNCTION_NAME=$(echo "$LAMBDA_ARN" | awk -F: '{print $NF}')
  LOG_GROUP="/aws/$STACK_NAME"

  echo "  Function name: $FUNCTION_NAME"
  echo "  CloudWatch Log Group: $LOG_GROUP"

  # Export so other functions can reference them
  export LAMBDA_ARN LAMBDA_URL FUNCTION_NAME LOG_GROUP
}

#######################################
# Invokes the Lambda function URL 5 times in parallel.
# Globals:
#   LAMBDA_URL
# Arguments:
#  None
#######################################
function invoke_lambda_parallel() {
  echo "Invoking $LAMBDA_URL 5 times in parallel..."
  for i in {1..5}; do
    curl -s -H "Content-Type: application/json" -X POST -d "$PAYLOAD" "$LAMBDA_URL" &
  done
  wait
  echo "All parallel requests completed."
}

#######################################
# Retrieves the most recent logs from CloudWatch for the deployed Lambda function.
# Globals:
#   LOG_GROUP
#   REGION
# Arguments:
#  None
#######################################
function collect_logs() {
  echo "Retrieving recent CloudWatch logs from $LOG_GROUP ..."
  # Adjust --limit, --start-time, or filter patterns as needed
  aws logs filter-log-events \
    --log-group-name "$LOG_GROUP" \
    --region "$REGION" \
    --limit 50 \
    --output json
}

#######################################
# MAIN
#######################################
function main() {
  load_node_info
  invoke_lambda_parallel
  collect_logs
}

main "$@"
