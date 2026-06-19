#!/bin/bash
set -e

API="http://localhost:8080/events"
METRICS_URL="http://localhost:8080/actuator/metrics"
CT="Content-Type: application/json"
TOTAL=${1:-3000}
PARALLEL=20

# Delimited strings (arrays don't survive export -f into subshells)
S_TENANTS="tenant-1|tenant-1|tenant-1|tenant-2|tenant-2|tenant-3"
S_EVENT_TYPES="purchase|purchase|purchase|login|login|payment|payment|transfer|withdrawal|refund|password_change|card_added|account_creation"
S_MERCHANTS="Amazon|BestBuy|Walmart|Target|Costco|Stripe|PayPal|Shopify|eBay|Etsy|Nordstrom|HomeDepot|Apple|Nike|Uber|DoorDash|Netflix|Spotify|Airbnb|Expedia"
S_CATEGORIES="electronics|retail|food|grocery|luxury|crypto|subscription|travel|property|clothing|entertainment|gaming|health|automotive|home"
S_NAMES="alice|bob|charlie|diana|edward|frank|grace|henry|ivan|julia|kevin|laura|mike|nancy|oscar|pam|quinn|rachel|steve|tina|uma|victor|wendy|xander|yolanda|zach|amber|brandon|chloe|derek|elena|felix|gina|hassan|iris|james|kara|leon|maya|nolan|olivia|priya"
S_DEVICES="iphone-15|iphone-14|pixel-8|galaxy-s24|macbook-pro|windows-desktop|ipad-air|linux-desktop|chrome-web|firefox-web|safari-web|android-tablet"
S_DOMAINS="gmail.com|yahoo.com|outlook.com|example.com|company.org|business.net|protonmail.com|icloud.com"
S_NORMAL_IPS="72.14.200.10|98.45.12.30|104.16.85.20|151.101.1.140|172.217.14.110|64.233.160.100|208.67.222.222|142.250.185.46|44.233.10.50|52.94.236.248|13.107.42.14|35.186.224.25|204.79.197.200|162.158.62.1|199.232.69.194"
S_VPN_IPS="103.86.96.100|146.70.33.2|193.138.218.74|89.44.9.12|37.120.198.100"
S_TOR_IPS="185.220.101.1|185.220.100.252|185.220.101.33|62.210.105.116|51.15.43.205"
S_BLOCKED_IPS="10.0.0.1"
S_BANKS="Chase|BofA|WellsFargo|Citi|HSBC|Revolut|Wise|PayPal"
S_REASONS="defective|wrong_item|changed_mind|duplicate_charge|unauthorized"
S_AGENTS="Chrome/125|Firefox/126|Safari/17|Edge/125|Mobile-Safari|Chrome-Mobile"
S_CARDS="visa|mastercard|amex|discover"
S_METHODS="email|google|apple|facebook|phone"

pick() {
  IFS='|' read -ra _items <<< "$1"
  echo "${_items[$((RANDOM % ${#_items[@]}))]}"
}

weighted_amount() {
  local roll=$((RANDOM % 100))
  if (( roll < 40 )); then
    echo "$((RANDOM % 48 + 3)).$(printf '%02d' $((RANDOM % 100)))"
  elif (( roll < 70 )); then
    echo "$((RANDOM % 200 + 50)).$(printf '%02d' $((RANDOM % 100)))"
  elif (( roll < 85 )); then
    echo "$((RANDOM % 800 + 200)).$(printf '%02d' $((RANDOM % 100)))"
  elif (( roll < 93 )); then
    echo "$((RANDOM % 4000 + 1000)).$(printf '%02d' $((RANDOM % 100)))"
  elif (( roll < 97 )); then
    echo "$((RANDOM % 10000 + 5000)).$(printf '%02d' $((RANDOM % 100)))"
  else
    echo "$((RANDOM % 40000 + 10001)).$(printf '%02d' $((RANDOM % 100)))"
  fi
}

pick_ip() {
  local roll=$((RANDOM % 100))
  if (( roll < 65 )); then
    pick "$S_NORMAL_IPS"
  elif (( roll < 78 )); then
    echo "$((RANDOM % 223 + 1)).$((RANDOM % 256)).$((RANDOM % 256)).$((RANDOM % 256))"
  elif (( roll < 87 )); then
    pick "$S_VPN_IPS"
  elif (( roll < 95 )); then
    pick "$S_TOR_IPS"
  else
    pick "$S_BLOCKED_IPS"
  fi
}

