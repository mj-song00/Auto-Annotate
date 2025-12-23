package auto.annotate.domain.highlight.service;

import auto.annotate.domain.document.dto.response.PdfRowRecord;

import java.util.List;

public interface HighlightService {
    List<PdfRowRecord> applyHighlights(List<PdfRowRecord> records, int condition);
}
