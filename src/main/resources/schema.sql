-- ============================================================================
-- recallcheck 스키마 (UnderFaker.sql 기준 + 실행 가능하도록 보강)
--
-- 원본 ERD 내보내기(UnderFaker.sql)에서 보강한 것:
--   1) 내부 생성 PK 6개에 AUTO_INCREMENT 추가  ← 없으면 INSERT 자체가 실패한다
--   2) 외래키 제약 추가
--   3) 매칭 조회용 인덱스 추가
--   4) user.email UNIQUE 추가
--   5) 문자셋 utf8mb4 (recall_file.file_div 가 한글 ENUM이라 필수)
--
-- recall.recall_uid 와 certification.cert_uid 는 Open API 원본 키를 그대로 쓰므로
-- AUTO_INCREMENT 를 붙이지 않는다.
-- ============================================================================

CREATE DATABASE IF NOT EXISTS `recallcheck`
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `recallcheck`;

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `api_sync_log`;
DROP TABLE IF EXISTS `match_result`;
DROP TABLE IF EXISTS `extraction`;
DROP TABLE IF EXISTS `verification`;
DROP TABLE IF EXISTS `recall_file`;
DROP TABLE IF EXISTS `recall`;
DROP TABLE IF EXISTS `certification`;
DROP TABLE IF EXISTS `user`;
SET FOREIGN_KEY_CHECKS = 1;

