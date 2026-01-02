package auto.annotate.domain.highlight.service;

import auto.annotate.common.utils.HospitalizationTokenMatcher;
import auto.annotate.common.utils.SurgeryTokenMatcher;
import auto.annotate.domain.document.dto.HighlightTarget;
import auto.annotate.domain.document.dto.HighlightType;
import auto.annotate.domain.document.dto.response.PdfRowRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static auto.annotate.common.utils.HospitalKeyUtils.*;


@Service
@Slf4j
@RequiredArgsConstructor
public class HighlightServiceImpl implements HighlightService {

    private final SurgeryTokenMatcher surgeryTokenMatcher;
    private final HospitalizationTokenMatcher hospitalizationTokenMatcher;

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

        List<PdfRowRecord> applied = records.stream()
                .map(r -> applyRuleByCondition(r, onlyType, finalHospitalKeys))
                .toList();

        if (onlyType == HighlightType.HAS_HOSPITALIZATION) {
            long looseCnt = applied.stream().filter(r -> r.getHighlightTypes().contains(HighlightType.HAS_HOSPITALIZATION)).count();
            log.info("[HOSP] appliedHasHospCnt={}", looseCnt);

            applied.stream()
                    .filter(r -> r.getHighlightTypes().contains(HighlightType.HAS_HOSPITALIZATION))
                    .limit(1)
                    .forEach(r -> log.info("[HOSP] sampleHit={}", r));
        }

        long marked = applied.stream()
                .filter(r -> r.getHighlightTypes().contains(onlyType))
                .count();
        log.info("after apply: type={}, markedRows={}", onlyType, marked);

        return applied;
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

    private static final Pattern INOUT_ANYWHERE = Pattern.compile("(\\d+)\\((\\d+)\\)");


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
                if (r.getTarget() != HighlightTarget.VISIT_SUMMARY) break;
                if (isPharmacy(r.getInstitutionName())) break;

                if (hospitalizationTokenMatcher.hasHospitalization(r.getDaysOfStayOrVisit())) {
                    types.add(HighlightType.HAS_HOSPITALIZATION);
                    log.info("[HOSP_RULE_HIT] page={}, inst='{}', inout='{}'",
                            r.getPageIndex(), r.getInstitutionName(), r.getDaysOfStayOrVisit());
                }
            }

            case HAS_SURGERY -> {
                if (r.getTarget() != HighlightTarget.TREATMENT_DETAIL) break;
                boolean hit = surgeryTokenMatcher.hasRealSurgeryToken(r.getTreatmentDetail());
                if (hit) types.add(HighlightType.HAS_SURGERY);
            }

            case MONTH_30_DRUG -> {
                // 지금 안 쓰면 비워도 됨
            }
        }

        return r.withHighlightTypes(types);
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

    private int safeParseInt(String v) {
        try { return Integer.parseInt(v); }
        catch (Exception e) { return 0; }
    }
}