build_attrs() {
  local evtype=$1
  local amount=$(weighted_amount)

  case "$evtype" in
    purchase|payment)
      echo "\"amount\":$amount,\"merchant\":\"$(pick "$S_MERCHANTS")\",\"category\":\"$(pick "$S_CATEGORIES")\""
      ;;
    transfer|withdrawal)
      echo "\"amount\":$amount,\"recipient\":\"acct-$((RANDOM % 90000 + 10000))\",\"bank\":\"$(pick "$S_BANKS")\""
      ;;
    refund)
      echo "\"amount\":$amount,\"originalOrderId\":\"ord-$((RANDOM % 90000 + 10000))\",\"reason\":\"$(pick "$S_REASONS")\""
      ;;
    login|password_change)
      echo "\"userAgent\":\"$(pick "$S_AGENTS")\""
      ;;
    card_added)
      echo "\"cardType\":\"$(pick "$S_CARDS")\",\"lastFour\":\"$((RANDOM % 9000 + 1000))\""
      ;;
    account_creation)
      echo "\"signupMethod\":\"$(pick "$S_METHODS")\",\"referralCode\":\"REF$((RANDOM % 10000))\""
      ;;
    *)
      echo "\"amount\":$amount"
      ;;
  esac
}

fire() {
  local tenant=$(pick "$S_TENANTS")
  local evtype=$(pick "$S_EVENT_TYPES")
  local name=$(pick "$S_NAMES")
  local custnum=$((RANDOM % 300 + 1))
  local cust="cust-${name}-$(printf '%03d' $custnum)"
  local ip=$(pick_ip)
  local dev="dev-${name}-$(pick "$S_DEVICES")"
  local email="${name}${custnum}@$(pick "$S_DOMAINS")"
  local phone="+1$((RANDOM % 900 + 100))555$((RANDOM % 9000 + 1000))"
  local attrs=$(build_attrs "$evtype")

  curl -s -o /dev/null -w "%{http_code}" -X POST "$API" \
    -H "$CT" \
    -H "X-Tenant-Id: $tenant" \
    -d "{\"tenantId\":\"$tenant\",\"eventType\":\"$evtype\",\"customerId\":\"$cust\",\"sourceIp\":\"$ip\",\"deviceId\":\"$dev\",\"email\":\"$email\",\"phoneNumber\":\"$phone\",\"attributes\":{$attrs}}"
}

fire_velocity_burst() {
  local tenant=$(pick "$S_TENANTS")
  local name=$(pick "$S_NAMES")
  local custnum=$((RANDOM % 300 + 1))
  local cust="cust-${name}-$(printf '%03d' $custnum)"
  local ip=$(pick_ip)
  local dev="dev-${name}-$(pick "$S_DEVICES")"
  local burst_count=$((RANDOM % 8 + 6))

  for j in $(seq 1 $burst_count); do
    local attrs=$(build_attrs "purchase")
    curl -s -o /dev/null -X POST "$API" \
      -H "$CT" \
      -H "X-Tenant-Id: $tenant" \
      -d "{\"tenantId\":\"$tenant\",\"eventType\":\"purchase\",\"customerId\":\"$cust\",\"sourceIp\":\"$ip\",\"deviceId\":\"$dev\",\"email\":\"${name}${custnum}@$(pick "$S_DOMAINS")\",\"phoneNumber\":\"+1$((RANDOM % 900 + 100))555$((RANDOM % 9000 + 1000))\",\"attributes\":{$attrs}}"
  done
}

get_metric() {
  local name=$1
  local tag=$2
  local url="$METRICS_URL/$name"
  if [ -n "$tag" ]; then url="$url?tag=$tag"; fi
  curl -s "$url" 2>/dev/null
}

