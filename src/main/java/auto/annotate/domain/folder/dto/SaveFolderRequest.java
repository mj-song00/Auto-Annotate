package auto.annotate.domain.folder.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class SaveFolderRequest {
    @NotBlank(message = "폴더이름을 입력해주세요.")
    private String name;
}
