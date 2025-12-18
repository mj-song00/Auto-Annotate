package auto.annotate.domain.document.service;

import auto.annotate.domain.document.entity.Document;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface DocumentService {
   List<Document> save(List<MultipartFile> multipartFile);

   Resource loadHighlightedFileAsResource(UUID documentId, int condition);
}
