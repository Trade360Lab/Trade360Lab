# Changelog

All notable changes to this project will be documented in this file.

---

## [0.9.2-alpha.1] - 2026-05-09

### Operational Readiness & Audit Hardening

### Added
- Live trading audit export for risk events and rejected orders.
- Persistent Binance testnet certification reports.
- Local database backup, restore, reset, and demo seed scripts.
- Database migration baseline for release-safe schema evolution.
- Diagnostics bundle collector for alpha troubleshooting.
- Readiness dashboard with service, database, version, and safety status.
- Dependency and secret scanning visibility in CI.
- GitHub issue and pull request templates for release and safety workflows.

### Changed
- Expanded health checks into readiness checks.
- Improved release validation with diagnostics and audit artifacts.
- Documented local recovery and troubleshooting flows.

### Safety Notes
- Real order submission remains disabled by default.
- Testnet certification reports are not production exchange certification.
- Audit exports are intended for alpha validation and troubleshooting.

---

## [0.9.1-alpha.1] - 2026-05-05

### Bug Fixes & Alpha Stabilization

### Fixed

- Stabilized tagged alpha release workflow.
- Fixed Docker Compose smoke validation readiness issues.
- Fixed OpenAPI artifact export reliability.
- Fixed frontend health dashboard behavior when backend services are unavailable.
- Fixed live trading unsafe secret validation edge cases.
- Fixed Playwright smoke test flakiness.
- Fixed Java/Python internal API contract test setup.
- Fixed demo mode labeling consistency.
- Fixed release checklist and runbook inaccuracies.

### Safety Notes

- Real order submission remains disabled by default.
- This patch release does not certify production live trading.
- Testnet validation remains separate from production readiness.

---

## [0.9.0-alpha.1] - 2026-05-04

### Release Hardening & Testnet Safety

### Added

- Release workflow for tagged alpha builds.
- Docker Compose smoke validation in CI.
- OpenAPI artifacts for Java API and Python engine.
- Root `.env.example` and release environment template.
- Service health dashboard in frontend.
- Binance testnet account/order snapshot certification checks.
- Live trading risk/audit visibility.
- Playwright smoke tests for core UI flows.
- Java/Python contract tests for internal execution APIs.
- Runbooks for testnet drill, kill switch drill, and rollback.

### Changed

- Aligned project versions to `0.9.0-alpha.1`.
- Explicit demo mode labeling for frontend demo data.
- Hardened startup checks for unsafe default secrets when live trading is enabled.

### Safety Notes

- Real order submission remains disabled by default.
- Testnet certification does not imply production readiness.

---

## [0.8.0-alpha.1] - 2026-04-29

### Live Trading Foundation

### Added

* Live exchange adapter foundation with a replaceable `LiveExchangeAdapter` contract and Binance REST adapter foundation.
* Secure live exchange credential handling with encrypted API key/secret persistence and response masking.
* Owner-scoped live sessions, live orders, live positions, risk events, circuit breaker state, and kill switch state.
* Live order lifecycle with `CREATED`, `SUBMITTED`, `ACCEPTED`, `PARTIALLY_FILLED`, `FILLED`, `CANCELED`, `REJECTED`, and `FAILED`.
* Mandatory live risk checks before exchange submission:
  * enabled live session
  * active kill switch/circuit breaker gates
  * exchange connectivity and credential checks
  * positive quantity and limit price validation
  * symbol whitelist
  * max order/position notional
  * duplicate open order prevention
  * balance guard when adapter balance data is available
* Manual emergency stop APIs for kill switch activate/reset.
* Circuit breaker framework for repeated failed/rejected live orders with manual reset.
* Position sync endpoint and local live position visibility.
* Minimal Live Trading frontend page for credentials, sessions, orders, positions, risk state, health, circuit breaker visibility, and kill switch.
* Python live exchange adapter contract and Binance adapter foundation tests.

### Safety Notes

* Real order submission is disabled by default through `LIVE_TRADING_REAL_ORDER_SUBMISSION_ENABLED=false`.
* Rejected orders are persisted with explicit `rejectedReason` and do not reach an exchange.
* API secrets are encrypted at rest and never returned through API responses.
* The Binance adapter uses public REST connectivity and testnet signed-order wiring when real submission is explicitly enabled.

### Known Limitations

* Advanced portfolio risk, websocket order/position sync, multi-exchange failover, multi-account execution, and production exchange certification remain future work.
* Current position and balance sync is adapter-contract ready; Binance signed account snapshots are intentionally not completed in this alpha.

---

## [0.7.0-alpha.1] - 2026-04-27

### Strategy Management Foundation

### Added

