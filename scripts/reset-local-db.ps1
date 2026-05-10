$ErrorActionPreference = "Stop"
$Service = $env:COMPOSE_SERVICE
if (-not $Service) { $Service = "postgres" }
$DbName = $env:DB_NAME
if (-not $DbName) { $DbName = "tradelab" }
$DbUser = $env:DB_USER
if (-not $DbUser) { $DbUser = "postgres" }
$Sql = "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$DbName' AND pid <> pg_backend_pid(); DROP DATABASE IF EXISTS $DbName; CREATE DATABASE $DbName;"
$Sql | docker compose exec -T $Service psql -U $DbUser -d postgres -v ON_ERROR_STOP=1
