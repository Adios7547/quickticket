package com.quickticket.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.quickticket.common.error.ErrorCode;

/**
 * 모든 API의 공통 응답 포맷.
 * 성공: { "result_code": "SUCCESS", "result_msg": "...", "data": {...} }
 * 실패: { "result_code": "FAIL", "result_msg": "...", "error_code": "..." }
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        String resultCode,
        String resultMsg,
        String errorCode,
        T data
) {
    private static final String SUCCESS = "SUCCESS";
    private static final String FAIL = "FAIL";

    public static <T> ApiResponse<T> success(String resultMsg, T data) {
        return new ApiResponse<>(SUCCESS, resultMsg, null, data);
    }

    public static ApiResponse<Void> fail(ErrorCode errorCode, String resultMsg) {
        return new ApiResponse<>(FAIL, resultMsg, errorCode.name(), null);
    }
}
