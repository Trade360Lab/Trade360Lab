#!/usr/bin/env bash
set -euo pipefail

COMPOSE_SERVICE="${COMPOSE_SERVICE:-postgres}"
DB_NAME="${DB_NAME:-tradelab}"
DB_USER="${DB_USER:-postgres}"

docker compose exec -T "$COMPOSE_SERVICE" psql -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 <<'SQL'
INSERT INTO users (email, password_hash, is_active)
VALUES ('demo@trade360lab.local', '$2a$10$demo.demo.demo.demo.demo.demo.demo.demo.demo.demo.demo', TRUE)
ON CONFLICT (email) DO NOTHING;
SQL
