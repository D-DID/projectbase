package com.underfaker.recallcheck.service.kc;

import com.underfaker.recallcheck.client.SafetyKoreaCertClient;
import com.underfaker.recallcheck.client.dto.CertListApiResponse;
import com.underfaker.recallcheck.common.KcCertNumbers;
import com.underfaker.recallcheck.dto.internal.KcLookup;
import com.underfaker.recallcheck.entity.Certification;
import com.underfaker.recallcheck.repository.CertificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * KC 인증번호 → 인증 정보 조회 (10/3 추가, KC인증 연동).
 *
 * 순서: 번호 정리(KcCertNumbers) → certification 캐시(7일 이내면 그대로) → KC 목록 API(번호 정확히 일치만)
 *       → 캐시 저장(upsert). 검증 1건당 번호 최대 3개, 대개 1회 호출.
 *
 * <b>예외를 밖으로 던지지 않는다.</b> 검증(VerificationService)이 이 결과를 기다리므로, API 가 느리거나 죽어도
 * 판정은 계속돼야 한다. 실패는 KcLookup.Status.UNAVAILABLE 로 돌려주고, 오래된 캐시가 있으면 그걸 쓴다(STALE_CACHE).
 * 한 번 실패하면 1분 동안 API 를 부르지 않는다 — 쿠팡 배치 검증 50건이 건마다 타임아웃을 기다리지 않게.
 *
 * 왜 전체 적재가 아니라 번호별 조회인가 — KC 인증 DB 는 수십만 건이고 목록 API 는 페이징이 없다.
 * 판정에 필요한 건 "사용자가 산 제품의 번호" 하나뿐이라 그때그때 조회하고 캐시한다.
 *
 * 트랜잭션: 이 클래스는 트랜잭션을 열지 않고, <b>DB 트랜잭션 밖에서</b> 불린다(VerificationService 가
 * 검증 트랜잭션을 열기 전에 부른다) — 외부 API 를 기다리는 동안 DB 커넥션을 잡고 있지 않게(10/3 리뷰).
 * 캐시 저장은 CertificationCacheWriter 가 자기 트랜잭션(REQUIRES_NEW)으로 하고, 실패하면 여기서 잡아
 * 조회 결과는 그대로 쓴다. 컬럼 길이 초과는 toEntity 에서 잘라 막는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KcLookupService {

    /** 캐시를 믿는 기간. 인증상태(적합 → 취소·사용금지)는 바뀔 수 있어서 너무 길게 두지 않는다. */
    static final Duration CACHE_TTL = Duration.ofDays(7);

    /** API 실패 후 다시 부르지 않는 시간 */
    static final long API_PAUSE_MILLIS = 60_000L;

    private final SafetyKoreaCertClient certClient;
    private final CertificationRepository certificationRepository;
    private final CertificationCacheWriter cacheWriter;

    private volatile long apiPausedUntil = 0L;

    /**
     * 검증 흐름용. 원문에서 번호를 뽑아 차례로 조회하고, 처음으로 찾은 결과를 돌려준다.
     * 하나도 못 찾으면 첫 번호의 결과(NOT_FOUND 또는 UNAVAILABLE).
     *
     * @param rawCertNum 사용자·확장이 보낸 KC 인증번호 칸 원문
     * @return 조회할 번호가 없으면 null (블록도 남기지 않는다)
     */
    public KcLookup lookup(String rawCertNum) {
        List<String> numbers = KcCertNumbers.extract(rawCertNum);
        if (numbers.isEmpty()) {
            return null;
        }
        KcLookup first = null;
        for (String number : numbers) {
            KcLookup result = lookupOne(number, false);
            if (result.isFound()) {
                return result;
            }
            if (first == null) {
                first = result;
            }
        }
        return first;
    }

    /**
     * 관리자 동기화용 — 캐시를 무시하고 API 로 다시 받는다. 1분 정지도 무시한다.
     *
     * @return 번호 모양이 아니면 null
     */
    public KcLookup refresh(String certNum) {
        String number = KcCertNumbers.first(certNum);
        return number == null ? null : lookupOne(number, true);
    }

    KcLookup lookupOne(String certNum, boolean force) {
        Certification cached = newest(certificationRepository.findByCertNum(certNum));
        if (!force && cached != null && isFresh(cached)) {
            return toLookup(cached, "CACHE");
        }
        if (!force && System.currentTimeMillis() < apiPausedUntil) {
            return cached != null ? toLookup(cached, "STALE_CACHE") : KcLookup.unavailable(certNum);
        }

        List<CertListApiResponse.Item> items;
        try {
            items = certClient.findByCertNumExact(certNum);
        } catch (RuntimeException e) {
            apiPausedUntil = System.currentTimeMillis() + API_PAUSE_MILLIS;
            log.warn("[KcLookup] {} 조회 실패 — {}초 동안 API 호출을 쉰다: {}",
                    certNum, API_PAUSE_MILLIS / 1000, e.getMessage());
            return cached != null ? toLookup(cached, "STALE_CACHE") : KcLookup.unavailable(certNum);
        }

        if (items.isEmpty()) {
            log.info("[KcLookup] {} — 인증 DB 에 없음", certNum);
            return KcLookup.notFound(certNum);
        }

        List<Certification> saved = new ArrayList<>();
        for (CertListApiResponse.Item item : items) {
            saved.add(saveQuietly(item));
        }
        Certification best = newest(saved);
        log.info("[KcLookup] {} — {} / {} ({}건)", certNum, best.getCertState(), best.getModelName(), items.size());
        return toLookup(best, "API");
    }

    /** 캐시 저장. 실패해도(동시 INSERT 로 PK 중복 등) 조회 결과는 버리지 않는다 — 다음 조회 때 다시 저장된다. */
    private Certification saveQuietly(CertListApiResponse.Item item) {
        try {
            return cacheWriter.upsert(item);
        } catch (RuntimeException e) {
            log.warn("[KcLookup] {} 캐시 저장 실패(조회 결과는 그대로 사용): {}", item.certNum(), e.getMessage());
            return toEntity(item);
        }
    }

    /**
     * API 응답 → 엔티티. schema.sql 의 컬럼 길이에 맞춰 자른다 — 길이 초과로 INSERT 가 실패하면
     * 이 저장에 참여한 검증 트랜잭션까지 롤백되기 때문이다. CertificationSyncService 도 이걸 쓴다.
     */
    public static Certification toEntity(CertListApiResponse.Item item) {
        return Certification.builder()
                .certUid(item.certUid())
                .certNum(cut(trim(item.certNum()), 64))
                .certState(cut(item.certState(), 100))
                .certDate(cut(item.certDate(), 8))
                .productName(cut(item.productName(), 255))
                .brandName(cut(item.brandName(), 255))
                .modelName(cut(item.modelName(), 255))
                .makerName(cut(item.makerName(), 255))
                .makerCntryName(cut(item.makerCntryName(), 255))
                .build();
    }

    static KcLookup toLookup(Certification c, String source) {
        return new KcLookup(KcLookup.Status.FOUND, c.getCertNum(), c.getCertState(), c.getCertDate(),
                c.getProductName(), c.getBrandName(), c.getModelName(), c.getMakerName(),
                c.getMakerCntryName(), source);
    }

    private static boolean isFresh(Certification c) {
        return c.getSyncedAt() != null && c.getSyncedAt().isAfter(LocalDateTime.now().minus(CACHE_TTL));
    }

    /** 같은 번호로 여러 행이면 인증일이 가장 최근인 것(yyyyMMdd 문자열 비교). */
    private static Certification newest(List<Certification> rows) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        return rows.stream()
                .max(Comparator.comparing(c -> c.getCertDate() == null ? "" : c.getCertDate()))
                .orElse(rows.get(0));
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }

    private static String cut(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
