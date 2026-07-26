package com.quickticket.event.controller;

import com.quickticket.common.api.ApiResponse;
import com.quickticket.event.dto.ActiveEventResponse;
import com.quickticket.event.service.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    /** 사용자 진입점: 현재 오픈된 이벤트 목록 (인증 불필요) */
    @GetMapping("/active")
    public ApiResponse<ActiveEventResponse.Events> active() {
        return ApiResponse.success("success", eventService.activeEvents());
    }
}
