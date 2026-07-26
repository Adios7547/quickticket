package com.quickticket.event.scheduler;

import com.quickticket.event.mapper.EventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * open_dt가 도래한 READY 이벤트를 자동으로 OPEN 전환한다.
 * (원 스펙의 수동 캐싱/오픈 트리거 API는 삭제되었고, Kafka 기반 자동 처리로 대체 예정 —
 *  Kafka 파이프라인이 들어가기 전까지 폴링 스케줄러로 대신한다.)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventOpenScheduler {

    private final EventMapper eventMapper;

    @Scheduled(fixedRate = 5000)
    @Transactional
    public void openReadyEvents() {
        int opened = eventMapper.openReadyEvents(LocalDateTime.now());
        if (opened > 0) {
            log.info("opened {} event(s) whose open_dt has arrived", opened);
        }
    }
}
