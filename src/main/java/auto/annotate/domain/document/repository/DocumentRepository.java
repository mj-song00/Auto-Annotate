package auto.annotate.domain.document.repository;

import auto.annotate.domain.document.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {
    List<Document> findAllByBundleKey(String bundleKey);
}
