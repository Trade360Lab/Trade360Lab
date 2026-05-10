#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -ne 1 ]; then
  printf 'Usage: %s <backup.dump>\n' "$0" >&2
  exit 2
fi

COMPOSE_SERVICE="${COMPOSE_SERVICE:-postgres}"
DB_NAME="${DB_NAME:-tradelab}"
DB_USER="${DB_USER:-postgres}"
BACKUP="$1"

test -f "$BACKUP"
docker compose exec -T "$COMPOSE_SERVICE" pg_restore --clean --if-exists -U "$DB_USER" -d "$DB_NAME" < "$BACKUP"
