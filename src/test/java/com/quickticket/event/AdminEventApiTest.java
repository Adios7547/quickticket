package com.quickticket.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminEventApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    private static final String FUTURE_OPEN_DT = LocalDateTime.now().plusYears(1)
            .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

    private static final String CREATE_BODY = """
            {
              "event_nm": "2026 콘서트",
              "open_dt": "%s",
              "total_quota": 1000,
              "valid_from": "20260701",
              "valid_to": "20260731",
              "row_cnt": 2,
              "col_cnt": 3,
              "seats": [
                {"seat_no": "A-1", "grade": "VIP"},
                {"seat_no": "A-2", "grade": "S"}
              ]
            }
            """.formatted(FUTURE_OPEN_DT);

    private long createEvent() throws Exception {
        MvcResult result = mockMvc.perform(post("/admin/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.path("data").path("event_id").asLong();
    }

    @Test
    @DisplayName("이벤트 생성: 좌석 맵 포함 201, READY 상태로 생성")
    void createEventWithSeats() throws Exception {
        mockMvc.perform(post("/admin/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.result_code").value("SUCCESS"))
                .andExpect(jsonPath("$.result_msg").value("created"))
                .andExpect(jsonPath("$.data.event_nm").value("2026 콘서트"))
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.registered_seat_cnt").value(6));
    }

    @Test
    @DisplayName("이벤트 생성: open_dt 형식 오류 400 INVALID_PARAM")
    void createEventInvalidOpenDt() throws Exception {
        String body = CREATE_BODY.replace(FUTURE_OPEN_DT, "2026-07-01 10:00");
        mockMvc.perform(post("/admin/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result_code").value("FAIL"))
                .andExpect(jsonPath("$.error_code").value("INVALID_PARAM"))
                .andExpect(jsonPath("$.result_msg").value("invalid open_dt format"));
    }

    @Test
    @DisplayName("이벤트 생성: open_dt가 과거면 400 INVALID_PARAM")
    void createEventPastOpenDt() throws Exception {
        String body = CREATE_BODY.replace(FUTURE_OPEN_DT, "20200101000000");
        mockMvc.perform(post("/admin/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("INVALID_PARAM"))
                .andExpect(jsonPath("$.result_msg").value("open_dt must not be in the past"));
    }

    @Test
    @DisplayName("이벤트 생성: 필수값 누락 400")
    void createEventMissingRequired() throws Exception {
        mockMvc.perform(post("/admin/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"total_quota\": 100}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("INVALID_PARAM"));
    }

    @Test
    @DisplayName("이벤트 상세 조회: 전체 필드 반환")
    void getEventDetail() throws Exception {
        long eventId = createEvent();
        mockMvc.perform(get("/admin/events/{eventId}", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.event_id").value(eventId))
                .andExpect(jsonPath("$.data.valid_from").value("20260701"))
                .andExpect(jsonPath("$.data.valid_to").value("20260731"))
                .andExpect(jsonPath("$.data.issued_count").value(0));
    }

    @Test
    @DisplayName("이벤트 조회: 없는 ID 404 EVENT_NOT_FOUND")
    void getEventNotFound() throws Exception {
        mockMvc.perform(get("/admin/events/{eventId}", 999999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code").value("EVENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("이벤트 목록: 상태 필터 및 페이징")
    void listEvents() throws Exception {
        createEvent();
        createEvent();
        mockMvc.perform(get("/admin/events").param("status", "READY").param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total_count").value(2))
                .andExpect(jsonPath("$.data.events.length()").value(2))
                .andExpect(jsonPath("$.data.page").value(1));
    }

    @Test
    @DisplayName("이벤트 수정: READY 상태에서 quota 변경")
    void updateReadyEvent() throws Exception {
        long eventId = createEvent();
        mockMvc.perform(put("/admin/events/{eventId}", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"total_quota\": 1500}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result_msg").value("updated"))
                .andExpect(jsonPath("$.data.total_quota").value(1500));
    }

    @Test
    @DisplayName("이벤트 수정: open_dt를 과거로 바꾸면 400 INVALID_PARAM")
    void updateEventPastOpenDt() throws Exception {
        long eventId = createEvent();
        mockMvc.perform(put("/admin/events/{eventId}", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"open_dt\": \"20200101000000\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("INVALID_PARAM"))
                .andExpect(jsonPath("$.result_msg").value("open_dt must not be in the past"));
    }

    @Test
    @DisplayName("이벤트 긴급 종료: CLOSED 전환 + queue_blocked")
    void closeEvent() throws Exception {
        long eventId = createEvent();
        mockMvc.perform(post("/admin/events/{eventId}/close", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"운영 이슈\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"))
                .andExpect(jsonPath("$.data.queue_blocked").value(true));
    }

    @Test
    @DisplayName("이벤트 수정/삭제: CLOSED 상태면 409 EVENT_LOCKED")
    void modifyClosedEventLocked() throws Exception {
        long eventId = createEvent();
        mockMvc.perform(post("/admin/events/{eventId}/close", eventId))
                .andExpect(status().isOk());

        mockMvc.perform(put("/admin/events/{eventId}", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"total_quota\": 2000}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code").value("EVENT_LOCKED"));

        mockMvc.perform(delete("/admin/events/{eventId}", eventId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code").value("EVENT_LOCKED"));
    }

    @Test
    @DisplayName("이벤트 삭제: READY 상태만 삭제 가능, 이후 404")
    void deleteReadyEvent() throws Exception {
        long eventId = createEvent();
        mockMvc.perform(delete("/admin/events/{eventId}", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DELETED"));

        mockMvc.perform(get("/admin/events/{eventId}", eventId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("활성 이벤트 목록: OPEN 이벤트 없으면 빈 배열")
    void activeEventsEmpty() throws Exception {
        createEvent(); // READY 상태 — active에 포함되지 않아야 함
        mockMvc.perform(get("/events/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result_code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.events.length()").value(0));
    }
}
