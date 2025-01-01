#!/bin/bash
# Deploy using AWS CloudFormation
# Copyright 2025 austin

set -eo pipefail

# Must Exist Beforehand
TEMPLATE_FILE="template.yaml"
S3_BUCKET="deployment.testing.bucket"
# Will Be Created
PACKAGED_TEMPLATE="deployed-template.yaml"
STACK_NAME="CloudEventTrace"
REGION="us-east-1"

#######################################
# Build src directory
# Globals:
#   TEMPLATE_FILE
# Arguments:
#  None
#######################################
function build_source() {
  echo "Building Package $SOURCE..."
  mvn clean package
  mvn verify
  echo "Build succeeded."
}

#######################################
# Validate against cloud formation scheme, not fool-proof!!
# Globals:
#   TEMPLATE_FILE
# Arguments:
#  None
#######################################
function validate_template() {
  echo "Validating template $TEMPLATE_FILE ..."
  aws cloudformation validate-template \
    --template-body "file://$TEMPLATE_FILE"
  echo "Template validation succeeded."
}

#######################################
# Uploads Artifacts to S3 and replaces references with resource paths
# Globals:
#   PACKAGED_TEMPLATE
#   S3_BUCKET
#   TEMPLATE_FILE
# Arguments:
#  None
#######################################
function package_template() {
  echo "Packaging template $TEMPLATE_FILE ..."
  aws cloudformation package \
    --template-file "$TEMPLATE_FILE" \
    --s3-bucket "$S3_BUCKET" \
    --output-template-file "$PACKAGED_TEMPLATE"
  echo "Packaged template output: $PACKAGED_TEMPLATE"
}

#######################################
# Deploys the stack to the specified region
# Globals:
#   PACKAGED_TEMPLATE
#   REGION
#   STACK_NAME
# Arguments:
#  None
#######################################
function deploy_stack() {
  echo "Deploying stack '$STACK_NAME' to region '$REGION' ..."
  aws cloudformation deploy \
    --template-file "$PACKAGED_TEMPLATE" \
    --stack-name "$STACK_NAME" \
    --capabilities CAPABILITY_IAM \
    --region "$REGION"
  echo "Deployment of stack '$STACK_NAME' complete!"
}

#######################################
# Displays the generated Lambda URL and Lambda ARN
# Globals:
#   STACK_NAME
# Arguments:
#  None
#######################################
function print_stack_outputs() {
  echo "Retrieving outputs for stack '$STACK_NAME' ..."
  aws cloudformation describe-stacks \
    --stack-name "$STACK_NAME" \
    --query "Stacks[0].Outputs" \
    --output table
}

#######################################
# description
# Globals:
#   STACK_NAME
# Arguments:
#  None
#######################################
function create_node_json() {
  aws cloudformation describe-stacks \
    --stack-name "$STACK_NAME" \
    --query "Stacks[0].Outputs" \
    --output json \
    | jq --arg platform "AWS" \
      --arg node_type "worker" \
      --arg tcp_port "8080" \
      --arg ssh_port "22" \
      --argjson num_cores 4 \
      --argjson weight 1 '(
        {
          "id": (.[] | select(.OutputKey == "FunctionArn").OutputValue),
          "host": (.[] | select(.OutputKey == "FunctionUrl").OutputValue),
        }
        +
        $ARGS.named
      )' >node.json
  cat node.json
}

#######################################
# If issues delete and start over
# Globals:
#   STACK_NAME
# Arguments:
#  None
#######################################
function delete_previous_stack() {
  aws cloudformation delete-stack --stack-name $STACK_NAME
}

#######################################
# MAIN
# Arguments:
#  None
#######################################
function main() {
  build_source
  validate_template
  package_template
  deploy_stack
  print_stack_outputs
  create_node_json
}

main "$@"
