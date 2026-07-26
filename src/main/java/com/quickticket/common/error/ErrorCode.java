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
    EVENT_LOCKED(HttpStatus.CONFLICT, "cannot modify opened event");

    private final HttpStatus status;
    private final String defaultMessage;
}
