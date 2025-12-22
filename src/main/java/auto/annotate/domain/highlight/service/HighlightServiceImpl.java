package auto.annotate.domain.highlight.service;

import auto.annotate.domain.document.dto.HighlightType;
import auto.annotate.domain.document.dto.response.VisitSummaryRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
@Service
@Slf4j
@RequiredArgsConstructor
public class HighlightServiceImpl implements HighlightService {

    private static final java.util.regex.Pattern IN_OUT_PATTERN =
            java.util.regex.Pattern.compile("^(\\d+)\\((\\d+)\\)$");

    private static class InOutDays {
        final int inpatient;   // 입원
        final int outpatient;  // 외래(내원)
        InOutDays(int inpatient, int outpatient) {
            this.inpatient = inpatient;
            this.outpatient = outpatient;
        }
    }

    private InOutDays parseInOutDays(String raw) {
        if (raw == null) return new InOutDays(0, 0);
        String v = raw.replaceAll("\\s+", "");

        var m = IN_OUT_PATTERN.matcher(v);
        if (m.find()) {
            return new InOutDays(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
        }

        // "11"처럼 괄호 없이 나오면 입원으로 취급(정책은 필요하면 바꿔)
        try {
            return new InOutDays(Integer.parseInt(v), 0);
        } catch (Exception e) {
            return new InOutDays(0, 0);
        }
    }

    /**
     * ✅ 병원명 정규화: 줄바꿈/공백/중점 등으로 깨져도 같은 병원으로 묶기
     * - aggregation key 용도라 공백 제거까지 함
     */
    private String normalizeInstitutionKey(String raw) {
        if (raw == null) return "";
        String s = raw;

        // 줄바꿈/탭 -> 공백
        s = s.replaceAll("[\\r\\n\\t]+", " ");

        // 중점/유사 점 제거
        s = s.replaceAll("[·•∙⋅]", " ");

        // 다중 공백 압축
        s = s.replaceAll("\\s{2,}", " ").trim();

        // ✅ 키로는 공백 제거해서 "서 울병원" 같은 케이스도 합침
        return s.replace(" ", "");
    }

    /** (선택) 약국 제외하고 싶으면 true 반환 */
    private boolean isPharmacy(String institutionName) {
        if (institutionName == null) return false;
        String s = institutionName.replaceAll("\\s+", "");
        return s.contains("약국");
    }

    @Override
    public List<VisitSummaryRecord> applyHighlights(List<VisitSummaryRecord> records, int condition) {
        if (records == null || records.isEmpty()) return List.of();

        // ✅ 병원별 "외래일수 누적" 계산 (7일 이상 병원 key 찾기)
        Set<String> hospitalKeysWith7OutpatientDays = findHospitalKeysWith7OutpatientDays(records);

        return records.stream()
                .map(r -> applyRule(r, hospitalKeysWith7OutpatientDays))
                .toList();
    }

    private VisitSummaryRecord applyRule(VisitSummaryRecord record, Set<String> hospitalKeysWith7OutpatientDays) {
        if (record == null) return null;

        Set<HighlightType> types = new HashSet<>();

        InOutDays d = parseInOutDays(record.getDaysOfStayOrVisit());

        // ✅ 입원(기간 상관 없음)
        if (d.inpatient > 0) {
            types.add(HighlightType.HAS_HOSPITALIZATION);
        }

        // ✅ 한 병원 7일 이상 내원: "정규화 key" 기준으로 비교
        String key = normalizeInstitutionKey(record.getInstitutionName());
        if (!key.isEmpty() && hospitalKeysWith7OutpatientDays.contains(key)) {
            types.add(HighlightType.VISIT_OVER_7_DAYS);
        }

        return types.isEmpty() ? record : record.withHighlightTypes(types);
    }

    private Set<String> findHospitalKeysWith7OutpatientDays(List<VisitSummaryRecord> records) {
        Map<String, Integer> outpatientSumByKey = new HashMap<>();

        for (VisitSummaryRecord r : records) {
            if (r == null) continue;

            String name = r.getInstitutionName();
            if (name == null || name.isBlank()) continue;

            // (선택) 약국 제외
            if (isPharmacy(name)) continue;

            int outpatient = parseInOutDays(r.getDaysOfStayOrVisit()).outpatient;
            if (outpatient <= 0) continue;

            String key = normalizeInstitutionKey(name);
            if (key.isEmpty()) continue;

            outpatientSumByKey.merge(key, outpatient, Integer::sum);
        }

        return outpatientSumByKey.entrySet().stream()
                .filter(e -> e.getValue() >= 7)
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());
    }
}
