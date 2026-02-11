package auto.annotate.domain.folder.repository;

import auto.annotate.domain.folder.entity.Folder;
import auto.annotate.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FolderRepository extends JpaRepository<Folder,UUID> {

    Optional<Folder> findById(UUID id);

    Optional<Folder> findByIdAndDeletedAtIsNull(UUID folderId);

    Page<Folder> findByUserAndDeletedAtIsNullOrderByCreatedAtDesc(User user, Pageable pageable);

    List<Folder> findAllByCreatedAtBeforeAndDeletedAtIsNull(LocalDateTime cutoff);
}
