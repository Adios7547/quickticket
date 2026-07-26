package com.quickticket.event.dto;

import com.quickticket.common.util.DateTimeUtil;
import com.quickticket.event.domain.Event;

public record EventCloseResponse(
        Long eventId,
        String status,
        String closedAt,
        boolean queueBlocked
) {
    public static EventCloseResponse from(Event event) {
        // queue_blocked: 대기열 차단 플래그 — Redis 대기열 구현 시 실제 플래그 전환과 연동 예정
        return new EventCloseResponse(event.getId(), event.getStatus().name(),
                DateTimeUtil.format(event.getClosedAt()), true);
    }
}
