package auto.annotate.domain.user.reposotiry;

import auto.annotate.domain.user.entity.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository< User, UUID> {
    Optional<User> findByEmail(@NotBlank @Email String email);
    Optional<User> findByNickName(@NotBlank String nickname);
}
