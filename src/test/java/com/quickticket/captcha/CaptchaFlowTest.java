package com.quickticket.captcha;

import com.quickticket.event.domain.Event;
import com.quickticket.event.domain.EventStatus;
import com.quickticket.event.mapper.EventMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
// 캡차 발동 시각을 0으로 고정 — 첫 폴링부터 captcha_required 가 true 가 되도록 강제.
// 입장 정원을 0으로 두어 admission 스케줄러가 대기 세션을 방으로 옮기지 않게 한다(캡차 단언 보호).
@TestPropertySource(properties = {
        "quickticket.captcha.min-delay-sec=0",
        "quickticket.captcha.max-delay-sec=0",
        "quickticket.admission.max-room=0"
})
class CaptchaFlowTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    EventMapper eventMapper;

    private Cookie session() {
        return new Cookie("session_key", UUID.randomUUID().toString());
    }

    /** 캡차 흐름 검증을 위해 OPEN 상태 이벤트를 직접 적재 (생성 API는 미래 open_dt만 허용하므로) */
    private long openEvent() {
        Event event = Event.builder()
                .eventNm("아이유 2026 콘서트")
                .openDt(LocalDateTime.now().minusMinutes(10))
                .totalQuota(1000)
                .issuedCount(0)
                .status(EventStatus.OPEN)
                .build();
        eventMapper.insert(event);
        return event.getId();
    }

    private int solve(String question) {
        // "24 + 3 = ?" 형태 파싱 — 사칙연산 캡차는 이렇게 쉽게 풀린다(속도제한·지연 목적)
        String[] t = question.replace(" = ?", "").split(" ");
        int a = Integer.parseInt(t[0]);
        int b = Integer.parseInt(t[2]);
        return switch (t[1]) {
            case "+" -> a + b;
            case "-" -> a - b;
            default -> a * b;
        };
    }

    @Test
    @DisplayName("전체 흐름: 순번 폴링 시 캡차 발동 → 문제 풀이 → seat_access_token 발급")
    void fullFlow() throws Exception {
        long eventId = openEvent();
        Cookie session = session();

        // 1) 대기열 진입
        mockMvc.perform(post("/queue/{eventId}/enter", eventId).cookie(session))
                .andExpect(status().isOk());

        // 2) 순번 폴링 — delay=0이므로 캡차 발동 상태
        mockMvc.perform(get("/queue/{eventId}/rank", eventId).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.captcha_required").value(true));

        // 3) 문제 발급
        MvcResult challengeResult = mockMvc.perform(get("/captcha/{eventId}/challenge", eventId).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.challenge_id").isNotEmpty())
                .andExpect(jsonPath("$.data.expires_in_sec").value(60))
                .andReturn();
        JsonNode data = objectMapper.readTree(challengeResult.getResponse().getContentAsString()).path("data");
        String challengeId = data.path("challenge_id").asText();
        int answer = solve(data.path("question").asText());

        // 4) 정답 검증 → 토큰 발급
        MvcResult verifyResult = mockMvc.perform(post("/captcha/{eventId}/verify", eventId).cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challenge_id\":\"" + challengeId + "\",\"answer\":" + answer + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result_msg").value("verified"))
                .andExpect(jsonPath("$.data.verified").value(true))
                .andExpect(jsonPath("$.data.seat_access_token").isNotEmpty())
                .andReturn();
        String token = objectMapper.readTree(verifyResult.getResponse().getContentAsString())
                .path("data").path("seat_access_token").asText();
        org.junit.jupiter.api.Assertions.assertFalse(token.isBlank());

        // 5) 통과 후에는 폴링에서 캡차 미발동
        mockMvc.perform(get("/queue/{eventId}/rank", eventId).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.captcha_required").value(false));

        // 6) 통과한 세션이 문제를 다시 요청하면 거부 — 같은 세션에는 재발동하지 않음(#2)
        mockMvc.perform(get("/captcha/{eventId}/challenge", eventId).cookie(session))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code").value("CAPTCHA_NOT_REQUIRED"));
    }

    @Test
    @DisplayName("오답이면 400 CAPTCHA_FAILED")
    void wrongAnswer() throws Exception {
        long eventId = openEvent();
        Cookie session = session();

        MvcResult challengeResult = mockMvc.perform(get("/captcha/{eventId}/challenge", eventId).cookie(session))
                .andExpect(status().isOk())
                .andReturn();
        String challengeId = objectMapper.readTree(challengeResult.getResponse().getContentAsString())
                .path("data").path("challenge_id").asText();

        mockMvc.perform(post("/captcha/{eventId}/verify", eventId).cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challenge_id\":\"" + challengeId + "\",\"answer\":-99999}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("CAPTCHA_FAILED"));
    }

    @Test
    @DisplayName("없는/만료된 challenge_id 검증은 404 CHALLENGE_NOT_FOUND")
    void challengeNotFound() throws Exception {
        long eventId = openEvent();
        mockMvc.perform(post("/captcha/{eventId}/verify", eventId).cookie(session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challenge_id\":\"" + UUID.randomUUID() + "\",\"answer\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code").value("CHALLENGE_NOT_FOUND"));
    }

    @Test
    @DisplayName("세션 쿠키 없이 문제 발급 요청은 404 SESSION_NOT_FOUND")
    void missingSession() throws Exception {
        long eventId = openEvent();
        mockMvc.perform(get("/captcha/{eventId}/challenge", eventId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code").value("SESSION_NOT_FOUND"));
    }

    @Test
    @DisplayName("한 문제는 한 번만 시도 가능 — 오답 후 같은 challenge 재사용 시 404")
    void challengeConsumedAfterAttempt() throws Exception {
        long eventId = openEvent();
        Cookie session = session();

        MvcResult challengeResult = mockMvc.perform(get("/captcha/{eventId}/challenge", eventId).cookie(session))
                .andReturn();
        String challengeId = objectMapper.readTree(challengeResult.getResponse().getContentAsString())
                .path("data").path("challenge_id").asText();

        // 첫 시도(오답) → 소비됨
        mockMvc.perform(post("/captcha/{eventId}/verify", eventId).cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challenge_id\":\"" + challengeId + "\",\"answer\":-1}"))
                .andExpect(status().isBadRequest());

        // 같은 challenge 재시도 → 이미 삭제되어 404
        mockMvc.perform(post("/captcha/{eventId}/verify", eventId).cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challenge_id\":\"" + challengeId + "\",\"answer\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code").value("CHALLENGE_NOT_FOUND"));
    }
}