* Strategy registry metadata on existing strategy records:

  * stable `strategy_key`
  * lifecycle status
  * strategy type
  * editable metadata/tags
  * latest version pointers
* Explicit immutable strategy version registry with:

  * version number
  * source file path
  * filename/content type/size/checksum
  * validation status and persisted validation report
  * execution engine version
* Strategy validation persistence per version.
* System strategy templates for Mean Reversion, Momentum, Breakout, and Trend Following.
* Strategy parameter presets with owner-scoped CRUD APIs.
* Strategy management APIs for registry, versions, validation, activation, templates, and presets.
* Minimal frontend strategy management views for strategy list, detail, version status, validation, activation, templates, and presets.

### Changed

* Uploaded strategy files now create a first-class strategy record plus an initial strategy version.
* Runs can reference `strategy_version_id` and `parameter_preset_id`.
* Run snapshots now persist exact `strategy_version_id` and parameter preset snapshot data when used.
* Python validation now performs syntax/contract checks and returns validation report, metadata, and engine compatibility fields.
* Java/Python execution logs include strategy and strategy version identifiers where available.

### Notes

* Existing `strategy_files` remains the root strategy registry table for compatibility with current `runs.strategy_id` references.
* Python validation still imports strategy modules to inspect runtime metadata; this is not a full execution sandbox.

---

## [0.6.0-alpha.1] - 2026-04-25

### Paper Trading Foundation

### Added

* Paper trading session model and API lifecycle:

  * create/list/get paper sessions
  * start, pause, and stop session transitions
  * owner-scoped access for all paper trading resources
* Simulated paper orders with:

  * market and limit order types
  * accepted, rejected, filled, and canceled statuses
  * persisted rejection reasons
* Simulated fills/trades for paper execution history.
* Paper position and balance tracking for long-only spot-style MVP flows.
* Java exchange adapter foundation with a `PaperExchangeAdapter`.
* Basic paper risk checks:

  * running session requirement
  * positive quantity
  * session symbol match
  * sufficient quote balance for buys
  * sufficient position for sells
  * max order notional guard
* Minimal frontend Paper Trading page for sessions, order entry, positions, fills, and orders.

### Notes

* Market orders use the latest stored candle close as the simulated execution price.
* Limit orders are accepted and filled only if the latest stored candle close satisfies the limit condition at submission time.
* This release does not implement live trading, live exchange order placement, or exchange API key workflows.

---

## [0.5.0-alpha.1] - 2026-04-24

### Scalable Execution Foundation

### Added

* Database-backed execution job model with:

  * `execution_jobs`
  * queued/running/succeeded/failed/canceled/retrying statuses
  * attempts and max attempts
  * job locking fields (`locked_by`, `locked_at`)
  * cancel request flag
  * job timing and error fields
* Queue-like `/api/runs` flow: run creation now creates a queued execution job instead of executing inline.
* In-process Java execution worker with configurable max parallel jobs and polling interval.
* Run/job APIs:

  * `GET /api/runs/{id}/execution`
  * `POST /api/runs/{id}/retry`
  * `POST /api/runs/{id}/cancel`
  * `GET /api/execution-jobs`
  * `GET /api/execution-jobs/{id}`
* Python execution contract now accepts and returns `jobId`.
* Structured logs now include `job_id` in Java and Python execution paths.
* Frontend status mapping for queued/running/failed/canceled execution states and minimal retry/cancel actions.

### Changed

* Java orchestration now separates the `Run` domain entity from `ExecutionJob` scheduling lifecycle.
* Run snapshots mark execution mode as `queued-http`.
* Result and artifact persistence remain on the existing successful-run path after worker execution.

### Notes

* The MVP uses a database-backed queue and in-process worker. No Kafka, RabbitMQ, Celery, or Kubernetes scheduler was introduced.
* Running Python workloads cannot be interrupted yet; canceling a running job records `cancel_requested` and Java suppresses result persistence when the Python call returns.

---

## [0.4.0-alpha.1] - 2026-04-23

### Artifact Storage & Data Platform Foundation

### Added

* Run artifact metadata and JSON payload storage:

  * summary export
  * metrics export
  * trades export
  * equity curve export
  * run report JSON
* Artifact APIs:

  * `GET /api/runs/{id}/artifacts`
  * `GET /api/runs/{id}/artifacts/{artifactId}`
  * `GET /api/runs/{id}/artifacts/{artifactId}/download`
* Dataset snapshot/version tables and API:

  * `dataset_snapshots`
  * `dataset_quality_reports`
  * `GET /api/datasets/{id}/versions`
  * `GET /api/datasets/{id}/quality`
  * `GET /api/dataset-snapshots/{snapshotId}`
