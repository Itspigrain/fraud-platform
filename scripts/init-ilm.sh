#!/bin/sh
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
ENV_FILE="${ROOT}/.env"

if [ -f "$ENV_FILE" ]; then
  while IFS= read -r line; do
    case "$line" in \#*|""|*[[:space:]]*=*) continue ;; *=*) export "$line" ;; esac
  done < "$ENV_FILE"
fi

ES_USER="$ES_USERNAME"
ES_PASS="$ES_PASSWORD"

RESP=$(mktemp)
cleanup() { rm -f "$RESP"; }
trap cleanup EXIT

es_put() {
  local label="$1" path="$2" body="$3"
  http_code=$(curl -s -o "$RESP" -w "%{http_code}" --max-time 15 \
    -u "${ES_USER}:${ES_PASS}" \
    -X PUT "${ES_HOST}${path}" \
    -H "Content-Type: application/json" \
    -d "$body")
  if [ "$http_code" -lt 200 ] || [ "$http_code" -gt 299 ]; then
    echo "FAILED (HTTP $http_code): $label"; cat "$RESP"; echo ""; exit 1
  fi
  printf "  %-45s OK\n" "$label"
}

es_put "fraud-events-policy (90d)"  "/_ilm/policy/fraud-events-policy" '{
  "policy":{"phases":{
    "hot":    {"min_age":"0ms","actions":{"rollover":{"max_age":"30d","max_primary_shard_size":"10gb"}}},
    "warm":   {"min_age":"30d","actions":{"shrink":{"number_of_shards":1},"forcemerge":{"max_num_segments":1}}},
    "delete": {"min_age":"90d","actions":{"delete":{}}}
  }}}'

es_put "fraud-alerts-policy (180d)" "/_ilm/policy/fraud-alerts-policy" '{
  "policy":{"phases":{
    "hot":    {"min_age":"0ms","actions":{"rollover":{"max_age":"30d"}}},
    "warm":   {"min_age":"60d","actions":{"forcemerge":{"max_num_segments":1}}},
    "delete": {"min_age":"180d","actions":{"delete":{}}}
  }}}'

es_put "fraud-audit-policy (7yr)"   "/_ilm/policy/fraud-audit-policy" '{
  "policy":{"phases":{
    "hot":    {"min_age":"0ms","actions":{"rollover":{"max_age":"30d"}}},
    "cold":   {"min_age":"90d","actions":{"readonly":{}}},
    "delete": {"min_age":"2555d","actions":{"delete":{}}}
  }}}'
