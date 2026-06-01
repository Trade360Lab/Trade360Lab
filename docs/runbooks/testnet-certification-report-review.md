# Testnet Certification Report Review

Target release: `v0.9.5-alpha.1`

Testnet certification reports persist the result of a Binance testnet drill. They are not production exchange certification and do not imply production readiness.

## Review Workflow

1. Run a drill from `Live Trading` with `Certify`.
2. Review the latest report in the same view.
3. For API usage:
   - `POST /api/live/certification/testnet/run`
   - `GET /api/live/certification/testnet/latest`
   - `GET /api/live/certification/testnet/{id}`
4. Confirm `realOrderSubmissionEnabled` is `false`.
5. Attach the report to the release issue if certification is part of release validation.

Report fields include exchange, environment, start/finish times, connectivity, account snapshot, open orders, reconciliation, risk checks, final result, and safety mode.
