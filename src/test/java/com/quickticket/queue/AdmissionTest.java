package com.quickticket.queue;

import com.quickticket.event.domain.Event;
import com.quickticket.event.domain.EventStatus;
import com.quickticket.event.mapper.EventMapper;
import com.quickticket.queue.dto.QueueRankResponse;
import com.quickticket.queue.service.AdmissionService;
import com.quickticket.queue.service.QueueService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 입장 제어(admission) 검증. 정원 3으로 축소해 초과 인원 대기 / 자리 반납 승격 / 만료 청소를 확인한다.
 * admitFromQueue 를 직접 호출하므로 스케줄러 타이밍과 무관하게 결정적으로 테스트된다.
 */
@SpringBootTest
@Transactional
@TestPropertySource(properties = {
        "quickticket.admission.max-room=3",
        "quickticket.admission.ttl-sec=300"
})
class AdmissionTest {

    @Autowired
    QueueService queueService;

    @Autowired
    AdmissionService admissionService;

    @Autowired
    EventMapper eventMapper;

    @Autowired
    StringRedisTemplate redisTemplate;

    private Long eventId;

    @AfterEach
    void cleanupRedis() {
        if (eventId != null) {
            redisTemplate.delete("queue:" + eventId);
            redisTemplate.delete("admission:room:" + eventId);
        }
    }

    private long openEvent() {
        Event event = Event.builder()
                .eventNm("아이유 2026 콘서트")
                .openDt(LocalDateTime.now().minusMinutes(10))
                .totalQuota(1000)
                .issuedCount(0)
                .status(EventStatus.OPEN)
                .build();
        eventMapper.insert(event);
        eventId = event.getId();
        return eventId;
    }

    /** n명을 순서대로 대기열에 진입시키고 세션키 목록을 진입 순서대로 반환 */
    private List<String> enterQueue(long eventId, int n) throws InterruptedException {
        List<String> sessions = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String session = UUID.randomUUID().toString();
            queueService.enter(eventId, session, true);
            sessions.add(session);
            Thread.sleep(2); // 진입 시각(score) 차이를 벌려 선착순을 확정
        }
        return sessions;
    }

    @Test
    @DisplayName("정원 초과 시 상위 정원만 입장, 나머지는 대기 유지")
    void admitsUpToCapacity() throws Exception {
        long eventId = openEvent();
        List<String> sessions = enterQueue(eventId, 5); // 정원 3 < 5명

        int admitted = admissionService.admitFromQueue(eventId);

        assertThat(admitted).isEqualTo(3);
        assertThat(admissionService.occupancy(eventId)).isEqualTo(3);
        // 먼저 진입한 3명 입장, 뒤 2명 대기
        assertThat(admissionService.isAdmitted(eventId, sessions.get(0))).isTrue();
        assertThat(admissionService.isAdmitted(eventId, sessions.get(2))).isTrue();
        assertThat(admissionService.isAdmitted(eventId, sessions.get(3))).isFalse();
        assertThat(redisTemplate.opsForZSet().zCard("queue:" + eventId)).isEqualTo(2L);
    }

    @Test
    @DisplayName("rank 폴링: 입장자는 admitted=true, 대기자는 순번 반환")
    void rankReflectsAdmission() throws Exception {
        long eventId = openEvent();
        List<String> sessions = enterQueue(eventId, 5);
        admissionService.admitFromQueue(eventId);

        QueueRankResponse admitted = queueService.rank(eventId, sessions.get(0));
        assertThat(admitted.admitted()).isTrue();
        assertThat(admitted.rank()).isZero();

        QueueRankResponse waiting = queueService.rank(eventId, sessions.get(4));
        assertThat(waiting.admitted()).isFalse();
        assertThat(waiting.rank()).isPositive();
    }

    @Test
    @DisplayName("자리 반납(release) 시 다음 대기자가 승격")
    void releaseFreesSlotForNext() throws Exception {
        long eventId = openEvent();
        List<String> sessions = enterQueue(eventId, 5);
        admissionService.admitFromQueue(eventId);

        // 입장자 1명이 예매 완료로 나감
        admissionService.release(eventId, sessions.get(0));
        assertThat(admissionService.occupancy(eventId)).isEqualTo(2);

        // 다시 입장 처리하면 대기 중이던 4번째(sessions[3])가 승격
        int admitted = admissionService.admitFromQueue(eventId);
        assertThat(admitted).isEqualTo(1);
        assertThat(admissionService.isAdmitted(eventId, sessions.get(3))).isTrue();
        assertThat(admissionService.occupancy(eventId)).isEqualTo(3);
    }

    @Test
    @DisplayName("만료된 입장자는 청소되고 그 자리에 대기자가 입장")
    void expiredSlotReclaimed() throws Exception {
        long eventId = openEvent();
        List<String> sessions = enterQueue(eventId, 5);
        admissionService.admitFromQueue(eventId); // sessions[0..2] 입장, [3],[4] 대기

        // 입장자 1명의 만료 시각을 과거로 조작 (방치·시간초과 재현)
        redisTemplate.opsForZSet().add("admission:room:" + eventId, sessions.get(0),
                System.currentTimeMillis() - 1000);

        int admitted = admissionService.admitFromQueue(eventId);

        assertThat(admissionService.isAdmitted(eventId, sessions.get(0))).isFalse(); // 청소됨
        assertThat(admitted).isEqualTo(1); // 빈 자리에 1명 승격
        assertThat(admissionService.occupancy(eventId)).isEqualTo(3);
    }
}
