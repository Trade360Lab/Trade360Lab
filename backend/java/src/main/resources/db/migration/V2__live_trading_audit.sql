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
