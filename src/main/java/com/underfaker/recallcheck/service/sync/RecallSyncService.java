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
 * 주의 1. Recall 의 PK 는 API 원본 키(recallUid)라 직접 할당된다.
 *         Persistable 구현으로 save() 앞 SELECT 는 막았지만, 대량 적재 시에는
 *         saveAll 배치 크기(hibernate.jdbc.batch_size)를 함께 조정할 것.
 * 주의 2. 목록 API 는 페이징 파라미터가 없고 최대 1,000건까지만 내려온다.
 *         전체 적재가 필요하면 conditionKey 를 publishDate 등으로 나눠 여러 번 호출해야 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class RecallSyncService {

    private final SafetyKoreaRecallClient recallClient;
    private final RecallRepository recallRepository;
    private final RecallFileRepository recallFileRepository;
    private final ApiSyncLogRepository apiSyncLogRepository;

    /**
     * FR-016 동기화 실행.
     *
     * @param adminId 실행한 관리자 (api_sync_log.admin_id)
     */
    public SyncLogResponse sync(Long adminId) {
        // TODO 1) ApiSyncLog.start(adminId, ApiType.RECALL) 생성
        //      2) recallClient.fetchList(...) 호출, resultCode 검사
        //      3) 신규는 저장, 기존은 Recall.syncFrom(...) 으로 갱신
        //      4) 각 건 fetchDetail 로 recallFiles 적재 (FileDiv.from 으로 한글 값 변환)
        //      5) log.finish(resultCode, recordCount) 후 저장 (FR-017)
        throw new UnsupportedOperationException("TODO: RecallSyncService.sync");
    }

    /** FR-017 동기화 이력 조회 */
    @Transactional(readOnly = true)
    public PageResponse<SyncLogResponse> getLogs(int page, int size) {
        throw new UnsupportedOperationException("TODO: getLogs");
    }
}
