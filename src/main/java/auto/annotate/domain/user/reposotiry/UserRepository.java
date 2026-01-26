package auto.annotate.domain.user.reposotiry;

import auto.annotate.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserRepository extends JpaRepository< User, UUID> {
}
