package auto.annotate.domain.document.dto;

import lombok.Getter;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;

@Getter
public enum HighlightType {
    VISIT_OVER_7_DAYS(
            HighlightTarget.VISIT_SUMMARY,
            "동일 병원 누적 내원 7일 이상",
            HighlightColor.YELLOW
    ),

    HAS_HOSPITALIZATION(
            HighlightTarget.VISIT_SUMMARY,
            "입원 내역 포함",
            HighlightColor.BLUE
    ),

    HAS_SURGERY(
            HighlightTarget.VISIT_SUMMARY,
            "수술 내역 포함",
            HighlightColor.RED
    ),

    MONTH_30_DRUG(
            HighlightTarget.DRUG_SUMMARY,
            "30일 초과 약제 복용",
            HighlightColor.ORANGE
    );

    private final HighlightTarget target;
    private final String description;
    private final HighlightColor color;

    HighlightType(
            HighlightTarget target,
            String description,
            HighlightColor color
    ) {
        this.target = target;
        this.description = description;
        this.color = color;
    }


    /**
     * PDFBox에서 바로 사용 가능한 PDColor 반환
     */
    public PDColor getPDColor() {
        return switch (color) {
            case YELLOW -> new PDColor(new float[]{1, 1, 0}, PDDeviceRGB.INSTANCE);
            case ORANGE -> new PDColor(new float[]{1, 0.5f, 0}, PDDeviceRGB.INSTANCE);
            case BLUE -> new PDColor(new float[]{0, 0, 1}, PDDeviceRGB.INSTANCE);
            case RED -> new PDColor(new float[]{1, 0, 0}, PDDeviceRGB.INSTANCE);
        };
    }
}