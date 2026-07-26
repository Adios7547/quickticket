package com.quickticket.event.dto;

import com.quickticket.common.util.DateTimeUtil;
import com.quickticket.event.domain.Event;

import java.util.List;

/** 사용자 진입점: 현재 오픈된 이벤트 (잔여 수량 포함) */
public record ActiveEventResponse(
        Long eventId,
        String eventNm,
        String openDt,
        Integer totalQuota,
        Integer remainingQuota,
        String status
) {
    public static ActiveEventResponse from(Event event) {
        return new ActiveEventResponse(event.getId(), event.getEventNm(), DateTimeUtil.format(event.getOpenDt()),
                event.getTotalQuota(), event.remainingQuota(), event.getStatus().name());
    }

    public record Events(List<ActiveEventResponse> events) {
    }
}
