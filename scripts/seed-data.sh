#!/bin/bash
set -e

BASE="http://localhost:8080"
CT="Content-Type: application/json"

echo "=== Seeding generic event platform with test data ==="
echo ""

post() {
  local endpoint="$1"
  local tenant="$2"
  local label="$3"
  local data="$4"
  printf "  %-50s " "$label"
  result=$(curl -s -w "\n%{http_code}" -X POST "$BASE$endpoint" \
    -H "$CT" -H "X-Tenant-Id: $tenant" -d "$data")
  http_code=$(echo "$result" | tail -1)
  body=$(echo "$result" | head -1)
  if [ "$http_code" -ge 200 ] && [ "$http_code" -lt 300 ]; then
    echo "OK ($http_code)"
  else
    echo "FAIL ($http_code): $body"
  fi
}

# --- Step 1: Create schemas ---
echo "[1/4] Creating schemas"

post "/schemas" "tenant-ecommerce" "E-commerce: purchase schema" \
  '{"eventType":"purchase","displayName":"Purchase Event","description":"E-commerce purchase","fields":[{"name":"customerId","type":"KEYWORD","required":true,"description":"Customer ID"},{"name":"amount","type":"DOUBLE","required":true,"description":"Purchase amount"},{"name":"currency","type":"KEYWORD","required":false,"description":"Currency code"},{"name":"merchant","type":"KEYWORD","required":false,"description":"Merchant name"},{"name":"category","type":"KEYWORD","required":false,"description":"Product category"},{"name":"sourceIp","type":"IP","required":false,"description":"Source IP address"}]}'

post "/schemas" "tenant-ecommerce" "E-commerce: login schema" \
  '{"eventType":"login","displayName":"Login Event","description":"User login","fields":[{"name":"customerId","type":"KEYWORD","required":true,"description":"Customer ID"},{"name":"sourceIp","type":"IP","required":false,"description":"Source IP"},{"name":"deviceId","type":"KEYWORD","required":false,"description":"Device ID"},{"name":"email","type":"KEYWORD","required":false,"description":"Email address"}]}'

post "/schemas" "tenant-banking" "Banking: transfer schema" \
  '{"eventType":"transfer","displayName":"Transfer Event","description":"Bank transfer","fields":[{"name":"accountId","type":"KEYWORD","required":true,"description":"Source account"},{"name":"recipientAccount","type":"KEYWORD","required":true,"description":"Recipient account"},{"name":"amount","type":"DOUBLE","required":true,"description":"Transfer amount"},{"name":"currency","type":"KEYWORD","required":false,"description":"Currency code"},{"name":"bank","type":"KEYWORD","required":false,"description":"Recipient bank"}]}'

post "/schemas" "tenant-banking" "Banking: withdrawal schema" \
  '{"eventType":"withdrawal","displayName":"Withdrawal Event","description":"ATM or counter withdrawal","fields":[{"name":"accountId","type":"KEYWORD","required":true,"description":"Account ID"},{"name":"amount","type":"DOUBLE","required":true,"description":"Withdrawal amount"},{"name":"method","type":"KEYWORD","required":false,"description":"Withdrawal method"},{"name":"location","type":"GEO_POINT","required":false,"description":"ATM location"}]}'

echo ""

# --- Step 2: Create rules ---
echo "[2/4] Creating rules"

post "/api/rules" "tenant-ecommerce" "Rule: high-value purchase" \
  '{"eventType":"purchase","name":"High Value Purchase","description":"Flag purchases over $10,000","ruleType":"CONDITION","conditions":[{"field":"amount","operator":"GREATER_THAN","value":"10000"}]}'

post "/api/rules" "tenant-ecommerce" "Rule: purchase velocity" \
  '{"eventType":"purchase","name":"Purchase Velocity","description":"Flag >5 purchases in 10 minutes","ruleType":"VELOCITY","groupByField":"customerId","timeWindowMinutes":10,"threshold":5}'

post "/api/rules" "tenant-banking" "Rule: large transfer" \
  '{"eventType":"transfer","name":"Large Transfer","description":"Flag transfers over $25,000","ruleType":"CONDITION","conditions":[{"field":"amount","operator":"GREATER_THAN","value":"25000"}]}'

echo ""

# --- Step 3: Send events ---
echo "[3/4] Sending events"

# E-commerce - normal purchases
post "/events" "tenant-ecommerce" "Small purchase - Alice" \
  '{"eventType":"purchase","attributes":{"customerId":"cust-alice-001","amount":49.99,"currency":"USD","merchant":"Amazon","category":"electronics","sourceIp":"72.14.200.10"}}'

post "/events" "tenant-ecommerce" "Medium purchase - Bob" \
  '{"eventType":"purchase","attributes":{"customerId":"cust-bob-002","amount":250.00,"currency":"USD","merchant":"BestBuy","category":"electronics","sourceIp":"98.45.12.30"}}'

post "/events" "tenant-ecommerce" "Login - Charlie" \
  '{"eventType":"login","attributes":{"customerId":"cust-charlie-003","sourceIp":"104.16.85.20","deviceId":"dev-charlie-win","email":"charlie@example.com"}}'

# E-commerce - high value (should trigger rule)
post "/events" "tenant-ecommerce" "High-value purchase - Grace" \
  '{"eventType":"purchase","attributes":{"customerId":"cust-grace-007","amount":15000.00,"currency":"USD","merchant":"Rolex","category":"luxury","sourceIp":"64.233.160.100"}}'

# E-commerce - velocity (rapid purchases)
for i in $(seq 1 7); do
  post "/events" "tenant-ecommerce" "Rapid purchase #$i - Ivan" \
    "{\"eventType\":\"purchase\",\"attributes\":{\"customerId\":\"cust-ivan-009\",\"amount\":$((RANDOM % 500 + 10)).99,\"currency\":\"USD\",\"merchant\":\"Store$i\",\"category\":\"retail\",\"sourceIp\":\"45.33.32.156\"}}"
done

# Banking - normal transfers
post "/events" "tenant-banking" "Normal transfer - Edward" \
  '{"eventType":"transfer","attributes":{"accountId":"acct-edward-001","recipientAccount":"acct-9876","amount":500.00,"currency":"USD","bank":"Chase"}}'

post "/events" "tenant-banking" "Withdrawal - Laura" \
  '{"eventType":"withdrawal","attributes":{"accountId":"acct-laura-002","amount":3000.00,"method":"ATM"}}'

# Banking - large transfer (should trigger rule)
post "/events" "tenant-banking" "Large transfer - Frank" \
  '{"eventType":"transfer","attributes":{"accountId":"acct-frank-003","recipientAccount":"acct-offshore-1","amount":50000.00,"currency":"USD","bank":"HSBC"}}'

echo ""

# --- Step 4: Summary ---
echo "[4/4] Checking index counts:"
for tenant in tenant-ecommerce tenant-banking; do
  count=$(curl -s "http://localhost:9200/events-${tenant}/_count" 2>/dev/null | python3 -c "import sys,json; print(json.load(sys.stdin).get('count','N/A'))" 2>/dev/null || echo "N/A")
  printf "  %-30s %s documents\n" "events-${tenant}" "$count"
done
for idx in alerts audit; do
  count=$(curl -s "http://localhost:9200/${idx}-*/_count" | python3 -c "import sys,json; print(json.load(sys.stdin).get('count',0))" 2>/dev/null)
  printf "  %-30s %s documents\n" "$idx" "$count"
done

echo ""
echo "=== Seeding complete ==="
