package auto.annotate.domain.highlight.service;

import auto.annotate.domain.document.dto.HighlightTarget;
import auto.annotate.domain.document.dto.HighlightType;
import auto.annotate.domain.document.dto.response.PdfRowRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static auto.annotate.common.utils.HospitalKeyUtils.isPharmacy;
import static auto.annotate.common.utils.HospitalKeyUtils.parseTotalDays;

@Service
@Slf4j
@RequiredArgsConstructor
public class HighlightServiceImpl implements HighlightService {

    // todo : 현재 사용되지 않아 주석처리된 메서드 정리
//
//    private static final java.util.regex.Pattern IN_OUT_PATTERN =
//            java.util.regex.Pattern.compile("^(\\d+)\\((\\d+)\\)$");

//    private static class InOutDays {
//        final int inpatient;   // 입원
//        final int outpatient;  // 외래(내원)
//        InOutDays(int inpatient, int outpatient) {
//            this.inpatient = inpatient;
//            this.outpatient = outpatient;
//        }
//    }
//
//    private InOutDays parseInOutDays(String raw) {
//        if (raw == null) return new InOutDays(0, 0);
//        String v = raw.replaceAll("\\s+", "");
//
//        var m = IN_OUT_PATTERN.matcher(v);
//        if (m.find()) {
//            return new InOutDays(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
//        }
//
//        // "11"처럼 괄호 없이 나오면 입원으로 취급(정책은 필요하면 바꿔)
//        try {
//            return new InOutDays(Integer.parseInt(v), 0);
//        } catch (Exception e) {
//            return new InOutDays(0, 0);
//        }
//    }

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
    private PdfRowRecord applyRuleByCondition(
            PdfRowRecord r,
            HighlightType onlyType,
            Set<String> hospitalKeysWith7OutpatientDays
    ) {
        log.info("[RULE] onlyType={}, target={}, page={}, head={}",
                onlyType, r.getTarget(), r.getPageIndex(),
                (r.getTreatmentDetail() == null ? "null" :
                        r.getTreatmentDetail().substring(0, Math.min(40, r.getTreatmentDetail().length())))
        );

        if (onlyType == HighlightType.HAS_SURGERY) {
            String norm = String.valueOf(r.getTreatmentDetail()).replaceAll("\\s+","");
            if (norm.contains("수술")) {
                log.info("[SOOL_ROW_SAMPLE] {}", norm);
            }
        }

        Set<HighlightType> types = new HashSet<>(r.getHighlightTypes());

        switch (onlyType) {
            case VISIT_OVER_7_DAYS -> {
                String key = normalizeHospitalKey(r.getInstitutionName());
                if (!key.isBlank() && hospitalKeysWith7OutpatientDays.contains(key)) {
                    types.add(HighlightType.VISIT_OVER_7_DAYS);
                }
            }

            case HAS_HOSPITALIZATION -> {
                String norm = String.valueOf(r.getTreatmentDetail()).replaceAll("\\s+", "");
                boolean contains = norm.contains("수술");
                boolean hit = hasRealSurgeryToken(r.getTreatmentDetail());

                log.info("[SURGERY_RULE] page={}, target={}, containsSool={}, hitToken={}, normSample={}",
                        r.getPageIndex(), r.getTarget(), contains, hit,
                        norm.substring(0, Math.min(80, norm.length()))
                );


                if (r.getTarget() == HighlightTarget.TREATMENT_DETAIL && hit) {
                    types.add(HighlightType.HAS_SURGERY);
                }
            }

            case HAS_SURGERY -> {
                String norm = String.valueOf(r.getTreatmentDetail()).replaceAll("\\s+", "");
                boolean hit = hasRealSurgeryToken(r.getTreatmentDetail());
                if (norm.contains("수술")) {
                    log.info("[SURGERY_HITCHECK] hit={}, norm={}", hit, norm);
                }

                if (r.getTarget() == HighlightTarget.TREATMENT_DETAIL && hit) {
                    types.add(HighlightType.HAS_SURGERY);
                }
            }

            default -> {
                // 나머지 조건은 추후 추가
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



//    private int parseInpatientDays(String s) {
//        if (s == null) return 0;
//        s = s.trim();
//
//        var m = java.util.regex.Pattern.compile("^(\\d+)\\((\\d+)\\)$").matcher(s);
//        if (m.find()) {
//            return safeParseInt(m.group(1)); // 괄호 앞 = 입원일수
//        }
//        return 0;
//    }

    // ✅ PdfRowRecord 전체 문자열에서 "입원(외래)일수" 패턴을 찾아 입원 여부 판단
//    private boolean hasHospitalizationLoose(PdfRowRecord r) {
//        if (r.getTarget() != HighlightTarget.VISIT_SUMMARY) return false;
//
//        // ✅ 레코드 전체를 문자열로 덤프 (필드 어디에 있든 잡기)
//        String blob = String.valueOf(r).replaceAll("\\s+", "");
//
//        Matcher m = Pattern.compile("(\\d+)\\((\\d+)\\)").matcher(blob);
//        while (m.find()) {
//            int inpatient = safeParseInt(m.group(1));
//            if (inpatient > 0) return true;
//        }
//        return false;
//    }

//    private String safe(String s) {
//        return s == null ? "" : s;
//    }

    private boolean isSurgeryByCodeName(String codeName) {
        if (codeName == null) return false;
        String s = codeName.replaceAll("\\s+", "");

        // 수술로 치면 안 되는 것(너가 발견한 반례)
        if (s.contains("수술후처치") || s.contains("단순처치")) return false;

        // 진짜 수술: 보수적으로는 endsWith 추천
        return s.endsWith("수술");
        // 또는 좀 더 넓게: return s.contains("수술");
    }

    // "…수술"로 끝나는 토큰을 잡아내기 위한 패턴
    private static final Pattern REAL_SURGERY_TOKEN =
            Pattern.compile("([가-힣A-Za-z0-9\\[\\]\\/\\-]{2,}수술)(?=\\d|$)");

    private boolean hasRealSurgeryToken(String rowText) {
        if (rowText == null) return false;

        String s = rowText.replaceAll("\\s+", ""); // 줄바꿈/공백 제거

        // ❌ 오탐 대표 케이스 제외
        if (s.contains("수술후처치")) return false;
        if (s.contains("단순처치")) return false;

        // ✅ "…수술" 토큰이 있으면 true
        return REAL_SURGERY_TOKEN.matcher(s).find();
    }
}