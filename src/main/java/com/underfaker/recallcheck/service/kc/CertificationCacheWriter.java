package com.underfaker.recallcheck.service.kc;

import com.underfaker.recallcheck.client.dto.CertListApiResponse;
import com.underfaker.recallcheck.entity.Certification;
import com.underfaker.recallcheck.repository.CertificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * certification 캐시 저장 전용 (10/3 추가, KC인증 연동).
 *
 * 왜 따로 떼어 REQUIRES_NEW 로 저장하나 (10/3 리뷰에서 발견):
 * 같은 새 인증번호를 두 요청이 거의 동시에 조회하면(더블클릭, 쿠팡 배치에 같은 상품 2건) 둘 다 캐시에 없다고 보고
 * 같은 cert_uid 로 INSERT 한다. 이 저장이 검증 트랜잭션에 섞여 있으면 늦은 쪽 검증이 커밋 때 PK 중복으로
 * 통째로 실패한다(500, 검증·추출 행까지 롤백). 저장을 자기 트랜잭션으로 분리하고 즉시 flush 해서,
 * 실패해도 이 저장만 롤백되고 호출 쪽(KcLookupService)이 잡아서 조회 결과는 그대로 쓴다.
 */
@Component
@RequiredArgsConstructor
public class CertificationCacheWriter {

    private final CertificationRepository certificationRepository;

    /**
     * 있으면 갱신, 없으면 추가. certUid 가 없는 행은 저장하지 않고 값만 돌려준다(PK 가 없다).
     *
     * @throws RuntimeException 저장 실패(PK 중복 등) — 이 트랜잭션만 롤백된다. 호출 쪽이 잡는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Certification upsert(CertListApiResponse.Item item) {
        Certification fresh = KcLookupService.toEntity(item);
        if (item.certUid() == null) {
            return fresh;
        }
        return certificationRepository.findById(item.certUid())
                .map(existing -> {
                    existing.syncFrom(fresh);
                    return existing;
                })
                .orElseGet(() -> certificationRepository.saveAndFlush(fresh));
    }

    /** 관리자 갱신용 — 인증 DB 에서 못 찾은 번호의 캐시 행도 "확인함"으로 시각을 갱신한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markChecked(String certNum) {
        for (Certification c : certificationRepository.findByCertNum(certNum)) {
            c.markChecked();
        }
    }
}
