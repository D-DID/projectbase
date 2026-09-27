package com.underfaker.recallcheck.service.sync;

import com.underfaker.recallcheck.client.SafetyKoreaRecallClient;
import com.underfaker.recallcheck.client.dto.RecallDetailApiResponse;
import com.underfaker.recallcheck.client.dto.RecallListApiResponse;
import com.underfaker.recallcheck.entity.Recall;
import com.underfaker.recallcheck.entity.RecallFile;
import com.underfaker.recallcheck.entity.enums.FileDiv;
import com.underfaker.recallcheck.repository.RecallFileRepository;
import com.underfaker.recallcheck.repository.RecallRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리콜 1건 저장 — <b>건별 트랜잭션 경계</b>.
 *
 * 왜 별도 클래스인가:
 * 구버전 RecallSyncService 는 클래스에 @Transactional 이 붙어 있어서 sync() 전체가 트랜잭션
 * 하나였다. 1건마다 상세 API 를 한 번 더 부르므로 4,249건을 적재하면 트랜잭션 하나 안에서
 * HTTP 호출이 8,000번 넘게 일어난다. 중간에 하나라도 터지면 앞에서 성공한 수천 건이 전부
 * 롤백되고 api_sync_log 도 안 남는다. DB 커넥션을 몇십 분씩 붙잡고 있는 것도 문제다.
 *
 * 그래서 저장 단위를 1건으로 쪼갰다. 여기서 중요한 건 <b>별도 빈으로 뺐다는 것</b>이다.
 * 같은 클래스 안에서 메서드를 호출하면 Spring 프록시를 타지 않아 @Transactional 이 무시된다
 * (self-invocation). RecallSyncService 안에 private 메서드로 두고 propagation 만 지정했다면
 * 트랜잭션은 여전히 하나였을 것이고, 겉보기엔 고친 것처럼 보이지만 아무것도 안 바뀐다.
 *
 * REQUIRES_NEW 인 이유: 호출자가 트랜잭션을 안 열도록 만들어 뒀지만, 나중에 누군가
 * RecallSyncService 에 @Transactional 을 다시 붙여도 건별 경계가 유지되게 하려는 방어다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecallUpsertService {

    private final SafetyKoreaRecallClient recallClient;
    private final RecallRepository recallRepository;
    private final RecallFileRepository recallFileRepository;

    /**
     * 리콜 1건을 저장하고, 요청 시 사진까지 채운다. 이 메서드 하나가 트랜잭션 하나다.
     *
     * @param withImages 상세 API 를 불러 recall_file 을 채울지 여부.
     *                   false 면 상세 호출을 건너뛰어 적재가 2배 이상 빨라진다.
     *                   전건 적재는 false 로 본문만 먼저 넣고, 사진은 나중에
     *                   RecallSyncService.syncImages() 로 따로 채우는 쪽이 안전하다.
     * @return 저장에 성공했으면 true. 실패는 예외를 던지지 않고 false 로 알린다 —
     *         한 건 때문에 기간 적재 전체가 멈추면 안 된다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean upsert(RecallListApiResponse.Item item, boolean withImages) {
        if (item == null || item.recallUid() == null) {
            return false;
        }
        try {
            Recall fresh = toEntity(item);
            recallRepository.findById(item.recallUid())
                    .ifPresentOrElse(
                            existing -> existing.syncFrom(fresh),
                            () -> recallRepository.save(fresh)
                    );
            if (withImages) {
                loadFiles(item.recallUid());
            }
            return true;
        } catch (RuntimeException e) {
            log.warn("[RecallUpsert] 적재 실패 (recallUid={}): {}", item.recallUid(), e.toString());
            return false;
        }
    }

    /**
     * 이미 적재된 리콜의 사진만 채운다. 본문 적재와 분리해서 돌릴 때 쓴다.
     *
     * @return 저장한 사진 행 수. 상세 조회 실패나 사진 없음은 0.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int upsertImagesOnly(Long recallUid) {
        try {
            return loadFiles(recallUid);
        } catch (RuntimeException e) {
            log.warn("[RecallUpsert] 사진 적재 실패 (recallUid={}): {}", recallUid, e.toString());
            return 0;
        }
    }

    /**
     * 상세 조회로 리콜 사진을 가져와 recall_file 에 채운다.
     *
     * 상세 조회 실패는 삼킨다. 사진은 본문이 아니라 부가정보라 이것 때문에 본문 적재까지
     * 버릴 이유가 없다. 9/20 에 fetchDetail() 의 BusinessException 이 트랜잭션 전체를
     * 롤백시키던 문제를 잡으면서 넣은 처리다.
     */
    private int loadFiles(Long recallUid) {
        RecallDetailApiResponse detail;
        try {
            detail = recallClient.fetchDetail(recallUid);
        } catch (RuntimeException e) {
            log.debug("[RecallUpsert] 상세 조회 실패 — 사진 없이 진행 (recallUid={}): {}",
                    recallUid, e.getMessage());
            return 0;
        }

        if (!detail.isSuccess() || detail.resultData() == null
                || detail.resultData().recallFiles() == null) {
            return 0;
        }

        recallFileRepository.deleteByRecallUid(recallUid);
        int saved = 0;
        for (RecallDetailApiResponse.RecallFileItem fileItem : detail.resultData().recallFiles()) {
            recallFileRepository.save(RecallFile.builder()
                    .recallUid(recallUid)
                    .fileDiv(FileDiv.from(fileItem.fileDiv()))
                    .imageUrl(fileItem.imageUrl())
                    .build());
            saved++;
        }
        return saved;
    }

    private Recall toEntity(RecallListApiResponse.Item item) {
        return Recall.builder()
                .recallUid(item.recallUid())
                .recallProductName(item.recallProductName())
                .recallBrandName(item.recallBrandName())
                .recallModelName(item.recallModelName())
                .recallModelCnt(item.recallModelCnt())
                .barcodeNum(item.barcodeNum())
                .certNum(item.certNum())
                .categoryName(item.categoryName())
                .recallTypeName(item.recallTypeName())
                .recallMeans(item.recallMeans())
                .recallCmpnyName(item.recallCmpnyName())
                .makerName(item.makerName())
                .makingCntryName(item.makingCntryName())
                .publishDate(item.publishDate())
                .harmDscr(item.harmDscr())
                .accidentCaseDscr(item.accidentCaseDscr())
                .publishActionDscr(item.publishActionDscr())
                .build();
    }
}
