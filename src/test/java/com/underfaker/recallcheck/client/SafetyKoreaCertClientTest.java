package com.underfaker.recallcheck.client;

import com.underfaker.recallcheck.client.dto.CertListApiResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** KC 목록 API 는 인증번호도 앞부분 일치로 돌려준다(10/3 실측) — 정확히 같은 번호만 남기는지 */
class SafetyKoreaCertClientTest {

    static CertListApiResponse.Item item(String certNum, String model) {
        return new CertListApiResponse.Item(1L, certNum, "적합", "20240419", "완구", null, model, "-", "중국");
    }

    @Test
    void 앞부분만_같은_번호는_버린다() {
        // conditionValue=CB067R2225 로 CB067R2225-4001 이 돌아왔다(10/3 실측). 비슷한 번호가 섞여 와도 걸러야 한다.
        List<CertListApiResponse.Item> api = List.of(
                item("CB067R2225-4001", "핑크풋 슬라임"),
                item("CB067R2225-4001A", "다른 모델"),
                item("CB067R2225-40011", "또 다른 모델"));

        List<CertListApiResponse.Item> exact = SafetyKoreaCertClient.exactMatches(api, "CB067R2225-4001");

        assertEquals(1, exact.size());
        assertEquals("핑크풋 슬라임", exact.get(0).modelName());
    }

    @Test
    void 일련번호_없는_값으로는_아무것도_고르지_않는다() {
        assertTrue(SafetyKoreaCertClient.exactMatches(List.of(item("CB067R2225-4001", "x")), "CB067R2225").isEmpty());
    }

    @Test
    void 대소문자_공백은_무시하고_null은_빈_목록() {
        assertEquals(1, SafetyKoreaCertClient.exactMatches(List.of(item(" cb067r2225-4001 ", "x")), "CB067R2225-4001").size());
        assertTrue(SafetyKoreaCertClient.exactMatches(null, "A11-1001").isEmpty());
        assertTrue(SafetyKoreaCertClient.exactMatches(List.of(item(null, "x")), "A11-1001").isEmpty());
    }
}
