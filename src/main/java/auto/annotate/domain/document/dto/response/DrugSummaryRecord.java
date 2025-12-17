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
public class DrugSummaryRecord {
    private final String sequence;
    private final String treatmentStartDate;
    private final String institutionName;
    private final String treatmentDetail;
    private final String drugCodeName;
    private final String dosePerTime;
    private final String doseCountPerDay;
    private final String totalDoseDays;

    @Builder.Default
    private final Set<HighlightType> highlightTypes =
            Collections.unmodifiableSet(new HashSet<>());

    public DrugSummaryRecord withHighlightTypes(Set<HighlightType> newTypes) {
        return DrugSummaryRecord.builder()
                .sequence(sequence)
                .treatmentStartDate(treatmentStartDate)
                .institutionName(institutionName)
                .treatmentDetail(treatmentDetail)
                .drugCodeName(drugCodeName)
                .dosePerTime(dosePerTime)
                .doseCountPerDay(doseCountPerDay)
                .totalDoseDays(totalDoseDays)
                .highlightTypes(Collections.unmodifiableSet(new HashSet<>(newTypes)))
                .build();
    }
}
