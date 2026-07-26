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

    /** 좌석 번호 (예: A-1) — 사람이 읽는 식별자, URL 경로 등에 사용 */
    private String seatNo;

    /** 행 번호(1부터). seat_no 문자열 정렬이 자릿수에 따라 깨지는 문제를 피하기 위해 별도 저장 */
    private int rowNo;

    /** 열 번호(1부터) */
    private int colNo;

    private SeatGrade grade;

    private SeatStatus status;
}
