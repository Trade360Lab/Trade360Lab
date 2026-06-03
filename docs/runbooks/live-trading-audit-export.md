# Live Trading Audit Export Runbook

Target release: `v0.9.6-alpha.1`

Live trading audit exports are for alpha validation and troubleshooting. They do not enable production order submission.

## Export Workflow

1. Sign in to the frontend.
2. Open `Live Trading`.
3. Use `Export audit` to download CSV audit events.
4. For API usage, call `GET /api/live/audit-events` or `GET /api/live/audit-events/export`.

Supported filters where practical:

- `from`
- `to`
- `exchange`
- `symbol`
- `status`
- `orderId`
- `reason`

Safety notes:

- Real order submission remains disabled by default.
- Rejected orders and risk events are audit artifacts for alpha validation.
- Audit exports may include operational metadata and should not be posted publicly without review.
