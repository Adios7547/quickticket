package com.quickticket.event.dto;

import java.util.List;

public record EventListResponse(
        List<EventResponse> events,
        long totalCount,
        int page,
        int size
) {
}
