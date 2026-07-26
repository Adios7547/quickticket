-- seat_no("A-1")는 문자열이라 자리수가 늘어나면 정렬이 깨지고(예: "A-10" < "A-2"),
-- 특정 행/열 조회 시 문자열 파싱이 필요해 비효율적이다.
-- row_no/col_no를 정수로 별도 저장해 좌석 맵 렌더링(정렬)과 조회 성능을 개선한다.
-- seat_no는 사람이 읽는 식별자(URL 경로 등)로 계속 사용하므로 그대로 둔다.

alter table seats add column row_no integer not null default 0;
alter table seats add column col_no integer not null default 0;
alter table seats alter column row_no drop default;
alter table seats alter column col_no drop default;

create index idx_seats_event_row_col on seats (event_id, row_no, col_no);
