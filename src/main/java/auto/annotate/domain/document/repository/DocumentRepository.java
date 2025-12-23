package auto.annotate.domain.document.repository;

import auto.annotate.domain.document.dto.HighlightTarget;
import auto.annotate.domain.document.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {
    Optional<Document> findFirstByBundleKeyAndTarget(String bundleKey, HighlightTarget target);
    List<Document> findByBundleKey(String bundleKey);
    Optional<Document> findByBundleKeyAndTarget(String bundleKey, HighlightTarget target);

}
