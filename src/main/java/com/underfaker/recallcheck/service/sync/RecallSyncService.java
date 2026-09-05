package com.underfaker.recallcheck.service.sync;

import com.underfaker.recallcheck.client.SafetyKoreaRecallClient;
import com.underfaker.recallcheck.common.PageResponse;
import com.underfaker.recallcheck.dto.response.SyncLogResponse;
import com.underfaker.recallcheck.repository.ApiSyncLogRepository;
import com.underfaker.recallcheck.repository.RecallFileRepository;
import com.underfaker.recallcheck.repository.RecallRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-016, 017 — 리콜 데이터 적재·로그 기록.
 *
 * 주의: Recall 의 PK 는 API 원본 키(recallUid)를 그대로 쓴다.
 * JPA 에서 ID 를 직접 할당하면 save() 마다 SELECT 가 선행되므로
 * Recall 이 Persistable 을 구현하거나 JDBC batch insert 로 처리할 것.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class RecallSyncService {

    private final SafetyKoreaRecallClient recallClient;
    private final RecallRepository recallRepository;
    private final RecallFileRepository recallFileRepository;
    private final ApiSyncLogRepository apiSyncLogRepository;

    /** FR-016 동기화 실행 */
    public SyncLogResponse sync() {
        // TODO 1) recallClient.fetchList(...) 페이징 순회
        //      2) 신규/변경 건만 upsert
        //      3) 상세 조회로 리콜 사진(recall_file) 적재
        //      4) ApiSyncLog 에 실행 시각·결과 코드·처리 건수 기록 (FR-017)
        throw new UnsupportedOperationException("TODO: RecallSyncService.sync");
    }

    /** FR-017 동기화 이력 조회 */
    @Transactional(readOnly = true)
    public PageResponse<SyncLogResponse> getLogs(int page, int size) {
        throw new UnsupportedOperationException("TODO: getLogs");
    }
}
