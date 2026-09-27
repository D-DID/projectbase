package com.underfaker.recallcheck.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * OCR 호출 건수 상한. 무료 할당량을 넘기 전에 스스로 멈춘다.
 *
 * 9/21 추가. 이유는 하나다 — CLOVA·Vision 키가 팀원(김동건) 계정이라 우리 실수가
 * 남의 청구서로 간다. "조심하자"는 대책이 아니라서 코드로 막는다.
 *
 * ── 동작 ──
 * 엔진별로 이번 달 호출 수를 세고, 상한에 닿으면 tryAcquire() 가 false 를 돌려준다.
 * 호출부는 false 를 받으면 HTTP 요청을 아예 만들지 않는다. 달이 바뀌면 0 으로 리셋한다.
 *
 * ── 왜 파일에 저장하나 ──
 * 메모리에만 두면 앱을 재시작할 때마다 카운터가 0 이 된다. 개발하면서 하루에 앱을
 * 스무 번 띄우는데, 그때마다 리셋되면 상한이 사실상 없는 것과 같다. 실제 호출은
 * 제공자 쪽에 누적되고 있는데 우리 카운터만 0 인 상태가 제일 위험하다.
 * 그래서 프로젝트 루트의 작은 파일에 기록한다. DB 를 쓰지 않는 이유는 이 값이
 * 비즈니스 데이터가 아니고, DB 가 꺼져 있어도 상한은 지켜져야 하기 때문이다.
 *
 * ── 한계 (반드시 알고 쓸 것) ──
 * 이 카운터는 <b>이 컴퓨터에서 나간 호출만</b> 센다. 팀원 셋이 각자 로컬에서 같은 키로
 * 돌리면 각자 80건씩, 합쳐서 240건이 나간다. 진짜 전역 상한은 제공자 콘솔의
 * 할당량 설정뿐이다. 이건 "내 쪽 실수로 폭주하는 것"을 막는 장치이지
 * "팀 전체 사용량 관리"가 아니다.
 */
@Slf4j
@Component
public class OcrCallBudget {

    /** CLOVA General OCR — 월 100건 무료. 여유를 두고 80 에서 끊는다. */
    public static final String CLOVA = "clova";
    /** Google Cloud Vision — 월 1,000건 무료. 여유를 두고 800 에서 끊는다. */
    public static final String VISION = "vision";

    private static final String KEY_MONTH = "month";
    private static final String KEY_PREFIX = "count.";

    private final Map<String, Integer> limits;
    private final Path stateFile;

    private final Map<String, Integer> used = new HashMap<>();
    private String month;
    /** 파일 저장이 실패한 적이 있는지. 한 번만 경고하려고 둔다. */
    private boolean saveBroken;

    public OcrCallBudget(
            @Value("${ocr.budget.clova-limit:80}") int clovaLimit,
            @Value("${ocr.budget.vision-limit:800}") int visionLimit,
            @Value("${ocr.budget.state-file:./ocr-budget.properties}") String stateFilePath) {

        this.limits = Map.of(CLOVA, clovaLimit, VISION, visionLimit);
        this.stateFile = Path.of(stateFilePath);
        load();

        log.info("[OcrBudget] 이번 달 상한 — CLOVA {}건, Vision {}건 (현재 사용 CLOVA {}, Vision {})",
                clovaLimit, visionLimit, usedOf(CLOVA), usedOf(VISION));
    }

