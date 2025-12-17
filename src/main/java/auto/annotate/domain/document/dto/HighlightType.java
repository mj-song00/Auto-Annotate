package auto.annotate.domain.document.dto;

public enum HighlightType {
    VISIT_OVER_7_DAYS("동일 병원 누적 내원 7일 이상",HighlightColor.YELLOW),
    MONTH_30_DRUG("30일 초과 약제 복용",HighlightColor.ORANGE),
    HAS_HOSPITALIZATION("입원 내역 포함", HighlightColor.BLUE),
    HAS_SURGERY("수술 내역 포함", HighlightColor.RED);

    private final String description;
    private final HighlightColor color;

    HighlightType(String description, HighlightColor color) {
        this.description = description;
        this.color = color;
    }

    public String getDescription() {
        return description;
    }

    public HighlightColor getColor() {
        return color;
    }
}
