package com.underfaker.recallcheck.entity.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * 리콜 사진 구분 (recall_file.file_div).
 *
 * DB ENUM 값과 API 응답 값이 모두 한글('전체사진','부분사진')이라
 * @Enumerated(STRING) 으로는 매핑할 수 없다. 아래 컨버터가 상수 ↔ 한글 문자열을 변환한다.
 */
public enum FileDiv {

    /** 전체사진 */
    FULL("전체사진"),

    /** 부분사진 */
    PART("부분사진");

    private final String dbValue;

    FileDiv(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    /** API 응답의 한글 값을 상수로 변환한다. 알 수 없는 값이면 null. */
    public static FileDiv from(String dbValue) {
        if (dbValue == null) {
            return null;
        }
        for (FileDiv v : values()) {
            if (v.dbValue.equals(dbValue)) {
                return v;
            }
        }
        return null;
    }

    @Converter(autoApply = true)
    public static class FileDivConverter implements AttributeConverter<FileDiv, String> {

        @Override
        public String convertToDatabaseColumn(FileDiv attribute) {
            return attribute == null ? null : attribute.getDbValue();
        }

        @Override
        public FileDiv convertToEntityAttribute(String dbData) {
            return FileDiv.from(dbData);
        }
    }
}
