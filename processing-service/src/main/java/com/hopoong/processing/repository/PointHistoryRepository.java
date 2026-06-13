package com.hopoong.processing.repository;

import com.hopoong.processing.entity.PointHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointHistoryRepository extends JpaRepository<PointHistory, Long> {

    boolean existsByEventId(String eventId);
}
