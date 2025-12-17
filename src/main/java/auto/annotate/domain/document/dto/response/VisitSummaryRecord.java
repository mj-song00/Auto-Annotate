package auto.annotate.domain.document.dto.response;

import auto.annotate.domain.document.dto.HighlightType;
import lombok.*;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * PDF에서 추출된 진료 기록의 한 행(Row)을 나타내는 불변 데이터 전송 객체 (DTO).
 * 처리 결과인 하이라이팅 플래그를 포함합니다.
 */
@Getter
@Builder
@NoArgsConstructor(force = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class VisitSummaryRecord {

    // 모든 필드는 private final 유지
    private final String sequence;
    private final String institutionName;
    private final String daysOfStayOrVisit;
    private final String totalMedicalFee;
    private final String insuranceBenefit;
    private final String userPaidAmount;
    private final String treatmentDetail;

    // 3. @Builder.Default 사용: Set 필드의 기본값 초기화 및 불변성 확보
    @Builder.Default
    private final Set<HighlightType> highlightTypes = Collections.unmodifiableSet(new HashSet<>());


    /**
     * 불변 객체에서 특정 필드(하이라이팅 플래그)만 변경된 새로운 객체를 반환하는 'Wither' 패턴 메서드.
     */
    public VisitSummaryRecord withHighlightTypes(Set<HighlightType> newTypes) {
        // 불변 Set으로 방어적 복사를 통해 안전하게 새 객체 생성
        Set<HighlightType> safeNewTypes = Collections.unmodifiableSet(new HashSet<>(newTypes));

        return VisitSummaryRecord.builder()
                .sequence(this.sequence)
                .institutionName(this.institutionName)
                .daysOfStayOrVisit(this.daysOfStayOrVisit)
                .totalMedicalFee(this.totalMedicalFee)
                .insuranceBenefit(this.insuranceBenefit)
                .userPaidAmount(this.userPaidAmount)
                .treatmentDetail(this.treatmentDetail)
                // 변경된 필드
                .highlightTypes(safeNewTypes)
                .build();
    }
}