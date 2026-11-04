package com.underfaker.recallcheck.service.sync;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 10/31 — 자동 갱신이 훑을 공표일 범위(SyncWindow). 오늘 = 2026-10-31, 최근 7일, 최대 90일 */
class SyncWindowTest {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 31);

    static SyncWindow w(String latest) {
        return SyncWindow.of(TODAY, latest, 7, 90);
    }

    @Test
    void 마지막_공표일이_최근_7일_안이면_최근_7일만() {
        assertEquals(new SyncWindow("20261025", "20261031"), w("20261030"));
    }

    @Test
    void 서버가_꺼져_있던_빈틈은_마지막_공표일부터() {
        assertEquals(new SyncWindow("20261010", "20261031"), w("20261010"));
    }

    @Test
    void 아주_오래됐으면_90일까지만() {
        assertEquals(new SyncWindow("20260803", "20261031"), w("20250101"));
    }

    @Test
    void 빈_DB_틀린_값_미래_날짜는_최근_7일() {
        SyncWindow recent = new SyncWindow("20261025", "20261031");
        assertEquals(recent, w(null));
        assertEquals(recent, w("2026-10-01"));
        assertEquals(recent, w("20260230"));   // 없는 날짜
        assertEquals(recent, w("20261105"));   // 오늘보다 뒤
    }

    @Test
    void 설정값이_이상해도_범위는_1일_이상_400일_이하() {
        assertEquals(new SyncWindow("20261031", "20261031"), SyncWindow.of(TODAY, null, 0, 0));
        assertEquals(new SyncWindow("20250927", "20261031"), SyncWindow.of(TODAY, "20200101", 7, 5000));   // 400일(오늘 포함)
        // 최대 일수가 최근 일수보다 작으면 최근 일수를 따른다
        assertEquals(new SyncWindow("20261025", "20261031"), SyncWindow.of(TODAY, "20200101", 7, 3));
    }
}
