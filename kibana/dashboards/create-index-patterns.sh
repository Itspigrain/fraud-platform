#!/bin/sh
set -e

KIBANA_HOST="${KIBANA_HOST:-http://localhost:5601}"

echo "Waiting for Kibana..."
until curl -s "$KIBANA_HOST/api/status" | grep -q '"overall":{"level":"available"'; do
  sleep 5
done

echo "Creating data views (index patterns)..."

for pattern in "events-*" "alerts-*" "audit-*" "application-logs-*"; do
  name=$(echo "$pattern" | sed 's/-\*//')
  timestamp="@timestamp"
  echo "  Creating data view: $pattern"
  curl -s -X POST "$KIBANA_HOST/api/data_views/data_view" \
    -H "kbn-xsrf: true" \
    -H "Content-Type: application/json" \
    -d "{
      \"data_view\": {
        \"title\": \"$pattern\",
        \"name\": \"$name\",
        \"timeFieldName\": \"$timestamp\"
      }
    }"
  echo ""
done

echo "Data views created. Build dashboards in Kibana UI at $KIBANA_HOST"
echo ""
echo "Dashboard 1 - Fraud Overview:"
echo "  - Metric: count on events-* (Total Events)"
echo "  - Metric: count on alerts-* (Fraud Alerts)"
echo "  - Metric: count on alerts-* where severity=HIGH or CRITICAL"
echo "  - Metric: unique count of customerId on events-*"
echo "  - Line chart: alerts-* by @timestamp (hourly)"
echo "  - Donut: alerts-* by severity"
echo "  - Bar: alerts-* by ruleId"
echo "  - Pie: events-* by geoip.country_name"
echo ""
echo "Dashboard 2 - Fraud Investigation:"
echo "  - Controls: customerId, sourceIp, deviceId, email"
echo "  - Table: events-* + alerts-* sorted by @timestamp"
echo "  - Table: audit-* filtered by eventId"
echo ""
echo "Dashboard 3 - Fraud Heatmap:"
echo "  - Map: events-* using geoip.location"
echo "  - Table: events-* terms agg on geoip.country_name"
echo "  - Histogram: alerts-* on riskScore (interval 20)"
