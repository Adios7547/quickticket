package com.quickticket.queue.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 입장 제어 — 예매 구역(room)의 수용량을 관리한다.
 *
 * room 은 Sorted Set: member = session_key, score = 만료 시각(epoch ms).
 * "방에 있다 = 입장했다"이므로 별도 플래그를 두지 않는다. score 가 과거면 만료(방치·시간초과)로 보고 청소한다.
 * 대기열(queue)에서 room 으로 옮기는 실제 입장 처리는 스케줄러가 분산 락 아래에서 admitFromQueue 로 호출한다.
 */
@Service
@RequiredArgsConstructor
public class AdmissionService {

    private static final String ROOM_KEY_PREFIX = "admission:room:";
    private static final String QUEUE_KEY_PREFIX = "queue:";

    private final StringRedisTemplate redisTemplate;

    @Value("${quickticket.admission.max-room:10}")
    private int maxRoom;

    @Value("${quickticket.admission.ttl-sec:300}")
    private long ttlSec;

    /** 이 세션이 현재 예매 구역에 입장한 상태인가 (만료 전이어야 함) */
    public boolean isAdmitted(Long eventId, String sessionKey) {
        Double expireAt = redisTemplate.opsForZSet().score(roomKey(eventId), sessionKey);
        return expireAt != null && expireAt > System.currentTimeMillis();
    }

    /** 활동 갱신 — 입장 상태인 세션의 만료 시각을 지금+TTL 로 연장한다 (좌석 고르는 중 쫓겨나지 않도록). */
    public void renew(Long eventId, String sessionKey) {
        long now = System.currentTimeMillis();
        Double expireAt = redisTemplate.opsForZSet().score(roomKey(eventId), sessionKey);
        if (expireAt != null && expireAt > now) { // 만료된 세션을 되살리지 않도록 현재 유효할 때만 연장
            redisTemplate.opsForZSet().add(roomKey(eventId), sessionKey, now + ttlSec * 1000);
        }
    }

    /** 예매 완료 등으로 자리 반납 — 즉시 room 에서 제거하여 다음 사람이 들어올 수 있게 한다. */
    public void release(Long eventId, String sessionKey) {
        redisTemplate.opsForZSet().remove(roomKey(eventId), sessionKey);
    }

    /** 현재 예매 구역 인원 (만료자 청소 후 기준) */
    public long occupancy(Long eventId) {
        String roomKey = roomKey(eventId);
        redisTemplate.opsForZSet().removeRangeByScore(roomKey, 0, System.currentTimeMillis());
        Long count = redisTemplate.opsForZSet().zCard(roomKey);
        return count == null ? 0 : count;
    }

    /**
     * 대기열 상위 세션을 여유분만큼 입장시킨다. 스케줄러가 분산 락 아래에서만 호출해야 한다.
     * 1) 만료자 청소  2) 여유 = 정원 - 현재인원  3) 큐 상위 여유명을 ZPOPMIN 으로 꺼내 room 에 추가.
     * @return 이번에 입장시킨 인원 수
     */
    public int admitFromQueue(Long eventId) {
        long now = System.currentTimeMillis();
        String roomKey = roomKey(eventId);
        String queueKey = queueKey(eventId);

        redisTemplate.opsForZSet().removeRangeByScore(roomKey, 0, now); // 만료자 청소
        Long occ = redisTemplate.opsForZSet().zCard(roomKey);
        long occupancy = occ == null ? 0 : occ;
        long free = maxRoom - occupancy;
        if (free <= 0) {
            return 0;
        }

        Set<TypedTuple<String>> promoted = redisTemplate.opsForZSet().popMin(queueKey, free);
        if (promoted == null || promoted.isEmpty()) {
            return 0;
        }
        long expireAt = now + ttlSec * 1000;
        int admitted = 0;
        for (TypedTuple<String> tuple : promoted) {
            String sessionKey = tuple.getValue();
            if (sessionKey != null) {
                redisTemplate.opsForZSet().add(roomKey, sessionKey, expireAt);
                admitted++;
            }
        }
        return admitted;
    }

    private String roomKey(Long eventId) {
        return ROOM_KEY_PREFIX + eventId;
    }

    private String queueKey(Long eventId) {
        return QUEUE_KEY_PREFIX + eventId;
    }
}
