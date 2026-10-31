package com.underfaker.recallcheck.service.sync;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

/**
 * 10/31 — 자동 갱신이 훑을 공표일 범위(yyyyMMdd, 양끝 포함).
 *
 * 규칙
 *   1. 기본은 최근 lookbackDays 일(오늘 포함) — 공표일이 지난 뒤에 등록·수정되는 공표문까지 잡는다.
 *      다시 받아도 upsert 라 중복이 생기지 않는다.
 *   2. DB 의 마지막 공표일이 그보다 오래됐으면 그날부터 — 서버가 며칠 꺼져 있었던 빈틈을 메운다.
 *   3. 단 오늘부터 maxCatchUpDays 일(오늘 포함)보다 앞으로는 가지 않는다(기간 적재 상한 400일 안).
 *      더 오래된 빈틈은 관리자 기간 적재로 채운다.
 *   마지막 공표일이 없거나(빈 DB) 형식이 틀리거나 오늘보다 뒤면 1 만 적용한다.
 *
 * 공표일 목록 조회는 2023-07-12 이후만 돌려준다(RecallSyncService 10/1 정정) — 자동 갱신은 늘 최근이라 상관없다.
 */
public record SyncWindow(String from, String to) {

    /** RecallSyncService.MAX_DAYS_PER_REQUEST 와 같은 값 — 기간 적재가 거절하지 않게 */
    static final int MAX_DAYS = 400;

    private static final DateTimeFormatter YYYYMMDD =
            DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT);

    /**
     * @param today             오늘(cron 시간대 기준)
     * @param latestPublishDate DB 에 있는 가장 늦은 공표일(yyyyMMdd). 없으면 null
     * @param lookbackDays      매번 다시 훑는 최근 일수. 1 미만이면 1
     * @param maxCatchUpDays    거슬러 올라가는 최대 일수. lookbackDays 보다 작으면 lookbackDays, 400 을 넘으면 400
     */
    public static SyncWindow of(LocalDate today, String latestPublishDate, int lookbackDays, int maxCatchUpDays) {
        int lookback = Math.min(Math.max(1, lookbackDays), MAX_DAYS);
        int catchUp = Math.min(Math.max(lookback, maxCatchUpDays), MAX_DAYS);
        LocalDate from = today.minusDays(lookback - 1L);
        LocalDate latest = parse(latestPublishDate);
        if (latest != null && latest.isBefore(from) && !latest.isAfter(today)) {
            from = latest;
        }
        LocalDate floor = today.minusDays(catchUp - 1L);
        if (from.isBefore(floor)) {
            from = floor;
        }
        return new SyncWindow(from.format(YYYYMMDD), today.format(YYYYMMDD));
    }

    private static LocalDate parse(String yyyymmdd) {
        if (yyyymmdd == null || !yyyymmdd.trim().matches("\\d{8}")) {
            return null;
        }
        try {
            return LocalDate.parse(yyyymmdd.trim(), YYYYMMDD);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
