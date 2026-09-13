package com.underfaker.recallcheck.entity;

import com.underfaker.recallcheck.entity.enums.FinalResult;
import com.underfaker.recallcheck.entity.enums.InputType;
import com.underfaker.recallcheck.entity.enums.VerificationChannel;
import com.underfaker.recallcheck.entity.enums.VerificationStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** verification — 검증 요청 1건. 입력 방식과 최종 판정 보관 */
@Getter
@Entity
@Table(name = "verification")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Verification extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "verification_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "input_type", nullable = false, length = 20)
    private InputType inputType;

    @Column(name = "input_url", length = 1000)
    private String inputUrl;

    @Column(name = "image_path", length = 512)
    private String imagePath;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private VerificationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "final_result", length = 20)
    private FinalResult finalResult;

    /**
     * 9/13 추가 — "제품 정보로 리콜 검증"(WEB, 단건) vs "쿠팡 구매 이력 검증"(EXTENSION, 배치)
     * 화면 구분용. VerificationChannel 참고.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private VerificationChannel channel;

    @Builder
    public Verification(Long userId, InputType inputType, String inputUrl, String imagePath,
                         VerificationChannel channel) {
        this.userId = userId;
        this.inputType = inputType;
        this.inputUrl = inputUrl;
        this.imagePath = imagePath;
        // channel 을 안 넘기면(기존 URL/이미지 검증 경로) 기본값 WEB — 지금은 전부 웹에서만 들어옴
        this.channel = channel != null ? channel : VerificationChannel.WEB;
        this.status = VerificationStatus.PENDING;
    }

    /** 판정 완료 처리 */
    public void complete(FinalResult finalResult) {
        this.finalResult = finalResult;
        this.status = VerificationStatus.DONE;
    }

    /** 처리 실패 처리 */
    public void fail() {
        this.status = VerificationStatus.FAILED;
        this.finalResult = FinalResult.UNKNOWN;
    }
}
