#!/usr/bin/env bash
# Seeds example brands and categories through the management API (/admin/api/catalogs/{type}),
# then lists them through the query API (/api/catalogs/{type}).
#
# Usage:
#   ./scripts/seed-catalogs.sh [base_url]
#
# base_url defaults to http://localhost:8080.
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"

post_catalog() {
    local type="$1"
    local payload="$2"

    local response
    response=$(curl -s -w '\n%{http_code}' -X POST "${BASE_URL}/admin/api/catalogs/${type}" \
        -H "Content-Type: application/json" \
        -d "${payload}")

    local status
    status=$(echo "${response}" | tail -n1)
    local body
    body=$(echo "${response}" | sed '$d')

    if [[ "${status}" == "201" ]]; then
        local name
        name=$(echo "${body}" | grep -o '"name":"[^"]*"' | head -n1 | cut -d'"' -f4)
        echo "  [ok]   ${type} '${name}' created (${status})"
    else
        echo "  [skip] ${type} payload -> HTTP ${status}: ${body}"
    fi
}

echo "Seeding brands into ${BASE_URL}/admin/api/catalogs/brand ..."

post_catalog brand '{
  "translations": {
    "en": {"name": "Logitech", "description": "Peripheral manufacturer"},
    "es": {"name": "Logitech", "description": "Fabricante de periféricos"}
  }
}'

post_catalog brand '{
  "translations": {
    "en": {"name": "Samsung", "description": "Electronics manufacturer"},
    "es": {"name": "Samsung", "description": "Fabricante de electrónica"}
  }
}'

post_catalog brand '{
  "translations": {
    "en": {"name": "Acme", "description": "Generic brand for everything"},
    "es": {"name": "Acme", "description": "Marca genérica para todo"}
  }
}'

echo
echo "Seeding categories into ${BASE_URL}/admin/api/catalogs/category ..."

post_catalog category '{
  "translations": {
    "en": {"name": "Peripherals", "description": "Keyboards, mice and accessories"},
    "es": {"name": "Periféricos", "description": "Teclados, ratones y accesorios"}
  }
}'

post_catalog category '{
  "translations": {
    "en": {"name": "Screens", "description": "Monitors and displays"},
    "es": {"name": "Pantallas", "description": "Monitores y displays"}
  }
}'

post_catalog category '{
  "translations": {
    "en": {"name": "Cables", "description": "Cables and adapters"},
    "es": {"name": "Cables", "description": "Cables y adaptadores"}
  }
}'

echo
echo "Current brands:"
curl -s "${BASE_URL}/api/catalogs/brand" | grep -o '"name":"[^"]*"'

echo
echo "Current categories:"
curl -s "${BASE_URL}/api/catalogs/category" | grep -o '"name":"[^"]*"'
