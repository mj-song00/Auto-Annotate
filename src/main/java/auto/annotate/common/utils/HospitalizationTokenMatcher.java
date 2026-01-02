package auto.annotate.common.utils;

import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class HospitalizationTokenMatcher {

    // () 와 （） 둘 다 허용
    private static final Pattern INOUT_ANYWHERE =
            Pattern.compile("(\\d+)[\\(（](\\d+)[\\)）]");


    /** "11(0)" 같은 값에서 입원일수(앞 숫자)를 반환. 파싱 실패 시 0 */
    public int extractInpatientDays(String daysOfStayOrVisit) {
        if (daysOfStayOrVisit == null) return 0;

        String v = daysOfStayOrVisit.replaceAll("\\s+", "");
        Matcher m = INOUT_ANYWHERE.matcher(v);
        if (!m.find()) return 0;

        try {
            return Integer.parseInt(m.group(1)); // 앞 숫자 = 입원
        } catch (Exception e) {
            return 0;
        }
    }

    /** 입원일수가 1 이상이면 true */
    public boolean hasHospitalization(String daysOfStayOrVisit) {
        return extractInpatientDays(daysOfStayOrVisit) > 0;
    }
}
