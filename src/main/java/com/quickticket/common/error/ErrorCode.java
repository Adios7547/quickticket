package com.quickticket.common.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 공통
    INVALID_PARAM(HttpStatus.BAD_REQUEST, "invalid parameter"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "unauthorized"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "internal server error"),

    // event
    EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "event not found"),
    EVENT_LOCKED(HttpStatus.CONFLICT, "cannot modify opened event"),

    // queue
    QUEUE_NOT_OPEN(HttpStatus.SERVICE_UNAVAILABLE, "queue not open"),
    ALREADY_ENTERED(HttpStatus.CONFLICT, "session already entered"),
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "session not found"),

    // captcha
    CAPTCHA_FAILED(HttpStatus.BAD_REQUEST, "wrong answer"),
    CHALLENGE_NOT_FOUND(HttpStatus.NOT_FOUND, "challenge expired"),
    CAPTCHA_NOT_REQUIRED(HttpStatus.CONFLICT, "captcha not required yet"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "too many requests");

    private final HttpStatus status;
    private final String defaultMessage;
}
