package com.underfaker.recallcheck.service.sync;

import com.underfaker.recallcheck.client.SafetyKoreaCertClient;
import com.underfaker.recallcheck.client.dto.CertListApiResponse;
import com.underfaker.recallcheck.dto.response.SyncLogResponse;
import com.underfaker.recallcheck.entity.ApiSyncLog;
import com.underfaker.recallcheck.entity.Certification;
import com.underfaker.recallcheck.entity.enums.ApiType;
import com.underfaker.recallcheck.repository.ApiSyncLogRepository;
import com.underfaker.recallcheck.repository.CertificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** KC인증 데이터 적재 */
@Service
@RequiredArgsConstructor
@Transactional
public class CertificationSyncService {

    private final SafetyKoreaCertClient certClient;
    private final CertificationRepository certificationRepository;
    private final ApiSyncLogRepository apiSyncLogRepository;

    public SyncLogResponse sync(Long adminId) {
        ApiSyncLog log = ApiSyncLog.start(adminId, ApiType.CERT);

        CertListApiResponse response =
                certClient.fetchList(SafetyKoreaCertClient.KEY_PRODUCT_NAME, "완구");
        int savedCount = 0;

        if (response.isSuccess() && response.resultData() != null) {
            // 1단계 — 대량 처리·성능 최적화는 2단계에서. 지금은 1건 이상 동작 확인이 목표라
            // 앞에서 5건만 잘라서 처리한다(전체를 다 돌리면 대량 데이터로 오래 걸린다).
            List<CertListApiResponse.Item> items = response.resultData().stream()
                    .limit(5)
                    .toList();

            for (CertListApiResponse.Item item : items) {
                Certification fresh = toEntity(item);

                certificationRepository.findById(item.certUid())
                        .ifPresentOrElse(
                                existing -> existing.syncFrom(fresh),
                                () -> certificationRepository.save(fresh)
                        );

                savedCount++;
            }
        }

        log.finish(response.resultCode(), savedCount);
        apiSyncLogRepository.save(log);

        return toResponse(log);
    }

    private Certification toEntity(CertListApiResponse.Item item) {
        return Certification.builder()
                .certUid(item.certUid())
                .certNum(item.certNum())
                .certState(item.certState())
                .certDate(item.certDate())
                .productName(item.productName())
                .brandName(item.brandName())
                .modelName(item.modelName())
                .makerName(item.makerName())
                .makerCntryName(item.makerCntryName())
                .build();
    }

    private SyncLogResponse toResponse(ApiSyncLog log) {
        return new SyncLogResponse(
                log.getId(), log.getAdminId(), log.getApiType(),
                log.getResultCode(), log.getRecordCount(),
                log.getStartedAt(), log.getFinishedAt());
    }
}