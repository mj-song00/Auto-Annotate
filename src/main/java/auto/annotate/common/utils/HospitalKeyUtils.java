package auto.annotate.common.utils;

import auto.annotate.domain.document.dto.response.PdfRowRecord;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class HospitalKeyUtils {
    private HospitalKeyUtils() {}

    public static boolean isPharmacy(String institutionName) {
        if (institutionName == null) return false;
        return institutionName.replaceAll("\\s+", "").contains("약국");
    }

    public static String normalizeHospitalKey(String institutionName) {
        if (institutionName == null) return "";
        String s = institutionName.trim();
        s = s.replaceAll("\\s+", "");
        s = s.replaceAll("[^가-힣a-zA-Z0-9]", "");
        return s.toLowerCase();
    }

    /** "11(0)" -> 11, "3(5)" -> 8, "7" -> 7 */
    public static int parseTotalDays(String daysOfStayOrVisit) {
        if (daysOfStayOrVisit == null) return 0;
        String s = daysOfStayOrVisit.trim();

        Matcher m = Pattern.compile("^(\\d+)\\((\\d+)\\)$").matcher(s);
        if (m.find()) {
            return safeParseInt(m.group(1)) + safeParseInt(m.group(2));
        }

        Matcher m2 = Pattern.compile("^(\\d+)$").matcher(s);
        if (m2.find()) return safeParseInt(m2.group(1));

        return 0;
    }

    private static int safeParseInt(String v) {
        try { return Integer.parseInt(v); }
        catch (Exception e) { return 0; }
    }

    public static Set<String> findHospitalKeysWith7Days(List<PdfRowRecord> records) {
        Map<String, Integer> sumByHospital = new HashMap<>();

        for (PdfRowRecord r : records) {
            if (isPharmacy(r.getInstitutionName())) continue;

            String key = normalizeHospitalKey(r.getInstitutionName());
            if (key.isBlank()) continue;

            int totalDays = parseTotalDays(r.getDaysOfStayOrVisit());
            if (totalDays <= 0) continue;

            sumByHospital.merge(key, totalDays, Integer::sum);
        }

        return sumByHospital.entrySet().stream()
                .filter(e -> e.getValue() >= 7)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }
}
