#!/bin/sh
# Source this file, don't run it: . scripts/load-env.sh
# Strips comment lines and blank lines before exporting.
ENV_FILE="$(dirname "$0")/../.env"
if [ ! -f "$ENV_FILE" ]; then
  echo "ERROR: .env file not found at $ENV_FILE"
  return 1 2>/dev/null || exit 1
fi
while IFS= read -r line; do
  case "$line" in
    \#*|"") continue ;;
    *=*) export "$line" ;;
  esac
done < "$ENV_FILE"
echo "Environment loaded from .env"
