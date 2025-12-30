package auto.annotate.common.utils;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class SurgeryTokenMatcher {

    // "…수술"로 끝나는 토큰을 잡아내기 위한 패턴
    private static final Pattern REAL_SURGERY_TOKEN =
            Pattern.compile("([가-힣A-Za-z0-9\\[\\]\\/\\-]{2,}수술)(?=\\d|$)");

    public boolean hasRealSurgeryToken(String rowText) {
        if (rowText == null) return false;

        String s = rowText.replaceAll("\\s+", ""); // 줄바꿈/공백 제거

        // ❌ 오탐 대표 케이스 제외
        if (s.contains("수술후처치")) return false;
        if (s.contains("단순처치")) return false;

        // ✅ "…수술" 토큰이 있으면 true
        return REAL_SURGERY_TOKEN.matcher(s).find();
    }
}
