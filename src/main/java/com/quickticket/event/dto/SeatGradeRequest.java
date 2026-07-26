package com.quickticket.event.dto;

import jakarta.validation.constraints.NotBlank;

/** 좌석별 등급 지정 (미지정 좌석은 기본 A 등급) */
public record SeatGradeRequest(
        @NotBlank(message = "seat_no is required")
        String seatNo,

        @NotBlank(message = "grade is required")
        String grade
) {
}
