# Local Backup And Restore

Target release: `v0.9.6-alpha.1`

These scripts are for local alpha PostgreSQL recovery workflows using Docker Compose defaults.

## Backup

```bash
scripts/backup-local-db.sh
```

## Restore

```bash
scripts/restore-local-db.sh backups/tradelab-YYYYMMDDTHHMMSSZ.dump
```

## Reset And Seed

```bash
scripts/reset-local-db.sh
scripts/seed-demo-data.sh
```

PowerShell equivalents are available with `.ps1` suffixes.

Safety notes:

- Reset drops the local database.
- Do not run these scripts against production infrastructure.
- Real order submission remains disabled by default by application configuration, not by these scripts.