* Python import quality report with gaps, duplicates, ordering, timeframe consistency, empty dataset, and too-small dataset checks.
* Frontend display for run artifacts and dataset quality/snapshot metadata.

### Changed

* Run snapshots now include `dataset_snapshot_id` when a matching dataset snapshot exists.
* Dataset import/upsert now creates snapshot and quality report records from existing dataset metadata.

### Notes

* Artifact storage uses DB-backed JSON payloads for MVP; the schema keeps `storage_path` for future file/object storage.
* No S3/MinIO/Kafka or feature store layer was introduced.

---

## [0.1.0-alpha.1] - 2026-04-13

### Platform Skeleton

Initial alpha baseline of the Trade360Lab monorepo.

### Added

* Frontend (Next.js) with quality gates:

  * lint
  * typecheck
  * tests
  * production build
* Backend services:

  * Java API (datasets, candles, imports)
  * Python service (market data parser)
* PostgreSQL integration via Docker.
* Docker Compose setup for full stack orchestration.
* Backend quality gates:

  * Python: ruff, pytest
  * Java: mvn test, checkstyle
* Release documentation:

  * changelog
  * alpha release checklist

### Changed

* Cleaned root npm scripts to match repository structure.
* Updated README with actual directories and startup instructions.

### Notes

* Provides a working foundation but does not include execution or strategy lifecycle.

---

## [0.2.0-alpha.1] - 2026-04-21

### Reproducible Execution Engine

This release introduces the core execution pipeline and reproducibility layer.

### Added

* Run domain for strategy execution lifecycle (create, track, complete runs).
* End-to-end execution flow: Java API -> Python engine -> result persistence.
* Internal Python execution endpoint for running strategies/backtests.
* Run result storage:

  * metrics (PnL, drawdown, win rate, etc.)
  * summary (timings, status)
  * optional artifacts (equity curve, trades)
* Immutable run snapshots for reproducibility.
* Initial version tracking:

  * strategy version
  * dataset version
  * execution parameters snapshot
  * market assumptions snapshot
  * engine version

### Changed

* Backend architecture evolved into:

  * Java -> control plane (API, orchestration)
  * Python -> execution plane (compute engine)
* Python service extended from data parser into execution-capable engine.

### Notes

* First version where strategy results can be reproduced deterministically.
* Execution engine may still use simplified/stub strategies depending on implementation stage.

---

## [0.2.1-alpha.1] - 2026-04-22

### Observability Foundation

This release improves system visibility and execution transparency.

### Added

* Structured JSON logging in Java and Python with shared trace fields:

  * `service`
  * `correlation_id`
  * `run_id`
  * `error`
* End-to-end run correlation across Java API and Python execution requests and responses.
* Run diagnostics persistence:

  * `error_details_json`
  * `execution_duration_ms`
* Extended run API payloads with timing metadata and structured error details.
* Structured Python execution failure contract:

  * `errorCode`
  * `errorMessage`
  * `stacktrace`
  * execution timing metadata

### Changed

* Improved Java <-> Python interaction logging with request/response timing and status.
* Run lifecycle transitions are now explicitly logged:

  * `CREATED -> QUEUED`
  * `QUEUED -> RUNNING`
  * `RUNNING -> SUCCEEDED | FAILED`
* Java now persists Python-side diagnostics instead of collapsing failures into a single message.
* Engine version advanced to `python-execution-engine/0.2.1-alpha.1`.

### Notes

* Focus remains internal reliability, debuggability, and operational transparency.
* No external metrics stack was introduced in this release.

---

## [0.3.0-alpha.1] - 2026-04-22

### Multi-User & Security Base

This release introduces the first security and multi-user platform baseline.

### Added

* User model and JWT authentication:

  * register endpoint
  * login endpoint
  * password hashing with BCrypt
* Ownership model for:

  * strategies
  * datasets
  * runs
* User-scoped access control across the Java API.
* Frontend authentication flow:

  * login page
  * register page
  * JWT persistence
  * automatic `Authorization` propagation
  * 401 -> login redirect
* Internal Python shared-secret protection for `/internal/*` endpoints.

### Changed

* Backend transitions from single-user tool to user-aware platform services.
* Datasets, strategies, and runs now resolve through `user_id` ownership boundaries.
* Execution flow now preserves user context from frontend -> Java API -> run creation.
* Engine version advanced to `python-execution-engine/0.3.0-alpha.1`.

### Notes

* This release intentionally stops at ownership-based authorization and does not add RBAC yet.
* Shared-secret protection keeps the Python execution API internal without introducing service mesh or OAuth infrastructure.
