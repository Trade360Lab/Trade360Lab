param([Parameter(Mandatory=$true)][string]$Backup)
$ErrorActionPreference = "Stop"
$Service = $env:COMPOSE_SERVICE
if (-not $Service) { $Service = "postgres" }
$DbName = $env:DB_NAME
if (-not $DbName) { $DbName = "tradelab" }
$DbUser = $env:DB_USER
if (-not $DbUser) { $DbUser = "postgres" }
if (-not (Test-Path $Backup)) { throw "Backup not found: $Backup" }
Get-Content -Encoding Byte $Backup | docker compose exec -T $Service pg_restore --clean --if-exists -U $DbUser -d $DbName
