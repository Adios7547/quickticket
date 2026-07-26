package com.quickticket.queue.dto;

public record QueueStatsResponse(
        Long eventId,
        long totalWaiting,
        int issuedCount,
        int totalQuota,
        double consumptionRate
) {
}
