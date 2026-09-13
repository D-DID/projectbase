package com.underfaker.recallcheck.service.sync;

import com.underfaker.recallcheck.client.SafetyKoreaRecallClient;
import com.underfaker.recallcheck.client.dto.RecallDetailApiResponse;
import com.underfaker.recallcheck.client.dto.RecallListApiResponse;
import com.underfaker.recallcheck.common.PageResponse;
import com.underfaker.recallcheck.dto.response.SyncLogResponse;
import com.underfaker.recallcheck.entity.ApiSyncLog;
import com.underfaker.recallcheck.entity.Recall;
import com.underfaker.recallcheck.entity.RecallFile;
import com.underfaker.recallcheck.entity.enums.ApiType;
import com.underfaker.recallcheck.entity.enums.FileDiv;
import com.underfaker.recallcheck.repository.ApiSyncLogRepository;
import com.underfaker.recallcheck.repository.RecallFileRepository;
import com.underfaker.recallcheck.repository.RecallRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-016, 017 — 리콜 데이터 적재·로그 기록.
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
     * @param adminId 실행한 관리자 (api_sync_log.admin_id)
     */
    public SyncLogResponse sync(Long adminId) {
        ApiSyncLog log = ApiSyncLog.start(adminId, ApiType.RECALL);

        RecallListApiResponse response =
                recallClient.fetchList(SafetyKoreaRecallClient.KEY_PRODUCT_NAME, "물티슈");
        int savedCount = 0;

        if (response.isSuccess() && response.resultData() != null) {
            for (RecallListApiResponse.Item item : response.resultData()) {
                Recall fresh = toEntity(item);

                recallRepository.findById(item.recallUid())
                        .ifPresentOrElse(
                                existing -> existing.syncFrom(fresh),
                                () -> recallRepository.save(fresh)
                        );

                loadFiles(item.recallUid());
                savedCount++;
            }
        }

        log.finish(response.resultCode(), savedCount);
        apiSyncLogRepository.save(log);

        return toResponse(log);
    }

    private void loadFiles(Long recallUid) {
        RecallDetailApiResponse detail = recallClient.fetchDetail(recallUid);
        if (!detail.isSuccess() || detail.resultData() == null
                || detail.resultData().recallFiles() == null) {
            return;
        }
        recallFileRepository.deleteByRecallUid(recallUid);
        for (RecallDetailApiResponse.RecallFileItem fileItem : detail.resultData().recallFiles()) {
            recallFileRepository.save(RecallFile.builder()
                    .recallUid(recallUid)
                    .fileDiv(FileDiv.from(fileItem.fileDiv()))
                    .imageUrl(fileItem.imageUrl())
                    .build());
        }
    }

    private Recall toEntity(RecallListApiResponse.Item item) {
        return Recall.builder()
                .recallUid(item.recallUid())
                .recallProductName(item.recallProductName())
                .recallBrandName(item.recallBrandName())
                .recallModelName(item.recallModelName())
                .recallModelCnt(item.recallModelCnt())
                .barcodeNum(item.barcodeNum())
                .certNum(item.certNum())
                .categoryName(item.categoryName())
                .recallTypeName(item.recallTypeName())
                .recallMeans(item.recallMeans())
                .recallCmpnyName(item.recallCmpnyName())
                .makerName(item.makerName())
                .makingCntryName(item.makingCntryName())
                .publishDate(item.publishDate())
                .harmDscr(item.harmDscr())
                .accidentCaseDscr(item.accidentCaseDscr())
                .publishActionDscr(item.publishActionDscr())
                .build();
    }

    private SyncLogResponse toResponse(ApiSyncLog log) {
        return new SyncLogResponse(
                log.getId(), log.getAdminId(), log.getApiType(),
                log.getResultCode(), log.getRecordCount(),
                log.getStartedAt(), log.getFinishedAt());
    }

    /** FR-017 동기화 이력 조회 */
    @Transactional(readOnly = true)
    public PageResponse<SyncLogResponse> getLogs(int page, int size) {
        Page<ApiSyncLog> logs =
                apiSyncLogRepository.findAllByOrderByStartedAtDesc(PageRequest.of(page, size));
        return PageResponse.from(logs.map(this::toResponse));
    }
}