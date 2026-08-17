package com.quickticket.queue.scheduler;

import com.quickticket.event.domain.Event;
import com.quickticket.event.domain.EventStatus;
import com.quickticket.event.mapper.EventMapper;
import com.quickticket.queue.service.AdmissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * 입장 처리 스케줄러 — OPEN 이벤트마다 대기열 상위 세션을 예매 구역으로 입장시킨다.
 *
 * 다중 인스턴스 환경에서 여러 스케줄러가 동시에 입장시켜 정원을 초과하는 것을 막기 위해
 * 이벤트별 SETNX 락(SET ... NX EX)을 잡은 인스턴스만 처리한다.
 * (SETNX 락은 소유 토큰 확인 후 해제하지만 자동 연장 등은 없음 — 좌석 단계에서 Redisson 도입 시 통일 검토)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdmissionScheduler {

    private static final String LOCK_KEY_PREFIX = "admission:lock:";
    private static final Duration LOCK_TTL = Duration.ofSeconds(5);

    private final StringRedisTemplate redisTemplate;
    private final EventMapper eventMapper;
    private final AdmissionService admissionService;

    @Scheduled(fixedRateString = "${quickticket.admission.scheduler-rate-ms:1000}")
    public void admit() {
        List<Event> openEvents = eventMapper.findByStatusOrderByOpenDt(EventStatus.OPEN);
        for (Event event : openEvents) {
            runWithLock(event.getId());
        }
    }

    private void runWithLock(Long eventId) {
        String lockKey = LOCK_KEY_PREFIX + eventId;
        String token = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, token, LOCK_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            return; // 다른 인스턴스가 이미 처리 중
        }
        try {
            int admitted = admissionService.admitFromQueue(eventId);
            if (admitted > 0) {
                log.debug("admitted {} session(s) into event {}", admitted, eventId);
            }
        } finally {
            if (token.equals(redisTemplate.opsForValue().get(lockKey))) { // 내 락일 때만 해제
                redisTemplate.delete(lockKey);
            }
        }
    }
}
