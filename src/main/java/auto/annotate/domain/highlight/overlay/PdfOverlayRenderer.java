package auto.annotate.domain.highlight.overlay;

import auto.annotate.domain.document.dto.HighlightType;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;

import java.awt.*;

import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

import static auto.annotate.domain.document.service.PdfTextUtil.keepEncodableOnly;


public class PdfOverlayRenderer {
    public void render(PDDocument document, List<HighlightMark> marks) throws IOException {
        Map<Integer, List<HighlightMark>> marksByPage = marks.stream()
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
                // 1) 상단 요약 박스
                Map<HighlightType, Integer> counts = countByType(pageMarks);
                drawSummaryBox(cs, mb, counts);

                // 2) 페이지 탭(오른쪽)
                Set<HighlightType> typesPresent = counts.entrySet().stream()
                        .filter(e -> e.getValue() != null && e.getValue() > 0)
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                drawPageTabs(cs, mb, typesPresent);

                // 3) 마진 바(왼쪽)
                drawMarginBars(cs, mb, pageMarks);
            }
        }
    }

    private Map<HighlightType, Integer> countByType(List<HighlightMark> pageMarks) {
        EnumMap<HighlightType, Integer> map = new EnumMap<>(HighlightType.class);
        for (HighlightMark m : pageMarks) {
            map.put(m.type, map.getOrDefault(m.type, 0) + 1);
        }
        return map;
    }

    // -------------------------
    // (A) 상단 요약 박스
    // -------------------------
    private void drawSummaryBox(PDPageContentStream cs, PDRectangle mb, Map<HighlightType, Integer> counts) throws IOException {
        String summary = buildSummaryText(counts);

        float margin = 18f;
        float boxH = 26f;
        float boxX = mb.getLowerLeftX() + margin;
        float boxY = mb.getUpperRightY() - margin - boxH;
        float boxW = mb.getWidth() - margin * 2;

        // 배경
        setFillAlpha(cs, 0.15f);
        cs.setNonStrokingColor(new Color(80, 80, 80)); // 연회색 느낌
        cs.addRect(boxX, boxY, boxW, boxH);
        cs.fill();

        // 테두리
        setFillAlpha(cs, 1.0f);
        cs.setStrokingColor(new Color(120, 120, 120));
        cs.setLineWidth(0.7f);
        cs.addRect(boxX, boxY, boxW, boxH);
        cs.stroke();

        // 텍스트
        cs.beginText();
        cs.setFont(PDType1Font.HELVETICA_BOLD, 10f);
        cs.setNonStrokingColor(Color.WHITE);
        cs.newLineAtOffset(boxX + 8f, boxY + 8f);
        PDFont font = PDType1Font.HELVETICA_BOLD;
        cs.setFont(font, 12);

        String safeSummary = keepEncodableOnly(font, summary);
        cs.showText(safeSummary);
        cs.endText();
    }

    private String buildSummaryText(Map<HighlightType, Integer> counts) {
        // 원하는 포맷으로 바꿔도 됨
        // 예: "⚑ 조건 요약: 7일이상 2 · 30일초과 1 · 입원 0 · 수술 1"
        StringBuilder sb = new StringBuilder("⚑ 조건 요약: ");
        boolean first = true;
        for (HighlightType t : HighlightType.values()) {
            int c = counts.getOrDefault(t, 0);
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

    // -------------------------
    // (B) 페이지 탭(오른쪽)
    // -------------------------
    private void drawPageTabs(PDPageContentStream cs, PDRectangle mb, Set<HighlightType> typesPresent) throws IOException {
        if (typesPresent.isEmpty()) return;

        float tabW = 6f;
        float tabH = 16f;
        float gap = 4f;

        float x = mb.getUpperRightX() - tabW;      // 오른쪽 끝에 붙임
        float yTop = mb.getUpperRightY() - 18f;    // 약간 아래에서 시작

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

    // -------------------------
    // (C) 마진 바(왼쪽)
    // -------------------------
    private void drawMarginBars(PDPageContentStream cs, PDRectangle mb, List<HighlightMark> pageMarks) throws IOException {
        float barX = mb.getLowerLeftX() + 10f;
        float barW = 3f;

        // 너무 겹치면 보기 안 좋으니 y가 비슷한 것끼리 합쳐주는 간단한 처리(옵션)
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

    // -------------------------
    // Helpers
    // -------------------------
    private void setFillAlpha(PDPageContentStream cs, float alpha) throws IOException {
        PDExtendedGraphicsState gs = new PDExtendedGraphicsState();
        gs.setNonStrokingAlphaConstant(alpha);
        cs.setGraphicsStateParameters(gs);
    }

    // 너 enum이 HighlightColor를 갖고 있다면 거기서 가져오면 됨
    private Color colorOf(HighlightType t) {
        return switch (t) {
            case VISIT_OVER_7_DAYS -> new Color(255, 217, 64);   // 노랑
            case MONTH_30_DRUG -> new Color(255, 153, 51);       // 주황
            case HAS_HOSPITALIZATION -> new Color(70, 140, 255); // 파랑
            case HAS_SURGERY -> new Color(255, 80, 80);          // 빨강
        };
    }
}
