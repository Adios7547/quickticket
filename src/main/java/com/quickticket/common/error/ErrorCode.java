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
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "session not found");

    private final HttpStatus status;
    private final String defaultMessage;
}
