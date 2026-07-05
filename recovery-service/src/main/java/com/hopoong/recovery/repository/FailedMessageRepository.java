package com.hopoong.recovery.repository;

import com.hopoong.recovery.entity.FailedMessage;
import com.hopoong.recovery.enums.FailureType;
import com.hopoong.recovery.enums.ReprocessStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FailedMessageRepository extends JpaRepository<FailedMessage, Long> {

    Optional<FailedMessage> findByConsumerNameAndEventId(String consumerName, String eventId);

    List<FailedMessage> findByReprocessStatus(ReprocessStatus reprocessStatus);

    boolean existsByConsumerNameAndEventId(String consumerName, String eventId);

    @Query("""
            select fm
            from FailedMessage fm
            where (:reprocessStatus is null or fm.reprocessStatus = :reprocessStatus)
              and (:failureType is null or fm.failureType = :failureType)
            order by fm.lastFailedAt desc, fm.id desc
            """)
    List<FailedMessage> findAllByFilters(
            @Param("reprocessStatus") ReprocessStatus reprocessStatus,
            @Param("failureType") FailureType failureType
    );
}