print_metrics() {
  local label=$1
  echo ""
  echo "=== $label ==="
  echo ""

  local heap=$(get_metric "jvm.memory.used" "area:heap")
  local heap_mb=$(echo "$heap" | python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    for m in d.get('measurements', []):
        if m['statistic'] == 'VALUE':
            print(f\"{m['value'] / 1048576:.1f}\")
except: print('N/A')" 2>/dev/null)
  echo "  JVM Heap Used:       ${heap_mb} MB"

  local heap_max=$(get_metric "jvm.memory.max" "area:heap")
  local heap_max_mb=$(echo "$heap_max" | python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    for m in d.get('measurements', []):
        if m['statistic'] == 'VALUE':
            v = m['value']
            if v > 0: print(f\"{v / 1048576:.1f}\")
            else: print('N/A')
except: print('N/A')" 2>/dev/null)
  echo "  JVM Heap Max:        ${heap_max_mb} MB"

  local http=$(get_metric "http.server.requests" "uri:/events")
  echo "$http" | python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    stats = {m['statistic']: m['value'] for m in d.get('measurements', [])}
    count = stats.get('COUNT', 0)
    total_time = stats.get('TOTAL_TIME', 0)
    max_time = stats.get('MAX', 0)
    avg = (total_time / count * 1000) if count > 0 else 0
    print(f'  HTTP /events:')
    print(f'    Total requests:    {int(count)}')
    print(f'    Avg latency:       {avg:.1f} ms')
    print(f'    Max latency:       {max_time * 1000:.1f} ms')
    print(f'    Total time:        {total_time:.2f} s')
except:
    print('  HTTP /events:        N/A')" 2>/dev/null

  local errors=$(get_metric "http.server.requests" "uri:/events,status:500")
  local err_count=$(echo "$errors" | python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    for m in d.get('measurements', []):
        if m['statistic'] == 'COUNT':
            print(int(m['value']))
except: print('0')" 2>/dev/null)
  echo "  5xx errors:          ${err_count:-0}"

  local threads=$(get_metric "jvm.threads.live")
  local thread_count=$(echo "$threads" | python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    for m in d.get('measurements', []):
        if m['statistic'] == 'VALUE':
            print(int(m['value']))
except: print('N/A')" 2>/dev/null)
  echo "  Live threads:        ${thread_count}"
}

export -f fire fire_velocity_burst pick weighted_amount pick_ip build_attrs
export API CT METRICS_URL
export S_TENANTS S_EVENT_TYPES S_MERCHANTS S_CATEGORIES S_NAMES S_DEVICES S_DOMAINS
export S_NORMAL_IPS S_VPN_IPS S_TOR_IPS S_BLOCKED_IPS
export S_BANKS S_REASONS S_AGENTS S_CARDS S_METHODS

echo "=== Bulk seeding $TOTAL events ==="

print_metrics "Pre-seed App Metrics"
echo ""

sent=0
failed=0
start=$(date +%s)

# Phase 1: velocity bursts
burst_count=$((TOTAL / 100))
if (( burst_count < 3 )); then burst_count=3; fi
echo ""
echo "[Phase 1] Sending $burst_count velocity bursts..."
for i in $(seq 1 $burst_count); do
  fire_velocity_burst &
  if (( i % 5 == 0 )); then wait; fi
done
wait
burst_events=$((burst_count * 9))
echo "  ~$burst_events burst events sent"

# Phase 2: diverse individual events
remaining=$((TOTAL - burst_events))
if (( remaining < 0 )); then remaining=0; fi
echo ""
echo "[Phase 2] Sending $remaining individual events..."

for batch_start in $(seq 1 $PARALLEL $remaining); do
  batch_end=$((batch_start + PARALLEL - 1))
  if [ $batch_end -gt $remaining ]; then batch_end=$remaining; fi

  pids=()
  for i in $(seq $batch_start $batch_end); do
    (
      code=$(fire)
      if [ "$code" = "200" ]; then exit 0; else exit 1; fi
    ) &
    pids+=($!)
  done

  for pid in "${pids[@]}"; do
    if wait $pid 2>/dev/null; then
      sent=$((sent + 1))
    else
      failed=$((failed + 1))
    fi
  done

  current=$((batch_end))
  if (( current % 200 == 0 || current == remaining )); then
    elapsed=$(( $(date +%s) - start ))
    rate=0
    if [ $elapsed -gt 0 ]; then rate=$((current / elapsed)); fi
    printf "\r  Progress: %d/%d | %d/s | %d failed" \
      "$current" "$remaining" "$rate" "$failed"
  fi
done

elapsed=$(( $(date +%s) - start ))
echo ""
echo ""
echo "=== Seeding complete ==="
echo "  Individual: $sent sent, $failed failed"
echo "  Burst: ~$burst_events events in $burst_count clusters"
echo "  Wall time: ${elapsed}s"
if [ $elapsed -gt 0 ]; then
  echo "  Throughput: $(( (sent + burst_events) / elapsed )) events/s"
fi

print_metrics "Post-seed App Metrics"

echo ""
sleep 2
echo "Elasticsearch index counts:"
for idx in events alerts audit; do
  count=$(curl -s "http://localhost:9200/${idx}-*/_count" | python3 -c "import sys,json; print(json.load(sys.stdin).get('count',0))" 2>/dev/null)
  printf "  %-20s %s documents\n" "$idx" "$count"
done
