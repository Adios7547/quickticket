package com.quickticket.event.service;

import com.quickticket.common.error.BusinessException;
import com.quickticket.common.error.ErrorCode;
import com.quickticket.common.util.DateTimeUtil;
import com.quickticket.event.domain.Event;
import com.quickticket.event.domain.EventStatus;
import com.quickticket.event.domain.Seat;
import com.quickticket.event.domain.SeatGrade;
import com.quickticket.event.domain.SeatStatus;
import com.quickticket.event.dto.ActiveEventResponse;
import com.quickticket.event.dto.EventCloseResponse;
import com.quickticket.event.dto.EventCreateRequest;
import com.quickticket.event.dto.EventDeleteResponse;
import com.quickticket.event.dto.EventListResponse;
import com.quickticket.event.dto.EventResponse;
import com.quickticket.event.dto.EventUpdateRequest;
import com.quickticket.event.dto.SeatGradeRequest;
import com.quickticket.event.mapper.EventMapper;
import com.quickticket.event.mapper.SeatMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventService {

    private static final int MAX_ROW_CNT = 26; // 행은 A~Z 알파벳으로 표기
    private static final SeatGrade DEFAULT_GRADE = SeatGrade.A;

    private final EventMapper eventMapper;
    private final SeatMapper seatMapper;

    @Transactional
    public EventResponse create(EventCreateRequest request) {
        LocalDate validFrom = DateTimeUtil.parseDate(request.validFrom(), "valid_from");
        LocalDate validTo = DateTimeUtil.parseDate(request.validTo(), "valid_to");
        validateValidRange(validFrom, validTo);

        LocalDateTime openDt = DateTimeUtil.parseDateTime(request.openDt(), "open_dt");
        validateOpenDt(openDt);

        Event event = Event.builder()
                .eventNm(request.eventNm())
                .openDt(openDt)
                .totalQuota(request.totalQuota())
                .issuedCount(0)
                .validFrom(validFrom)
                .validTo(validTo)
                .status(EventStatus.READY)
                .build();
        eventMapper.insert(event); // useGeneratedKeys로 event.id 채워짐

        int registeredSeatCnt = request.hasSeatMap() ? registerSeats(event, request) : 0;
        return EventResponse.created(event, registeredSeatCnt);
    }

    public EventListResponse list(EventStatus status, int page, int size) {
        if (page < 1 || size < 1) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "page and size must be positive");
        }
        long totalCount = eventMapper.countByStatus(status);
        List<EventResponse> items = eventMapper.findPage(status, size, (page - 1) * size)
                .stream().map(EventResponse::summary).toList();
        return new EventListResponse(items, totalCount, page, size);
    }

    public EventResponse get(Long eventId) {
        return EventResponse.detail(findEvent(eventId));
    }

    @Transactional
    public EventResponse update(Long eventId, EventUpdateRequest request) {
        Event event = findEvent(eventId);
        if (!event.isReady()) {
            throw new BusinessException(ErrorCode.EVENT_LOCKED, "cannot modify opened event");
        }

        LocalDate validFrom = request.validFrom() == null ? null
                : DateTimeUtil.parseDate(request.validFrom(), "valid_from");
        LocalDate validTo = request.validTo() == null ? null
                : DateTimeUtil.parseDate(request.validTo(), "valid_to");
        validateValidRange(
                validFrom != null ? validFrom : event.getValidFrom(),
                validTo != null ? validTo : event.getValidTo());

        if (request.totalQuota() != null && request.totalQuota() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "total_quota must be positive");
        }

        LocalDateTime openDt = request.openDt() == null ? null
                : DateTimeUtil.parseDateTime(request.openDt(), "open_dt");
        if (openDt != null) {
            validateOpenDt(openDt);
        }

        event.update(request.eventNm(), openDt, request.totalQuota(), validFrom, validTo);
        eventMapper.update(event);
        return EventResponse.detail(event);
    }

    @Transactional
    public EventCloseResponse close(Long eventId, String reason) {
        Event event = findEvent(eventId);
        event.close(reason);
        eventMapper.update(event);
        // TODO: 대기열 구현 시 Redis 이벤트 플래그 전환 + 신규 진입 차단 연동
        return EventCloseResponse.from(event);
    }

    @Transactional
    public EventDeleteResponse delete(Long eventId) {
        Event event = findEvent(eventId);
        if (!event.isReady()) {
            throw new BusinessException(ErrorCode.EVENT_LOCKED, "cannot delete opened event");
        }
        seatMapper.deleteByEventId(eventId);
        eventMapper.delete(eventId);
        return EventDeleteResponse.of(eventId);
    }

    public ActiveEventResponse.Events activeEvents() {
        List<ActiveEventResponse> events = eventMapper.findByStatusOrderByOpenDt(EventStatus.OPEN)
                .stream().map(ActiveEventResponse::from).toList();
        return new ActiveEventResponse.Events(events);
    }

    private Event findEvent(Long eventId) {
        Event event = eventMapper.findById(eventId);
        if (event == null) {
            throw new BusinessException(ErrorCode.EVENT_NOT_FOUND);
        }
        return event;
    }

    private void validateOpenDt(LocalDateTime openDt) {
        if (openDt.isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "open_dt must not be in the past");
        }
    }

    private void validateValidRange(LocalDate validFrom, LocalDate validTo) {
        if (validFrom != null && validTo != null && validFrom.isAfter(validTo)) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "valid_from must not be after valid_to");
        }
    }

    /** 행/열 그리드로 좌석 일괄 생성. seats로 개별 좌석 등급 지정, 나머지는 기본 A 등급. */
    private int registerSeats(Event event, EventCreateRequest request) {
        Integer rowCnt = request.rowCnt();
        Integer colCnt = request.colCnt();
        if (rowCnt == null || colCnt == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "row_cnt and col_cnt are both required");
        }
        if (rowCnt < 1 || rowCnt > MAX_ROW_CNT) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "row_cnt must be between 1 and " + MAX_ROW_CNT);
        }
        if (colCnt < 1) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "col_cnt must be positive");
        }

        Map<String, SeatGrade> gradeOverrides = parseGradeOverrides(request.seats());

        List<Seat> seats = new ArrayList<>(rowCnt * colCnt);
        for (int row = 0; row < rowCnt; row++) {
            for (int col = 1; col <= colCnt; col++) {
                String seatNo = (char) ('A' + row) + "-" + col;
                SeatGrade grade = gradeOverrides.getOrDefault(seatNo, DEFAULT_GRADE);
                gradeOverrides.remove(seatNo);
                seats.add(Seat.builder()
                        .eventId(event.getId())
                        .seatNo(seatNo)
                        .rowNo(row + 1)
                        .colNo(col)
                        .grade(grade)
                        .status(SeatStatus.AVAILABLE)
                        .build());
            }
        }
        if (!gradeOverrides.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAM,
                    "seat_no not in grid: " + String.join(", ", gradeOverrides.keySet()));
        }
        seatMapper.insertAll(seats);
        return seats.size();
    }

    private Map<String, SeatGrade> parseGradeOverrides(List<SeatGradeRequest> seats) {
        Map<String, SeatGrade> overrides = new HashMap<>();
        if (seats == null) {
            return overrides;
        }
        for (SeatGradeRequest seat : seats) {
            try {
                overrides.put(seat.seatNo(), SeatGrade.valueOf(seat.grade()));
            } catch (IllegalArgumentException e) {
                throw new BusinessException(ErrorCode.INVALID_PARAM, "invalid seat grade: " + seat.grade());
            }
        }
        return overrides;
    }
}
