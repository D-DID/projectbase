package com.underfaker.recallcheck.service.sync;

import com.underfaker.recallcheck.client.SafetyKoreaRecallClient;
import com.underfaker.recallcheck.client.dto.RecallListApiResponse;
import com.underfaker.recallcheck.common.PageResponse;
import com.underfaker.recallcheck.dto.response.RangeSyncResponse;
import com.underfaker.recallcheck.dto.response.SyncLogResponse;
import com.underfaker.recallcheck.entity.ApiSyncLog;
import com.underfaker.recallcheck.entity.enums.ApiType;
import com.underfaker.recallcheck.exception.BusinessException;
import com.underfaker.recallcheck.exception.ErrorCode;
import com.underfaker.recallcheck.repository.ApiSyncLogRepository;
import com.underfaker.recallcheck.repository.RecallRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * FR-016, 017 — 국가기술표준원 Open API 리콜 데이터 적재·로그 기록.
 *
 * ── 9/20 구조 변경 ──
 * 전건(공표 4,249건) 적재가 구버전 구조로는 불가능해서 셋을 고쳤다.
 *
 * (1) 클래스 레벨 @Transactional 제거
 *     sync() 전체가 트랜잭션 하나였다. 1건마다 상세 API 를 부르므로 전건 적재는
 *     트랜잭션 하나에서 HTTP 호출 8,000회 + 커넥션 수십 분 점유가 된다. 중간에 터지면
 *     성공분까지 전부 롤백된다. 저장 단위는 RecallUpsertService 가 건별로 끊는다.
 *     — 그 클래스를 별도 빈으로 뺀 이유는 거기 주석 참조(self-invocation).
 *
 * (2) 공표일을 프로퍼티가 아니라 요청 파라미터로도 받는다
 *     구버전은 application.properties 의 recall-publish-date 하나만 봤다. 날짜를 바꾸려면
 *     매번 앱을 재기동해야 했고, 공표일이 수백 개인 전건 적재에는 쓸 수 없는 방식이다.
 *     syncRange() 가 from~to 를 하루씩 훑는다. 기존 sync() 는 그대로 둬서 호환을 지킨다.
 *
 * (3) 사진 적재를 분리할 수 있게 했다
 *     상세 API 호출이 적재 시간의 대부분이다. withImages=false 로 본문만 빠르게 넣고,
 *     사진은 syncImages() 로 나중에 채우는 편이 전건 적재에서 훨씬 안전하다.
 *
 * <b>목록 API 는 페이징이 없고 1회 응답 상한이 1,000건이다.</b> 그래서 conditionKey=all 로
 * 전건을 한 번에 받는 건 불가능하고(4,249 > 1,000), 공표일로 쪼개 받는 것 말고는 방법이 없다.
 * 공표가 없는 날은 2004(No Data)로 즉시 응답하므로 빈 날짜를 훑는 비용은 크지 않다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecallSyncService {

    /** publishDate 는 yyyyMMdd 8자리. 형식이 틀리면 API 가 4005(Invalid Parameter)로 답한다. */
    private static final Pattern PUBLISH_DATE = Pattern.compile("^\\d{8}$");

    /**
     * yyyyMMdd 포맷터.
     *
     * ResolverStyle.STRICT 를 반드시 지정해야 한다. 기본값인 SMART 는 실재하지 않는 날짜를
     * 예외 없이 조용히 보정한다 — 2026 은 평년이라 2월 29일이 없는데 "20260229" 를 넣으면
     * DateTimeParseException 이 아니라 2026-02-28 이 돌아온다. 오타 하나가 엉뚱한 날짜를
     * 적재하는 것으로 이어지므로 STRICT 로 막는다.
     *
     * STRICT 에서는 연도 패턴이 yyyy(year-of-era)가 아니라 uuuu(proleptic year)여야 한다.
     * yyyy 를 그대로 쓰면 era 정보가 없다며 파싱이 통째로 실패한다.
     */
    private static final DateTimeFormatter YYYYMMDD =
            DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT);

    /** 한 번의 기간 적재로 훑을 수 있는 최대 날짜 수. 요청 하나가 몇 시간씩 걸리는 걸 막는다. */
    private static final int MAX_DAYS_PER_REQUEST = 400;

    private final SafetyKoreaRecallClient recallClient;
    private final RecallUpsertService recallUpsertService;
    private final RecallRepository recallRepository;
    private final ApiSyncLogRepository apiSyncLogRepository;

    /** 적재 대상 공표일 (yyyyMMdd). 단일 날짜 적재용. 기간 적재는 요청 파라미터를 쓴다. */
    @Value("${openapi.safety-korea.recall-publish-date}")
    private String recallPublishDate;

    /** 한 번에 적재할 최대 건수. 0 이하 = 전건. */
    @Value("${openapi.safety-korea.recall-sync-max:0}")
    private int recallSyncMax;

    /** 목록 API 호출 사이 대기(ms). 날짜를 수백 개 훑을 때 상대 서버에 부하를 덜 준다. */
    @Value("${openapi.safety-korea.request-delay-ms:120}")
    private long requestDelayMs;

    // ------------------------------------------------------------------ 단일 날짜 (기존 호환)

    /**
     * FR-016 리콜 데이터 동기화 — application.properties 의 공표일 하나만 적재한다.
     *
     * 적재 0건은 예외가 아니다. 해당 공표일에 건이 없으면 API 가 2004(No Data)로 정상 응답한다.
     */
    public SyncLogResponse sync(Long adminId) {
        if (!isValidDate(recallPublishDate)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "openapi.safety-korea.recall-publish-date 는 yyyyMMdd 8자리여야 합니다. 현재 값: "
                            + recallPublishDate);
        }

        ApiSyncLog syncLog = ApiSyncLog.start(adminId, ApiType.RECALL);
        DayResult result = syncOneDay(recallPublishDate, true, recallSyncMax, null);

        syncLog.finish(shortCode(result.resultCode()), result.saved());
        apiSyncLogRepository.save(syncLog);

        log.info("[RecallSync] publishDate={} 조회 {}건, 적재 {}건",
                recallPublishDate, result.fetched(), result.saved());
        return toResponse(syncLog);
    }

    // ------------------------------------------------------------------ 기간 적재 (신규)

    /**
     * FR-016 기간 적재 — from 부터 to 까지 하루씩 훑는다.
     *
     * @param adminId        실행한 관리자 (api_sync_log.admin_id)
     * @param from           시작 공표일 yyyyMMdd
     * @param to             종료 공표일 yyyyMMdd (포함)
     * @param withImages     상세 API 로 사진까지 채울지. 전건 적재는 false 를 권한다.
     * @param max            전체 적재 상한. 0 이하면 제한 없음.
     * @param categoryFilter category_name 에 이 문자열이 포함된 건만 적재. null 이면 전부.
     *                       유아·어린이제품만 넣고 싶으면 "어린이" 를 넘긴다
     *                       (실데이터의 category_name 은 "어린이&gt;완구" 뿐 아니라
     *                        "기타어린이제품&gt;" 형태도 있어서 prefix 가 아니라 포함 검사다).
     */
    public RangeSyncResponse syncRange(Long adminId, String from, String to,
                                       boolean withImages, int max, String categoryFilter) {
        LocalDate start = parseDate(from, "from");
        LocalDate end = parseDate(to, "to");
        if (end.isBefore(start)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "to 가 from 보다 앞섭니다. from=" + from + " to=" + to);
        }
        long days = start.datesUntil(end.plusDays(1)).count();
        if (days > MAX_DAYS_PER_REQUEST) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "한 번에 훑을 수 있는 날짜는 " + MAX_DAYS_PER_REQUEST + "일까지입니다. 요청: " + days
                            + "일. 기간을 나눠서 호출하세요.");
        }

        ApiSyncLog syncLog = ApiSyncLog.start(adminId, ApiType.RECALL);
        LocalDateTime startedAt = LocalDateTime.now();

        int daysScanned = 0, daysWithData = 0, fetched = 0, saved = 0, failed = 0;
        List<String> failedDates = new ArrayList<>();

        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            String dateStr = d.format(YYYYMMDD);
            int remaining = max > 0 ? max - saved : Integer.MAX_VALUE;
            if (remaining <= 0) {
                log.info("[RecallSync] 상한 {}건 도달 — {} 에서 중단", max, dateStr);
                break;
            }

            DayResult r = syncOneDay(dateStr, withImages, remaining, categoryFilter);
            daysScanned++;
            fetched += r.fetched();
            saved += r.saved();
            failed += r.failed();
            if (r.fetched() > 0) {
                daysWithData++;
            }
            if (r.error()) {
                failedDates.add(dateStr);
            }

            if (daysScanned % 30 == 0) {
                log.info("[RecallSync] 진행 {}/{}일 — 누적 조회 {}건 적재 {}건",
                        daysScanned, days, fetched, saved);
            }
            sleepQuietly();
        }

        syncLog.finish(shortCode(failedDates.isEmpty() ? "2000" : "2000-P"), saved);
        apiSyncLogRepository.save(syncLog);

        log.info("[RecallSync] 기간 적재 완료 {}~{} — {}일 훑음, 공표 있던 날 {}일, 조회 {}건, 적재 {}건, 실패 {}건",
                from, to, daysScanned, daysWithData, fetched, saved, failed);

        return new RangeSyncResponse(from, to, daysScanned, daysWithData, fetched, saved, failed,
                withImages, syncLog.getId(), startedAt, LocalDateTime.now(), failedDates);
    }

    /**
     * 이미 적재된 리콜 중 사진이 없는 건만 골라 상세 API 로 사진을 채운다.
     *
     * 본문을 withImages=false 로 빠르게 넣은 뒤 이걸 나눠 돌리는 용도다.
     * 상세 API 는 건당 1회라 한 번에 다 돌리면 오래 걸리므로 limit 로 끊는다.
     *
     * @param limit 이번 호출에서 처리할 최대 건수
     * @return 사진을 새로 채운 리콜 건수
     */
    public int syncImages(int limit) {
        List<Long> targets = recallRepository.findUidsWithoutFiles(
                PageRequest.of(0, Math.max(1, limit)));
        int done = 0;
        for (Long uid : targets) {
            if (recallUpsertService.upsertImagesOnly(uid) > 0) {
                done++;
            }
            sleepQuietly();
        }
        log.info("[RecallSync] 사진 적재 — 대상 {}건 중 {}건에 사진을 채움", targets.size(), done);
        return done;
    }

    // ------------------------------------------------------------------ 내부

    /** api_sync_log.result_code 컬럼 길이. 엔티티의 @Column(length = 10) 과 같아야 한다. */
    private static final int RESULT_CODE_MAX = 10;

    /**
     * result_code 를 컬럼 길이에 맞춰 자른다.
     *
     * 9/21 추가. 기간 적재에서 하루라도 실패하면 "2000-PARTIAL"(12자) 을 넣으려다
     * VARCHAR(10) 를 넘겨 저장 단계에서 터졌다. 그러면 수천 건을 이미 다 적재해 놓고도
     * 응답이 500 으로 돌아온다 — 적재 자체는 성공했는데 기록용 로그 한 줄 때문에 실패로 보인다.
     * 외부 API 가 돌려주는 코드도 길이를 보장하지 않으므로 한곳에서 방어한다.
     */
    private static String shortCode(String code) {
        if (code == null) {
            return null;
        }
        return code.length() <= RESULT_CODE_MAX ? code : code.substring(0, RESULT_CODE_MAX);
    }

    /** 하루치 적재 결과 */
    private record DayResult(int fetched, int saved, int failed, String resultCode, boolean error) {
    }

    /**
     * 공표일 하루를 적재한다.
     *
     * 목록 조회가 실패해도 예외를 밖으로 던지지 않는다 — 기간 적재 중 하루가 실패했다고
     * 나머지 수백 일을 버릴 이유가 없다. 실패한 날짜는 응답의 failedDates 로 돌려주므로
     * 그 날짜만 다시 돌리면 된다.
     */
    private DayResult syncOneDay(String publishDate, boolean withImages,
                                 int limit, String categoryFilter) {
        RecallListApiResponse response;
        try {
            response = recallClient.fetchList(SafetyKoreaRecallClient.KEY_PUBLISH_DATE, publishDate);
        } catch (RuntimeException e) {
            log.warn("[RecallSync] 목록 조회 실패 (publishDate={}): {}", publishDate, e.getMessage());
            return new DayResult(0, 0, 0, "ERROR", true);
        }

        if (!response.isSuccess() || response.resultData() == null || response.resultData().isEmpty()) {
            return new DayResult(0, 0, 0, response.resultCode(), false);
        }

        List<RecallListApiResponse.Item> items = response.resultData();
        int fetched = items.size();

        // 목록 API 상한이 1,000건이라 하루에 그 이상 공표된 날은 잘려서 내려올 수 있다.
        if (fetched >= 1000) {
            log.warn("[RecallSync] publishDate={} 가 1,000건으로 상한에 걸렸다 — 일부가 누락됐을 수 있다",
                    publishDate);
        }

        int saved = 0, failed = 0;
        for (RecallListApiResponse.Item item : items) {
            if (saved >= limit) {
                break;
            }
            if (categoryFilter != null && !matchesCategory(item, categoryFilter)) {
                continue;
            }
            if (recallUpsertService.upsert(item, withImages)) {
                saved++;
            } else {
                failed++;
            }
        }
        return new DayResult(fetched, saved, failed, response.resultCode(), false);
    }

    private boolean matchesCategory(RecallListApiResponse.Item item, String filter) {
        String category = item.categoryName();
        return category != null && category.contains(filter);
    }

    private LocalDate parseDate(String raw, String paramName) {
        if (!isValidDate(raw)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    paramName + " 은 yyyyMMdd 8자리여야 합니다. 현재 값: " + raw);
        }
        try {
            return LocalDate.parse(raw, YYYYMMDD);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    paramName + " 이 실재하는 날짜가 아닙니다: " + raw);
        }
    }

    private boolean isValidDate(String raw) {
        return raw != null && PUBLISH_DATE.matcher(raw).matches();
    }

    private void sleepQuietly() {
        if (requestDelayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(requestDelayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private SyncLogResponse toResponse(ApiSyncLog syncLog) {
        return new SyncLogResponse(
                syncLog.getId(), syncLog.getAdminId(), syncLog.getApiType(),
                syncLog.getResultCode(), syncLog.getRecordCount(),
                syncLog.getStartedAt(), syncLog.getFinishedAt());
    }

    /** FR-017 동기화 이력 조회 */
    @Transactional(readOnly = true)
    public PageResponse<SyncLogResponse> getLogs(int page, int size) {
        Page<ApiSyncLog> logs =
                apiSyncLogRepository.findAllByOrderByStartedAtDesc(PageRequest.of(page, size));
        return PageResponse.from(logs.map(this::toResponse));
    }
}
