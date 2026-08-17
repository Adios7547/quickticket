package com.quickticket.event.mapper;

import com.quickticket.event.domain.Event;
import com.quickticket.event.domain.EventStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface EventMapper {

    /** MyBatis insert는 void/int/long/boolean만 리턴 가능 — useGeneratedKeys 설정으로 insert 후 event.id가 채워진다 */
    void insert(Event event);

    Event findById(Long id);

    List<Event> findPage(@Param("status") EventStatus status,
                         @Param("limit") int limit,
                         @Param("offset") int offset);

    long countByStatus(@Param("status") EventStatus status);

    List<Event> findByStatusOrderByOpenDt(@Param("status") EventStatus status);

    int update(Event event);

    int delete(Long id);

    /** open_dt가 지난 READY 이벤트를 OPEN으로 일괄 전환. 반환값은 전환된 건수. */
    int openReadyEvents(@Param("now") LocalDateTime now);
}
