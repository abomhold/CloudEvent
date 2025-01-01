#!/bin/bash

# Global variable to store intermediate results
result=""

# Function to encode message
function encode_message() {
  local msg="$1"
  local shift="$2"
  local endpoint="$3"

  # Create JSON payload
  local json=$(jq \
    --arg msg "$msg" \
    --arg shift "$shift" \
    -n '{
            "msg": $msg,
            "shift": $shift
        }')

  echo "Sending request to $endpoint:"
  echo "$json"

  # Send request and capture output
  echo "Making request..."
  local response
  response=$(curl -s -H "Content-Type: application/json" -X POST -d "$json" "$endpoint")

  # Check if response is empty
  if test -z "$response"; then
    echo "Error: Empty response received"
    exit 1
  fi

  # Extract the value from response using jq
  result=$(echo "$response" | jq -r '.value // empty')

  # Check if value was extracted using test command
  if test -z "$result"; then
    echo "Error: Failed to extract value from response"
    echo "Raw response: $response"
    exit 1
  fi

  echo "Response received:"
  echo "$result"
  return 0
}

#######################################
# Main function that orchestrates the encryption process
# by calling two encryption services in sequence
# Globals:
#   result - stores the intermediate encryption result
# Arguments:
#   None
# Returns:
#   None
#######################################
function main() {
  # Input message and shift value
  local message="ServerlessComputingWithFaaSInspector"
  local shift="42"

  # First endpoint
  local endpoint1="https://3jqfwbarn3.execute-api.us-east-2.amazonaws.com/test-deploy/"

  echo "Calling encryption service..."
  encode_message "$message" "$shift" "$endpoint1"

  # Store the intermediate result
  local first_result="$result"

  # Second endpoint
  local endpoint2="https://sfutojhi63.execute-api.us-east-2.amazonaws.com/test-deploy"

  echo "Calling decryption service..."
  encode_message "$first_result" "$shift" "$endpoint2"
}

# Execute main function with any provided arguments
main "$@"
