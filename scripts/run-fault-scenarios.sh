#!/usr/bin/env bash
set -euo pipefail

api_base="${API_BASE_URL:-http://localhost:8080}"

for command_name in curl jq; do
  if ! command -v "${command_name}" >/dev/null 2>&1; then
    echo "Required command not found: ${command_name}" >&2
    exit 1
  fi
done

configure_provider() {
  local mode="$1"
  curl --fail --silent --show-error \
    --request PUT "${api_base}/api/v1/simulator/provider" \
    --header 'Content-Type: application/json' \
    --data "{\"mode\":\"${mode}\"}" >/dev/null
}

create_payment() {
  local scenario="$1"
  local scenario_key
  scenario_key="$(printf '%s' "${scenario}" | tr '[:upper:]' '[:lower:]')"
  local key="fault-${scenario_key}-$(date +%s)-${RANDOM}"
  curl --fail --silent --show-error \
    --request POST "${api_base}/api/v1/payments" \
    --header 'Content-Type: application/json' \
    --header 'X-Merchant-Id: fault-lab' \
    --header "Idempotency-Key: ${key}" \
    --data '{"amount":42.50,"currency":"USD"}' \
    | jq --raw-output '.id'
}

wait_for_status() {
  local payment_id="$1"
  local expected_status="$2"
  local attempts=0
  while (( attempts < 80 )); do
    local status
    status="$(curl --fail --silent --show-error \
      "${api_base}/api/v1/payments/${payment_id}" | jq --raw-output '.status')"
    if [[ "${status}" == "${expected_status}" ]]; then
      return 0
    fi
    attempts=$((attempts + 1))
    sleep 0.25
  done
  echo "Payment ${payment_id} did not reach ${expected_status}" >&2
  return 1
}

wait_for_attempts() {
  local expected_attempts="$1"
  local attempts=0
  while (( attempts < 80 )); do
    local observed
    observed="$(curl --fail --silent --show-error \
      "${api_base}/api/v1/simulator/provider" | jq '.attempts')"
    if (( observed >= expected_attempts )); then
      return 0
    fi
    attempts=$((attempts + 1))
    sleep 0.25
  done
  echo "Provider did not record ${expected_attempts} attempts" >&2
  return 1
}

assert_attempts() {
  local expected="$1"
  local observed
  observed="$(curl --fail --silent --show-error \
    "${api_base}/api/v1/simulator/provider" | jq '.attempts')"
  if [[ "${observed}" != "${expected}" ]]; then
    echo "Expected ${expected} provider attempts but observed ${observed}" >&2
    exit 1
  fi
}

run_final_decision_scenario() {
  local mode="$1"
  local expected_status="$2"
  local expected_attempts="$3"
  configure_provider "${mode}"
  local payment_id
  payment_id="$(create_payment "${mode}")"
  wait_for_status "${payment_id}" "${expected_status}"
  assert_attempts "${expected_attempts}"
  echo "PASS ${mode}: ${payment_id} -> ${expected_status} (${expected_attempts} attempt(s))"
}

run_unresolved_scenario() {
  local mode="$1"
  configure_provider "${mode}"
  local payment_id
  payment_id="$(create_payment "${mode}")"
  wait_for_attempts 3
  assert_attempts 3
  wait_for_status "${payment_id}" "RECEIVED"
  echo "PASS ${mode}: ${payment_id} remains RECEIVED after the bounded retry budget"
}

run_timeout_scenario() {
  configure_provider TIMEOUT
  local payment_id
  payment_id="$(create_payment TIMEOUT)"
  wait_for_attempts 1
  sleep 4
  wait_for_status "${payment_id}" "RECEIVED"

  local observed_attempts
  local successful_authorizations
  observed_attempts="$(curl --fail --silent --show-error \
    "${api_base}/api/v1/simulator/provider" | jq '.attempts')"
  successful_authorizations="$(curl --fail --silent --show-error \
    "${api_base}/api/v1/simulator/provider" | jq '.successfulAuthorizations')"
  if (( observed_attempts < 1 || observed_attempts > 3 )); then
    echo "Expected one to three bounded timeout attempts but observed ${observed_attempts}" >&2
    exit 1
  fi
  if [[ "${successful_authorizations}" != "1" ]]; then
    echo "Expected one idempotent provider effect but observed ${successful_authorizations}" >&2
    exit 1
  fi
  echo "PASS TIMEOUT: ${payment_id} remains RECEIVED while the provider applied one idempotent effect"
}

run_final_decision_scenario HEALTHY AUTHORIZED 1
run_final_decision_scenario TRANSIENT_THEN_SUCCESS AUTHORIZED 3
run_final_decision_scenario DECLINE DECLINED 1
run_unresolved_scenario UNAVAILABLE
run_timeout_scenario

echo "All fault scenarios passed. Exhausted events are available on payments.received.v1.dlt."
