package auto.annotate.domain.document.repository;

import auto.annotate.domain.document.dto.HighlightTarget;
import auto.annotate.domain.document.entity.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {
    Optional<Document> findByBundleKeyAndTarget(String bundleKey, HighlightTarget target);

    List<Document> findALLByFolderId(UUID folderId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update Document d
           set d.deletedAt = :now
         where d.folder.id = :folderId
           and d.deletedAt is null
    """)
    int softDeleteByFolderId(@Param("folderId") UUID folderId, @Param("now") LocalDateTime now);
}

