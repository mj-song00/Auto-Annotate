package auto.annotate.domain.document.service;

import auto.annotate.domain.document.entity.Document;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DocumentService {
   List<Document> save(List<MultipartFile> multipartFile);
}
