-- ============================================================================
-- 개발 검증용 샘플 리콜 데이터
--
-- ※ 실제 국가기술표준원 공표 데이터가 아닙니다.
--   Open API 연동(FR-009 · FR-016) 전까지 매칭 로직을 검증하기 위한 가상 데이터이며,
--   실제 데이터와 구분되도록 recall_uid 를 900001 부터 부여했습니다.
--   실데이터 적재 시 아래 DELETE 로 먼저 제거하십시오.
-- ============================================================================

USE `recallcheck`;

    DELETE FROM `recall_file` WHERE `recall_uid` >= 900000;
DELETE FROM `match_result` WHERE `recall_uid` >= 900000;
DELETE FROM `recall`      WHERE `recall_uid` >= 900000;

INSERT INTO `recall`
(`recall_uid`, `recall_product_name`, `recall_brand_name`, `recall_model_name`, `recall_model_cnt`,
 `barcode_num`, `cert_num`, `category_name`, `recall_type_name`, `recall_means`,
 `recall_cmpny_name`, `maker_name`, `making_cntry_name`, `publish_date`,
 `harm_dscr`, `accident_case_dscr`, `publish_action_dscr`, `synced_at`)
VALUES
(900001, '가정용 물티슈', '베이비케어', 'WT-100C,WT-100D', 2,
 '8801234567890', 'HU07-123456', '위생용품', '명령에 따른 리콜', '교환 및 환불',
 '샘플유통(주)', '샘플산업', '대한민국', '20260723',
 '가습기살균제 성분(CMIT/MIT) 기준치 초과 검출',
 '피부 접촉 시 자극 및 알레르기 반응 우려', '판매점 반품 및 환불 실시', NOW()),

(900002, '유아용 섬유제품(책가방)', '스쿨백', 'T3S-T-1-503', 1,
 '8801234567891', 'CB07-654321', '어린이제품', '명령에 따른 리콜', '수거 및 환불',
 '샘플상사', '샘플텍스타일', '베트남', '20260610',
 '프탈레이트계 가소제 기준치 초과 검출',
 '장시간 접촉 시 내분비계 교란 우려', '구입처 반품 또는 제조사 회수 요청', NOW()),

(900003, '휴대용 선풍기', '쿨윈드', 'CW-220,CW-220S,CW-230', 3,
 '8801234567892', 'XU07-778899', '전기용품', '자발적 리콜', '무상 수리',
 '샘플전자(주)', '샘플전자', '중국', '20260415',
 '배터리 과열로 인한 발화 가능성',
 '충전 중 과열 및 연기 발생 사례 3건 접수', '사용 중단 후 제조사 서비스센터 문의', NOW()),

(900004, 'LED 스탠드 조명', '라이트온', 'LO-DESK-15', 1,
 '8801234567893', 'HK07-112233', '전기용품', '권고에 따른 리콜', '교환',
 '샘플조명', '샘플라이팅', '대한민국', '20260302',
 '절연 불량으로 인한 감전 위험',
 '누전으로 인한 감전 신고 1건', '즉시 사용 중단 및 교환 신청', NOW()),

(900005, '스테인리스 텀블러', '데일리컵', 'DC-500,DC-350', 2,
 '8801234567894', 'AB07-445566', '주방용품', '명령에 따른 리콜', '수거 및 환불',
 '샘플리빙', '샘플메탈', '중국', '20260128',
 '니켈 용출량 기준치 초과',
 '장기 사용 시 중금속 노출 우려', '사용 중단 후 판매처 환불 요청', NOW()),

(900006, '어린이용 킥보드', '롤링키즈', 'RK-K3', 1,
 '8801234567895', 'CB07-990011', '어린이제품', '명령에 따른 리콜', '수거 및 교환',
 '샘플스포츠', '샘플기어', '대한민국', '20251215',
 '핸들 결합부 파손으로 인한 전도 위험',
 '주행 중 핸들 이탈로 인한 찰과상 2건', '사용 중단 후 무상 교환', NOW()),

