package auto.annotate.domain.document.dto.response;

import auto.annotate.domain.document.dto.HighlightTarget;
import auto.annotate.domain.document.dto.HighlightType;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Getter
@Builder
@NoArgsConstructor(force = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PdfRowRecord {

    private final int pageIndex;                 // 0-based
    private final HighlightTarget target;        // VISIT_SUMMARY / DRUG_SUMMARY ...
    private final String rawLine;                // 디버깅용 원문 라인

    // 공통(대부분 PDF에 공통으로 쓰이거나 엑셀에 자주 쓰는 값)
    private final String sequence;               // 순번 (VISIT_SUMMARY에 존재)
    private final String institutionName;        // 병·의원&약국

    // VISIT_SUMMARY
    private final String daysOfStayOrVisit;      // 예: 11(0)
    private final String totalMedicalFee;        // 총 진료비(건강보험 적용분)
    private final String insuranceBenefit;       // 건강보험 등 혜택받은 금액
    private final String userPaidAmount;         // 내가 낸 의료비(진료비)

    //진료내역
    // SURGERY(또는 처치/진료내역) 테이블용
    private final String treatmentStartDate;   // 진료시작일
    private final String treatmentItem;        // 진료내역
    private final String codeName;             // 코드명  ✅ 여기서 "수술" 필터
    private final String dosePerOnce;          // 1회 투약량 (없으면 null)
    private final String timesPerDay;          // 1회 투여횟수
    private final String totalDays;            // 총 투약일수

    // 기타
    private final String treatmentDetail;


    @Builder.Default
    private final Set<HighlightType> highlightTypes = new HashSet<>();

    public PdfRowRecord withHighlightTypes(Set<HighlightType> types) {
        return PdfRowRecord.builder()
                .pageIndex(this.pageIndex)
                .target(this.target)
                .rawLine(this.rawLine)
                .sequence(this.sequence)
                .institutionName(this.institutionName)
                .daysOfStayOrVisit(this.daysOfStayOrVisit)
                .totalMedicalFee(this.totalMedicalFee)
                .insuranceBenefit(this.insuranceBenefit)
                .userPaidAmount(this.userPaidAmount)
                .treatmentDetail(this.treatmentDetail)
                .highlightTypes(new HashSet<>(types))
                .build();
    }
}
