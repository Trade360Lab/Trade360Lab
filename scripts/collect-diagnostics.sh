#!/usr/bin/env bash
set -uo pipefail

OUT="${DIAGNOSTICS_DIR:-diagnostics}"
JAVA_URL="${JAVA_URL:-http://127.0.0.1:18080}"
PYTHON_URL="${PYTHON_URL:-http://127.0.0.1:18000}"
FRONTEND_URL="${FRONTEND_URL:-http://127.0.0.1:3000}"
TOKEN="${AUTH_TOKEN:-}"

mkdir -p "$OUT"

write_json() {
  local name="$1"
  local url="$2"
  local auth_args=()
  if [ -n "$TOKEN" ]; then
    auth_args=(-H "Authorization: Bearer $TOKEN")
  fi
  if ! curl -fsS "${auth_args[@]}" "$url" -o "$OUT/$name"; then
    printf '{"status":"unavailable","url":"%s"}\n' "$url" > "$OUT/$name"
  fi
}

printf '{"release":"0.9.2-alpha.1","frontendUrl":"%s","javaUrl":"%s","pythonUrl":"%s"}\n' "$FRONTEND_URL" "$JAVA_URL" "$PYTHON_URL" > "$OUT/app-version.json"
write_json java-health.json "$JAVA_URL/api/health"
write_json java-readiness.json "$JAVA_URL/api/readiness"
write_json python-health.json "$PYTHON_URL/health"
write_json python-readiness.json "$PYTHON_URL/readiness"
write_json latest-risk-events.json "$JAVA_URL/api/live/risk/events"
write_json latest-certification-report.json "$JAVA_URL/api/live/certification/testnet/latest"
write_json openapi-java.json "$JAVA_URL/v3/api-docs"
write_json openapi-python.json "$PYTHON_URL/openapi.json"

if ! docker compose config > "$OUT/docker-compose-config.txt" 2>&1; then
  printf 'docker compose config unavailable\n' > "$OUT/docker-compose-config.txt"
fi

printf '%s\n' "$OUT"
