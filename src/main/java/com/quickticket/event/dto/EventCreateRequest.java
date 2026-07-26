package com.quickticket.event.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * 이벤트 생성 요청. 좌석 맵 등록(구 POST /admin/events/{eventId}/seats)이 통합되어
 * row_cnt/col_cnt/seats(선택)를 함께 받는다.
 */
public record EventCreateRequest(
        @NotBlank(message = "event_nm is required")
        String eventNm,

        @NotBlank(message = "open_dt is required")
        String openDt,

        @NotNull(message = "total_quota is required")
        @Positive(message = "total_quota must be positive")
        Integer totalQuota,

        @NotBlank(message = "valid_from is required")
        String validFrom,

        @NotBlank(message = "valid_to is required")
        String validTo,

        Integer rowCnt,

        Integer colCnt,

        @Valid
        List<SeatGradeRequest> seats
) {
    public boolean hasSeatMap() {
        return rowCnt != null || colCnt != null;
    }
}
