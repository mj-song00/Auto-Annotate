package auto.annotate.domain.user.reposotiry;

import auto.annotate.domain.user.entity.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository< User, UUID> {
    Optional<User> findByEmail(@NotBlank @Email String email);
    Optional<User> findByNickName(@NotBlank String nickname);

    @Modifying
    @Query("""
        update User u
           set u.refreshTokenHash = :newHash,
               u.refreshTokenExpiresAt = :newExp
         where u.id = :userId
           and u.refreshTokenHash = :currentHash
    """)
    int rotateRefreshToken(UUID userId, String currentHash, String newHash, LocalDateTime newExp);

    @Modifying
    @Query("""
        update User u
           set u.refreshTokenHash = null,
               u.refreshTokenExpiresAt = null
         where u.id = :userId
    """)
    int clearRefreshToken(UUID userId);
}
