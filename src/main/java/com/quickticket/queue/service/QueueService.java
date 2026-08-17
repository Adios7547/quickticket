package com.quickticket.queue.service;

import com.quickticket.captcha.service.CaptchaService;
import com.quickticket.common.error.BusinessException;
import com.quickticket.common.error.ErrorCode;
import com.quickticket.event.domain.Event;
import com.quickticket.event.domain.EventStatus;
import com.quickticket.event.mapper.EventMapper;
import com.quickticket.queue.dto.QueueEnterResponse;
import com.quickticket.queue.dto.QueueRankResponse;
import com.quickticket.queue.dto.QueueStatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis Sorted Set 기반 가상 대기열.
 * key: queue:{eventId}, member: session_key, score: 진입 시각(ms) — 먼저 진입한 세션이 낮은 순위를 가짐.
 */
@Service
@RequiredArgsConstructor
public class QueueService {

    private static final String QUEUE_KEY_PREFIX = "queue:";

    private final StringRedisTemplate redisTemplate;
    private final EventMapper eventMapper;
    private final CaptchaService captchaService;
    private final AdmissionService admissionService;

    @Value("${quickticket.queue.processing-rate-per-sec:50}")
    private int processingRatePerSec;

    public QueueEnterResponse enter(Long eventId, String sessionKey, boolean isNewSession) {
        Event event = findEvent(eventId);
        if (event.getStatus() != EventStatus.OPEN) {
            throw new BusinessException(ErrorCode.QUEUE_NOT_OPEN);
        }

        String key = queueKey(eventId);
        if (!isNewSession && redisTemplate.opsForZSet().score(key, sessionKey) != null) {
            throw new BusinessException(ErrorCode.ALREADY_ENTERED);
        }

        redisTemplate.opsForZSet().add(key, sessionKey, System.currentTimeMillis());
        long rank = rankOf(key, sessionKey);
        return new QueueEnterResponse(eventId, sessionKey, rank, estimateWaitSec(rank));
    }

    public QueueRankResponse rank(Long eventId, String sessionKey) {
        if (sessionKey == null) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }
        // 1. 이미 입장했으면 순번 대신 admitted 반환 + 활동 갱신(입장 상태 TTL 연장)
        if (admissionService.isAdmitted(eventId, sessionKey)) {
            admissionService.renew(eventId, sessionKey);
            return new QueueRankResponse(eventId, 0, 0, totalWaiting(eventId), false, true);
        }
        // 2. 대기 중이면 큐 순번 조회 (입장하면서 큐에서 빠졌거나 세션이 만료됐으면 not found)
        String key = queueKey(eventId);
        Long zeroBasedRank = redisTemplate.opsForZSet().rank(key, sessionKey);
        if (zeroBasedRank == null) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }
        long rank = zeroBasedRank + 1;
        boolean captchaRequired = captchaService.isChallengeRequired(eventId, sessionKey);
        return new QueueRankResponse(eventId, rank, estimateWaitSec(rank), totalWaiting(eventId), captchaRequired, false);
    }

    public QueueStatsResponse stats(Long eventId) {
        Event event = findEvent(eventId);
        double consumptionRate = event.getTotalQuota() == 0 ? 0
                : event.getIssuedCount() * 100.0 / event.getTotalQuota();
        return new QueueStatsResponse(eventId, totalWaiting(eventId), event.getIssuedCount(),
                event.getTotalQuota(), consumptionRate);
    }

    private long totalWaiting(Long eventId) {
        Long count = redisTemplate.opsForZSet().zCard(queueKey(eventId));
        return count == null ? 0 : count;
    }

    private long rankOf(String key, String sessionKey) {
        Long zeroBased = redisTemplate.opsForZSet().rank(key, sessionKey);
        return (zeroBased == null ? 0 : zeroBased) + 1;
    }

    /** 처리량은 임시 상수(quickticket.queue.processing-rate-per-sec) — 부하 테스트 단계에서 실측치로 보정 예정 */
    private long estimateWaitSec(long rank) {
        return (long) Math.ceil((double) rank / processingRatePerSec);
    }

    private Event findEvent(Long eventId) {
        Event event = eventMapper.findById(eventId);
        if (event == null) {
            throw new BusinessException(ErrorCode.EVENT_NOT_FOUND);
        }
        return event;
    }

    private String queueKey(Long eventId) {
        return QUEUE_KEY_PREFIX + eventId;
    }
}
