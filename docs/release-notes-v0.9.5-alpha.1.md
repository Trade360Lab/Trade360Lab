# v0.9.5-alpha.1 - Backtest Diagnostics & Strategy Report

Release date: 2026-05-30

## What changed

- Added optional backtest diagnostics on run results.
- Added Strategy Report with risk, trade distribution, stability segments, and warning codes.
- Added `strategy-report-{runId}.json` as an exportable run artifact.
- Updated run detail UI with Strategy Report cards, warnings, drawdown summary, trade distribution, and stability table.
- Updated compare UI with diagnostics status, warnings count, stability status, win rate, profit factor, and trade count fields.
- Updated release validation checklist for diagnostics, old runs without diagnostics, and report artifact validation.

## Contract

`diagnostics` is optional and backward-compatible. Old run results may return `diagnostics` as absent or null. New successful backtest runs include:

- `diagnosticsStatus`: `healthy`, `mixed`, `fragile`, or `unavailable`
- `risk`: max drawdown, drawdown window, and recovery bars
- `trades`: trade count, win/loss counts, win rate, profit factor, average/best/worst trade, streaks
- `stability`: segmented period summary with segment status
- `warnings`: explicit codes such as `LOW_TRADE_COUNT`, `HIGH_DRAWDOWN`, `NO_TRADES`, and `PROFIT_FACTOR_UNAVAILABLE`

## Safety

- Backtest diagnostics are research artifacts, not trading signals.
- Strategy reports do not imply production readiness.
- Real order submission remains disabled by default.
- Testnet/backtest validation is not production exchange certification.

## Validation notes

- Validate Python diagnostics edge cases: empty trades, all losses, all wins, drawdown, stability, and warnings.
- Validate Java/Python diagnostics deserialization and old results without diagnostics.
- Validate frontend Strategy Report rendering for present and missing diagnostics.
- Validate report artifact download and contents.

## Rollback notes

No database migration is required for this release. Diagnostics are stored in existing run JSON payloads and generated report artifacts. Rollback can ignore diagnostics fields and remove generated strategy report artifacts for affected runs if needed.
