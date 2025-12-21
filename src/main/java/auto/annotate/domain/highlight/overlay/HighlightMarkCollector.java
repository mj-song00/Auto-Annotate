package auto.annotate.domain.highlight.overlay;

import auto.annotate.domain.document.dto.HighlightType;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HighlightMarkCollector {
    /**
     * "조건별 키워드"를 PDF에서 찾아서 marks 생성
     * - 당장 동작 확인용: 조건마다 대표 키워드 몇 개를 검색
     * - 실제로는 records 기반으로 병원명/약제명 등 "진짜 값"을 넣으면 정확도 올라감
     */
    public List<HighlightMark> collectByKeywords(PDDocument document, Map<HighlightType, List<String>> keywordsByType) throws IOException {
        List<HighlightMark> marks = new ArrayList<>();

        for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
            PDPage page = document.getPage(pageIndex);

            for (Map.Entry<HighlightType, List<String>> e : keywordsByType.entrySet()) {
                HighlightType type = e.getKey();

                for (String keyword : e.getValue()) {
                    if (keyword == null || keyword.isBlank()) continue;

                    List<PDRectangle> rects = findTextRectsInPage(document, pageIndex, keyword);

                    for (PDRectangle r : rects) {
                        marks.add(new HighlightMark(pageIndex, type, r));
                    }
                }
            }
        }

        return marks;
    }

    /**
     * 한 페이지에서 keyword가 등장한 위치들을 PDRectangle로 반환
     * - 완벽한 “줄 전체”가 아니라, keyword 범위만 잡음
     */
    private List<PDRectangle> findTextRectsInPage(PDDocument doc, int pageIndex, String keyword) throws IOException {
        KeywordPositionStripper stripper = new KeywordPositionStripper(keyword);
        stripper.setStartPage(pageIndex + 1);
        stripper.setEndPage(pageIndex + 1);

        // 텍스트 추출 트리거 (결과 문자열은 안 써도 됨)
        stripper.getText(doc);

        return stripper.getRects();
    }

    /**
     * PDFTextStripper를 상속해서 "키워드가 등장한 텍스트 청크"에서 좌표를 얻는다.
     * - writeString 호출 단위(보통 단어/라인 조각) 안에서 keyword를 찾는 방식이라
     *   대부분의 키워드(입원/수술/30일 등)는 잘 잡힘.
     */
    private static class KeywordPositionStripper extends PDFTextStripper {
        private final String keyword;
        private final List<PDRectangle> rects = new ArrayList<>();

        protected KeywordPositionStripper(String keyword) throws IOException {
            super();
            this.keyword = keyword;
            setSortByPosition(true);
        }

        public List<PDRectangle> getRects() {
            return rects;
        }

        @Override
        protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
            // textPositions 길이 == text 길이가 안 맞는 경우가 가끔 있어 방어
            if (text == null || text.isEmpty() || textPositions == null || textPositions.isEmpty()) {
                return;
            }

            int from = 0;
            while (true) {
                int idx = text.indexOf(keyword, from);
                if (idx < 0) break;

                int endExclusive = idx + keyword.length();
                if (endExclusive <= textPositions.size()) {
                    PDRectangle rect = toRect(textPositions.subList(idx, endExclusive));
                    if (rect != null) rects.add(rect);
                }
                from = endExclusive;
            }
        }

        private PDRectangle toRect(List<TextPosition> tps) {
            if (tps == null || tps.isEmpty()) return null;

            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            float maxH = 0f;

            for (TextPosition tp : tps) {
                float x = tp.getXDirAdj();
                float y = tp.getYDirAdj();
                float w = tp.getWidthDirAdj();
                float h = tp.getHeightDir();

                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x + w);
                maxY = Math.max(maxY, y);
                maxH = Math.max(maxH, h);
            }

            // yDirAdj는 "baseline" 성격이라 아래로 높이를 내려서 박스 잡음 (대부분 잘 보임)
            float padding = 1.5f;
            float rectX = minX - padding;
            float rectY = (minY - maxH) - padding;
            float rectW = (maxX - minX) + padding * 2;
            float rectH = maxH + padding * 2;

            if (rectW <= 0 || rectH <= 0) return null;
            return new PDRectangle(rectX, rectY, rectW, rectH);
        }
    }
}
