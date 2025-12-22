package auto.annotate.domain.highlight.overlay;

import auto.annotate.domain.document.dto.HighlightType;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class PdfOverlayRenderer {

    private final PDType0Font summaryFont;

    public PdfOverlayRenderer(PDDocument document) throws IOException {
        InputStream is = PdfOverlayRenderer.class.getResourceAsStream("/fonts/NotoSansKR-VariableFont_wght.ttf");
        if (is == null) {
            throw new IllegalStateException("Font not found in classpath: /fonts/NotoSansKR-VariableFont_wght.ttf");
        }
        try (is) {
            this.summaryFont = PDType0Font.load(document, is, true);
        }
    }

    public void render(
            PDDocument document,
            List<HighlightMark> marks,
            Map<HighlightType, Integer> summaryCounts
    ) throws IOException {

        List<HighlightMark> safeMarks = (marks == null) ? Collections.emptyList() : marks;

        Map<Integer, List<HighlightMark>> marksByPage = safeMarks.stream()
                .collect(Collectors.groupingBy(m -> m.pageIndex));

        for (Map.Entry<Integer, List<HighlightMark>> entry : marksByPage.entrySet()) {
            int pageIndex = entry.getKey();
            List<HighlightMark> pageMarks = entry.getValue();

            PDPage page = document.getPage(pageIndex);
            PDRectangle mb = page.getMediaBox();

            try (PDPageContentStream cs = new PDPageContentStream(
                    document,
                    page,
                    PDPageContentStream.AppendMode.APPEND,
                    true,
                    true
            )) {
                // ✅ 1) 요약 박스: PDF 전체 요약을 1페이지(0)에만
                if (pageIndex == 0) {
                    drawSummaryBox(cs, mb, summaryCounts);
                }

                // ✅ 2) 페이지 탭/마진바는 "이 페이지 marks" 기준
                Map<HighlightType, Integer> pageCounts = countByTypeFromMarks(pageMarks);

                Set<HighlightType> typesPresent = pageCounts.entrySet().stream()
                        .filter(e -> e.getValue() != null && e.getValue() > 0)
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toCollection(LinkedHashSet::new));

                drawPageTabs(cs, mb, typesPresent);
                drawMarginBars(cs, mb, pageMarks);
            }
        }
    }

    // ✅ marks 기준 카운트 (페이지 탭용)
    private Map<HighlightType, Integer> countByTypeFromMarks(List<HighlightMark> pageMarks) {
        EnumMap<HighlightType, Integer> map = new EnumMap<>(HighlightType.class);
        if (pageMarks == null) return map;

        for (HighlightMark m : pageMarks) {
            if (m == null || m.type == null) continue;
            map.put(m.type, map.getOrDefault(m.type, 0) + 1);
        }
        return map;
    }

    // records 기준 카운트는 Renderer가 아니라 Service 쪽에서 만들고,
    // render(document, marks, summaryCounts)로 주입받는 형태가 베스트라서 여기서는 제거해도 됨.

    private void drawSummaryBox(PDPageContentStream cs, PDRectangle mb, Map<HighlightType, Integer> counts) throws IOException {
        String summary = buildSummaryText(counts);

        float margin = 18f;
        float boxH = 26f;
        float boxX = mb.getLowerLeftX() + margin;
        float boxY = mb.getUpperRightY() - margin - boxH;
        float boxW = mb.getWidth() - margin * 2;

        setFillAlpha(cs, 0.15f);
        cs.setNonStrokingColor(new Color(80, 80, 80));
        cs.addRect(boxX, boxY, boxW, boxH);
        cs.fill();

        setFillAlpha(cs, 1.0f);
        cs.setStrokingColor(new Color(120, 120, 120));
        cs.setLineWidth(0.7f);
        cs.addRect(boxX, boxY, boxW, boxH);
        cs.stroke();

        cs.beginText();
        cs.setNonStrokingColor(Color.WHITE);
        cs.setFont(summaryFont, 10f);
        cs.newLineAtOffset(boxX + 8f, boxY + 8f);
        cs.showText(summary);
        cs.endText();
    }

    private String buildSummaryText(Map<HighlightType, Integer> counts) {
        if (counts == null) return "조건 요약: 해당 없음";

        StringBuilder sb = new StringBuilder("조건 요약: ");
        boolean first = true;

        for (HighlightType t : HighlightType.values()) {
            int c = counts.getOrDefault(t, 0);
            // 원하면 0은 숨김:
            // if (c == 0) continue;

            if (!first) sb.append(" · ");
            sb.append(shortLabel(t)).append(" ").append(c);
            first = false;
        }
        return sb.toString();
    }

    private String shortLabel(HighlightType t) {
        return switch (t) {
            case VISIT_OVER_7_DAYS -> "7일이상";
            case MONTH_30_DRUG -> "30일초과";
            case HAS_HOSPITALIZATION -> "입원";
            case HAS_SURGERY -> "수술";
        };
    }

    private void drawPageTabs(PDPageContentStream cs, PDRectangle mb, Set<HighlightType> typesPresent) throws IOException {
        if (typesPresent == null || typesPresent.isEmpty()) return;

        float tabW = 6f;
        float tabH = 16f;
        float gap = 4f;

        float x = mb.getUpperRightX() - tabW;
        float yTop = mb.getUpperRightY() - 18f;

        int i = 0;
        for (HighlightType t : typesPresent) {
            float y = yTop - (tabH + gap) * i;

            setFillAlpha(cs, 0.85f);
            cs.setNonStrokingColor(colorOf(t));
            cs.addRect(x, y, tabW, tabH);
            cs.fill();

            i++;
        }
        setFillAlpha(cs, 1.0f);
    }

    private void drawMarginBars(PDPageContentStream cs, PDRectangle mb, List<HighlightMark> pageMarks) throws IOException {
        if (pageMarks == null || pageMarks.isEmpty()) return;

        float barX = mb.getLowerLeftX() + 10f;
        float barW = 3f;

        List<HighlightMark> sorted = new ArrayList<>(pageMarks);
        sorted.sort(Comparator.comparingDouble(m -> m.rect.getLowerLeftY()));

        for (HighlightMark m : sorted) {
            float y = m.rect.getLowerLeftY();
            float h = Math.max(m.rect.getHeight(), 10f);

            setFillAlpha(cs, 0.90f);
            cs.setNonStrokingColor(colorOf(m.type));
            cs.addRect(barX, y, barW, h);
            cs.fill();
        }
        setFillAlpha(cs, 1.0f);
    }

    private void setFillAlpha(PDPageContentStream cs, float alpha) throws IOException {
        PDExtendedGraphicsState gs = new PDExtendedGraphicsState();
        gs.setNonStrokingAlphaConstant(alpha);
        cs.setGraphicsStateParameters(gs);
    }

    private Color colorOf(HighlightType t) {
        return switch (t) {
            case VISIT_OVER_7_DAYS -> new Color(255, 217, 64);
            case MONTH_30_DRUG -> new Color(255, 153, 51);
            case HAS_HOSPITALIZATION -> new Color(70, 140, 255);
            case HAS_SURGERY -> new Color(255, 80, 80);
        };
    }
}
