package auto.annotate.domain.folder.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
@Getter
@AllArgsConstructor
public class FolderPageResponse {
    private List<FolderResponse> content;
    private int number;
    private long totalElements;
    private int totalPages;
}

