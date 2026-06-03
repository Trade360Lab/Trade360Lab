<h1 align="center">Модель данных</h1>

<h2 align="center">`runs`</h2>

Главная таблица запусков.

Основные поля:

- `id`
- `strategy_id`
- `strategy_version_id`
- `parameter_preset_id`
- `status`
- `exchange`
- `symbol`
- `interval`
- `date_from`
- `date_to`
- `params_json`
- `metrics_json`
- `error_message`
- `created_at`
- `started_at`
- `finished_at`

Назначение:

- хранит идентичность запуска и его жизненный цикл
- хранит сериализованный запрос в `params_json`
- хранит summary результата в `metrics_json`
- хранит текст ошибки в `error_message`
- связывает запуск с exact strategy version через `strategy_version_id`, если run создан через strategy management flow

<h2 align="center">`strategy_files`</h2>

Root strategy registry table сохранена под историческим именем для совместимости с `runs.strategy_id`.

Основные поля:

- `id`
- `user_id`
- `strategy_key`
- `name`
- `description`
- `strategy_type`
- `lifecycle_status`
- `latest_version`
- `latest_version_id`
- `filename`
- `storage_path`
- `status`
- `validation_error`
- `parameters_schema_json`
- `metadata_json`
- `tags_json`
- `content_type`
- `size_bytes`
- `checksum`
- `uploaded_at`
- `created_at`
- `updated_at`

Связь:

- `user_id -> users.id`
- `latest_version_id -> strategy_versions.id` логически указывает на active/latest source version

`lifecycle_status`: `DRAFT`, `ACTIVE`, `DEPRECATED`, `ARCHIVED`.
`status`: latest validation status compatibility field, currently `PENDING`, `VALID`, `INVALID`.

<h2 align="center">`strategy_versions`</h2>

Immutable source/version registry для стратегий.

Основные поля:

- `id`
- `strategy_id`
- `version`
- `file_path`
- `filename`
- `content_type`
- `size_bytes`
- `checksum`
- `validation_status`
- `validation_report`
- `parameters_schema_json`
- `metadata_json`
- `execution_engine_version`
- `created_at`
- `created_by`

Связь:

- `strategy_id -> strategy_files.id`
- `created_by -> users.id`

`validation_status`: `PENDING`, `VALID`, `WARNING`, `INVALID`.
Файлы связаны с version через checksum и storage path. Платформа не выполняет произвольные uploaded files, пока validation не пройдена и создание run не выбрало executable version.

<h2 align="center">`strategy_templates`</h2>

System-owned starter templates.

Основные поля:

- `id`
- `template_key`
- `name`
- `description`
- `strategy_type`
- `category`
- `default_parameters_json`
- `template_reference`
- `metadata_json`
- `created_at`

<h2 align="center">`strategy_parameter_presets`</h2>

Reusable owner-scoped parameter payloads для стратегий.

Основные поля:

- `id`
- `strategy_id`
- `user_id`
- `name`
- `preset_payload`
- `created_at`
- `updated_at`

Связь:

- `strategy_id -> strategy_files.id`
- `user_id -> users.id`

Runs копируют effective preset payload в `run_snapshots.parameter_preset_snapshot_json`.

<h2 align="center">`execution_jobs`</h2>

Таблица queue-like lifecycle для выполнения runs.

Основные поля:

- `id`
- `run_id`
- `user_id`
- `status`
- `priority`
- `attempt_count`
- `max_attempts`
- `queued_at`
- `started_at`
- `finished_at`
- `locked_at`
- `locked_by`
- `cancel_requested`
- `error_code`
- `error_message`
- `created_at`
- `updated_at`

Связь:

- `run_id -> runs.id`

Активный job (`QUEUED`, `RETRYING`, `RUNNING`) должен быть единственным для run. Это защищает run от duplicate active execution.

<h2 align="center">`backtest_trades`</h2>

Таблица сделок конкретного запуска.

Основные поля:

- `id`
- `run_id`
- `entry_time`
- `exit_time`
- `entry_price`
- `exit_price`
- `quantity`
- `pnl`
- `fee`

Связь:

- `run_id -> runs.id`

<h2 align="center">`backtest_equity_points`</h2>

Таблица точек кривой капитала.

Основные поля:

- `id`
- `run_id`
- `timestamp`
- `equity`

Связь:

- `run_id -> runs.id`

<h2 align="center">`run_artifacts`</h2>

Metadata и JSON payload артефактов запуска.

Основные поля:

- `id`
- `run_id`
- `artifact_type`
- `artifact_name`
- `content_type`
- `storage_path`
- `payload_json`
- `size_bytes`
- `created_at`

Связь:

- `run_id -> runs.id`

Минимальные типы артефактов:

- `EQUITY_CURVE`
- `TRADES`
- `METRICS_JSON`
- `SUMMARY_JSON`
- `REPORT_JSON`

<h2 align="center">`dataset_snapshots`</h2>

