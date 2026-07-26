package com.quickticket.common.util;

import com.quickticket.common.error.BusinessException;
import com.quickticket.common.error.ErrorCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * API 스펙의 날짜 문자열 포맷 변환.
 * 일시: yyyyMMddHHmmss (예: 20260701100000), 일자: yyyyMMdd (예: 20260701)
 */
public final class DateTimeUtil {

    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private DateTimeUtil() {
    }

    public static LocalDateTime parseDateTime(String value, String fieldName) {
        try {
            return LocalDateTime.parse(value, DATETIME);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "invalid " + fieldName + " format");
        }
    }

    public static LocalDate parseDate(String value, String fieldName) {
        try {
            return LocalDate.parse(value, DATE);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "invalid " + fieldName + " format");
        }
    }

    public static String format(LocalDateTime value) {
        return value == null ? null : value.format(DATETIME);
    }

    public static String format(LocalDate value) {
        return value == null ? null : value.format(DATE);
    }
}
