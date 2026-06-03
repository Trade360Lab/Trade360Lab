# v0.9.6-alpha.1 - Live Risk Hardening & Release Evidence

Release date: 2026-06-03

## What changed

- Aligned monorepo, frontend, Java API, Python parser, diagnostics scripts, smoke tests, and release metadata on `0.9.6-alpha.1`.
- Hardened live-order pre-submit risk checks with portfolio exposure, daily notional, realized-loss, slippage, and market-data staleness guards.
- Added live risk status exposure fields so operators can see synced position exposure, open-order exposure, accepted daily notional, realized loss, and configured guard thresholds.
- Updated release checklist and documentation for `v0.9.6-alpha.1` validation evidence.
- Updated alpha release workflow copy to describe the current release scope.

## Contract

Live order placement remains guarded before adapter submission. Rejected orders are persisted with explicit `rejectedReason` values and corresponding risk audit events. `GET /api/live/risk/status` now includes additional optional summary fields:

- `syncedPositionExposure`
- `openOrderExposure`
- `acceptedDailyNotional`
- `realizedIntradayLoss`
- `maxAllowedDailyLoss`
- `maxAllowedSlippagePercent`
- `maxMarketDataAgeSeconds`

## Safety

- Real order submission remains disabled by default.
- Live risk guards reduce unsafe submissions but do not certify production exchange trading.
- Testnet/backtest validation is not production exchange certification.
- Strategy reports and diagnostics remain research artifacts, not trading signals.

## Validation notes

- Validate frontend, Python, Java, Docker smoke, OpenAPI export, and diagnostics bundle commands from the release checklist.
- Validate live risk rejection reasons for exposure, daily notional, realized loss, stale market data, and slippage.
- Validate risk audit visibility for every rejected live order.

## Rollback notes

No database migration is required for this release. Rollback can redeploy `v0.9.5-alpha.1`; new risk status fields are response-only and rejected orders remain auditable through existing `rejected_reason` and risk event records.
