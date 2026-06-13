package com.hopoong.processing.repository;

import com.hopoong.processing.entity.MessageProcessLog;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageProcessLogRepository extends JpaRepository<MessageProcessLog, Long> {

    boolean existsByEventIdAndConsumerNameAndProcessStatusIn(
            String eventId,
            String consumerName,
            Collection<String> processStatuses
    );
}
