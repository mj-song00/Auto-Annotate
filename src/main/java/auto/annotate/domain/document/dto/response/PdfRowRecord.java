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

    private final String institutionName;
    private final String daysOfStayOrVisit;      // 예: 11(0)
    private final String treatmentDetail;


    @Builder.Default
    private final Set<HighlightType> highlightTypes = new HashSet<>();

    public PdfRowRecord withHighlightTypes(Set<HighlightType> types) {
        return PdfRowRecord.builder()
                .pageIndex(this.pageIndex)
                .target(this.target)
                .rawLine(this.rawLine)
                .institutionName(this.institutionName)
                .daysOfStayOrVisit(this.daysOfStayOrVisit)
                .treatmentDetail(this.treatmentDetail)
                .highlightTypes(new HashSet<>(types))
                .build();
    }
}
