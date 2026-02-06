package auto.annotate.domain.folder.repository;

import auto.annotate.domain.folder.entity.Folder;
import auto.annotate.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FolderRepository extends JpaRepository<Folder,UUID> {

    Optional<Folder> findById(UUID id);

    List<Folder> findByUserAndDeletedAtIsNull(User user);

    Optional<Folder> findByIdAndDeletedAtIsNull(UUID folderId);
}