    /**
     * 호출 1건을 예약한다.
     *
     * @param engine {@link #CLOVA} 또는 {@link #VISION}
     * @return true 면 호출해도 된다. false 면 상한에 닿았으니 호출하지 말 것.
     */
    public synchronized boolean tryAcquire(String engine) {
        rollMonthIfNeeded();

        int limit = limits.getOrDefault(engine, 0);
        if (limit <= 0) {
            // 모르는 엔진은 막는다. 새 엔진을 붙이면서 상한 설정을 깜빡하는 걸
            // 조용히 통과시키면 이 클래스를 만든 의미가 없다.
            log.error("[OcrBudget] 상한이 설정되지 않은 엔진이라 호출을 막는다: {}", engine);
            return false;
        }

        int current = usedOf(engine);
        if (current >= limit) {
            log.error("[OcrBudget] {} 이번 달 상한 도달 — {}/{}건. 호출하지 않는다. "
                            + "더 쓰려면 ocr.budget.{}-limit 을 올리거나 다음 달을 기다릴 것.",
                    engine, current, limit, engine);
            return false;
        }

        used.put(engine, current + 1);
        save();

        int remaining = limit - (current + 1);
        if (remaining <= 10) {
            log.warn("[OcrBudget] {} 잔여 {}건 — 상한 {}건에 근접했다.", engine, remaining, limit);
        } else {
            log.info("[OcrBudget] {} 호출 {}/{}건", engine, current + 1, limit);
        }
        return true;
    }

    /** 남은 건수. 화면이나 관리 API 에서 보여 줄 때 쓴다. */
    public synchronized int remaining(String engine) {
        rollMonthIfNeeded();
        return Math.max(0, limits.getOrDefault(engine, 0) - usedOf(engine));
    }

    /** 이번 달 사용 건수. */
    public synchronized int used(String engine) {
        rollMonthIfNeeded();
        return usedOf(engine);
    }

    // ------------------------------------------------------------------ 내부

    private int usedOf(String engine) {
        return used.getOrDefault(engine, 0);
    }

    private void rollMonthIfNeeded() {
        String now = YearMonth.now().toString();
        if (!now.equals(month)) {
            log.info("[OcrBudget] 달이 바뀌어 카운터를 초기화한다: {} → {}", month, now);
            month = now;
            used.clear();
            save();
        }
    }

    private void load() {
        String now = YearMonth.now().toString();

        if (!Files.exists(stateFile)) {
            month = now;
            return;
        }

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(stateFile)) {
            props.load(in);
        } catch (IOException e) {
            // 읽기 실패 시 0 부터 시작한다. 여기서 앱을 죽이면 안 된다 —
            // 상한 장치가 앱 기동을 막는 건 과잉이다.
            log.warn("[OcrBudget] 상태 파일을 읽지 못했다. 0 부터 센다: {} ({})",
                    stateFile, e.getMessage());
            month = now;
            return;
        }

        String saved = props.getProperty(KEY_MONTH);
        if (!now.equals(saved)) {
            // 지난 달 기록이면 버린다.
            month = now;
            return;
        }

        month = saved;
        for (String engine : limits.keySet()) {
            String raw = props.getProperty(KEY_PREFIX + engine);
            if (raw == null) {
                continue;
            }
            try {
                used.put(engine, Math.max(0, Integer.parseInt(raw.trim())));
            } catch (NumberFormatException e) {
                log.warn("[OcrBudget] 상태 파일의 {} 값이 숫자가 아니라 무시한다: {}", engine, raw);
            }
        }
    }

    private void save() {
        Properties props = new Properties();
        props.setProperty(KEY_MONTH, month);
        used.forEach((engine, count) -> props.setProperty(KEY_PREFIX + engine, String.valueOf(count)));

        try {
            Path parent = stateFile.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream out = Files.newOutputStream(stateFile)) {
                props.store(out, "OCR 호출 건수 — 자동 생성. 손으로 고치지 말 것.");
            }
            saveBroken = false;
        } catch (IOException e) {
            if (!saveBroken) {
                // 한 번만 경고한다. 매 호출마다 같은 줄을 찍으면 로그만 지저분해진다.
                log.error("[OcrBudget] 상태 파일을 쓰지 못했다. 앱을 재시작하면 카운터가 0 으로 "
                                + "돌아가니 주의할 것: {} ({})",
                        stateFile, e.getMessage());
                saveBroken = true;
            }
        }
    }
}
