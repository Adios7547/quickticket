package com.quickticket.event.mapper;

import com.quickticket.event.domain.Seat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SeatMapper {

    void insertAll(@Param("seats") List<Seat> seats);

    long countByEventId(Long eventId);

    int deleteByEventId(Long eventId);
}
