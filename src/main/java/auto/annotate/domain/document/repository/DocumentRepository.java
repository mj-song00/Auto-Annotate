package auto.annotate.domain.document.repository;

import auto.annotate.domain.document.dto.HighlightTarget;
import auto.annotate.domain.document.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {
    Optional<Document> findFirstByBundleKeyAndTarget(UUID documentId, HighlightTarget target);

    Optional<Document> findByBundleKeyAndTarget(String bundleKey, HighlightTarget target);

}