(900007, '전기 온수 매트', '따뜻한밤', 'WM-QUEEN-2, WM-SINGLE-2', 2,
 '8801234567896', 'XU07-334455', '전기용품', '자발적 리콜', '무상 수리 및 환불',
 '샘플홈(주)', '샘플히팅', '대한민국', '20251104',
 '온도 조절기 오작동으로 인한 과열',
 '취침 중 저온화상 사례 1건', '전원 차단 후 제조사 회수 요청', NOW()),

(900008, '아기 젖병 세정제', '퓨어워시', 'PW-500ML', 1,
 '8801234567897', 'HU07-667788', '위생용품', '명령에 따른 리콜', '교환 및 환불',
 '샘플케어', '샘플케미컬', '대한민국', '20250922',
 '보존제 함량 기준 초과',
 '잔류 성분 섭취 우려', '사용 중단 및 판매처 환불', NOW());

INSERT INTO `recall_file` (`recall_uid`, `file_div`, `image_url`) VALUES
(900001, '전체사진', 'https://example.invalid/sample/900001_full.jpg'),
(900001, '부분사진', 'https:
//example.invalid/sample
/900001_label.jpg'),
(900002, '전체사진', 'https://example.invalid/sample/900002_full.jpg'),
(900003, '전체사진', 'https://example.invalid/sample/900003_full.jpg');

-- ---------------------------------------------------------------------------
-- 9/17 추가 — normalized_* 컬럼 백필 (이 INSERT 로 데이터를 넣었다면 반드시 같이 실행할 것)
--
-- 왜 필요한가:
--   recall 의 normalized_product_name / normalized_model_name / normalized_cert_num 은
--   Recall 엔티티의 @PrePersist·@PreUpdate 에서 TextNormalizer 로 계산된다. 즉 JPA 를
--   거쳐 저장할 때만 채워진다. 위 INSERT 처럼 SQL 로 직접 넣으면 이 세 컬럼이 NULL 로 남는다.
--
--   그런데 후보조회(RecallQueryService.findCandidates)와 검색(RecallRepository.search)은
--   전부 normalized_* 컬럼을 기준으로 LIKE 를 건다. NULL LIKE '%물티슈%' 는 NULL 이라
--   어떤 검색어를 넣어도 0건이 나오고, 검증을 돌려도 "대조할 리콜 후보가 없습니다" 만 뜬다.
--   9/17 에 실제로 이 증상으로 한참 헤맸다 — 정규화 로직이 틀린 게 아니라 데이터가 빈 것이었다.
--
--   국표원 Open API 실연동(RecallSyncService)으로 적재하는 데이터는 JPA 를 타므로
--   이 문제가 없다. 샘플 8건이 실데이터로 교체되면 아래 UPDATE 도 같이 지우면 된다.
--
-- 아래 REPLACE 중첩은 common/TextNormalizer.normalize() 와 같은 규칙이다
-- (공백·하이픈·콤마·괄호·점·슬래시 제거 후 대문자). 규칙을 바꾸면 양쪽을 같이 고칠 것.
UPDATE `recall`
SET
  `normalized_product_name` = UPPER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(IFNULL(`recall_product_name`,''),' ',''),'-',''),',',''),'(',''),')',''),'.',''),'/','')),
  `normalized_model_name`   = UPPER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(IFNULL(`recall_model_name`,''),' ',''),'-',''),',',''),'(',''),')',''),'.',''),'/','')),
  `normalized_cert_num`     = UPPER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(IFNULL(`cert_num`,''),' ',''),'-',''),',',''),'(',''),')',''),'.',''),'/',''))
WHERE `recall_uid` > 0;

SELECT COUNT(*) AS `적재된 샘플 리콜 건수` FROM `recall` WHERE `recall_uid` >= 900000;

-- 백필이 제대로 됐는지 확인용 — normalized_product_name 이 NULL 인 행이 0 이어야 한다
SELECT COUNT(*) AS `정규화 안 된 행(0이어야 정상)` FROM `recall` WHERE `normalized_product_name` IS NULL;
