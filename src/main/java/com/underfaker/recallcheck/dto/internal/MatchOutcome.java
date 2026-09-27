package com.underfaker.recallcheck.dto.internal;

import java.util.List;

/**
 * MatchingService.matchWithImage() 의 결과.
 *
 * 9/24 추가. 후보 목록만 돌려주던 match() 로는 "이번 판정에 이미지 판독을 썼는지, 썼다면
 * 무엇을 읽었는지"를 VerificationService 가 알 수 없었다. 그걸 알아야 extraction 에 저장하고,
 * 나중에 FR-014 판정근거를 다시 계산할 때 같은 값으로 재현할 수 있다.
 *
 * MatchingService 가 직접 extraction 을 쓰지 않는 이유: 의존 방향이
 * VerificationService → ExtractionService / MatchingService 단방향이다(VerificationService 주석).
 *
 * @param candidates 판정된 후보 (점수 내림차순)
 * @param insight    이번에 쓴 이미지 판독 결과. 호출하지 않았거나 실패했으면 ImageInsight.NONE
 */
public record MatchOutcome(List<MatchCandidate> candidates, ImageInsight insight) {

    public MatchOutcome {
        candidates = candidates == null ? List.of() : candidates;
        insight = insight == null ? ImageInsight.NONE : insight;
    }
}
