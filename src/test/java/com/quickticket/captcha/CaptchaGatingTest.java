package com.quickticket.captcha;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 발동 시각 게이팅(#1) 검증 — 발동 지연을 매우 길게 잡아 캡차가 "아직 발동 전" 상태를 만든다.
 * 이 상태에서 문제 발급을 요청하면 거부되어야 봇이 미리 풀고 넘어가는 우회가 막힌다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "quickticket.captcha.min-delay-sec=3600",
        "quickticket.captcha.max-delay-sec=3600"
})
class CaptchaGatingTest {

    @Autowired
    MockMvc mockMvc;

    private Cookie session() {
        return new Cookie("session_key", UUID.randomUUID().toString());
    }

    @Test
    @DisplayName("발동 시각 전에는 문제 발급을 거부 — 409 CAPTCHA_NOT_REQUIRED (#1)")
    void challengeRejectedBeforeDue() throws Exception {
        long eventId = 9001L; // 게이팅 판정은 DB 이벤트와 무관 (세션 발동 시각만 확인)
        mockMvc.perform(get("/captcha/{eventId}/challenge", eventId).cookie(session()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code").value("CAPTCHA_NOT_REQUIRED"));
    }
}
