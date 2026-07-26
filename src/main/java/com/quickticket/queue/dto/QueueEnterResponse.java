package com.quickticket.queue.dto;

public record QueueEnterResponse(
        Long eventId,
        String sessionKey,
        long rank,
        long estimatedWaitSec
) {
}
