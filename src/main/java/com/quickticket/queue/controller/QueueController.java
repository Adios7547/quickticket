package com.quickticket.queue.controller;

import com.quickticket.common.api.ApiResponse;
import com.quickticket.queue.dto.QueueEnterResponse;
import com.quickticket.queue.dto.QueueRankResponse;
import com.quickticket.queue.service.QueueService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/queue")
@RequiredArgsConstructor
public class QueueController {

    private static final String SESSION_COOKIE = "session_key";

    private final QueueService queueService;

    @PostMapping("/{eventId}/enter")
    public ApiResponse<QueueEnterResponse> enter(@PathVariable Long eventId,
                                                 @CookieValue(name = SESSION_COOKIE, required = false) String existingSessionKey,
                                                 HttpServletResponse response) {
        boolean isNewSession = existingSessionKey == null;
        String sessionKey = isNewSession ? UUID.randomUUID().toString() : existingSessionKey;

        QueueEnterResponse result = queueService.enter(eventId, sessionKey, isNewSession);

        if (isNewSession) {
            ResponseCookie cookie = ResponseCookie.from(SESSION_COOKIE, sessionKey)
                    .httpOnly(true)
                    .path("/")
                    .build();
            response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        }
        return ApiResponse.success("entered", result);
    }

    @GetMapping("/{eventId}/rank")
    public ApiResponse<QueueRankResponse> rank(@PathVariable Long eventId,
                                               @CookieValue(name = SESSION_COOKIE, required = false) String sessionKey) {
        return ApiResponse.success("success", queueService.rank(eventId, sessionKey));
    }
}
