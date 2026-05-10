package com.example.back.livetrading.repository;

import com.example.back.livetrading.entity.LiveRiskEventEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface LiveRiskEventRepository extends JpaRepository<LiveRiskEventEntity, Long>, JpaSpecificationExecutor<LiveRiskEventEntity> {

    List<LiveRiskEventEntity> findTop50ByUserIdOrderByCreatedAtDesc(Long userId);

    List<LiveRiskEventEntity> findAllByUserIdAndOrderIdOrderByCreatedAtDesc(Long userId, Long orderId);

    Optional<LiveRiskEventEntity> findFirstByUserIdOrderByCreatedAtDesc(Long userId);
}
