package com.quickticket.queue.controller;

import com.quickticket.common.api.ApiResponse;
import com.quickticket.queue.dto.QueueStatsResponse;
import com.quickticket.queue.service.QueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AdminQueueController {

    private final QueueService queueService;

    /** 대기열 관제 통계 (구 GET /queue/{eventId}/status에서 이동) */
    @GetMapping("/admin/events/{eventId}/queue/stats")
    public ApiResponse<QueueStatsResponse> stats(@PathVariable Long eventId) {
        return ApiResponse.success("success", queueService.stats(eventId));
    }
}
