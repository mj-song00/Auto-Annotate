package auto.annotate.domain.document.dto.response;

import auto.annotate.domain.document.dto.HighlightType;
import lombok.*;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Getter
@Builder
@NoArgsConstructor(force = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class TreatmentDetailRecord {
    private final String sequence;
    private final String treatmentStartDate;
    private final String institutionName;
    private final String department;
    private final String visitType; // 입원 / 외래
    private final String mainDiseaseCode;
    private final String mainDiseaseName;
    private final String visitDays;
    private final String totalMedicalFee;
    private final String insuranceBenefit;
    private final String userPaidAmount;

    @Builder.Default
    private final Set<HighlightType> highlightTypes =
            Collections.unmodifiableSet(new HashSet<>());

    public TreatmentDetailRecord withHighlightTypes(Set<HighlightType> newTypes) {
        return TreatmentDetailRecord.builder()
                .sequence(sequence)
                .treatmentStartDate(treatmentStartDate)
                .institutionName(institutionName)
                .department(department)
                .visitType(visitType)
                .mainDiseaseCode(mainDiseaseCode)
                .mainDiseaseName(mainDiseaseName)
                .visitDays(visitDays)
                .totalMedicalFee(totalMedicalFee)
                .insuranceBenefit(insuranceBenefit)
                .userPaidAmount(userPaidAmount)
                .highlightTypes(Collections.unmodifiableSet(new HashSet<>(newTypes)))
                .build();
    }
}
