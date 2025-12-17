package auto.annotate.domain.highlight.service;

import auto.annotate.domain.document.dto.response.VisitSummaryRecord;

import java.util.List;

public interface HighlightService {
    List<VisitSummaryRecord> applyHighlights(List<VisitSummaryRecord> records);
}
