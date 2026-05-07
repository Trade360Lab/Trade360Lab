package com.example.back.livetrading.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Data;

@Data
@Entity
@Table(name = "testnet_certification_reports")
public class TestnetCertificationReportEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String exchange;

    @Column(nullable = false)
    private String environment;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at", nullable = false)
    private Instant finishedAt;

    @Column(name = "connectivity_status", nullable = false)
    private String connectivityStatus;

    @Column(name = "account_snapshot_status", nullable = false)
    private String accountSnapshotStatus;

    @Column(name = "open_orders_status", nullable = false)
    private String openOrdersStatus;

    @Column(name = "reconciliation_status", nullable = false)
    private String reconciliationStatus;

    @Column(name = "risk_checks_status", nullable = false)
    private String riskChecksStatus;

    @Column(name = "final_result", nullable = false)
    private String finalResult;

    @Column(name = "real_order_submission_enabled", nullable = false)
    private boolean realOrderSubmissionEnabled;

    @Column(columnDefinition = "TEXT")
    private String message;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (startedAt == null) {
            startedAt = now;
        }
        if (finishedAt == null) {
            finishedAt = now;
        }
    }
}
