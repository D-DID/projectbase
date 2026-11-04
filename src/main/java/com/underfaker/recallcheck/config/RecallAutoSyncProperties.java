package com.underfaker.recallcheck.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 10/31 — 리콜 데이터 자동 갱신(FR-016) 설정. recall.auto-sync.*
 *
 * 프로퍼티를 하나도 안 적어도 켜진 채로 기본값으로 돈다(각자 application.properties 를 고치지 않아도 되게).
 * 끄려면 recall.auto-sync.enabled=false. 실행 시각·대상 기간은 RecallAutoSyncScheduler 주석 참조.
 *
 * cron·zone 은 @Scheduled 가 같은 키를 직접 읽는다. 여기 기본값과 그쪽 기본값은 같은 상수를 쓴다.
 */
@ConfigurationProperties(prefix = "recall.auto-sync")
public class RecallAutoSyncProperties {

    /** 6시간마다 20분(00:20·06:20·12:20·18:20). 정각을 피해 상대 서버가 붐비는 시각을 비킨다 */
    public static final String DEFAULT_CRON = "0 20 */6 * * *";
    public static final String DEFAULT_ZONE = "Asia/Seoul";

    /** 자동 갱신을 쓸지. 테스트(application-it.properties·RecallCheckApplicationTests)는 끈다 */
    private boolean enabled = true;

    /** 서버가 뜰 때 한 번 갱신할지. 발표·개발 PC 는 정기 시각에 서버가 꺼져 있을 때가 많아서 기본 켬 */
    private boolean runOnStartup = true;

    /** 매번 다시 훑는 최근 일수(오늘 포함). 공표일이 지난 뒤에 등록·수정되는 공표문을 잡는다 */
    private int lookbackDays = 7;

    /**
     * DB 의 마지막 공표일이 이보다 오래됐어도 이만큼만 거슬러 올라간다(오늘 포함).
     * 그보다 오래된 빈틈은 관리자 기간 적재(/api/admin/sync/recalls/range)로 채운다.
     */
    private int maxCatchUpDays = 90;

    /** 한 번에 사진을 채울 리콜 수(상세 API 호출 수). 0 이면 사진은 안 채운다 */
    private int imageLimit = 30;

    /**
     * 동기화 이력(api_sync_log)에 남길 관리자 user_id. 비우면 ADMIN 권한 계정 중 id 가 가장 작은 계정.
     * 관리자 계정이 하나도 없으면 이력 행은 남기지 않고 서버 로그에만 쓴다(admin_id 가 NOT NULL·FK 라서).
     */
    private Long adminId;

    /** 문서용 — 실제 값은 @Scheduled 가 recall.auto-sync.cron 을 직접 읽는다 */
    private String cron = DEFAULT_CRON;

    /** 오늘 날짜·cron 의 시간대 */
    private String zone = DEFAULT_ZONE;

    /** 서버가 뜨고 나서 기동 갱신까지 기다리는 시간(ms) — 기동 직후 첫 요청과 겹치지 않게 */
    private long startupDelayMs = 20_000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isRunOnStartup() {
        return runOnStartup;
    }

    public void setRunOnStartup(boolean runOnStartup) {
        this.runOnStartup = runOnStartup;
    }

    public int getLookbackDays() {
        return lookbackDays;
    }

    public void setLookbackDays(int lookbackDays) {
        this.lookbackDays = lookbackDays;
    }

    public int getMaxCatchUpDays() {
        return maxCatchUpDays;
    }

    public void setMaxCatchUpDays(int maxCatchUpDays) {
        this.maxCatchUpDays = maxCatchUpDays;
    }

    public int getImageLimit() {
        return imageLimit;
    }

    public void setImageLimit(int imageLimit) {
        this.imageLimit = imageLimit;
    }

    public Long getAdminId() {
        return adminId;
    }

    public void setAdminId(Long adminId) {
        this.adminId = adminId;
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public String getZone() {
        return zone;
    }

    public void setZone(String zone) {
        this.zone = zone;
    }

    public long getStartupDelayMs() {
        return startupDelayMs;
    }

    public void setStartupDelayMs(long startupDelayMs) {
        this.startupDelayMs = startupDelayMs;
    }
}
