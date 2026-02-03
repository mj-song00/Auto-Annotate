package auto.annotate.domain.folder.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class FolderResponse {
    private UUID id;
    private String name;

    public static FolderResponse of(UUID id, String name) {
        return new FolderResponse(id, name);
    }
}
