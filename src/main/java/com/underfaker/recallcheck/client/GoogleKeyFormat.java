package com.underfaker.recallcheck.client;

/**
 * Google 인증값 형식 판별. Spring 의존이 없어 단독으로 테스트할 수 있게 따로 뺐다.
 *
 * 9/24 — 서비스 계정 콘솔의 '키' 열에 보이는 값(키 ID)을 API 키로 오인해
 * google.vision.api-key 에 넣은 일이 있었다. 형식만 보고도 걸러낼 수 있다.
 *
 *   API 키        : 'AIza' 로 시작, 39자            예) AIzaSyA-…
 *   서비스 계정 키 ID : 16진수 40자                    예) 3cc7e90f8004c5e3…
 *                   → 이름표일 뿐 비밀값이 아니다. 인증에 못 쓴다.
 *   서비스 계정 키  : JSON 파일 (private_key 포함)   → credentials-path 로 경로를 넘긴다.
 */
public final class GoogleKeyFormat {

    private GoogleKeyFormat() {
    }

    /** Google Cloud API 키 형식인가. 'AQ.' 로 시작하는 신형 키도 받아 준다. */
    public static boolean looksLikeApiKey(String key) {
        if (key == null) {
            return false;
        }
        String k = key.trim();
        return (k.startsWith("AIza") && k.length() >= 30) || k.startsWith("AQ.");
    }

    /**
     * 서비스 계정 키 ID 모양(16진수 40자)인가.
     * 뒤에 콜론이 붙은 경우도 잡는다 — ${키ID:} 로 잘못 쓴 것을 풀면 그렇게 남는다.
     */
    public static boolean looksLikeKeyId(String key) {
        return key != null && key.trim().matches("[0-9a-fA-F]{38,40}:?");
    }
}
