package com.hopoong.order.publisher;

import com.hopoong.order.entity.EventLog;
import com.hopoong.order.enums.EventPublishStatus;
import com.hopoong.order.repository.EventLogRepository;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EventLogPublishTransactionService {

    private final EventLogRepository eventLogRepository;

    // [발행 대상(READY/RETRYING) 이벤트 조회]
    @Transactional(readOnly = true)
    public List<EventLog> findPublishCandidates(Collection<EventPublishStatus> statuses, LocalDateTime now) {
        return eventLogRepository.findPublishCandidates(statuses, now);
    }

    // [재검증 + 재시도 카운터/상태 갱신]
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public EventLog claimForPublish(Long eventLogId, LocalDateTime now, Collection<EventPublishStatus> statuses) {
        EventLog eventLog = eventLogRepository.findById(eventLogId).orElse(null);
        if (eventLog == null || !statuses.contains(eventLog.getPublishStatus())) {
            return null;
        }

        if (eventLog.getNextRetryAt() != null && eventLog.getNextRetryAt().isAfter(now)) {
            return null;
        }

        eventLog.markPublishAttemptStarted(now);
        return eventLog;
    }

    // [발행 성공 상태(PUBLISHED) 반영]
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPublished(Long eventLogId, LocalDateTime now) {
        EventLog eventLog = eventLogRepository.findById(eventLogId)
                .orElseThrow(() -> new IllegalStateException("발행 완료 처리 대상 이벤트를 찾을 수 없습니다. id=" + eventLogId));
        eventLog.markPublished(now);
    }

    // [발행 실패 상태(RETRYING 또는 FAILED) 반영]
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public EventLog markPublishFailure(Long eventLogId, String failureReason, LocalDateTime now, LocalDateTime nextRetryAt) {
        EventLog eventLog = eventLogRepository.findById(eventLogId)
                .orElseThrow(() -> new IllegalStateException("발행 실패 처리 대상 이벤트를 찾을 수 없습니다. id=" + eventLogId));

        if (nextRetryAt == null) {
            eventLog.markFailed(failureReason, now);
        } else {
            eventLog.markRetryScheduled(nextRetryAt, failureReason, now);
        }

        return eventLog;
    }
}
