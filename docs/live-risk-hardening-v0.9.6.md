# v0.9.6-alpha.1 Live Risk Hardening

`v0.9.6-alpha.1` keeps real order submission disabled by default and strengthens pre-submit live-order guards. These controls reduce unsafe adapter submissions; they are not production exchange certification.

## Guards

- Kill switch and circuit breaker gates must be clear.
- Session must be `ENABLED` and credentials must be active and valid.
- Quantity, limit price, symbol whitelist, per-order notional, duplicate open order, and balance checks still run before adapter submission.
- Portfolio exposure combines synced position exposure, open-order exposure, and the candidate order notional before comparing with the session max position notional.
- Daily notional combines same-day submitted/accepted/filled order exposure with the candidate order notional before comparing with the session max daily notional.
- Realized intraday loss uses synced negative realized PnL and rejects once it exceeds `LIVE_TRADING_DEFAULT_MAX_DAILY_LOSS_NOTIONAL`.
- Limit-order slippage compares requested price with the latest market price and rejects when the configured percent is exceeded.
- Market-data staleness rejects price snapshots older than `LIVE_TRADING_MAX_MARKET_DATA_AGE_SECONDS`.

## Configuration

- `LIVE_TRADING_DEFAULT_MAX_DAILY_LOSS_NOTIONAL` default: `250.00000000`
- `LIVE_TRADING_MAX_ALLOWED_SLIPPAGE_PERCENT` default: `2.00000000`
- `LIVE_TRADING_MAX_MARKET_DATA_AGE_SECONDS` default: `60`

## Operator evidence

`GET /api/live/risk/status` now includes exposure and threshold fields for release validation:

- `syncedPositionExposure`
- `openOrderExposure`
- `acceptedDailyNotional`
- `realizedIntradayLoss`
- `maxAllowedDailyLoss`
- `maxAllowedSlippagePercent`
- `maxMarketDataAgeSeconds`

Every rejected order persists its `rejectedReason` and emits a risk audit event.