Версионированное представление dataset metadata для воспроизводимости.

Основные поля:

- `id`
- `dataset_id`
- `dataset_version`
- `source_exchange`
- `symbol`
- `interval`
- `start_time`
- `end_time`
- `row_count`
- `checksum`
- `source_metadata_json`
- `coverage_metadata_json`
- `created_at`

<h2 align="center">`dataset_quality_reports`</h2>

Сохраненные результаты data quality checks.

Основные поля:

- `id`
- `dataset_id`
- `dataset_snapshot_id`
- `quality_status`
- `issues_json`
- `checked_at`

`quality_status`: `OK`, `WARNING`, `FAILED`.

<h2 align="center">Дополнительная таблица `candles`</h2>

Для выполнения бэктеста сервис берет исходные OHLCV-данные из `candles`.

Основные поля:

- `exchange`
- `symbol`
- `interval`
- `open_time`
- `close_time`
- `open`
- `high`
- `low`
- `close`
- `volume`

<h2 align="center">`paper_trading_sessions`</h2>

Paper trading session принадлежит пользователю и задает market/context для simulated execution.

Основные поля:

- `id`
- `user_id`
- `name`
- `exchange`
- `symbol`
- `timeframe`
- `status`
- `initial_balance`
- `current_balance`
- `base_currency`
- `quote_currency`
- `started_at`
- `stopped_at`
- `created_at`
- `updated_at`

`status`: `CREATED`, `RUNNING`, `PAUSED`, `STOPPED`, `FAILED`.

<h2 align="center">`paper_orders`</h2>

Simulated order внутри paper session.

Основные поля:

- `id`
- `session_id`
- `user_id`
- `symbol`
- `side`
- `type`
- `status`
- `quantity`
- `price`
- `filled_quantity`
- `average_fill_price`
- `created_at`
- `updated_at`
- `filled_at`
- `rejected_reason`

Rejected orders сохраняются для audit/history, но не создают fills.

<h2 align="center">`paper_fills`</h2>

История simulated fills/trades для восстановления paper trading history.

Основные поля:

- `id`
- `order_id`
- `session_id`
- `symbol`
- `side`
- `quantity`
- `price`
- `fee`
- `fee_currency`
- `executed_at`

<h2 align="center">`paper_positions`</h2>

Минимальная long-only позиция по session/symbol.

Основные поля:

- `id`
- `session_id`
- `symbol`
- `quantity`
- `average_entry_price`
- `realized_pnl`
- `unrealized_pnl`
- `updated_at`

MVP не моделирует margin, futures, leverage или short selling.

<h2 align="center">`live_exchange_credentials`</h2>

Encrypted owner-scoped exchange credentials.

Основные поля:

- `id`
- `user_id`
- `exchange`
- `key_reference`
- `encrypted_api_key`
- `encrypted_api_secret`
- `is_active`
- `created_at`
- `updated_at`

Raw API keys и secrets не возвращаются в API responses.

<h2 align="center">`live_trading_sessions`</h2>

Manual live execution boundary с per-session risk limits.

Основные поля:

- `id`
- `user_id`
- `name`
- `exchange`
- `symbol`
- `base_currency`
- `quote_currency`
- `status`
- `max_order_notional`
- `max_position_notional`
- `max_daily_notional`
- `symbol_whitelist`

`v0.9.6-alpha.1` uses these caps together with runtime configuration for max daily loss, max slippage percent, and max market-data age. No new table columns are required for those response-only guards.
- `created_at`
- `updated_at`

`status`: `CREATED`, `ENABLED`, `DISABLED`.

<h2 align="center">`live_orders`</h2>

Auditable live order lifecycle.

Основные поля:

- `id`
- `user_id`
- `session_id`
- `strategy_id`
- `strategy_version_id`
- `exchange`
- `symbol`
- `side`
- `type`
- `quantity`
- `requested_price`
- `executed_price`
- `status`
- `exchange_order_id`
- `submitted_at`
- `updated_at`
- `filled_at`
- `rejected_reason`
- `source_run_id`

`status`: `CREATED`, `SUBMITTED`, `ACCEPTED`, `PARTIALLY_FILLED`, `FILLED`, `CANCELED`, `REJECTED`, `FAILED`.

<h2 align="center">`live_positions`</h2>

Local live position snapshots, синхронизированные из exchange adapters.

Основные поля:

- `id`
- `user_id`
- `exchange`
- `symbol`
- `quantity`
- `average_entry_price`
- `realized_pnl`
- `unrealized_pnl`
- `updated_at`
- `sync_status`

<h2 align="center">`risk_events`</h2>

Audit trail для live risk decisions и safety state changes.

<h2 align="center">`circuit_breaker_state`</h2>

Per-user/per-exchange automatic safety stop state.

<h2 align="center">`kill_switch_state`</h2>

Per-user emergency stop state. В активном состоянии новые live orders блокируются до adapter submission.
