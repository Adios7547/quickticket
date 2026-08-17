package com.quickticket.captcha.dto;

/** 캡차 검증 성공 응답. seat_access_token은 이후 좌석 API 접근에 사용된다. */
public record CaptchaVerifyResponse(
        boolean verified,
        String seatAccessToken,
        long tokenExpiresInSec
) {
}
