# Diagnostics Collection

Target release: `v0.9.2-alpha.1`

Use the diagnostics collector when validating alpha releases or attaching troubleshooting context to GitHub Issues.

```bash
scripts/collect-diagnostics.sh
```

The script writes to `diagnostics/` by default and handles unavailable services by writing small `unavailable` JSON payloads.

Collected artifacts include:

- app version
- Java health and readiness
- Python health and readiness
- Docker Compose config
- latest risk events when authenticated
- latest certification report when authenticated
- Java and Python OpenAPI payloads

Set `AUTH_TOKEN` to include authenticated live trading endpoints.
