package com.quickticket.captcha.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CaptchaVerifyRequest(
        @NotBlank(message = "challenge_id is required")
        String challengeId,

        @NotNull(message = "answer is required")
        Integer answer
) {
}
