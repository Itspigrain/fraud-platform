#!/bin/bash
set -e

API="http://localhost:8080/events"
CT="Content-Type: application/json"

echo "=== Seeding fraud platform with test data ==="
echo ""

post() {
  local label="$1"
  local data="$2"
  printf "  %-50s " "$label"
  result=$(curl -s -X POST "$API" -H "X-Tenant-Id: tenant-1" -H "$CT" -d "$data")
  decision=$(echo "$result" | python3 -c "import sys,json; r=json.load(sys.stdin); print(f'score={r[\"riskScore\"]} decision={r[\"decision\"]} alerts={len(r[\"alerts\"])}')" 2>/dev/null)
  echo "$decision"
}

# --- Normal transactions (should ALLOW) ---
echo "[1/6] Normal transactions"
post "Small purchase - Alice" \
  '{"tenantId":"tenant-1","eventType":"purchase","customerId":"cust-alice-001","sourceIp":"72.14.200.10","deviceId":"dev-alice-mac","email":"alice@example.com","phoneNumber":"+14155551001","attributes":{"amount":49.99,"merchant":"Amazon","category":"electronics"}}'

post "Medium purchase - Bob" \
  '{"tenantId":"tenant-1","eventType":"purchase","customerId":"cust-bob-002","sourceIp":"98.45.12.30","deviceId":"dev-bob-iphone","email":"bob@example.com","phoneNumber":"+14155551002","attributes":{"amount":250.00,"merchant":"BestBuy","category":"electronics"}}'

post "Login - Charlie" \
  '{"tenantId":"tenant-1","eventType":"login","customerId":"cust-charlie-003","sourceIp":"104.16.85.20","deviceId":"dev-charlie-win","email":"charlie@example.com","phoneNumber":"+14155551003","attributes":{}}'

post "Payment - Diana" \
  '{"tenantId":"tenant-2","eventType":"payment","customerId":"cust-diana-004","sourceIp":"151.101.1.140","deviceId":"dev-diana-android","email":"diana@example.com","phoneNumber":"+14155551004","attributes":{"amount":1200.00,"merchant":"Stripe","category":"subscription"}}'

post "Transfer - Edward" \
  '{"tenantId":"tenant-1","eventType":"transfer","customerId":"cust-edward-005","sourceIp":"172.217.14.110","deviceId":"dev-edward-mac","email":"edward@example.com","phoneNumber":"+14155551005","attributes":{"amount":500.00,"recipient":"acct-9876","bank":"Chase"}}'

echo ""

# --- High-value transactions (should trigger HIGH_VALUE rule) ---
echo "[2/6] High-value transactions (amount > \$10,000)"
post "Large wire transfer - Frank" \
  '{"tenantId":"tenant-1","eventType":"transfer","customerId":"cust-frank-006","sourceIp":"73.222.15.44","deviceId":"dev-frank-win","email":"frank@example.com","phoneNumber":"+14155551006","attributes":{"amount":25000.00,"recipient":"acct-offshore-1","bank":"HSBC"}}'

post "Big purchase - Grace" \
  '{"tenantId":"tenant-2","eventType":"purchase","customerId":"cust-grace-007","sourceIp":"64.233.160.100","deviceId":"dev-grace-ipad","email":"grace@example.com","phoneNumber":"+14155551007","attributes":{"amount":15000.00,"merchant":"Rolex","category":"luxury"}}'

post "Huge payment - Henry" \
  '{"tenantId":"tenant-1","eventType":"payment","customerId":"cust-henry-008","sourceIp":"208.67.222.222","deviceId":"dev-henry-mac","email":"henry@example.com","phoneNumber":"+14155551008","attributes":{"amount":50000.00,"merchant":"RealEstateCo","category":"property"}}'

echo ""

