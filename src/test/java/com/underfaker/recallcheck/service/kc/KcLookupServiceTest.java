package com.underfaker.recallcheck.service.kc;

import com.underfaker.recallcheck.client.SafetyKoreaCertClient;
import com.underfaker.recallcheck.client.dto.CertListApiResponse;
import com.underfaker.recallcheck.dto.internal.KcLookup;
import com.underfaker.recallcheck.entity.Certification;
import com.underfaker.recallcheck.exception.BusinessException;
import com.underfaker.recallcheck.exception.ErrorCode;
import com.underfaker.recallcheck.repository.CertificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * KC 인증번호 조회 — 캐시·API·실패 처리. API 응답 값은 10/3 실측(certificationList.json) 그대로다.
 * 외부 호출 없이 SafetyKoreaCertClient·CertificationRepository 를 Mockito 로 대신한다.
 */
class KcLookupServiceTest {

    SafetyKoreaCertClient client;
    CertificationRepository repository;
    CertificationCacheWriter writer;
    KcLookupService service;

    /** 10/3 실측 — CB067R2225-4001 */
    static CertListApiResponse.Item pinkFoot() {
        return new CertListApiResponse.Item(5751111L, "CB067R2225-4001", "적합", "20240419",
                "완구", "", "핑크풋 슬라임", "-", "중국");
    }

