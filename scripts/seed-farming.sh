#!/bin/bash
set -e

BASE="http://localhost:8080"
CT="Content-Type: application/json"
TENANT="tenant-farming"

echo "=== Seeding farming tenant data ==="
echo ""

post() {
  local endpoint="$1"
  local label="$2"
  local data="$3"
  printf "  %-55s " "$label"
  result=$(curl -s -w "\n%{http_code}" -X POST "$BASE$endpoint" \
    -H "$CT" -H "X-Tenant-Id: $TENANT" -d "$data")
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

post "/schemas" "Crop sale schema" \
  '{"eventType":"crop_sale","displayName":"Crop Sale","description":"Sale of harvested crops","fields":[{"name":"farmerId","type":"KEYWORD","required":true,"description":"Farmer ID"},{"name":"crop","type":"KEYWORD","required":true,"description":"Crop type"},{"name":"quantityKg","type":"DOUBLE","required":true,"description":"Quantity in kg"},{"name":"pricePerKg","type":"DOUBLE","required":true,"description":"Price per kg"},{"name":"totalAmount","type":"DOUBLE","required":true,"description":"Total sale amount"},{"name":"buyerId","type":"KEYWORD","required":false,"description":"Buyer ID"},{"name":"region","type":"KEYWORD","required":false,"description":"Growing region"},{"name":"certifiedOrganic","type":"BOOLEAN","required":false,"description":"Organic certification status"}]}'

post "/schemas" "Subsidy claim schema" \
  '{"eventType":"subsidy_claim","displayName":"Subsidy Claim","description":"Government farming subsidy claim","fields":[{"name":"farmerId","type":"KEYWORD","required":true,"description":"Farmer ID"},{"name":"programCode","type":"KEYWORD","required":true,"description":"Subsidy program code"},{"name":"claimAmount","type":"DOUBLE","required":true,"description":"Claimed amount"},{"name":"acreage","type":"DOUBLE","required":true,"description":"Declared acreage"},{"name":"crop","type":"KEYWORD","required":false,"description":"Crop for subsidy"},{"name":"county","type":"KEYWORD","required":false,"description":"County of farm"},{"name":"sourceIp","type":"IP","required":false,"description":"Submission IP"}]}'

post "/schemas" "Equipment purchase schema" \
  '{"eventType":"equipment_purchase","displayName":"Equipment Purchase","description":"Farm equipment purchase or lease","fields":[{"name":"farmerId","type":"KEYWORD","required":true,"description":"Farmer ID"},{"name":"equipmentType","type":"KEYWORD","required":true,"description":"Type of equipment"},{"name":"amount","type":"DOUBLE","required":true,"description":"Purchase amount"},{"name":"vendor","type":"KEYWORD","required":false,"description":"Equipment vendor"},{"name":"financed","type":"BOOLEAN","required":false,"description":"Whether financed"},{"name":"sourceIp","type":"IP","required":false,"description":"Source IP"}]}'

post "/schemas" "Yield report schema" \
  '{"eventType":"yield_report","displayName":"Yield Report","description":"Crop yield report for a season","fields":[{"name":"farmerId","type":"KEYWORD","required":true,"description":"Farmer ID"},{"name":"crop","type":"KEYWORD","required":true,"description":"Crop type"},{"name":"yieldKg","type":"DOUBLE","required":true,"description":"Reported yield in kg"},{"name":"acreage","type":"DOUBLE","required":true,"description":"Acreage harvested"},{"name":"season","type":"KEYWORD","required":false,"description":"Growing season"},{"name":"county","type":"KEYWORD","required":false,"description":"County"}]}'

echo ""

# --- Step 2: Create rules ---
echo "[2/4] Creating rules"

post "/api/rules" "Rule: high-value crop sale" \
  '{"eventType":"crop_sale","name":"High Value Crop Sale","description":"Flag crop sales over $50,000 - possible invoice fraud","ruleType":"CONDITION","conditions":[{"field":"totalAmount","operator":"GREATER_THAN","value":"50000"}],"verdict":"REVIEW","severity":"HIGH"}'

post "/api/rules" "Rule: excessive subsidy claim" \
  '{"eventType":"subsidy_claim","name":"Excessive Subsidy Claim","description":"Flag subsidy claims over $100,000","ruleType":"CONDITION","conditions":[{"field":"claimAmount","operator":"GREATER_THAN","value":"100000"}],"verdict":"REVIEW","severity":"HIGH"}'

post "/api/rules" "Rule: subsidy claim velocity" \
  '{"eventType":"subsidy_claim","name":"Subsidy Claim Velocity","description":"Flag >3 subsidy claims from same farmer in 60 minutes","ruleType":"VELOCITY","groupByField":"farmerId","timeWindowMinutes":60,"threshold":3,"verdict":"BLOCK","severity":"CRITICAL"}'

post "/api/rules" "Rule: impossible yield" \
  '{"eventType":"yield_report","name":"Impossible Yield","description":"Flag yield reports exceeding 15,000 kg per acre - biologically implausible","ruleType":"CONDITION","conditions":[{"field":"yieldKg","operator":"GREATER_THAN","value":"15000"}],"verdict":"FLAG","severity":"MEDIUM"}'

post "/api/rules" "Rule: large equipment purchase" \
  '{"eventType":"equipment_purchase","name":"Large Equipment Purchase","description":"Flag equipment purchases over $200,000","ruleType":"CONDITION","conditions":[{"field":"amount","operator":"GREATER_THAN","value":"200000"}],"verdict":"REVIEW","severity":"HIGH"}'

echo ""

# --- Step 3: Send events ---
echo "[3/4] Sending events"

echo ""
echo "  -- Normal crop sales --"

post "/events" "Wheat sale - farmer-jones-001" \
  '{"eventType":"crop_sale","attributes":{"farmerId":"farmer-jones-001","crop":"wheat","quantityKg":8500.0,"pricePerKg":0.28,"totalAmount":2380.00,"buyerId":"buyer-midwest-grain","region":"kansas","certifiedOrganic":false}}'

post "/events" "Corn sale - farmer-smith-002" \
  '{"eventType":"crop_sale","attributes":{"farmerId":"farmer-smith-002","crop":"corn","quantityKg":22000.0,"pricePerKg":0.19,"totalAmount":4180.00,"buyerId":"buyer-adm-trading","region":"iowa","certifiedOrganic":false}}'

post "/events" "Organic soybean sale - farmer-chen-003" \
  '{"eventType":"crop_sale","attributes":{"farmerId":"farmer-chen-003","crop":"soybean","quantityKg":5000.0,"pricePerKg":0.62,"totalAmount":3100.00,"buyerId":"buyer-whole-foods","region":"illinois","certifiedOrganic":true}}'

post "/events" "Rice sale - farmer-patel-004" \
  '{"eventType":"crop_sale","attributes":{"farmerId":"farmer-patel-004","crop":"rice","quantityKg":12000.0,"pricePerKg":0.45,"totalAmount":5400.00,"buyerId":"buyer-rice-exchange","region":"arkansas","certifiedOrganic":false}}'

post "/events" "Cotton sale - farmer-williams-005" \
  '{"eventType":"crop_sale","attributes":{"farmerId":"farmer-williams-005","crop":"cotton","quantityKg":7000.0,"pricePerKg":1.65,"totalAmount":11550.00,"buyerId":"buyer-textile-co","region":"mississippi","certifiedOrganic":false}}'

echo ""
echo "  -- Suspicious crop sale (should trigger high-value rule) --"

post "/events" "Inflated organic sale - farmer-fraud-010" \
  '{"eventType":"crop_sale","attributes":{"farmerId":"farmer-fraud-010","crop":"soybean","quantityKg":50000.0,"pricePerKg":1.80,"totalAmount":90000.00,"buyerId":"buyer-shell-corp","region":"nebraska","certifiedOrganic":true}}'

echo ""
echo "  -- Normal subsidy claims --"

post "/events" "CRP claim - farmer-jones-001" \
  '{"eventType":"subsidy_claim","attributes":{"farmerId":"farmer-jones-001","programCode":"CRP","claimAmount":12500.00,"acreage":150.0,"crop":"wheat","county":"sedgwick","sourceIp":"72.14.200.10"}}'

post "/events" "ARC-CO claim - farmer-smith-002" \
  '{"eventType":"subsidy_claim","attributes":{"farmerId":"farmer-smith-002","programCode":"ARC-CO","claimAmount":8200.00,"acreage":320.0,"crop":"corn","county":"polk","sourceIp":"98.45.12.30"}}'

post "/events" "EQIP claim - farmer-chen-003" \
  '{"eventType":"subsidy_claim","attributes":{"farmerId":"farmer-chen-003","programCode":"EQIP","claimAmount":15000.00,"acreage":90.0,"crop":"soybean","county":"champaign","sourceIp":"104.16.85.20"}}'

echo ""
echo "  -- Suspicious subsidy claim (should trigger excessive amount rule) --"

post "/events" "Fake mega-claim - farmer-fraud-010" \
  '{"eventType":"subsidy_claim","attributes":{"farmerId":"farmer-fraud-010","programCode":"PLC","claimAmount":250000.00,"acreage":5000.0,"crop":"corn","county":"lancaster","sourceIp":"185.220.101.1"}}'

echo ""
echo "  -- Subsidy velocity burst (should trigger velocity rule) --"

for i in $(seq 1 5); do
  post "/events" "Rapid subsidy claim #$i - farmer-fraud-011" \
    "{\"eventType\":\"subsidy_claim\",\"attributes\":{\"farmerId\":\"farmer-fraud-011\",\"programCode\":\"CRP\",\"claimAmount\":$((RANDOM % 20000 + 5000)).00,\"acreage\":$((RANDOM % 200 + 50)).0,\"crop\":\"wheat\",\"county\":\"county-$i\",\"sourceIp\":\"185.220.100.252\"}}"
done

echo ""
echo "  -- Normal equipment purchases --"

post "/events" "Tractor - farmer-smith-002" \
  '{"eventType":"equipment_purchase","attributes":{"farmerId":"farmer-smith-002","equipmentType":"tractor","amount":85000.00,"vendor":"John Deere","financed":true,"sourceIp":"98.45.12.30"}}'

post "/events" "Irrigation system - farmer-patel-004" \
  '{"eventType":"equipment_purchase","attributes":{"farmerId":"farmer-patel-004","equipmentType":"irrigation_system","amount":32000.00,"vendor":"Valley Irrigation","financed":false,"sourceIp":"151.101.1.140"}}'

echo ""
echo "  -- Suspicious equipment purchase (should trigger large purchase rule) --"

post "/events" "Phantom combine - farmer-fraud-010" \
  '{"eventType":"equipment_purchase","attributes":{"farmerId":"farmer-fraud-010","equipmentType":"combine_harvester","amount":450000.00,"vendor":"Ghost Equipment LLC","financed":true,"sourceIp":"185.220.101.33"}}'

echo ""
echo "  -- Normal yield reports --"

post "/events" "Wheat yield - farmer-jones-001" \
  '{"eventType":"yield_report","attributes":{"farmerId":"farmer-jones-001","crop":"wheat","yieldKg":4200.0,"acreage":150.0,"season":"2025-fall","county":"sedgwick"}}'

post "/events" "Corn yield - farmer-smith-002" \
  '{"eventType":"yield_report","attributes":{"farmerId":"farmer-smith-002","crop":"corn","yieldKg":11200.0,"acreage":320.0,"season":"2025-fall","county":"polk"}}'

post "/events" "Soybean yield - farmer-chen-003" \
  '{"eventType":"yield_report","attributes":{"farmerId":"farmer-chen-003","crop":"soybean","yieldKg":3100.0,"acreage":90.0,"season":"2025-fall","county":"champaign"}}'

echo ""
echo "  -- Suspicious yield report (should trigger impossible yield rule) --"

post "/events" "Impossible corn yield - farmer-fraud-010" \
  '{"eventType":"yield_report","attributes":{"farmerId":"farmer-fraud-010","crop":"corn","yieldKg":80000.0,"acreage":5.0,"season":"2025-fall","county":"lancaster"}}'

echo ""

# --- Step 4: Summary ---
echo "[4/4] Checking index counts:"
sleep 1
count=$(curl -s "http://localhost:9200/events-${TENANT}/_count" 2>/dev/null | python3 -c "import sys,json; print(json.load(sys.stdin).get('count','N/A'))" 2>/dev/null || echo "N/A")
printf "  %-35s %s documents\n" "events-${TENANT}" "$count"

for idx in alerts audit; do
  count=$(curl -s "http://localhost:9200/${idx}-*/_count" | python3 -c "import sys,json; print(json.load(sys.stdin).get('count',0))" 2>/dev/null)
  printf "  %-35s %s documents\n" "$idx" "$count"
done

echo ""
echo "=== Farming tenant seeding complete ==="
echo ""
echo "Expected alerts:"
echo "  - High Value Crop Sale:    farmer-fraud-010 (soybean sale \$90k)"
echo "  - Excessive Subsidy Claim: farmer-fraud-010 (PLC claim \$250k)"
echo "  - Subsidy Claim Velocity:  farmer-fraud-011 (5 claims in burst)"
echo "  - Impossible Yield:        farmer-fraud-010 (80,000 kg on 5 acres = 16,000 kg/acre)"
echo "  - Large Equipment Purchase: farmer-fraud-010 (combine \$450k)"
