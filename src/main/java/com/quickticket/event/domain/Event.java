package com.quickticket.event.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Event {

    private Long id;

    private String eventNm;

    /** 쿠폰 발급(대기열 오픈) 시작 시각 */
    private LocalDateTime openDt;

    /** 쿠폰 총 발행 수량 */
    private int totalQuota;

    /** 현재까지 발급된 수량 */
    private int issuedCount;

    private LocalDate validFrom;
    private LocalDate validTo;

    private EventStatus status;

    private String closeReason;
    private LocalDateTime closedAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public boolean isReady() {
        return status == EventStatus.READY;
    }

    public void update(String eventNm, LocalDateTime openDt, Integer totalQuota,
                       LocalDate validFrom, LocalDate validTo) {
        if (eventNm != null) this.eventNm = eventNm;
        if (openDt != null) this.openDt = openDt;
        if (totalQuota != null) this.totalQuota = totalQuota;
        if (validFrom != null) this.validFrom = validFrom;
        if (validTo != null) this.validTo = validTo;
    }

    public void close(String reason) {
        this.status = EventStatus.CLOSED;
        this.closeReason = reason;
        this.closedAt = LocalDateTime.now();
    }

    public int remainingQuota() {
        return totalQuota - issuedCount;
    }
}
