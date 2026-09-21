package com.underfaker.recallcheck.service.matching;

import com.underfaker.recallcheck.common.TextNormalizer;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 표기 정규화 — 공백·기호·대소문자·라벨.
 *
 * 이 클래스가 있는 이유: 판매글의 "아트박스 허니 슬라임 100g"와 공표문의
 * "(제품명) 허니 슬라임"처럼 같은 제품이 다른 문자열로 표기된다.
 * 정규화 없이 비교하면 매칭률이 0 이 된다.
 *
 * 9/14 — 실제 규칙은 common.TextNormalizer 로 옮겼다. entity.Recall 이 normalized_*
 * 컬럼을 채울 때 이 빈을 주입받을 수 없어서(JPA 엔티티는 생성자 주입 불가) 규칙이 두 군데로
 * 갈라져 있었던 게 후보조회 정규화 버그의 원인이었다.
 *
 * 9/20 — 모델명 전용 규칙(normalizeModelName)이 여기에만 남아 있어서 규칙이 또 갈라져 있었다.
 * 엔티티는 저장할 때 normalize() 를 쓰고 매칭은 normalizeModelName() 으로 비교했으니,
 * 기호 구성이 다른 모델명에서 저장값과 비교값이 어긋났다. 그 규칙도 TextNormalizer 로 옮겼다.
 * 이제 이 클래스는 순수 위임이다 — 규칙을 여기에 다시 적지 말 것.
 *
 * <b>반환값 계약</b>: 정규화 결과가 없으면(값이 "-" 이거나, 기호만 있어 남는 글자가 없으면)
 * 빈 문자열이 아니라 <b>null</b> 이다. 호출부는 null 을 "이 항목으로는 검색·비교할 수 없다"로
 * 다루어야 한다. 빈 문자열로 LIKE 를 걸면 '%%' 가 되어 전건이 끌려온다.
 */
@Component
public class FieldNormalizer {

    /** 공백·괄호·특수문자·라벨 제거 후 대문자 통일. 남는 글자가 없으면 null. */
    public String normalize(String raw) {
        return TextNormalizer.normalize(raw);
    }

    /** 모델명 전용 — 영문·숫자·한글만 남긴다. 남는 글자가 없으면 null. */
    public String normalizeModelName(String raw) {
        return TextNormalizer.normalizeModelName(raw);
    }

    /** 후보조회 LIKE 조건으로 쓸 수 있을 때만 정규화 값을 돌려준다. 아니면 null. */
    public String searchKey(String raw) {
        return TextNormalizer.searchKey(raw);
    }

    /** 모델명을 후보조회 LIKE 조건으로 쓸 수 있을 때만 돌려준다. 아니면 null. */
    public String modelSearchKey(String raw) {
        return TextNormalizer.modelSearchKey(raw);
    }

    /**
     * 목록형 값을 개별 값으로 쪼갠다. 원본 전체도 후보에 포함된다.
     *
     * 9/21 — 규칙을 TextNormalizer 로 옮겼다. MatchProfile 이 static 이라 이 빈을 주입받을 수
     * 없는데, 프로파일 판정도 같은 기준으로 쪼갠 값을 봐야 하기 때문이다.
     * 규칙이 두 군데로 갈라지면 9/14·9/20 과 같은 종류의 버그가 또 난다.
     */
    public List<String> splitList(String raw) {
        return TextNormalizer.splitList(raw);
    }
}
