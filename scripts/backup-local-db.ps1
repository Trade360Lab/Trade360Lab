$ErrorActionPreference = "Stop"
$Service = $env:COMPOSE_SERVICE
if (-not $Service) { $Service = "postgres" }
$DbName = $env:DB_NAME
if (-not $DbName) { $DbName = "tradelab" }
$DbUser = $env:DB_USER
if (-not $DbUser) { $DbUser = "postgres" }
$BackupDir = $env:BACKUP_DIR
if (-not $BackupDir) { $BackupDir = "backups" }
New-Item -ItemType Directory -Force -Path $BackupDir | Out-Null
$Stamp = (Get-Date).ToUniversalTime().ToString("yyyyMMddTHHmmssZ")
$Out = Join-Path $BackupDir "tradelab-$Stamp.dump"
docker compose exec -T $Service pg_dump -U $DbUser -Fc $DbName | Set-Content -Encoding Byte $Out
Write-Output $Out