    @BeforeEach
    void setUp() {
        client = mock(SafetyKoreaCertClient.class);
        repository = mock(CertificationRepository.class);
        writer = new CertificationCacheWriter(repository);   // 진짜 writer + 가짜 repository
        service = new KcLookupService(client, repository, writer);
        when(repository.findByCertNum(anyString())).thenReturn(List.of());
        when(repository.findById(any())).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(Certification.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void 캐시에_없으면_API로_찾아_저장하고_FOUND() {
        when(client.findByCertNumExact("CB067R2225-4001")).thenReturn(List.of(pinkFoot()));

        KcLookup r = service.lookup("cb067r2225-4001");   // 소문자로 와도 정리해서 조회

        assertEquals(KcLookup.Status.FOUND, r.status());
        assertEquals("핑크풋 슬라임", r.modelName());
        assertEquals("적합", r.certState());
        assertEquals("API", r.source());
        verify(repository).saveAndFlush(any(Certification.class));
    }

    @Test
    void 캐시가_7일_이내면_API를_부르지_않는다() {
        Certification cached = KcLookupService.toEntity(pinkFoot());   // syncedAt = 지금
        when(repository.findByCertNum("CB067R2225-4001")).thenReturn(List.of(cached));

        KcLookup r = service.lookup("CB067R2225-4001");

        assertEquals("CACHE", r.source());
        assertEquals("핑크풋 슬라임", r.modelName());
        verify(client, never()).findByCertNumExact(anyString());
    }

    @Test
    void 캐시가_오래되면_API로_다시_받고_기존_행을_갱신한다() throws Exception {
        Certification old = KcLookupService.toEntity(pinkFoot());
        setSyncedAt(old, LocalDateTime.now().minusDays(8));
        when(repository.findByCertNum("CB067R2225-4001")).thenReturn(List.of(old));
        when(repository.findById(5751111L)).thenReturn(Optional.of(old));
        when(client.findByCertNumExact("CB067R2225-4001")).thenReturn(List.of(pinkFoot()));

        KcLookup r = service.lookup("CB067R2225-4001");

        assertEquals("API", r.source());
        verify(client).findByCertNumExact("CB067R2225-4001");
        verify(repository, never()).saveAndFlush(any(Certification.class));   // 이미 있는 행은 syncFrom 으로 갱신
    }

    @Test
    void API가_빈_목록이면_NOT_FOUND() {
        // 10/3 실측: 없는 번호 XX00000-00000 → resultCode 2000 + 빈 목록
        when(client.findByCertNumExact("XX00000-00000")).thenReturn(List.of());

        KcLookup r = service.lookup("XX00000-00000");

        assertEquals(KcLookup.Status.NOT_FOUND, r.status());
        assertEquals("XX00000-00000", r.certNum());
        verify(repository, never()).saveAndFlush(any(Certification.class));
    }

    @Test
    void API가_실패하면_UNAVAILABLE이고_1분간_다시_부르지_않는다() {
        when(client.findByCertNumExact(anyString()))
                .thenThrow(new BusinessException(ErrorCode.OPENAPI_CALL_FAILED, "timeout"));

        KcLookup first = service.lookup("CB067R2225-4001");
        KcLookup second = service.lookup("HH07952-13056");

        assertEquals(KcLookup.Status.UNAVAILABLE, first.status());
        assertEquals(KcLookup.Status.UNAVAILABLE, second.status());
        verify(client, times(1)).findByCertNumExact(anyString());   // 두 번째는 API 를 안 부른다
    }

    @Test
    void API가_실패해도_오래된_캐시가_있으면_그걸_쓴다() throws Exception {
        Certification old = KcLookupService.toEntity(pinkFoot());
        setSyncedAt(old, LocalDateTime.now().minusDays(30));
        when(repository.findByCertNum("CB067R2225-4001")).thenReturn(List.of(old));
        when(client.findByCertNumExact(anyString())).thenThrow(new RuntimeException("connect timed out"));

        KcLookup r = service.lookup("CB067R2225-4001");

        assertEquals(KcLookup.Status.FOUND, r.status());
        assertEquals("STALE_CACHE", r.source());
    }

    @Test
    void 번호가_아닌_값이면_null이고_아무것도_부르지_않는다() {
        assertNull(service.lookup("공급자적합성"));
        assertNull(service.lookup(null));
        verify(client, never()).findByCertNumExact(anyString());
        verify(repository, never()).findByCertNum(anyString());
    }

    @Test
    void 여러_번호_중_인증DB에_있는_것을_돌려준다() {
        when(client.findByCertNumExact("XX00000-00000")).thenReturn(List.of());
        when(client.findByCertNumExact("CB067R2225-4001")).thenReturn(List.of(pinkFoot()));

        KcLookup r = service.lookup("XX00000-00000, CB067R2225-4001");

        assertEquals(KcLookup.Status.FOUND, r.status());
        assertEquals("CB067R2225-4001", r.certNum());
    }

    @Test
    void 관리자_refresh는_캐시가_새것이어도_API를_부른다() {
        when(repository.findByCertNum("CB067R2225-4001")).thenReturn(List.of(KcLookupService.toEntity(pinkFoot())));
        when(client.findByCertNumExact("CB067R2225-4001")).thenReturn(List.of(pinkFoot()));

        assertEquals("API", service.refresh("CB067R2225-4001").source());
        verify(client).findByCertNumExact("CB067R2225-4001");
    }

    @Test
    void 캐시_저장이_실패해도_조회_결과는_FOUND로_돌려준다() {
        // 같은 새 번호를 두 요청이 동시에 저장하면 늦은 쪽이 PK 중복으로 실패한다(10/3 리뷰). 검증은 계속돼야 한다.
        when(client.findByCertNumExact("CB067R2225-4001")).thenReturn(List.of(pinkFoot()));
        when(repository.saveAndFlush(any(Certification.class)))
                .thenThrow(new RuntimeException("Duplicate entry '5751111' for key 'PRIMARY'"));

        KcLookup r = service.lookup("CB067R2225-4001");

        assertEquals(KcLookup.Status.FOUND, r.status());
        assertEquals("핑크풋 슬라임", r.modelName());
    }

    @Test
    void 컬럼_길이를_넘는_값은_잘라서_저장한다() {
        CertListApiResponse.Item longItem = new CertListApiResponse.Item(1L, " A11-1001 ", "적합", "202404190",
                "완구", null, "모".repeat(300), "-", "중국");
        Certification c = KcLookupService.toEntity(longItem);
        assertEquals(255, c.getModelName().length());
        assertEquals("20240419", c.getCertDate());
        assertEquals("A11-1001", c.getCertNum());
    }

    private static void setSyncedAt(Certification c, LocalDateTime t) throws Exception {
        Field f = Certification.class.getDeclaredField("syncedAt");
        f.setAccessible(true);
        f.set(c, t);
    }
}
