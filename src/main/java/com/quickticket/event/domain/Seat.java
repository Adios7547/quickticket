package com.quickticket.event.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Seat {

    private Long id;

    private Long eventId;

    /** 좌석 번호 (예: A-1) */
    private String seatNo;

    private SeatGrade grade;

    private SeatStatus status;
}
