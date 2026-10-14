#!/usr/bin/env bash
# Seeds example brands, categories, Spanish autonomous communities (region) and provinces through the management API (/admin/api/catalogs/{type}),
# then lists them through the query API (/api/catalogs/{type}).
#
# Usage:
#   ./scripts/seed-catalogs.sh [base_url]
#
# base_url defaults to http://localhost:8080.
# The management API requires role ADMIN (HTTP Basic): credentials are taken from
# SEED_USER / SEED_PASSWORD, defaulting to the dev user admin/admin (see app.security.users).
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
SEED_USER="${SEED_USER:-admin}"
SEED_PASSWORD="${SEED_PASSWORD:-admin}"

post_catalog() {
    local type="$1"
    local payload="$2"

    local response
    response=$(curl -s -w '\n%{http_code}' -X POST "${BASE_URL}/admin/api/catalogs/${type}" \
        -u "${SEED_USER}:${SEED_PASSWORD}" \
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

# Comunidades autónomas y provincias de España: los datos (códigos INE) viven en scripts/data/*.csv,
# compartidos con seed-catalogs.ps1. Ningún valor contiene comillas, así que el JSON se arma con printf.
DATA_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/data"

echo
echo "Seeding Spanish autonomous communities into ${BASE_URL}/admin/api/catalogs/region ..."
while IFS='|' read -r ine_code name_es name_en description_es description_en; do
    description_en="${description_en%$'\r'}"
    post_catalog region "$(printf '{"ineCode": %s, "translations": {"en": {"name": "%s", "description": "%s"}, "es": {"name": "%s", "description": "%s"}}}' \
        "${ine_code}" "${name_en}" "${description_en}" "${name_es}" "${description_es}")"
done < <(tail -n +2 "${DATA_DIR}/spain-regions.csv")

echo
echo "Seeding Spanish provinces into ${BASE_URL}/admin/api/catalogs/province ..."
while IFS='|' read -r ine_code region_code name_es name_en description_es description_en; do
    description_en="${description_en%$'\r'}"
    post_catalog province "$(printf '{"ineCode": %s, "regionCode": %s, "translations": {"en": {"name": "%s", "description": "%s"}, "es": {"name": "%s", "description": "%s"}}}' \
        "${ine_code}" "${region_code}" "${name_en}" "${description_en}" "${name_es}" "${description_es}")"
done < <(tail -n +2 "${DATA_DIR}/spain-provinces.csv")

echo
echo "Current brands:"
curl -s "${BASE_URL}/api/catalogs/brand" | grep -o '"name":"[^"]*"'

echo
echo "Current categories:"
curl -s "${BASE_URL}/api/catalogs/category" | grep -o '"name":"[^"]*"'

echo
echo "Autonomous communities: $(curl -s "${BASE_URL}/api/catalogs/region" | grep -o '"id":' | wc -l)"
echo "Provinces: $(curl -s "${BASE_URL}/api/catalogs/province" | grep -o '"id":' | wc -l)"
