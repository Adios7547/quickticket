package com.quickticket.event.dto;

/** 이벤트 수정 요청 — 모든 필드 optional, 전달된 값만 반영 */
public record EventUpdateRequest(
        String eventNm,
        Integer totalQuota,
        String openDt,
        String validFrom,
        String validTo
) {
}
