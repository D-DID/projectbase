package com.underfaker.recallcheck.service.sync;

import com.underfaker.recallcheck.client.SafetyKoreaCertClient;
import com.underfaker.recallcheck.dto.response.SyncLogResponse;
import com.underfaker.recallcheck.repository.ApiSyncLogRepository;
import com.underfaker.recallcheck.repository.CertificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** KC인증 데이터 적재 */
@Service
@RequiredArgsConstructor
@Transactional
public class CertificationSyncService {

    private final SafetyKoreaCertClient certClient;
    private final CertificationRepository certificationRepository;
    private final ApiSyncLogRepository apiSyncLogRepository;

    /**
     * @param adminId 실행한 관리자 (api_sync_log.admin_id)
     */
    public SyncLogResponse sync(Long adminId) {
        // TODO ApiSyncLog.start(adminId, ApiType.CERT) → fetchList → upsert → finish
        throw new UnsupportedOperationException("TODO: CertificationSyncService.sync");
    }
}
