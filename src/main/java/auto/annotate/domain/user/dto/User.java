package auto.annotate.domain.user.dto;

import auto.annotate.domain.user.enums.UserRole;
import lombok.Getter;

import java.util.UUID;

@Getter
public class User {
    private final UUID id;
    private final String nickname;
    private final UserRole role;

    public User(UUID id, String nickname, UserRole role){
        this.id = id;
        this.nickname = nickname;
        this.role = role;
    }
}
