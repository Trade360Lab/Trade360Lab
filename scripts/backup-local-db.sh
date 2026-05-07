#!/usr/bin/env bash
set -euo pipefail

COMPOSE_SERVICE="${COMPOSE_SERVICE:-postgres}"
DB_NAME="${DB_NAME:-tradelab}"
DB_USER="${DB_USER:-postgres}"
BACKUP_DIR="${BACKUP_DIR:-backups}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT="${BACKUP_DIR}/tradelab-${STAMP}.dump"

mkdir -p "$BACKUP_DIR"
docker compose exec -T "$COMPOSE_SERVICE" pg_dump -U "$DB_USER" -Fc "$DB_NAME" > "$OUT"
printf '%s\n' "$OUT"
