package auto.annotate.domain.folder.repository;

import auto.annotate.domain.folder.entity.Folder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FolderRepository extends JpaRepository<Folder,UUID> {
}
