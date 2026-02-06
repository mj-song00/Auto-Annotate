package auto.annotate.domain.folder.dto.response;

import auto.annotate.domain.document.dto.HighlightTarget;
import auto.annotate.domain.document.entity.Document;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class FolderDocumentResponse {

    private UUID documentId;
    private HighlightTarget target;

    public static FolderDocumentResponse of(Document document) {
        return new FolderDocumentResponse(
                document.getId(),
                document.getTarget()
        );
    }
}
