package auto.annotate.domain.document.repository;

import auto.annotate.domain.document.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

}
