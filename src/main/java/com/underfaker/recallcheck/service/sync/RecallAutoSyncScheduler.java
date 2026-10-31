package com.underfaker.recallcheck.service.sync;

import com.underfaker.recallcheck.config.RecallAutoSyncProperties;
import com.underfaker.recallcheck.dto.response.RangeSyncResponse;
import com.underfaker.recallcheck.entity.User;
import com.underfaker.recallcheck.entity.enums.Role;
import com.underfaker.recallcheck.repository.RecallRepository;
import com.underfaker.recallcheck.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * FR-016 리콜 데이터 자동 갱신(10/31). 팀 결정 사항이었는데 빠져 있었다 — 그동안은 관리자가
 * /api/admin/sync/recalls/range 를 직접 불러야 새 공표문이 들어왔다(10/30 수동 적재가 마지막).
 *
 * 언제
 *   - 정기: recall.auto-sync.cron(기본 6시간마다 20분, Asia/Seoul)
 *   - 기동: 서버가 뜨고 recall.auto-sync.startup-delay-ms(기본 20초) 뒤 한 번 — 개발·발표 PC 는 정기 시각에
 *     서버가 꺼져 있을 때가 많아서, 켤 때마다 따라잡는다.
 *
 * 무엇을
 *   1. 공표일 범위를 정한다(SyncWindow) — 최근 7일, DB 마지막 공표일이 더 오래됐으면 그날부터(최대 90일).
 *   2. 그 범위를 기간 적재(RecallSyncService.syncRange)로 본문만 받는다(withImages=false — 빠르다). upsert 라 다시 받아도 중복 없음.
 *   3. 사진이 없는 리콜의 사진을 최신 공표문부터 imageLimit 건 채운다(RecallSyncService.syncImages).
 *
 * 관리자 수동 적재와 같은 코드를 쓴다. 실패해도 예외를 밖으로 던지지 않는다(다음 실행에서 다시 받는다).
 * 앞 실행이 아직 돌고 있으면 건너뛴다. 끄려면 recall.auto-sync.enabled=false.
 *
 * 부수 효과: 다시 받은 공표문은 recall.synced_at 이 바뀐다. 같은 입력을 다시 확인하면 그 시각이 기존 검증보다
 * 늦으므로 다시 판정한다(VerificationService#reuseVerification) — 새로 쌓이지는 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecallAutoSyncScheduler {

    private final RecallSyncService recallSyncService;
    private final RecallRepository recallRepository;
    private final UserRepository userRepository;
    private final RecallAutoSyncProperties properties;

    private final AtomicBoolean running = new AtomicBoolean(false);

    /** 정기 실행 */
    @Scheduled(cron = "${recall.auto-sync.cron:" + RecallAutoSyncProperties.DEFAULT_CRON + "}",
            zone = "${recall.auto-sync.zone:" + RecallAutoSyncProperties.DEFAULT_ZONE + "}")
    public void onSchedule() {
        if (properties.isEnabled()) {
            run("정기");
        }
    }

    /** 기동 실행 — 서버 기동을 붙잡지 않게 따로 스레드에서 기다렸다가 돈다 */
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!properties.isEnabled() || !properties.isRunOnStartup()) {
            log.info("[RecallAutoSync] 기동 갱신 꺼짐(recall.auto-sync.enabled={}, run-on-startup={})",
                    properties.isEnabled(), properties.isRunOnStartup());
            return;
        }
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(Math.max(0L, properties.getStartupDelayMs()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            run("기동");
        }, "recall-auto-sync-startup");
        t.setDaemon(true);
        t.start();
    }

    /**
     * 한 번 갱신한다.
     *
     * @param trigger 로그에 남길 이름(정기·기동)
     * @return 기간 적재 결과. 앞 실행이 도는 중이었거나 실패했으면 null
     */
    public RangeSyncResponse run(String trigger) {
        LocalDate today;
        try {
            today = LocalDate.now(ZoneId.of(properties.getZone()));
        } catch (RuntimeException e) {
            log.warn("[RecallAutoSync] recall.auto-sync.zone 값이 틀림({}) — Asia/Seoul 로 계산", properties.getZone());
            today = LocalDate.now(ZoneId.of(RecallAutoSyncProperties.DEFAULT_ZONE));
        }
        return run(trigger, today);
    }

    /** 테스트용 — 오늘 날짜를 받는다 */
    RangeSyncResponse run(String trigger, LocalDate today) {
        if (!running.compareAndSet(false, true)) {
            log.info("[RecallAutoSync] {} — 앞 실행이 아직 도는 중이라 건너뜀", trigger);
            return null;
        }
        try {
            String latest = recallRepository.findLatestPublishDate();
            SyncWindow window = SyncWindow.of(today, latest,
                    properties.getLookbackDays(), properties.getMaxCatchUpDays());
            Long adminId = resolveAdminId();
            log.info("[RecallAutoSync] {} 시작 — 공표일 {}~{} (DB 마지막 공표일 {}){}", trigger,
                    window.from(), window.to(), latest,
                    adminId == null ? " · 관리자 계정이 없어 동기화 이력은 남기지 않음" : "");

            RangeSyncResponse r = recallSyncService.syncRange(adminId, window.from(), window.to(), false, 0, null);
            int images = properties.getImageLimit() > 0 ? recallSyncService.syncImages(properties.getImageLimit()) : 0;

            log.info("[RecallAutoSync] {} 끝 — {}일 훑음(공표 있던 날 {}일), 조회 {}건, 저장 {}건, 실패 {}건, 사진 채움 {}건{}",
                    trigger, r.daysScanned(), r.daysWithData(), r.fetched(), r.saved(), r.failed(), images,
                    r.failedDates() == null || r.failedDates().isEmpty() ? "" : ", 조회 실패한 날 " + r.failedDates());
            return r;
        } catch (RuntimeException e) {
            log.warn("[RecallAutoSync] {} 실패 — 다음 실행에서 다시 받는다: {}", trigger, e.toString());
            return null;
        } finally {
            running.set(false);
        }
    }

    /** 이력(api_sync_log.admin_id)에 남길 관리자. 없으면 null — 이력 행 없이 적재만 한다 */
    Long resolveAdminId() {
        Long configured = properties.getAdminId();
        if (configured != null) {
            if (userRepository.existsById(configured)) {
                return configured;
            }
            log.warn("[RecallAutoSync] recall.auto-sync.admin-id={} 계정이 없음 — ADMIN 계정 중 하나로 남긴다", configured);
        }
        return userRepository.findFirstByRoleOrderByIdAsc(Role.ADMIN).map(User::getId).orElse(null);
    }
}