# --- Velocity abuse (>5 events in 10 min for same customer) ---
echo "[3/6] Velocity abuse - rapid-fire from same customer"
for i in $(seq 1 8); do
  post "Rapid txn #$i - Ivan" \
    "{\"tenantId\":\"tenant-1\",\"eventType\":\"purchase\",\"customerId\":\"cust-ivan-009\",\"sourceIp\":\"45.33.32.156\",\"deviceId\":\"dev-ivan-bot\",\"email\":\"ivan@example.com\",\"phoneNumber\":\"+14155551009\",\"attributes\":{\"amount\":$((RANDOM % 500 + 10)).99,\"merchant\":\"Store$i\",\"category\":\"retail\"}}"
done

echo ""

# --- Mixed scenarios ---
echo "[4/6] Multiple event types from various tenants"
post "Account creation - Julia" \
  '{"tenantId":"tenant-3","eventType":"account_creation","customerId":"cust-julia-010","sourceIp":"93.184.216.34","deviceId":"dev-julia-chrome","email":"julia@newaccount.com","phoneNumber":"+44207946001","attributes":{"referralCode":"REF123","signupMethod":"email"}}'

post "Password change - Kevin" \
  '{"tenantId":"tenant-1","eventType":"password_change","customerId":"cust-kevin-011","sourceIp":"198.51.100.50","deviceId":"dev-kevin-firefox","email":"kevin@example.com","phoneNumber":"+14155551011","attributes":{}}'

post "Withdrawal - Laura" \
  '{"tenantId":"tenant-2","eventType":"withdrawal","customerId":"cust-laura-012","sourceIp":"203.0.113.75","deviceId":"dev-laura-app","email":"laura@example.com","phoneNumber":"+14155551012","attributes":{"amount":3000.00,"destination":"bank-acct-5555","method":"ACH"}}'

post "Refund - Mike" \
  '{"tenantId":"tenant-1","eventType":"refund","customerId":"cust-mike-013","sourceIp":"142.250.185.46","deviceId":"dev-mike-safari","email":"mike@example.com","phoneNumber":"+14155551013","attributes":{"amount":899.99,"originalOrderId":"ord-7890","reason":"defective"}}'

post "Card addition - Nancy" \
  '{"tenantId":"tenant-2","eventType":"card_added","customerId":"cust-nancy-014","sourceIp":"151.101.65.140","deviceId":"dev-nancy-android","email":"nancy@example.com","phoneNumber":"+14155551014","attributes":{"cardType":"visa","lastFour":"4242"}}'

echo ""

# --- High-value + velocity combo ---
echo "[5/6] Combined triggers - high value with velocity"
for i in $(seq 1 6); do
  post "Rapid high-value #$i - Oscar" \
    "{\"tenantId\":\"tenant-1\",\"eventType\":\"purchase\",\"customerId\":\"cust-oscar-015\",\"sourceIp\":\"185.199.108.153\",\"deviceId\":\"dev-oscar-bot\",\"email\":\"oscar@suspicious.com\",\"phoneNumber\":\"+14155551015\",\"attributes\":{\"amount\":$((12000 + RANDOM % 8000)).00,\"merchant\":\"Crypto Exchange $i\",\"category\":\"crypto\"}}"
done

echo ""

# --- Legitimate high-frequency, low-risk user ---
echo "[6/6] Legitimate busy user - many small transactions"
for i in $(seq 1 4); do
  post "Small purchase #$i - Pam" \
    "{\"tenantId\":\"tenant-1\",\"eventType\":\"purchase\",\"customerId\":\"cust-pam-016\",\"sourceIp\":\"72.14.200.10\",\"deviceId\":\"dev-pam-iphone\",\"email\":\"pam@example.com\",\"phoneNumber\":\"+14155551016\",\"attributes\":{\"amount\":$((RANDOM % 50 + 5)).99,\"merchant\":\"Coffee Shop\",\"category\":\"food\"}}"
done

echo ""
echo "=== Seeding complete ==="
echo ""

# Summary
echo "Checking index counts:"
for idx in events alerts audit; do
  count=$(curl -s "http://localhost:9200/${idx}-*/_count" | python3 -c "import sys,json; print(json.load(sys.stdin).get('count',0))" 2>/dev/null)
  printf "  %-20s %s documents\n" "$idx" "$count"
done
