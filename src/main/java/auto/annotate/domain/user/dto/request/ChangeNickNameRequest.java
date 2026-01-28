package auto.annotate.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ChangeNickNameRequest {
    @NotBlank(message = "변경할 닉네임을 입력해주세요.")
    private String newNickName;
}
