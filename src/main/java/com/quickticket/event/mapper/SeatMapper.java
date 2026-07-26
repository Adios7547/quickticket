package com.quickticket.event.mapper;

import com.quickticket.event.domain.Seat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SeatMapper {

    void insertAll(@Param("seats") List<Seat> seats);

    /** row_no, col_no 순으로 정렬 — seat_no 문자열 정렬은 자릿수에 따라 깨지므로(A-10 < A-2) 사용하지 않음 */
    List<Seat> findByEventId(Long eventId);

    long countByEventId(Long eventId);

    int deleteByEventId(Long eventId);
}
