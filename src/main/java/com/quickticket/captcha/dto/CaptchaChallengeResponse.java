package com.quickticket.captcha.dto;

/** 캡차 문제 발급 응답. 정답은 내려주지 않고 Redis에만 보관한다. */
public record CaptchaChallengeResponse(
        String challengeId,
        String question,
        long expiresInSec
) {
}
