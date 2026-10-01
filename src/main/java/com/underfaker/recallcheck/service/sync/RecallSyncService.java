package com.underfaker.recallcheck.service.sync;

import com.underfaker.recallcheck.client.SafetyKoreaRecallClient;
import com.underfaker.recallcheck.client.dto.RecallListApiResponse;
import com.underfaker.recallcheck.common.PageResponse;
import com.underfaker.recallcheck.dto.response.KeywordSyncResponse;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
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
 *
 * ── 10/1 정정 ──
 * "공표일로 쪼개 받는 것 말고는 방법이 없다"는 틀렸다. 공표일 조회는 2023-07-12 이후만
 * 돌려준다(실측: 2022-01 전체 0건, 2023-07 은 12·14·24일 5건). 그 이전은 공표일로는
 * 안 나오지만 품목명 조회(recallProductName=완구)에는 2012-03-05 공표분까지 나온다.
 * 과거분은 syncByKeywords() 로 채운다. 기간 적재(syncRange)는 2023-07-12 이후 보충용이다.
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

    // ------------------------------------------------------------------ 품목명 키워드 적재 (10/1 추가)

    /** 목록 API 1회 응답 상한. 이만큼 왔으면 잘렸다고 본다. */
    private static final int LIST_CAP = 1000;

    /**
     * 어린이제품 안전관리 대상 34품목(제4차 어린이제품 안전관리 기본계획 붙임3, 2025.1)을
     * 부분 일치용으로 줄인 말. recall_product_name 이 "기타완구(완구)", "중의류(아섬)(아동용 섬유제품)"
     * 처럼 오므로 품목명의 핵심 명사만 넣는다. 붙임3의 "기타 어린이제품"은 어린이·유아·아동으로 받는다.
     */
    static final List<String> CHILD_SEEDS = List.of(
            // 안전인증 4
            "물놀이", "놀이기구", "보호장치", "비비탄",
            // 안전확인 16
            "섬유제품", "보호용품", "보호장구", "안전모", "합성수지", "완구", "학용품",
            "삼륜차", "자전거", "보행기", "유모차", "캐리어", "침대", "의자",
            "온열팩", "주머니난로", "구명복", "스케이트보드",
            // 공급자적합성확인 14
            "가죽제품", "안경테", "선글라스", "물안경", "운동화", "롤러스케이트",
            "스키", "스노보드", "킥보드", "장신구", "쇼핑카트", "가구", "우산", "양산",
            // 기타 어린이제품
            "어린이", "유아", "아동");

    /**
     * 어린이제품 밖의 KC 대상(전기용품·생활용품) 중 리콜이 잦은 품목. 최근 3년 DB 에 없는
     * 옛 품목을 찾아내는 출발점이다. 여기서 받은 품목명이 다시 키워드가 되므로(expand)
     * 빠짐없이 적을 필요는 없다.
     */
    static final List<String> GENERAL_SEEDS = List.of(
            "전기", "전지", "충전", "어댑터", "전원", "조명", "램프", "등기구", "전선", "멀티탭",
            "콘센트", "스위치", "히터", "난로", "장판", "매트", "온수", "찜질", "드라이어", "선풍기",
            "가습기", "제습기", "냉장", "냉동", "세탁", "청소기", "레인지", "밥솥", "오븐", "커피",
            "믹서", "분쇄", "모니터", "컴퓨터", "텔레비전", "오디오", "스피커", "이어폰", "휴대",
            "무선", "자동차", "타이어", "헬멧", "압력", "냄비", "프라이팬", "라이터", "가스", "버너",
            "캠핑", "텐트", "사다리", "의류", "신발", "가방", "운동", "전동", "공구", "스쿠터",
            "보일러", "에어컨", "공기청정", "정수기", "비데", "면도기", "헤어", "안마", "마사지");

    /**
     * FR-016 품목명 키워드 적재 — 공표일로는 안 나오는 2023-07-12 이전 공표분을 채운다.
     *
     * 동작:
     *   1. keywords 를 대기열에 넣는다. 비었으면 CHILD_SEEDS + GENERAL_SEEDS.
     *      fromDb=true 면 DB 에 이미 있는 품목명에서 뽑은 키워드도 넣는다.
     *   2. 하나씩 recallProductName 부분 일치로 조회해 건별로 upsert 한다.
     *      같은 리콜이 여러 키워드에 걸리므로 이번 실행에서 이미 저장한 recallUid 는 건너뛴다.
     *   3. expand=true 면 받은 행의 품목명에서 새 키워드를 뽑아 대기열 끝에 붙인다.
     *      1,000건 상한에 걸린 넓은 키워드("전기")도 이 단계에서 잘게 나뉘어 다시 훑어진다.
     *   4. 상한에 안 걸린 키워드를 포함하는 긴 키워드는 부르지 않는다(KeywordQueue 주석).
     *
     * 몇 번을 다시 돌려도 결과가 같다(upsert). 중간에 끊겨도 저장된 건은 남는다.
     *
     * @param keywords   조회할 품목명 조각들. null/빈 목록이면 기본 목록.
     * @param expand     받은 품목명으로 키워드를 넓혀 갈지(기본 true)
     * @param fromDb     DB 에 있는 품목명도 출발 키워드로 쓸지(기본 true)
     * @param prune      이미 다 받은 키워드를 포함하는 키워드를 생략할지(기본 true)
     * @param maxCalls   이번 실행에서 부를 목록 API 최대 횟수. 남은 건 pendingKeywords 로 돌려준다.
     * @param withImages 상세 API 로 사진까지 채울지. 기본 false — 사진은 /recalls/images 로 따로.
     */
    public KeywordSyncResponse syncByKeywords(Long adminId, List<String> keywords, boolean expand,
                                              boolean fromDb, boolean prune, int maxCalls,
                                              boolean withImages) {
        if (maxCalls <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "maxCalls 는 1 이상이어야 합니다.");
        }

        KeywordQueue queue = new KeywordQueue(prune);
        List<String> seeds = (keywords == null || keywords.isEmpty())
                ? concat(CHILD_SEEDS, GENERAL_SEEDS) : keywords;
        for (String k : seeds) {
            queue.offer(KeywordQueue.cleanUserKeyword(k));
        }
        if (fromDb) {
            for (String name : recallRepository.findDistinctProductNames()) {
                queue.offer(KeywordQueue.keywordOf(name));
            }
        }

        ApiSyncLog syncLog = ApiSyncLog.start(adminId, ApiType.RECALL);
        LocalDateTime startedAt = LocalDateTime.now();

        Set<Long> seen = new HashSet<>();
        Map<String, Integer> insertedByYear = new TreeMap<>();
        List<String> capped = new ArrayList<>();
        List<String> failedKeywords = new ArrayList<>();
        List<KeywordSyncResponse.KeywordResult> results = new ArrayList<>();
        int calls = 0, withData = 0, fetched = 0, inserted = 0, updated = 0, failed = 0;
        String oldest = null, newest = null;

        String keyword;
        while (calls < maxCalls && (keyword = queue.next()) != null) {
            calls++;
            RecallListApiResponse response;
            try {
                response = recallClient.fetchList(SafetyKoreaRecallClient.KEY_PRODUCT_NAME, keyword);
            } catch (RuntimeException e) {
                log.warn("[RecallSync] 키워드 '{}' 목록 조회 실패: {}", keyword, e.getMessage());
                failedKeywords.add(keyword);
                results.add(new KeywordSyncResponse.KeywordResult(keyword, 0, 0, false, "ERROR"));
                sleepQuietly();
                continue;
            }

            List<RecallListApiResponse.Item> items =
                    response.resultData() == null ? List.of() : response.resultData();
            boolean isCapped = items.size() >= LIST_CAP;
            if (isCapped) {
                capped.add(keyword);
                log.warn("[RecallSync] 키워드 '{}' 가 {}건 상한에 걸렸다 — 결과 품목명으로 잘게 다시 훑는다",
                        keyword, LIST_CAP);
            } else {
                queue.markComplete(keyword);
            }

            int newHere = 0;
            for (RecallListApiResponse.Item item : items) {
                if (expand) {
                    queue.offer(KeywordQueue.keywordOf(item.recallProductName()));
                }
                Long uid = item.recallUid();
                if (uid == null || !seen.add(uid)) {
                    continue;
                }
                String date = item.publishDate();
                if (date != null && PUBLISH_DATE.matcher(date).matches()) {
                    if (oldest == null || date.compareTo(oldest) < 0) oldest = date;
                    if (newest == null || date.compareTo(newest) > 0) newest = date;
                }
                boolean existed = recallRepository.existsById(uid);
                if (!recallUpsertService.upsert(item, withImages)) {
                    failed++;
                } else if (existed) {
                    updated++;
                } else {
                    inserted++;
                    newHere++;
                    String year = (date != null && date.length() >= 4) ? date.substring(0, 4) : "unknown";
                    insertedByYear.merge(year, 1, Integer::sum);
                }
            }

            fetched += items.size();
            if (!items.isEmpty()) {
                withData++;
                results.add(new KeywordSyncResponse.KeywordResult(
                        keyword, items.size(), newHere, isCapped, response.resultCode()));
            }
            log.info("[RecallSync] 키워드 {}/{} '{}' — 조회 {}건, 신규 {}건 (누적 신규 {}건)",
                    calls, maxCalls, keyword, items.size(), newHere, inserted);
            sleepQuietly();
        }

        List<String> pending = queue.remaining();
        syncLog.finish(shortCode(failedKeywords.isEmpty() && pending.isEmpty() ? "2000" : "2000-P"),
                inserted + updated);
        apiSyncLogRepository.save(syncLog);

        log.info("[RecallSync] 키워드 적재 완료 — 호출 {}회(생략 {}), 조회 {}건, 고유 {}건, 신규 {}건, 갱신 {}건, 실패 {}건, "
                        + "공표일 {}~{}, 상한 {}개, 조회실패 {}개, 남은 키워드 {}개",
                calls, queue.skipped(), fetched, seen.size(), inserted, updated, failed,
                oldest, newest, capped.size(), failedKeywords.size(), pending.size());

        return new KeywordSyncResponse(calls, withData, queue.skipped(), fetched, seen.size(),
                inserted, updated, failed, oldest, newest, insertedByYear, capped, failedKeywords,
                pending, withImages, syncLog.getId(), startedAt, LocalDateTime.now(), results);
    }

    private static List<String> concat(List<String> a, List<String> b) {
        List<String> out = new ArrayList<>(a);
        out.addAll(b);
        return out;
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
