package auto.annotate.domain.user.entity;

import auto.annotate.common.entity.Timestamped;
import auto.annotate.domain.document.entity.Document;
import auto.annotate.domain.user.enums.UserRole;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Entity
@Table(name = "Users")
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PROTECTED) // 외부 직접 호출을 막기 위해 protected 설정
public class User extends Timestamped {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String nickName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole userRole; // 사용자 역할

    @Column
    private String password;

    @Column
    private LocalDateTime deletedAt;

    @OneToMany(mappedBy = "user", orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Document> documents = new ArrayList<>();

    @Column(name = "refresh_token_hash", length = 128)
    private String refreshTokenHash;

    @Column(name = "refresh_token_expires_at")
    private LocalDateTime refreshTokenExpiresAt;

    public User(
            @NotBlank @Email String email,
            @NotBlank String nickName,
            String encodedPassword,
            UserRole userRole
    ){
        this.email = email;
        this.nickName = nickName;
        this.password = encodedPassword;
        this.userRole = userRole;
    }

    // 비밀번호 변경
    public void updatePassword(String password) {
        this.password = password;
    }

    // 닉네임 변경
    public void updateNickName(String nickName) {
        this.nickName = nickName;
    }

    // 회원 탈퇴
    public void updateDeletedAt() {
        this.deletedAt = LocalDateTime.now();
    }

    public void updateRefreshToken(String hash, LocalDateTime expiresAt) {
        this.refreshTokenHash = hash;
        this.refreshTokenExpiresAt = expiresAt;
    }

    public void clearRefreshToken() {
        this.refreshTokenHash = null;
        this.refreshTokenExpiresAt = null;
    }
}
