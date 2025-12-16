package auto.annotate.domain.document.dto;

import lombok.Getter;

@Getter
public class UploadFileInfo {
    private final String originalName;
    private final String originalPath;

    public UploadFileInfo(String originalName, String originalPath) {
        this.originalName = originalName;
        this.originalPath = originalPath;
    }
}
