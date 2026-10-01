package com.underfaker.recallcheck.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 품목명 키워드 적재 결과 (FR-016, 10/1 추가).
 *
 * 공표일 조회가 2023-07-12 이후만 돌려줘서, 그 이전 공표분은 품목명 부분 일치로 가져온다.
 * 같은 리콜이 여러 키워드에 걸리므로 fetched(중복 포함)와 unique(중복 제거)가 다르게 나오는 게 정상이다.
 *
 * @param keywordsQueried  실제로 API 를 부른 키워드 수
 * @param keywordsWithData 1건 이상 돌려준 키워드 수
 * @param keywordsSkipped  이미 다 받은 키워드에 포함돼 부르지 않은 수("완구"를 다 받았으면 "기타완구"는 생략)
 * @param fetched          API 가 돌려준 총 건수(키워드 간 중복 포함)
 * @param unique           이번 실행에서 본 서로 다른 리콜 수
 * @param inserted         DB 에 새로 들어간 건수 — 과거분이 얼마나 채워졌는지는 이 값으로 본다
 * @param updated          이미 있던 행을 다시 동기화한 건수
 * @param failed           저장 실패 건수(건별 트랜잭션이라 나머지는 살아 있다)
 * @param oldestPublishDate 이번에 본 가장 오래된 공표일(yyyyMMdd)
 * @param newestPublishDate 이번에 본 가장 최근 공표일(yyyyMMdd)
 * @param insertedByYear   새로 들어간 건수의 공표 연도별 분포
 * @param cappedKeywords   1,000건 상한에 걸린 키워드. expand=true 면 결과 품목명으로 이미 잘게 다시 훑었다.
 *                         expand=false 로 돌렸다면 이 키워드를 더 잘게 나눠서 다시 호출해야 한다.
 * @param failedKeywords   목록 조회 자체가 실패한 키워드. 비어 있어야 정상. 남으면 그것만 keywords 로 다시 돌린다.
 * @param pendingKeywords  maxCalls 에 걸려 못 부른 키워드. 비어 있어야 다 돈 것이다.
 * @param results          키워드별 결과(1건 이상 받았거나 실패한 것만)
 */
public record KeywordSyncResponse(

        int keywordsQueried,
        int keywordsWithData,
        int keywordsSkipped,
        int fetched,
        int unique,
        int inserted,
        int updated,
        int failed,
        String oldestPublishDate,
        String newestPublishDate,
        Map<String, Integer> insertedByYear,
        List<String> cappedKeywords,
        List<String> failedKeywords,
        List<String> pendingKeywords,
        boolean withImages,
        Long syncId,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        List<KeywordResult> results
) {

    /**
     * @param keyword    조회한 키워드
     * @param fetched    API 가 돌려준 건수(1,000 이면 상한)
     * @param inserted   이 키워드로 DB 에 새로 들어간 건수
     * @param capped     1,000건 상한에 걸렸는지
     * @param resultCode API 결과 코드(2000/2004) 또는 ERROR
     */
    public record KeywordResult(String keyword, int fetched, int inserted, boolean capped, String resultCode) {
    }
}
