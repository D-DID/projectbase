package com.underfaker.recallcheck.service.sync;

import com.underfaker.recallcheck.common.KcCertNumbers;
import com.underfaker.recallcheck.dto.internal.KcLookup;
import com.underfaker.recallcheck.dto.response.SyncLogResponse;
import com.underfaker.recallcheck.entity.ApiSyncLog;
import com.underfaker.recallcheck.entity.Certification;
import com.underfaker.recallcheck.entity.enums.ApiType;
import com.underfaker.recallcheck.repository.ApiSyncLogRepository;
import com.underfaker.recallcheck.repository.CertificationRepository;
import com.underfaker.recallcheck.service.kc.CertificationCacheWriter;
import com.underfaker.recallcheck.service.kc.KcLookupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * KC인증 캐시(certification) 관리자 동기화 — FR-016.
 *
 * ── 10/3 변경 (KC인증 연동) ──
 * 예전엔 "완구"로 목록을 불러 앞 5건만 저장했다(동작 확인용 골격). 그 5건은 판정 어디에서도 쓰이지 않았다.
 * 이제 판정은 검증할 때 KcLookupService 가 인증번호로 바로 조회·캐시한다. 그래서 여기서는
 *   (1) 관리자가 지정한 인증번호를 API 로 다시 받거나
 *   (2) 지정하지 않으면 7일 넘은 캐시를 오래된 순으로 max 건 다시 받는다 — 인증상태(취소·사용금지) 변화를 반영.
 * KC 인증 DB 는 수십만 건이고 목록 API 는 페이징이 없어서 전체 적재는 하지 않는다.
 *
 * 트랜잭션을 열지 않는다 — 건마다 API 를 기다리므로 묶으면 커넥션을 오래 잡는다. 저장은 건별로
 * CertificationCacheWriter(REQUIRES_NEW)가 커밋한다. API 가 연달아 실패하면 남은 건은 건너뛴다(10/3 리뷰).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CertificationSyncService {

    /** 한 번에 다시 받을 최대 건수 상한 */
    static final int MAX_LIMIT = 500;

    /** 조회 실패가 이만큼 연달아 나면 API 가 죽은 것으로 보고 멈춘다(건마다 타임아웃을 기다리지 않게) */
    static final int STOP_AFTER_CONSECUTIVE_FAILURES = 3;

    private final KcLookupService kcLookupService;
    private final CertificationCacheWriter cacheWriter;
    private final CertificationRepository certificationRepository;
    private final ApiSyncLogRepository apiSyncLogRepository;

    /**
     * @param certNums 다시 받을 인증번호(원문 그대로 넣어도 된다. 번호만 골라 낸다). 비우면 오래된 캐시를 갱신.
     * @param max      certNums 를 비웠을 때 갱신할 최대 건수 (1~500)
     */
    public SyncLogResponse sync(Long adminId, List<String> certNums, int max) {
        ApiSyncLog syncLog = ApiSyncLog.start(adminId, ApiType.CERT);

        Set<String> targets = new LinkedHashSet<>();
        if (certNums != null) {
            for (String raw : certNums) {
                targets.addAll(KcCertNumbers.extract(raw));
            }
        }
        if (targets.isEmpty()) {
            int limit = Math.max(1, Math.min(max, MAX_LIMIT));
            LocalDateTime before = LocalDateTime.now().minusDays(7);
            for (Certification c : certificationRepository.findBySyncedAtIsNullOrSyncedAtBefore(
                    before, PageRequest.of(0, limit, Sort.by(Sort.Direction.ASC, "syncedAt")))) {
                if (c.getCertNum() != null) {
                    targets.add(c.getCertNum());
                }
            }
        }

        int found = 0;
        int consecutiveFailures = 0;
        int skipped = 0;
        List<String> notFound = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        for (String certNum : targets) {
            if (consecutiveFailures >= STOP_AFTER_CONSECUTIVE_FAILURES) {
                skipped++;
                continue;
            }
            KcLookup result = kcLookupService.refresh(certNum);
            if (result == null) {
                continue;
            }
            switch (result.status()) {
                case FOUND -> {
                    found++;
                    consecutiveFailures = 0;
                }
                case NOT_FOUND -> {
                    notFound.add(certNum);
                    consecutiveFailures = 0;
                    markCheckedQuietly(certNum);   // 다음 갱신 때 같은 행이 계속 맨 앞에 걸리지 않게
                }
                case UNAVAILABLE -> {
                    failed.add(certNum);
                    consecutiveFailures++;
                }
            }
        }

        syncLog.finish(failed.isEmpty() ? "2000" : "2000-P", found);
        apiSyncLogRepository.save(syncLog);
        log.info("[CertSync] 대상 {}건 — 갱신 {}건, 인증 DB 에 없음 {}건{}, 조회 실패 {}건{}, API 연속 실패로 건너뜀 {}건",
                targets.size(), found, notFound.size(), notFound.isEmpty() ? "" : " " + notFound,
                failed.size(), failed.isEmpty() ? "" : " " + failed, skipped);

        return toResponse(syncLog);
    }

    private void markCheckedQuietly(String certNum) {
        try {
            cacheWriter.markChecked(certNum);
        } catch (RuntimeException e) {
            log.warn("[CertSync] {} 확인 시각 갱신 실패: {}", certNum, e.getMessage());
        }
    }

    private SyncLogResponse toResponse(ApiSyncLog log) {
        return new SyncLogResponse(
                log.getId(), log.getAdminId(), log.getApiType(),
                log.getResultCode(), log.getRecordCount(),
                log.getStartedAt(), log.getFinishedAt());
    }
}
