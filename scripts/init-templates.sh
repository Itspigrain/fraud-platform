#!/bin/sh
set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
if [ -f "${ROOT}/.env" ]; then
  while IFS= read -r line; do
    case "$line" in \#*|""|*[[:space:]]*=*) continue ;; *=*) export "$line" ;; esac
  done < "${ROOT}/.env"
fi
ES_USER="$ES_USERNAME"; ES_PASS="$ES_PASSWORD"
PIPELINE_DIR="${ROOT}/elasticsearch/pipelines"
TEMPLATE_DIR="${ROOT}/elasticsearch/templates"
RESP=$(mktemp); trap 'rm -f "$RESP"' EXIT

req() {
  local m="$1" label="$2" path="$3"; shift 3
  http_code=$(curl -s -o "$RESP" -w "%{http_code}" --max-time 15 \
    -u "${ES_USER}:${ES_PASS}" -X "$m" "${ES_HOST}${path}" \
    -H "Content-Type: application/json" "$@")
  if [ "$http_code" -lt 200 ] || [ "$http_code" -gt 299 ]; then
    echo "FAILED ($http_code): $label"; cat "$RESP"; echo ""; exit 1
  fi
}

echo "Connecting..."; http_code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 10 \
  -u "${ES_USER}:${ES_PASS}" "${ES_HOST}/_cluster/health")
[ "$http_code" = "401" ] && echo "ERROR: bad credentials" && exit 1
echo "  Ready (HTTP $http_code)"; echo ""

echo "[1/6] Pipelines..."
for f in "${PIPELINE_DIR}"/*.json; do
  name=$(basename "$f" .json); printf "  %-40s " "$name"
  req PUT "$name" "/_ingest/pipeline/${name}" --data-binary @"$f"; echo "OK"
done; echo ""

echo "[2/6] IP threat intel index + enrich policy..."
printf "  %-40s " "create index"; req PUT "idx" "/ip-threat-intel" -d '{
  "mappings":{"dynamic":"strict","properties":{"ip":{"type":"ip"},"type":{"type":"keyword"},"source":{"type":"keyword"},"score":{"type":"integer"},"description":{"type":"keyword","index":false},"added_at":{"type":"date"},"expires_at":{"type":"date"}}}}'; echo "OK"
printf "  %-40s " "seed doc"; req PUT "seed" "/ip-threat-intel/_doc/seed" -d '{"ip":"0.0.0.1","type":"placeholder","source":"init","score":0,"description":"seed","added_at":"2026-01-01T00:00:00Z","expires_at":"2026-01-01T01:00:00Z"}'; echo "OK"
printf "  %-40s " "create enrich policy"; req PUT "policy" "/_enrich/policy/ip-threat-intel-policy" -d '{"match":{"indices":"ip-threat-intel","match_field":"ip","enrich_fields":["type","source","score","description"]}}'; echo "OK"
printf "  %-40s " "execute enrich policy"; req POST "execute" "/_enrich/policy/ip-threat-intel-policy/_execute"; echo "OK"
printf "  %-40s " "patch pipeline-events"; req PUT "pe" "/_ingest/pipeline/pipeline-events" -d '{"description":"events","processors":[{"date":{"field":"eventTime","target_field":"@timestamp","formats":["ISO8601"],"ignore_failure":true}},{"geoip":{"field":"sourceIp","target_field":"geoip","properties":["country_name","city_name","region_name","location"],"ignore_missing":true,"ignore_failure":true}},{"enrich":{"policy_name":"ip-threat-intel-policy","field":"sourceIp","target_field":"threat","ignore_missing":true,"ignore_failure":true}},{"script":{"lang":"painless","source":"if(ctx.attributes!=null){if(ctx.attributes.amount!=null)ctx.amount=ctx.attributes.amount;if(ctx.attributes.merchant!=null)ctx.merchant=ctx.attributes.merchant;if(ctx.attributes.category!=null)ctx.category=ctx.attributes.category;}","ignore_failure":true}},{"remove":{"field":["host","log","agent","ecs","input","@version"],"ignore_missing":true}}],"on_failure":[{"set":{"field":"pipeline_error","value":"{{_ingest.on_failure_message}}"}}]}'; echo "OK"
printf "  %-40s " "patch pipeline-alerts"; req PUT "pa" "/_ingest/pipeline/pipeline-alerts" -d '{"description":"alerts","processors":[{"date":{"field":"detectedAt","target_field":"@timestamp","formats":["ISO8601"],"ignore_failure":true}},{"geoip":{"field":"sourceIp","target_field":"geoip","properties":["country_name","city_name","region_name","location"],"ignore_missing":true,"ignore_failure":true}},{"enrich":{"policy_name":"ip-threat-intel-policy","field":"sourceIp","target_field":"threat","ignore_missing":true,"ignore_failure":true}},{"remove":{"field":["host","log","agent","ecs","input","@version"],"ignore_missing":true}}],"on_failure":[{"set":{"field":"pipeline_error","value":"{{_ingest.on_failure_message}}"}}]}'; echo "OK"; echo ""

echo "[3/6] ILM..."; sh "${ROOT}/scripts/init-ilm.sh"; echo ""

echo "[4/6] Index templates..."
for f in "${TEMPLATE_DIR}"/*.json; do
  name=$(basename "$f" .json); printf "  %-45s " "$name"
  req PUT "$name" "/_index_template/${name}" --data-binary @"$f"; echo "OK"
done; echo ""

echo "[5/6] Role..."
req PUT "role" "/_security/role/fraud_app_role" -d '{"cluster":["monitor"],"indices":[{"names":["events-*","alerts-*","audit-*","application-logs-*","alert-notifications*","ip-threat-intel",".enrich-ip-threat-intel-policy-*"],"privileges":["create_index","index","read","view_index_metadata","manage_ilm"]}]}'
echo "  OK"; echo ""

echo "[6/6] Done. Run: bash scripts/sync-ip-feeds.sh"
