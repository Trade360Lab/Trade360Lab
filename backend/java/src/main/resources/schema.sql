CREATE TABLE IF NOT EXISTS users (
                                     id BIGSERIAL PRIMARY KEY,
                                     email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );

CREATE INDEX IF NOT EXISTS idx_users_email
    ON users (email);


CREATE TABLE IF NOT EXISTS datasets (
                                        id VARCHAR(64) PRIMARY KEY,
    user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    source VARCHAR(64),
    symbol VARCHAR(64),
    "interval" VARCHAR(32),
    imported_at TIMESTAMPTZ,
    rows_count INTEGER,
    start_at TIMESTAMPTZ,
    end_at TIMESTAMPTZ,
    version VARCHAR(128),
    fingerprint VARCHAR(128),
    quality_flags_json TEXT,
    lineage_json TEXT,
    payload TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );

ALTER TABLE datasets
    ADD COLUMN IF NOT EXISTS user_id BIGINT REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE datasets
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

CREATE INDEX IF NOT EXISTS idx_datasets_created_at
    ON datasets (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_datasets_user_created_at
    ON datasets (user_id, created_at DESC);


CREATE TABLE IF NOT EXISTS strategy_files (
                                              id BIGSERIAL PRIMARY KEY,
                                              user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
                                              name VARCHAR(255),
    filename VARCHAR(255) NOT NULL,
    storage_path TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    validation_error TEXT,
    parameters_schema_json TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );

ALTER TABLE strategy_files
    ADD COLUMN IF NOT EXISTS user_id BIGINT REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE strategy_files
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

CREATE INDEX IF NOT EXISTS idx_strategy_files_created_at
    ON strategy_files (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_strategy_files_user_created_at
    ON strategy_files (user_id, created_at DESC);

ALTER TABLE strategy_files
    ADD COLUMN IF NOT EXISTS strategy_key VARCHAR(128);

ALTER TABLE strategy_files
    ADD COLUMN IF NOT EXISTS description TEXT;

ALTER TABLE strategy_files
    ADD COLUMN IF NOT EXISTS strategy_type VARCHAR(64);

ALTER TABLE strategy_files
    ADD COLUMN IF NOT EXISTS lifecycle_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT';

ALTER TABLE strategy_files
    ADD COLUMN IF NOT EXISTS latest_version VARCHAR(128);

ALTER TABLE strategy_files
    ADD COLUMN IF NOT EXISTS latest_version_id BIGINT;

ALTER TABLE strategy_files
    ADD COLUMN IF NOT EXISTS content_type VARCHAR(128);

ALTER TABLE strategy_files
    ADD COLUMN IF NOT EXISTS size_bytes BIGINT;

ALTER TABLE strategy_files
    ADD COLUMN IF NOT EXISTS checksum VARCHAR(128);

ALTER TABLE strategy_files
    ADD COLUMN IF NOT EXISTS uploaded_at TIMESTAMPTZ;

ALTER TABLE strategy_files
    ADD COLUMN IF NOT EXISTS metadata_json TEXT;

ALTER TABLE strategy_files
    ADD COLUMN IF NOT EXISTS tags_json TEXT;

ALTER TABLE strategy_files
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

CREATE UNIQUE INDEX IF NOT EXISTS idx_strategy_files_user_strategy_key
    ON strategy_files (user_id, strategy_key);

CREATE INDEX IF NOT EXISTS idx_strategy_files_user_status
    ON strategy_files (user_id, lifecycle_status, created_at DESC);

CREATE TABLE IF NOT EXISTS strategy_versions (
                                                 id BIGSERIAL PRIMARY KEY,
                                                 strategy_id BIGINT NOT NULL REFERENCES strategy_files(id) ON DELETE CASCADE,
    version VARCHAR(128) NOT NULL,
    file_path TEXT NOT NULL,
    filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(128),
    size_bytes BIGINT NOT NULL,
    checksum VARCHAR(128) NOT NULL,
    validation_status VARCHAR(32) NOT NULL,
    validation_report TEXT,
    parameters_schema_json TEXT,
    metadata_json TEXT,
    execution_engine_version VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT strategy_versions_strategy_version_key UNIQUE (strategy_id, version),
    CONSTRAINT strategy_versions_strategy_checksum_key UNIQUE (strategy_id, checksum)
    );

CREATE INDEX IF NOT EXISTS idx_strategy_versions_strategy_created_at
    ON strategy_versions (strategy_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_strategy_versions_validation_status
    ON strategy_versions (validation_status);

CREATE TABLE IF NOT EXISTS strategy_templates (
                                                  id BIGSERIAL PRIMARY KEY,
                                                  template_key VARCHAR(128) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    strategy_type VARCHAR(64),
    category VARCHAR(64),
    default_parameters_json TEXT NOT NULL,
    template_reference TEXT NOT NULL,
    metadata_json TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );

CREATE TABLE IF NOT EXISTS strategy_parameter_presets (
                                                          id BIGSERIAL PRIMARY KEY,
                                                          strategy_id BIGINT NOT NULL REFERENCES strategy_files(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    preset_payload TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );

CREATE INDEX IF NOT EXISTS idx_strategy_parameter_presets_strategy_user
    ON strategy_parameter_presets (strategy_id, user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_strategy_parameter_presets_user_created_at
    ON strategy_parameter_presets (user_id, created_at DESC);


CREATE TABLE IF NOT EXISTS candles (
                                        id BIGSERIAL PRIMARY KEY,
                                        exchange VARCHAR(32) NOT NULL,
    symbol VARCHAR(32) NOT NULL,
    "interval" VARCHAR(16) NOT NULL,
    open_time TIMESTAMPTZ NOT NULL,
    close_time TIMESTAMPTZ NOT NULL,
    open NUMERIC(20, 8) NOT NULL,
    high NUMERIC(20, 8) NOT NULL,
    low NUMERIC(20, 8) NOT NULL,
    close NUMERIC(20, 8) NOT NULL,
    volume NUMERIC(28, 8) NOT NULL
    );

CREATE INDEX IF NOT EXISTS idx_candles_market_range
    ON candles (exchange, symbol, "interval", open_time);


CREATE TABLE IF NOT EXISTS runs (
                                    id BIGSERIAL PRIMARY KEY,
                                    user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
                                    strategy_id BIGINT NOT NULL REFERENCES strategy_files(id) ON DELETE RESTRICT,
    strategy_name VARCHAR(255) NOT NULL,
    dataset_id VARCHAR(64),
    run_name VARCHAR(255),
    correlation_id VARCHAR(128) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL,
    exchange VARCHAR(64) NOT NULL,
    symbol VARCHAR(64) NOT NULL,
    "interval" VARCHAR(32) NOT NULL,
    date_from TIMESTAMPTZ NOT NULL,
    date_to TIMESTAMPTZ NOT NULL,
    params_json TEXT,
    summary_json TEXT,
    metrics_json TEXT,
    artifacts_json TEXT,
    error_message TEXT,
    error_details_json TEXT,
    engine_version VARCHAR(128),
    execution_duration_ms BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ
    );

ALTER TABLE runs
    ADD COLUMN IF NOT EXISTS user_id BIGINT REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE runs
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

CREATE INDEX IF NOT EXISTS idx_runs_status
    ON runs (status);

CREATE INDEX IF NOT EXISTS idx_runs_created_at
    ON runs (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_runs_user_created_at
    ON runs (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_runs_strategy_id
    ON runs (strategy_id);

CREATE TABLE IF NOT EXISTS execution_jobs (
                                              id BIGSERIAL PRIMARY KEY,
                                              run_id BIGINT NOT NULL REFERENCES runs(id) ON DELETE CASCADE,
    user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(32) NOT NULL,
    priority INTEGER NOT NULL DEFAULT 0,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 3,
    queued_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    locked_at TIMESTAMPTZ,
    locked_by VARCHAR(128),
    cancel_requested BOOLEAN NOT NULL DEFAULT FALSE,
    error_code VARCHAR(128),
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );

ALTER TABLE execution_jobs
    ADD COLUMN IF NOT EXISTS user_id BIGINT REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE execution_jobs
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

CREATE INDEX IF NOT EXISTS idx_execution_jobs_user_created_at
    ON execution_jobs (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_execution_jobs_status_priority
    ON execution_jobs (status, priority DESC, queued_at ASC);

CREATE INDEX IF NOT EXISTS idx_execution_jobs_run_created_at
    ON execution_jobs (run_id, created_at DESC);

CREATE UNIQUE INDEX IF NOT EXISTS idx_execution_jobs_one_active_per_run
    ON execution_jobs (run_id)
    WHERE status IN ('QUEUED', 'RETRYING', 'RUNNING');

CREATE TABLE IF NOT EXISTS run_artifacts (
                                             id BIGSERIAL PRIMARY KEY,
                                             run_id BIGINT NOT NULL REFERENCES runs(id) ON DELETE CASCADE,
    artifact_type VARCHAR(64) NOT NULL,
    artifact_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    storage_path TEXT,
    payload_json TEXT,
    size_bytes BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );

CREATE INDEX IF NOT EXISTS idx_run_artifacts_run_id_created_at
    ON run_artifacts (run_id, created_at);

CREATE INDEX IF NOT EXISTS idx_run_artifacts_run_id_type
    ON run_artifacts (run_id, artifact_type);

ALTER TABLE runs
    ADD COLUMN IF NOT EXISTS run_name VARCHAR(255);

ALTER TABLE runs
    ADD COLUMN IF NOT EXISTS summary_json TEXT;

ALTER TABLE runs
    ADD COLUMN IF NOT EXISTS engine_version VARCHAR(128);

ALTER TABLE runs
    ADD COLUMN IF NOT EXISTS error_details_json TEXT;

ALTER TABLE runs
    ADD COLUMN IF NOT EXISTS execution_duration_ms BIGINT;

ALTER TABLE strategy_files
    ADD COLUMN IF NOT EXISTS user_id BIGINT REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE runs
    ADD COLUMN IF NOT EXISTS user_id BIGINT REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE runs
    ADD COLUMN IF NOT EXISTS strategy_version_id BIGINT REFERENCES strategy_versions(id) ON DELETE RESTRICT;

ALTER TABLE runs
    ADD COLUMN IF NOT EXISTS parameter_preset_id BIGINT REFERENCES strategy_parameter_presets(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_runs_strategy_version_id
    ON runs (strategy_version_id);


CREATE TABLE IF NOT EXISTS run_snapshots (
                                             run_id BIGINT PRIMARY KEY REFERENCES runs(id) ON DELETE CASCADE,
    strategy_version VARCHAR(128) NOT NULL,
    dataset_version VARCHAR(128) NOT NULL,
    dataset_snapshot_id BIGINT,
    params_snapshot_json TEXT NOT NULL,
    execution_config_snapshot_json TEXT NOT NULL,
    market_assumptions_snapshot_json TEXT NOT NULL,
    engine_version VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );

ALTER TABLE run_snapshots
    ADD COLUMN IF NOT EXISTS dataset_snapshot_id BIGINT;

ALTER TABLE run_snapshots
    ADD COLUMN IF NOT EXISTS strategy_version_id BIGINT REFERENCES strategy_versions(id) ON DELETE RESTRICT;

ALTER TABLE run_snapshots
    ADD COLUMN IF NOT EXISTS parameter_preset_id BIGINT REFERENCES strategy_parameter_presets(id) ON DELETE SET NULL;

ALTER TABLE run_snapshots
    ADD COLUMN IF NOT EXISTS parameter_preset_snapshot_json TEXT;

CREATE TABLE IF NOT EXISTS dataset_snapshots (
                                                 id BIGSERIAL PRIMARY KEY,
                                                 dataset_id VARCHAR(64) NOT NULL REFERENCES datasets(id) ON DELETE CASCADE,
    dataset_version VARCHAR(128) NOT NULL,
    source_exchange VARCHAR(64),
    symbol VARCHAR(64),
    "interval" VARCHAR(32),
    start_time TIMESTAMPTZ,
    end_time TIMESTAMPTZ,
    row_count INTEGER,
    checksum VARCHAR(128),
    source_metadata_json TEXT,
    coverage_metadata_json TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );

CREATE UNIQUE INDEX IF NOT EXISTS idx_dataset_snapshots_dataset_version
    ON dataset_snapshots (dataset_id, dataset_version);

CREATE INDEX IF NOT EXISTS idx_dataset_snapshots_dataset_created_at
    ON dataset_snapshots (dataset_id, created_at DESC);

CREATE TABLE IF NOT EXISTS dataset_quality_reports (
                                                       id BIGSERIAL PRIMARY KEY,
                                                       dataset_id VARCHAR(64) NOT NULL REFERENCES datasets(id) ON DELETE CASCADE,
    dataset_snapshot_id BIGINT REFERENCES dataset_snapshots(id) ON DELETE CASCADE,
    quality_status VARCHAR(32) NOT NULL,
    issues_json TEXT NOT NULL,
    checked_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );

CREATE INDEX IF NOT EXISTS idx_dataset_quality_reports_dataset_checked_at
    ON dataset_quality_reports (dataset_id, checked_at DESC);

CREATE INDEX IF NOT EXISTS idx_dataset_quality_reports_snapshot_checked_at
    ON dataset_quality_reports (dataset_snapshot_id, checked_at DESC);


CREATE TABLE IF NOT EXISTS backtest_trades (
                                               id BIGSERIAL PRIMARY KEY,
                                               run_id BIGINT NOT NULL REFERENCES runs(id) ON DELETE CASCADE,
    entry_time TIMESTAMPTZ,
    exit_time TIMESTAMPTZ,
    entry_price DOUBLE PRECISION NOT NULL,
    exit_price DOUBLE PRECISION NOT NULL,
    quantity DOUBLE PRECISION NOT NULL,
    pnl DOUBLE PRECISION NOT NULL,
    fee DOUBLE PRECISION NOT NULL
    );

CREATE INDEX IF NOT EXISTS idx_backtest_trades_run_id
    ON backtest_trades (run_id);

CREATE TABLE IF NOT EXISTS paper_trading_sessions (
                                                       id BIGSERIAL PRIMARY KEY,
                                                       user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    exchange VARCHAR(64) NOT NULL,
    symbol VARCHAR(64) NOT NULL,
    timeframe VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    initial_balance NUMERIC(28, 8) NOT NULL,
    current_balance NUMERIC(28, 8) NOT NULL,
    base_currency VARCHAR(32) NOT NULL,
    quote_currency VARCHAR(32) NOT NULL,
    started_at TIMESTAMPTZ,
    stopped_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );

CREATE INDEX IF NOT EXISTS idx_paper_trading_sessions_user_created_at
    ON paper_trading_sessions (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_paper_trading_sessions_user_status
    ON paper_trading_sessions (user_id, status);

CREATE TABLE IF NOT EXISTS paper_orders (
                                            id BIGSERIAL PRIMARY KEY,
                                            session_id BIGINT NOT NULL REFERENCES paper_trading_sessions(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    symbol VARCHAR(64) NOT NULL,
    side VARCHAR(16) NOT NULL,
    type VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL,
    quantity NUMERIC(28, 8) NOT NULL,
    price NUMERIC(28, 8),
    filled_quantity NUMERIC(28, 8) NOT NULL DEFAULT 0,
    average_fill_price NUMERIC(28, 8),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    filled_at TIMESTAMPTZ,
    rejected_reason TEXT
    );

CREATE INDEX IF NOT EXISTS idx_paper_orders_session_created_at
    ON paper_orders (session_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_paper_orders_user_created_at
    ON paper_orders (user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS paper_fills (
                                           id BIGSERIAL PRIMARY KEY,
                                           order_id BIGINT NOT NULL REFERENCES paper_orders(id) ON DELETE CASCADE,
    session_id BIGINT NOT NULL REFERENCES paper_trading_sessions(id) ON DELETE CASCADE,
    symbol VARCHAR(64) NOT NULL,
    side VARCHAR(16) NOT NULL,
    quantity NUMERIC(28, 8) NOT NULL,
    price NUMERIC(28, 8) NOT NULL,
    fee NUMERIC(28, 8) NOT NULL,
    fee_currency VARCHAR(32) NOT NULL,
    executed_at TIMESTAMPTZ NOT NULL
    );

CREATE INDEX IF NOT EXISTS idx_paper_fills_session_executed_at
    ON paper_fills (session_id, executed_at DESC);

CREATE INDEX IF NOT EXISTS idx_paper_fills_order_id
    ON paper_fills (order_id);

CREATE TABLE IF NOT EXISTS paper_positions (
                                               id BIGSERIAL PRIMARY KEY,
                                               session_id BIGINT NOT NULL REFERENCES paper_trading_sessions(id) ON DELETE CASCADE,
    symbol VARCHAR(64) NOT NULL,
    quantity NUMERIC(28, 8) NOT NULL,
    average_entry_price NUMERIC(28, 8) NOT NULL,
    realized_pnl NUMERIC(28, 8) NOT NULL,
    unrealized_pnl NUMERIC(28, 8) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT paper_positions_session_symbol_key UNIQUE (session_id, symbol)
    );

CREATE INDEX IF NOT EXISTS idx_paper_positions_session_id
    ON paper_positions (session_id);

CREATE INDEX IF NOT EXISTS idx_backtest_trades_run_id_entry_time
    ON backtest_trades (run_id, entry_time);


CREATE TABLE IF NOT EXISTS backtest_equity_points (
                                                      id BIGSERIAL PRIMARY KEY,
                                                      run_id BIGINT NOT NULL REFERENCES runs(id) ON DELETE CASCADE,
    timestamp TIMESTAMPTZ NOT NULL,
    equity DOUBLE PRECISION NOT NULL
    );

CREATE INDEX IF NOT EXISTS idx_backtest_equity_points_run_id_timestamp
    ON backtest_equity_points (run_id, timestamp);

CREATE TABLE IF NOT EXISTS live_exchange_credentials (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    exchange VARCHAR(64) NOT NULL,
    key_reference VARCHAR(128) NOT NULL,
    encrypted_api_key TEXT NOT NULL,
    encrypted_api_secret TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_live_exchange_credentials_user_exchange
    ON live_exchange_credentials (user_id, exchange, is_active, updated_at DESC);

CREATE TABLE IF NOT EXISTS live_trading_sessions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    exchange VARCHAR(64) NOT NULL,
    symbol VARCHAR(64) NOT NULL,
    base_currency VARCHAR(32) NOT NULL,
    quote_currency VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    max_order_notional NUMERIC(28, 8) NOT NULL,
    max_position_notional NUMERIC(28, 8) NOT NULL,
    max_daily_notional NUMERIC(28, 8) NOT NULL,
    symbol_whitelist TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_live_trading_sessions_user_created_at
    ON live_trading_sessions (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_live_trading_sessions_user_status
    ON live_trading_sessions (user_id, status);

CREATE TABLE IF NOT EXISTS live_orders (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    session_id BIGINT NOT NULL REFERENCES live_trading_sessions(id) ON DELETE CASCADE,
    strategy_id BIGINT REFERENCES strategy_files(id) ON DELETE SET NULL,
    strategy_version_id BIGINT REFERENCES strategy_versions(id) ON DELETE SET NULL,
    exchange VARCHAR(64) NOT NULL,
    symbol VARCHAR(64) NOT NULL,
    side VARCHAR(16) NOT NULL,
    type VARCHAR(16) NOT NULL,
    quantity NUMERIC(28, 8) NOT NULL,
    requested_price NUMERIC(28, 8),
    executed_price NUMERIC(28, 8),
    status VARCHAR(32) NOT NULL,
    exchange_order_id VARCHAR(128),
    submitted_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    filled_at TIMESTAMPTZ,
    rejected_reason TEXT,
    source_run_id BIGINT REFERENCES runs(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_live_orders_user_created_at
    ON live_orders (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_live_orders_user_status
    ON live_orders (user_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_live_orders_exchange_order_id
    ON live_orders (exchange, exchange_order_id);

CREATE TABLE IF NOT EXISTS live_positions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    exchange VARCHAR(64) NOT NULL,
    symbol VARCHAR(64) NOT NULL,
    quantity NUMERIC(28, 8) NOT NULL,
    average_entry_price NUMERIC(28, 8) NOT NULL,
    realized_pnl NUMERIC(28, 8) NOT NULL,
    unrealized_pnl NUMERIC(28, 8) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    sync_status VARCHAR(32) NOT NULL,
    CONSTRAINT live_positions_user_exchange_symbol_key UNIQUE (user_id, exchange, symbol)
);

CREATE INDEX IF NOT EXISTS idx_live_positions_user_exchange
    ON live_positions (user_id, exchange, symbol);

CREATE TABLE IF NOT EXISTS risk_events (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    order_id BIGINT REFERENCES live_orders(id) ON DELETE SET NULL,
    strategy_id BIGINT REFERENCES strategy_files(id) ON DELETE SET NULL,
    exchange VARCHAR(64) NOT NULL,
    symbol VARCHAR(64),
    event_type VARCHAR(64) NOT NULL,
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_risk_events_user_created_at
    ON risk_events (user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS circuit_breaker_state (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    exchange VARCHAR(64) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    reason TEXT,
    triggered_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT circuit_breaker_state_user_exchange_key UNIQUE (user_id, exchange)
);

CREATE INDEX IF NOT EXISTS idx_circuit_breaker_state_user_active
    ON circuit_breaker_state (user_id, active);

CREATE TABLE IF NOT EXISTS kill_switch_state (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    reason TEXT,
    activated_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT kill_switch_state_user_key UNIQUE (user_id)
);

CREATE TABLE IF NOT EXISTS testnet_certification_reports (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    exchange VARCHAR(64) NOT NULL,
    environment VARCHAR(32) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ NOT NULL,
    connectivity_status VARCHAR(64) NOT NULL,
    account_snapshot_status VARCHAR(64) NOT NULL,
    open_orders_status VARCHAR(64) NOT NULL,
    reconciliation_status VARCHAR(64) NOT NULL,
    risk_checks_status VARCHAR(64) NOT NULL,
    final_result VARCHAR(64) NOT NULL,
    real_order_submission_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    message TEXT
);

CREATE INDEX IF NOT EXISTS idx_testnet_certification_reports_user_exchange_finished
    ON testnet_certification_reports (user_id, exchange, finished_at DESC);
