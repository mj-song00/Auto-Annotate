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
public class PrescriptionRecord {

    private final String sequence;
    private final String treatmentStartDate;
    private final String institutionName;
    private final String prescriptionType; // 처방 / 조제
    private final String drugName;
    private final String ingredientName;
    private final String dosePerTime;
    private final String doseCountPerDay;
    private final String totalDoseDays;

    @Builder.Default
    private final Set<HighlightType> highlightTypes =
            Collections.unmodifiableSet(new HashSet<>());

    public PrescriptionRecord withHighlightTypes(Set<HighlightType> newTypes) {
        return PrescriptionRecord.builder()
                .sequence(sequence)
                .treatmentStartDate(treatmentStartDate)
                .institutionName(institutionName)
                .prescriptionType(prescriptionType)
                .drugName(drugName)
                .ingredientName(ingredientName)
                .dosePerTime(dosePerTime)
                .doseCountPerDay(doseCountPerDay)
                .totalDoseDays(totalDoseDays)
                .highlightTypes(Collections.unmodifiableSet(new HashSet<>(newTypes)))
                .build();
    }
}
