package com.hopoong.account.repository;

import com.hopoong.account.entity.PointHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointHistoryRepository extends JpaRepository<PointHistoryEntity, Long> {

    boolean existsByEventId(String eventId);
}
