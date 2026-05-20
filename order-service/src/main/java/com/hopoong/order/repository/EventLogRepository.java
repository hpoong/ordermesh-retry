package com.hopoong.order.repository;

import com.hopoong.order.entity.EventLog;
import com.hopoong.order.enums.EventPublishStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventLogRepository extends JpaRepository<EventLog, Long> {

    @Query("""
            SELECT eventLog
            FROM EventLog eventLog
            WHERE eventLog.publishStatus IN :statuses
              AND (eventLog.nextRetryAt IS NULL OR eventLog.nextRetryAt <= :now)
            ORDER BY eventLog.occurredAt ASC
            """)
    List<EventLog> findPublishCandidates(
            @Param("statuses") Collection<EventPublishStatus> statuses,
            @Param("now") LocalDateTime now
    );
}
