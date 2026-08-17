package com.quickticket.captcha.controller;

import com.quickticket.captcha.dto.CaptchaChallengeResponse;
import com.quickticket.captcha.dto.CaptchaVerifyRequest;
import com.quickticket.captcha.dto.CaptchaVerifyResponse;
import com.quickticket.captcha.service.CaptchaService;
import com.quickticket.captcha.service.CaptchaService.CaptchaChallengeResult;
import com.quickticket.captcha.service.CaptchaService.CaptchaVerifyResult;
import com.quickticket.common.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/captcha")
@RequiredArgsConstructor
public class CaptchaController {

    private static final String SESSION_COOKIE = "session_key";

    private final CaptchaService captchaService;

    @GetMapping("/{eventId}/challenge")
    public ApiResponse<CaptchaChallengeResponse> challenge(
            @PathVariable Long eventId,
            @CookieValue(name = SESSION_COOKIE, required = false) String sessionKey) {
        CaptchaChallengeResult result = captchaService.issueChallenge(eventId, sessionKey);
        return ApiResponse.success("success",
                new CaptchaChallengeResponse(result.challengeId(), result.question(), result.expiresInSec()));
    }

    @PostMapping("/{eventId}/verify")
    public ApiResponse<CaptchaVerifyResponse> verify(
            @PathVariable Long eventId,
            @CookieValue(name = SESSION_COOKIE, required = false) String sessionKey,
            @Valid @RequestBody CaptchaVerifyRequest request) {
        CaptchaVerifyResult result = captchaService.verify(eventId, sessionKey, request.challengeId(), request.answer());
        return ApiResponse.success("verified",
                new CaptchaVerifyResponse(true, result.seatAccessToken(), result.tokenExpiresInSec()));
    }
}
