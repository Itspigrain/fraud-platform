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
INDEX="ip-threat-intel"
BULK_SIZE=500
TMP_DIR="/tmp/ip-feeds"
NOW=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
EXPIRES=$(date -u -d '+25 hours' +"%Y-%m-%dT%H:%M:%SZ" 2>/dev/null \
       || date -u -v+25H +"%Y-%m-%dT%H:%M:%SZ")

mkdir -p "$TMP_DIR"
BULK_FILE="$TMP_DIR/bulk.ndjson"
RESP=$(mktemp)
cleanup() { rm -f "$RESP"; rm -rf "$TMP_DIR"; }
trap cleanup EXIT
> "$BULK_FILE"

es_bulk() {
  [ -s "$BULK_FILE" ] || return
  http_code=$(curl -s -o "$RESP" -w "%{http_code}" --max-time 30 \
    -u "${ES_USER}:${ES_PASS}" \
    -X POST "${ES_HOST}/${INDEX}/_bulk" \
    -H "Content-Type: application/x-ndjson" \
    --data-binary @"$BULK_FILE")
  if [ "$http_code" -lt 200 ] || [ "$http_code" -gt 299 ]; then
    echo "  WARNING: bulk returned HTTP $http_code"; cat "$RESP"; echo ""
  fi
  > "$BULK_FILE"
}

index_ip() {
  local ip="$1" type="$2" source="$3" score="$4" desc="$5"
  case "$ip" in \#*|""|*[[:space:]]*) return ;; esac
  printf '{"index":{"_id":"%s","_index":"%s"}}\n' "$ip" "$INDEX" >> "$BULK_FILE"
  printf '{"ip":"%s","type":"%s","source":"%s","score":%s,"description":"%s","added_at":"%s","expires_at":"%s"}\n' \
    "$ip" "$type" "$source" "$score" "$desc" "$NOW" "$EXPIRES" >> "$BULK_FILE"
  lines=$(wc -l < "$BULK_FILE")
  [ "$lines" -ge $((BULK_SIZE * 2)) ] && es_bulk
}

echo "[1/4] Tor exits (torproject.org)..."
FILE="$TMP_DIR/tor1.txt"
if curl -sf --max-time 30 "https://check.torproject.org/torbulkexitlist" -o "$FILE"; then
  n=0; while IFS= read -r ip; do index_ip "$ip" "tor_exit" "torproject.org" 85 "Tor exit node"; n=$((n+1)); done < "$FILE"
  echo "  $n IPs"
else echo "  WARNING: torproject.org unreachable, skipping"; fi

echo "[2/4] Tor exits (dan.me.uk)..."
FILE="$TMP_DIR/tor2.txt"
if curl -sf --max-time 30 -A "fraud-platform/1.0" "https://www.dan.me.uk/torlist/?exit" -o "$FILE"; then
  n=0; while IFS= read -r ip; do index_ip "$ip" "tor_exit" "dan.me.uk" 85 "Tor exit node"; n=$((n+1)); done < "$FILE"
  echo "  $n IPs"
else echo "  WARNING: dan.me.uk unreachable, skipping"; fi

echo "[3/4] VPN/proxy IPs (X4BNet)..."
FILE="$TMP_DIR/vpn.txt"
if curl -sf --max-time 60 \
    "https://raw.githubusercontent.com/X4BNet/lists_vpn/main/output/vpn/ipv4.txt" -o "$FILE"; then
  n=0
  while IFS= read -r line; do
    case "$line" in \#*|"") continue ;; esac
    prefix=$(echo "$line" | cut -d'/' -f2)
    [ -n "$prefix" ] && [ "$prefix" -lt 24 ] 2>/dev/null && continue
    index_ip "$(echo "$line" | cut -d'/' -f1)" "vpn" "X4BNet" 65 "Known VPN provider IP"
    n=$((n+1))
  done < "$FILE"
  echo "  $n IPs"
else echo "  WARNING: X4BNet unreachable, skipping"; fi

