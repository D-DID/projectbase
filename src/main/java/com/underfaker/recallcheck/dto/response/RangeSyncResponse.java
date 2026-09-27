package com.underfaker.recallcheck.dto.response;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 기간 적재 결과 (FR-016).
 *
 * 단일 날짜 적재의 SyncLogResponse 와 달리 "며칠을 훑어서 몇 건을 넣었는지"가 필요하다.
 * 공표가 없는 날이 대부분이라 daysScanned 와 daysWithData 의 차이가 크게 나는 게 정상이다.
 *
 * @param from         시작 공표일 (yyyyMMdd)
 * @param to           종료 공표일 (yyyyMMdd)
 * @param daysScanned  목록 API 를 호출한 날짜 수
 * @param daysWithData 공표 건이 있던 날짜 수
 * @param fetched      목록 API 가 돌려준 총 건수
 * @param saved        실제로 저장에 성공한 건수
 * @param failed       저장에 실패한 건수 (건별 트랜잭션이라 나머지는 살아 있다)
 * @param withImages   상세 API 를 불러 사진까지 채웠는지
 * @param syncId       api_sync_log 행 id
 * @param failedDates  목록 조회 자체가 실패한 날짜들. 비어 있어야 정상이고,
 *                     남아 있으면 그 날짜만 다시 돌리면 된다.
 */
public record RangeSyncResponse(

        String from,
        String to,
        int daysScanned,
        int daysWithData,
        int fetched,
        int saved,
        int failed,
        boolean withImages,
        Long syncId,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        List<String> failedDates
) {
}
