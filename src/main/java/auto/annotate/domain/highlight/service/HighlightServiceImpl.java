package auto.annotate.domain.highlight.service;

import auto.annotate.domain.document.dto.HighlightType;
import auto.annotate.domain.document.dto.response.VisitSummaryRecord;
import auto.annotate.domain.highlight.dto.response.HighlightResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class HighlightServiceImpl implements HighlightService {

    @Override
    public List<VisitSummaryRecord> applyHighlights(List<VisitSummaryRecord> records) {

        // 1️⃣ HighlightResult 생성 (내부 연산용)
        HighlightResult highlightResult = new HighlightResult(
                findHospitalsWith7DaysVisit(records),
                findHospitalsWithHospitalization(records),
                findHospitalsWithSurgery(records),
                findDrugsOver30Days(records)
        );

        // 2️⃣ 각 row에 하이라이트 적용 후 List로 반환
        return records.stream()
                .map(record -> applyRule(record, highlightResult))
                .toList();
    }

    private VisitSummaryRecord applyRule(VisitSummaryRecord record, HighlightResult highlightResult) {
        Set<HighlightType> types = new HashSet<>();

        if (highlightResult.has7DaysVisit(record.getInstitutionName())) {
            types.add(HighlightType.VISIT_OVER_7_DAYS);
        }

        if (highlightResult.isHospitalizationHospital(record.getInstitutionName())) {
            types.add(HighlightType.HAS_HOSPITALIZATION);
        }

        if (highlightResult.isSurgeryHospital(record.getInstitutionName())) {
            types.add(HighlightType.HAS_SURGERY);
        }

        if (highlightResult.hasDrugOver30Days(record.getTreatmentDetail())) {
            types.add(HighlightType.MONTH_30_DRUG);
        }

        return types.isEmpty() ? record : record.withHighlightTypes(types);
    }


    /**
     * 병원별 내원일수 합계가 7일 이상인 병원 목록 추출
     */
    private Set<String> findHospitalsWith7DaysVisit(
            List<VisitSummaryRecord> records
    ) {
        return records.stream()
                .collect(Collectors.groupingBy(
                        VisitSummaryRecord::getInstitutionName,
                        Collectors.summingInt(this::parseDays)
                ))
                .entrySet().stream()
                .filter(entry -> entry.getValue() >= 7)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    private int parseDays(VisitSummaryRecord record) {
        try {
            return Integer.parseInt(record.getDaysOfStayOrVisit());
        } catch (Exception e) {
            return 0;
        }
    }

    private Set<String> findHospitalsWithHospitalization(
            List<VisitSummaryRecord> records
    ) {
        return records.stream()
                .filter(record ->
                        record.getTreatmentDetail() != null &&
                                record.getTreatmentDetail().contains("입원")
                )
                .map(VisitSummaryRecord::getInstitutionName)
                .collect(Collectors.toSet());
    }

    private Set<String> findHospitalsWithSurgery(
            List<VisitSummaryRecord> records
    ) {
        return records.stream()
                .filter(record ->
                        record.getTreatmentDetail() != null &&
                                record.getTreatmentDetail().contains("수술")
                )
                .map(VisitSummaryRecord::getInstitutionName)
                .collect(Collectors.toSet());
    }

    private Set<String> findDrugsOver30Days(
            List<VisitSummaryRecord> records
    ) {
        return records.stream()
                .filter(record -> parseDays(record) > 30)
                .map(VisitSummaryRecord::getTreatmentDetail)
                .collect(Collectors.toSet());
    }
}