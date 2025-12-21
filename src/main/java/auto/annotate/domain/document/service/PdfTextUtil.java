package auto.annotate.domain.document.service;

import org.apache.pdfbox.pdmodel.font.PDFont;

import java.io.IOException;

public final class PdfTextUtil {
    private PdfTextUtil() {}

    public static String keepEncodableOnly(PDFont font, String text) throws IOException {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            try {
                font.encode(ch);
                sb.append(ch);
            } catch (IllegalArgumentException e) {
                sb.append("?"); // 또는 그냥 스킵하려면 ""로
            }
            i += Character.charCount(cp);
        }
        return sb.toString();
    }
}