echo "[4/4] Datacenter IPs (firehol)..."
FILE="$TMP_DIR/dc.txt"
if curl -sf --max-time 60 \
    "https://raw.githubusercontent.com/firehol/blocklist-ipsets/master/datacenters.netset" -o "$FILE"; then
  n=0
  while IFS= read -r line; do
    case "$line" in \#*|"") continue ;; esac
    prefix=$(echo "$line" | cut -d'/' -f2)
    [ -n "$prefix" ] && [ "$prefix" -lt 28 ] 2>/dev/null && continue
    index_ip "$(echo "$line" | cut -d'/' -f1)" "datacenter" "firehol" 40 "Datacenter/hosting IP"
    n=$((n+1))
  done < "$FILE"
  echo "  $n IPs"
else echo "  WARNING: firehol unreachable, skipping"; fi

es_bulk

echo ""
echo "Removing expired entries..."
curl -s -o /dev/null -u "${ES_USER}:${ES_PASS}" \
  -X POST "${ES_HOST}/${INDEX}/_delete_by_query?conflicts=proceed&refresh=true" \
  -H "Content-Type: application/json" \
  -d "{\"query\":{\"range\":{\"expires_at\":{\"lt\":\"${NOW}\"}}}}"
echo "  Done"

echo "Re-executing enrich policy..."
http_code=$(curl -s -o "$RESP" -w "%{http_code}" --max-time 60 \
  -u "${ES_USER}:${ES_PASS}" \
  -X POST "${ES_HOST}/_enrich/policy/ip-threat-intel-policy/_execute")
if [ "$http_code" -ge 200 ] && [ "$http_code" -le 299 ]; then
  echo "  Done"
else
  echo "  WARNING: enrich policy execute returned HTTP $http_code"
  cat "$RESP"; echo ""
fi

echo ""
COUNT=$(curl -s -u "${ES_USER}:${ES_PASS}" \
  "${ES_HOST}/${INDEX}/_count" 2>/dev/null | grep -o '"count":[0-9]*' | cut -d: -f2)
echo "Sync complete at ${NOW}"
echo "Total IPs in threat intel index: ${COUNT:-unknown}"

# ── Patch enrich processor into pipelines now that enrich index exists ────────
patch_pipeline() {
  local name="$1" date_field="$2"
  http_code=$(curl -s -o "$RESP" -w "%{http_code}" --max-time 15 \
    -u "${ES_USER}:${ES_PASS}" \
    -X GET "${ES_HOST}/_ingest/pipeline/${name}")
  # Only patch if enrich processor not already present
  if grep -q "ip-threat-intel-policy" "$RESP" 2>/dev/null; then
    echo "  $name: enrich already present, skipping"
    return
  fi
  echo "  Patching $name with enrich processor..."
  cat > /tmp/pipeline-patch.json << PATCHEOF
{
  "processors": [
    { "date": { "field": "${date_field}", "target_field": "@timestamp", "formats": ["ISO8601"], "ignore_failure": true } },
    { "geoip": { "field": "sourceIp", "target_field": "geoip", "properties": ["country_name","city_name","region_name","location"], "ignore_missing": true, "ignore_failure": true } },
    { "enrich": { "policy_name": "ip-threat-intel-policy", "field": "sourceIp", "target_field": "threat", "ignore_missing": true, "ignore_failure": true } },
    { "remove": { "field": ["host","log","agent","ecs","input","@version"], "ignore_missing": true } }
  ]
}
PATCHEOF
  http_code=$(curl -s -o "$RESP" -w "%{http_code}" --max-time 15 \
    -u "${ES_USER}:${ES_PASS}" \
    -X PUT "${ES_HOST}/_ingest/pipeline/${name}" \
    -H "Content-Type: application/json" \
    --data-binary @/tmp/pipeline-patch.json)
  if [ "$http_code" -ge 200 ] && [ "$http_code" -le 299 ]; then
    echo "    OK"
  else
    echo "    WARNING: patch returned HTTP $http_code"; cat "$RESP"; echo ""
  fi
}

echo "Patching pipelines with enrich processor..."
patch_pipeline "pipeline-events" "eventTime"
patch_pipeline "pipeline-alerts" "detectedAt"
