package com.example.back.livetrading.repository;

import com.example.back.livetrading.entity.LivePositionEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LivePositionRepository extends JpaRepository<LivePositionEntity, Long> {

    List<LivePositionEntity> findAllByUserIdOrderByExchangeAscSymbolAsc(Long userId);

    Optional<LivePositionEntity> findByUserIdAndExchangeAndSymbol(Long userId, String exchange, String symbol);

    List<LivePositionEntity> findAllByUserIdAndExchange(Long userId, String exchange);
}

