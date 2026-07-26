package com.quickticket.event.controller;

import com.quickticket.common.api.ApiResponse;
import com.quickticket.event.domain.EventStatus;
import com.quickticket.event.dto.EventCloseRequest;
import com.quickticket.event.dto.EventCloseResponse;
import com.quickticket.event.dto.EventCreateRequest;
import com.quickticket.event.dto.EventDeleteResponse;
import com.quickticket.event.dto.EventListResponse;
import com.quickticket.event.dto.EventResponse;
import com.quickticket.event.dto.EventUpdateRequest;
import com.quickticket.event.service.EventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/events")
@RequiredArgsConstructor
public class AdminEventController {

    private final EventService eventService;

    @PostMapping
    public ResponseEntity<ApiResponse<EventResponse>> create(@Valid @RequestBody EventCreateRequest request) {
        EventResponse response = eventService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("created", response));
    }

    @GetMapping
    public ApiResponse<EventListResponse> list(@RequestParam(required = false) EventStatus status,
                                               @RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success("success", eventService.list(status, page, size));
    }

    @GetMapping("/{eventId}")
    public ApiResponse<EventResponse> get(@PathVariable Long eventId) {
        return ApiResponse.success("success", eventService.get(eventId));
    }

    @PutMapping("/{eventId}")
    public ApiResponse<EventResponse> update(@PathVariable Long eventId,
                                             @RequestBody EventUpdateRequest request) {
        return ApiResponse.success("updated", eventService.update(eventId, request));
    }

    @PostMapping("/{eventId}/close")
    public ApiResponse<EventCloseResponse> close(@PathVariable Long eventId,
                                                 @RequestBody(required = false) EventCloseRequest request) {
        String reason = request == null ? null : request.reason();
        return ApiResponse.success("closed", eventService.close(eventId, reason));
    }

    @DeleteMapping("/{eventId}")
    public ApiResponse<EventDeleteResponse> delete(@PathVariable Long eventId) {
        return ApiResponse.success("deleted", eventService.delete(eventId));
    }
}
