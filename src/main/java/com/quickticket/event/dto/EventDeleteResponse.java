package com.quickticket.event.dto;

public record EventDeleteResponse(
        Long eventId,
        String status
) {
    public static EventDeleteResponse of(Long eventId) {
        return new EventDeleteResponse(eventId, "DELETED");
    }
}
