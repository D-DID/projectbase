-- =====================================================================
-- 10/31 검증 이력 중복 정리 (기존 데이터, 한 번만)
--
-- 서버는 10/31부터 같은 사용자·채널·같은 입력이면 새 검증을 쌓지 않는다(VerificationService#findSameInput).
-- 그 전에 쌓인 중복은 이 스크립트로 지운다. 같은 묶음에서 가장 최근 검증(verification_id 가 가장 큰 것)만 남긴다.
--
-- 같은 입력 = 직접입력·확장 추출 행(source='MANUAL')의 제품명·브랜드·모델명·제조사·바코드·KC 인증번호가 모두 같음.
--   (서버 판별은 KC 칸 상태·원문까지 보지만 SQL 로는 raw_text 안이라 여기서는 뺐다 — 그래서 서버보다 조금 넓게 묶는다)
--   URL·사진 업로드 검증(input_url / image_path 가 있는 것)은 건드리지 않는다.
-- extraction·match_result 는 FK ON DELETE CASCADE 라 verification 을 지우면 같이 지워진다(schema.sql).
-- 주의: 예전 검증에서 '사진으로 찾기'(Vision)를 했고 최근 검증에서는 안 했으면 그 결과는 사라진다.
--
-- 순서: 1) 미리보기 → 2) 지울 건수 확인 → 3) 삭제. 3) 전에 덤프를 떠 두는 것을 권장.
-- =====================================================================

USE recallcheck;

-- 1) 미리보기 — 중복 묶음과 남길 id
SELECT v.user_id, v.channel, e.product_name, e.cert_num,
       COUNT(*) AS cnt, MAX(v.verification_id) AS keep_id
FROM verification v
JOIN extraction e ON e.verification_id = v.verification_id AND e.source = 'MANUAL'
WHERE v.input_url IS NULL AND v.image_path IS NULL
GROUP BY v.user_id, v.channel, e.product_name, e.brand_name, e.model_name, e.maker_name, e.barcode_num, e.cert_num
HAVING COUNT(*) > 1
ORDER BY cnt DESC;

-- 2) 지울 건수
SELECT COUNT(*) AS to_delete
FROM verification v
JOIN extraction e ON e.verification_id = v.verification_id AND e.source = 'MANUAL'
JOIN (
    SELECT v2.user_id, v2.channel, e2.product_name, e2.brand_name, e2.model_name, e2.maker_name,
           e2.barcode_num, e2.cert_num, MAX(v2.verification_id) AS keep_id
    FROM verification v2
    JOIN extraction e2 ON e2.verification_id = v2.verification_id AND e2.source = 'MANUAL'
    WHERE v2.input_url IS NULL AND v2.image_path IS NULL
    GROUP BY v2.user_id, v2.channel, e2.product_name, e2.brand_name, e2.model_name, e2.maker_name,
             e2.barcode_num, e2.cert_num
    HAVING COUNT(*) > 1
) k ON k.user_id = v.user_id AND k.channel = v.channel
   AND k.product_name <=> e.product_name AND k.brand_name <=> e.brand_name
   AND k.model_name <=> e.model_name AND k.maker_name <=> e.maker_name
   AND k.barcode_num <=> e.barcode_num AND k.cert_num <=> e.cert_num
WHERE v.input_url IS NULL AND v.image_path IS NULL
  AND v.verification_id <> k.keep_id;

-- 3) 삭제 — 위 두 결과를 확인한 뒤 주석을 풀고 실행
--    Workbench 의 Safe Updates 가 막으면: SET SQL_SAFE_UPDATES = 0; 실행 후 다시, 끝나면 = 1
-- DELETE v
-- FROM verification v
-- JOIN extraction e ON e.verification_id = v.verification_id AND e.source = 'MANUAL'
-- JOIN (
--     SELECT v2.user_id, v2.channel, e2.product_name, e2.brand_name, e2.model_name, e2.maker_name,
--            e2.barcode_num, e2.cert_num, MAX(v2.verification_id) AS keep_id
--     FROM verification v2
--     JOIN extraction e2 ON e2.verification_id = v2.verification_id AND e2.source = 'MANUAL'
--     WHERE v2.input_url IS NULL AND v2.image_path IS NULL
--     GROUP BY v2.user_id, v2.channel, e2.product_name, e2.brand_name, e2.model_name, e2.maker_name,
--              e2.barcode_num, e2.cert_num
--     HAVING COUNT(*) > 1
-- ) k ON k.user_id = v.user_id AND k.channel = v.channel
--    AND k.product_name <=> e.product_name AND k.brand_name <=> e.brand_name
--    AND k.model_name <=> e.model_name AND k.maker_name <=> e.maker_name
--    AND k.barcode_num <=> e.barcode_num AND k.cert_num <=> e.cert_num
-- WHERE v.input_url IS NULL AND v.image_path IS NULL
--   AND v.verification_id <> k.keep_id;