-- ----------------------------------------------------------------- user
CREATE TABLE `user` (
    `user_id`       BIGINT          NOT NULL AUTO_INCREMENT,
    `email`         VARCHAR(255)    NULL,
    `password`      VARCHAR(255)    NULL,
    `username`      VARCHAR(100)    NULL,
    `user_role`     ENUM('USER','ADMIN') NULL,
    `created_at`    DATETIME        NULL,
    `updated_at`    DATETIME        NULL,
    CONSTRAINT `PK_USER` PRIMARY KEY (`user_id`),
    CONSTRAINT `UK_USER_EMAIL` UNIQUE (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------- recall
CREATE TABLE `recall` (
    `recall_uid`            BIGINT          NOT NULL,   -- Open API recallUid (직접 할당)
    `recall_product_name`   VARCHAR(255)    NULL,
    `recall_brand_name`     VARCHAR(255)    NULL,
    `recall_model_name`     VARCHAR(1000)   NULL,       -- 콤마 구분 목록
    `recall_model_cnt`      INT             NULL,
    `barcode_num`           VARCHAR(64)     NULL,
    `cert_num`              VARCHAR(255)    NULL,       -- 콤마 구분 목록
    `category_name`         VARCHAR(255)    NULL,
    `recall_type_name`      VARCHAR(100)    NULL,
    `recall_means`          VARCHAR(255)    NULL,
    `recall_cmpny_name`     VARCHAR(255)    NULL,
    `maker_name`            VARCHAR(255)    NULL,
    `making_cntry_name`     VARCHAR(255)    NULL,
    `publish_date`          CHAR(8)         NULL,
    `harm_dscr`             TEXT            NULL,       -- 제품 결함
    `accident_case_dscr`    TEXT            NULL,       -- 위해 정보
    `publish_action_dscr`   TEXT            NULL,       -- 소비자 행동요령
    `synced_at`             DATETIME        NULL,
    CONSTRAINT `PK_RECALL` PRIMARY KEY (`recall_uid`),
    INDEX `IX_RECALL_BARCODE` (`barcode_num`),
    INDEX `IX_RECALL_CERT_NUM` (`cert_num`),
    INDEX `IX_RECALL_PRODUCT_NAME` (`recall_product_name`),
    INDEX `IX_RECALL_PUBLISH_DATE` (`publish_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------- recall_file
CREATE TABLE `recall_file` (
    `recall_file_id`    BIGINT          NOT NULL AUTO_INCREMENT,
    `recall_uid`        BIGINT          NOT NULL,
    `file_div`          ENUM('전체사진','부분사진') NULL,
    `image_url`         VARCHAR(512)    NULL,
    CONSTRAINT `PK_RECALL_FILE` PRIMARY KEY (`recall_file_id`),
    CONSTRAINT `FK_RECALL_FILE_RECALL` FOREIGN KEY (`recall_uid`)
        REFERENCES `recall` (`recall_uid`) ON DELETE CASCADE,
    INDEX `IX_RECALL_FILE_RECALL` (`recall_uid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------- certification
CREATE TABLE `certification` (
    `cert_uid`          BIGINT          NOT NULL,   -- Open API certUid (직접 할당)
    `cert_num`          VARCHAR(64)     NULL,
    `cert_state`        VARCHAR(100)    NULL,
    `cert_date`         CHAR(8)         NULL,
    `product_name`      VARCHAR(255)    NULL,
    `brand_name`        VARCHAR(255)    NULL,
    `model_name`        VARCHAR(255)    NULL,
    `maker_name`        VARCHAR(255)    NULL,
    `maker_cntry_name`  VARCHAR(255)    NULL,
    `synced_at`         DATETIME        NULL,
    CONSTRAINT `PK_CERTIFICATION` PRIMARY KEY (`cert_uid`),
    INDEX `IX_CERT_NUM` (`cert_num`),
    INDEX `IX_CERT_MODEL_NAME` (`model_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------- verification
CREATE TABLE `verification` (
    `verification_id`   BIGINT          NOT NULL AUTO_INCREMENT,
    `user_id`           BIGINT          NOT NULL,
    `input_type`        ENUM('URL','IMAGE','MANUAL') NULL,
    `input_url`         VARCHAR(1000)   NULL,
    `image_path`        VARCHAR(512)    NULL,
    `status`            ENUM('PENDING','DONE','FAILED') NULL,
    `final_result`      ENUM('MATCH','PARTIAL','NO_MATCH','UNKNOWN') NULL,
    `created_at`        DATETIME        NULL,
    `updated_at`        DATETIME        NULL,
    CONSTRAINT `PK_VERIFICATION` PRIMARY KEY (`verification_id`),
    CONSTRAINT `FK_VERIFICATION_USER` FOREIGN KEY (`user_id`)
        REFERENCES `user` (`user_id`),
    INDEX `IX_VERIFICATION_USER` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------- extraction
CREATE TABLE `extraction` (
    `extraction_id`     BIGINT          NOT NULL AUTO_INCREMENT,
    `verification_id`   BIGINT          NOT NULL,
    `source`            ENUM('URL','IMAGE','MANUAL') NULL,
    `product_name`      VARCHAR(255)    NULL,
    `brand_name`        VARCHAR(255)    NULL,
    `model_name`        VARCHAR(255)    NULL,
    `maker_name`        VARCHAR(255)    NULL,
    `barcode_num`       VARCHAR(64)     NULL,
    `cert_num`          VARCHAR(64)     NULL,
    `raw_text`          TEXT            NULL,
    `confidence`        DOUBLE          NULL,
    `created_at`        DATETIME        NULL,
    CONSTRAINT `PK_EXTRACTION` PRIMARY KEY (`extraction_id`),
    CONSTRAINT `FK_EXTRACTION_VERIFICATION` FOREIGN KEY (`verification_id`)
        REFERENCES `verification` (`verification_id`) ON DELETE CASCADE,
    INDEX `IX_EXTRACTION_VERIFICATION` (`verification_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------- match_result
CREATE TABLE `match_result` (
    `match_id`          BIGINT          NOT NULL AUTO_INCREMENT,
    `verification_id`   BIGINT          NOT NULL,
    `recall_uid`        BIGINT          NOT NULL,
    `similarity_score`  DOUBLE          NULL,
    `matched_field`     VARCHAR(255)    NULL,
    `decision`          ENUM('MATCH','PARTIAL','NO_MATCH') NULL,
    `reason`            VARCHAR(1000)   NULL,
    `created_at`        DATETIME        NULL,
    CONSTRAINT `PK_MATCH_RESULT` PRIMARY KEY (`match_id`),
    CONSTRAINT `FK_MATCH_RESULT_VERIFICATION` FOREIGN KEY (`verification_id`)
        REFERENCES `verification` (`verification_id`) ON DELETE CASCADE,
    CONSTRAINT `FK_MATCH_RESULT_RECALL` FOREIGN KEY (`recall_uid`)
        REFERENCES `recall` (`recall_uid`),
    INDEX `IX_MATCH_RESULT_VERIFICATION` (`verification_id`, `similarity_score`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------- api_sync_log
CREATE TABLE `api_sync_log` (
    `sync_id`       BIGINT      NOT NULL AUTO_INCREMENT,
    `admin_id`      BIGINT      NOT NULL,
    `api_type`      ENUM('RECALL','CERT') NULL,
    `result_code`   VARCHAR(10) NULL,
    `record_count`  INT         NULL,
    `started_at`    DATETIME    NULL,
    `finished_at`   DATETIME    NULL,
    CONSTRAINT `PK_API_SYNC_LOG` PRIMARY KEY (`sync_id`),
    CONSTRAINT `FK_API_SYNC_LOG_USER` FOREIGN KEY (`admin_id`)
        REFERENCES `user` (`user_id`),
    INDEX `IX_API_SYNC_LOG_TYPE` (`api_type`, `started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
