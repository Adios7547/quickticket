package com.quickticket.event.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.quickticket.common.util.DateTimeUtil;
import com.quickticket.event.domain.Event;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventResponse(
        Long eventId,
        String eventNm,
        String openDt,
        Integer totalQuota,
        Integer issuedCount,
        String validFrom,
        String validTo,
        String status,
        Integer registeredSeatCnt
) {
    /** 생성 응답: 좌석 등록 수 포함 */
    public static EventResponse created(Event event, Integer registeredSeatCnt) {
        return new EventResponse(event.getId(), event.getEventNm(), DateTimeUtil.format(event.getOpenDt()),
                event.getTotalQuota(), null, null, null, event.getStatus().name(), registeredSeatCnt);
    }

    /** 목록 응답: 발급 현황 포함 */
    public static EventResponse summary(Event event) {
        return new EventResponse(event.getId(), event.getEventNm(), DateTimeUtil.format(event.getOpenDt()),
                event.getTotalQuota(), event.getIssuedCount(), null, null, event.getStatus().name(), null);
    }

    /** 상세 응답: 유효기간까지 전체 필드 */
    public static EventResponse detail(Event event) {
        return new EventResponse(event.getId(), event.getEventNm(), DateTimeUtil.format(event.getOpenDt()),
                event.getTotalQuota(), event.getIssuedCount(), DateTimeUtil.format(event.getValidFrom()),
                DateTimeUtil.format(event.getValidTo()), event.getStatus().name(), null);
    }
}
