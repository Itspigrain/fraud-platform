#!/bin/sh
set -e

ES_HOST="http://elasticsearch:9200"

echo "Waiting for Elasticsearch..."
until curl -s "$ES_HOST/_cluster/health" | grep -q '"status":"green"\|"status":"yellow"'; do
  sleep 2
done

echo "Loading index templates..."

for template in /elasticsearch/templates/*.json; do
  name=$(basename "$template" .json)
  echo "  Loading $name..."
  curl -s -X PUT "$ES_HOST/_index_template/$name" \
    -H "Content-Type: application/json" \
    -d @"$template"
  echo ""
done

echo "Index templates loaded successfully."
