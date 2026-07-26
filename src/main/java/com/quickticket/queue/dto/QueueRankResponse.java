package com.quickticket.queue.dto;

public record QueueRankResponse(
        Long eventId,
        long rank,
        long estimatedWaitSec,
        long totalWaiting
) {
}
