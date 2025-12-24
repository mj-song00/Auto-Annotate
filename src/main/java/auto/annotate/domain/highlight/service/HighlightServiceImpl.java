package auto.annotate.domain.highlight.service;

import auto.annotate.domain.document.dto.HighlightTarget;
import auto.annotate.domain.document.dto.HighlightType;
import auto.annotate.domain.document.dto.response.PdfRowRecord;
import auto.annotate.domain.document.dto.response.VisitSummaryRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
     * ✅ 병원명 정규화 (aggregation key 용도)
     */
    private String normalizeInstitutionKey(String raw) {
        if (raw == null) return "";
        String s = raw;

        s = s.replaceAll("[\\r\\n\\t]+", " ");
        s = s.replaceAll("[·•∙⋅]", " ");
        s = s.replaceAll("\\s{2,}", " ").trim();

        // ✅ 키는 공백 제거
        return s.replace(" ", "");
    }

    /** 약국 제외 */
    private boolean isPharmacy(String institutionName) {
        if (institutionName == null) return false;
        String s = institutionName.replaceAll("\\s+", "");
        return s.contains("약국");
    }

    @Override
    public List<PdfRowRecord> applyHighlights(List<PdfRowRecord> records, int condition) {
        if (records == null || records.isEmpty()) return List.of();

        HighlightType onlyType = mapConditionToType(condition);

        // ✅ 필요한 사전 계산만
        Set<String> hospitalKeysWith7OutpatientDays = Set.of();
        if (onlyType == HighlightType.VISIT_OVER_7_DAYS) {
            hospitalKeysWith7OutpatientDays = findHospitalKeysWith7OutpatientDays(records);
        }

        log.info("applyHighlights condition={}, onlyType={}, recordsSize={}, keysSize={}",
                condition, onlyType, records.size(), hospitalKeysWith7OutpatientDays.size());
        log.info("7days hospital keys sample={}",
                hospitalKeysWith7OutpatientDays.stream().limit(5).toList());
        final Set<String> finalHospitalKeys = hospitalKeysWith7OutpatientDays;

        long marked = records.stream()
                .filter(r -> r.getHighlightTypes().contains(onlyType))
                .count();
        log.info("after apply: type={}, markedRows={}", onlyType, marked);

        return records.stream()
                .map(r -> applyRuleByCondition(r, onlyType, finalHospitalKeys))
                .toList();
    }

    private HighlightType mapConditionToType(int condition) {
        return switch (condition) {
            case 0 -> HighlightType.VISIT_OVER_7_DAYS;
            case 1 -> HighlightType.MONTH_30_DRUG;
            case 2 -> HighlightType.HAS_HOSPITALIZATION;
            case 3 -> HighlightType.HAS_SURGERY;
            default -> HighlightType.VISIT_OVER_7_DAYS;
        };
    }

    /**
     * ✅ condition(onlyType)에 해당하는 룰만 적용해서 highlightTypes를 세팅한다.
     * - immutable 방식: record.withHighlightTypes(types) 사용
     */
    private PdfRowRecord applyRuleByCondition(
            PdfRowRecord r,
            HighlightType onlyType,
            Set<String> hospitalKeysWith7OutpatientDays
    ) {
        Set<HighlightType> types = new HashSet<>(r.getHighlightTypes());

        switch (onlyType) {
            case VISIT_OVER_7_DAYS -> {
                String key = normalizeHospitalKey(r.getInstitutionName());
                if (!key.isBlank() && hospitalKeysWith7OutpatientDays.contains(key)) {
                    types.add(HighlightType.VISIT_OVER_7_DAYS);
                }
            }

            case HAS_HOSPITALIZATION -> {
                // ✅ 입원은 요약에서만 판단하는 게 안전
                if (r.getTarget() == HighlightTarget.VISIT_SUMMARY) {
                    int inpatientDays = parseInpatientDays(r.getDaysOfStayOrVisit());
                    if (inpatientDays > 0) {
                        types.add(HighlightType.HAS_HOSPITALIZATION);
                    }
                }
            }

            default -> {
                // 나머지 조건은 추후 추가
            }
        }

        return r.withHighlightTypes(types);
    }



    // 기존 "전체 룰" 적용 메서드는 남겨둬도 되지만,
    // condition별만 쓸 거면 사실상 applyRuleByCondition만 사용하면 됨.
    private VisitSummaryRecord applyRule(VisitSummaryRecord record, Set<String> hospitalKeysWith7OutpatientDays) {
        if (record == null) return null;

        Set<HighlightType> types = new HashSet<>();
        InOutDays d = parseInOutDays(record.getDaysOfStayOrVisit());

        if (d.inpatient > 0) {
            types.add(HighlightType.HAS_HOSPITALIZATION);
        }

        String key = normalizeInstitutionKey(record.getInstitutionName());
        if (!key.isEmpty() && hospitalKeysWith7OutpatientDays.contains(key)) {
            types.add(HighlightType.VISIT_OVER_7_DAYS);
        }

        return types.isEmpty() ? record : record.withHighlightTypes(types);
    }

    private Set<String> findHospitalKeysWith7OutpatientDays(List<PdfRowRecord> records) {
        Map<String, Integer> sumByHospital = new HashMap<>();

        for (PdfRowRecord r : records) {
            // 누적 일수 근거는 진료정보요약만 사용
            if (r.getTarget() != HighlightTarget.VISIT_SUMMARY) continue;

            //  약국 제외
            if (isPharmacy(r.getInstitutionName())) continue;

            String key = normalizeHospitalKey(r.getInstitutionName());
            if (key.isBlank()) continue;

            int totalDays = parseTotalDays(r.getDaysOfStayOrVisit()); // 11(0) -> 11
            if (totalDays <= 0) continue;

            sumByHospital.merge(key, totalDays, Integer::sum);
        }

        return sumByHospital.entrySet().stream()
                .filter(e -> e.getValue() >= 7)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    private String normalizeHospitalKey(String institutionName) {
        if (institutionName == null) return "";
        String s = institutionName.trim();

        // 공백/탭/줄바꿈 제거
        s = s.replaceAll("\\s+", "");

        // 특수문자 제거(한글/영문/숫자만 남김)
        s = s.replaceAll("[^가-힣a-zA-Z0-9]", "");

        // 영문은 소문자로 통일
        return s.toLowerCase();
    }

    /** 예: "11(0)" -> 11, "0(8)" -> 8, "3(5)" -> 8, "7" -> 7 */
    private int parseTotalDays(String daysOfStayOrVisit) {
        if (daysOfStayOrVisit == null) return 0;
        String s = daysOfStayOrVisit.trim();

        Matcher m = Pattern.compile("^(\\d+)\\((\\d+)\\)$").matcher(s);
        if (m.find()) {
            int inDays = safeParseInt(m.group(1));
            int outDays = safeParseInt(m.group(2));
            return inDays + outDays;
        }

        Matcher m2 = Pattern.compile("^(\\d+)$").matcher(s);
        if (m2.find()) {
            return safeParseInt(m2.group(1));
        }

        return 0;
    }

    private int parseInpatientDays(String s) {
        if (s == null) return 0;
        s = s.trim();

        var m = java.util.regex.Pattern.compile("^(\\d+)\\((\\d+)\\)$").matcher(s);
        if (m.find()) {
            return safeParseInt(m.group(1)); // 괄호 앞 = 입원일수
        }
        return 0;
    }

    private int safeParseInt(String v) {
        try {
            return Integer.parseInt(v);
        } catch (Exception e) {
            return 0;
        }
    }
}