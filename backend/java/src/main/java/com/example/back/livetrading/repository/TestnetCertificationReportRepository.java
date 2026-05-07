package com.example.back.livetrading.repository;

import com.example.back.livetrading.entity.TestnetCertificationReportEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestnetCertificationReportRepository extends JpaRepository<TestnetCertificationReportEntity, Long> {

    Optional<TestnetCertificationReportEntity> findFirstByUserIdAndExchangeOrderByFinishedAtDesc(Long userId, String exchange);

    Optional<TestnetCertificationReportEntity> findByIdAndUserId(Long id, Long userId);
}
