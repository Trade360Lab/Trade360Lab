package com.example.back.livetrading.repository;

import com.example.back.livetrading.entity.LiveOrderEntity;
import com.example.back.livetrading.entity.LiveOrderSide;
import com.example.back.livetrading.entity.LiveOrderStatus;
import com.example.back.livetrading.entity.LiveOrderType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LiveOrderRepository extends JpaRepository<LiveOrderEntity, Long> {

    List<LiveOrderEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<LiveOrderEntity> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndExchangeAndSymbolAndSideAndTypeAndQuantityAndStatusInAndIdNot(
            Long userId,
            String exchange,
            String symbol,
            LiveOrderSide side,
            LiveOrderType type,
            java.math.BigDecimal quantity,
            Collection<LiveOrderStatus> statuses,
            Long id
    );

    long countByUserIdAndExchangeAndStatus(Long userId, String exchange, LiveOrderStatus status);

    List<LiveOrderEntity> findAllByUserIdAndExchangeAndStatusIn(Long userId, String exchange, Collection<LiveOrderStatus> statuses);

    List<LiveOrderEntity> findAllByUserIdAndSessionIdAndStatusInAndCreatedAtGreaterThanEqual(
            Long userId,
            Long sessionId,
            Collection<LiveOrderStatus> statuses,
            Instant createdAt
    );
}

