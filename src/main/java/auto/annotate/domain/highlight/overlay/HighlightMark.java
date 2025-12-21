package auto.annotate.domain.highlight.overlay;

import auto.annotate.domain.document.dto.HighlightType;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

public class HighlightMark {
    public final int pageIndex;        // 0-based
    public final HighlightType type;   // 조건 타입 (7일이상/30일초과/입원/수술 등)
    public final PDRectangle rect;     // 해당 텍스트(또는 키워드)의 위치

    public HighlightMark(int pageIndex, HighlightType type, PDRectangle rect) {
        this.pageIndex = pageIndex;
        this.type = type;
        this.rect = rect;
    }
}
