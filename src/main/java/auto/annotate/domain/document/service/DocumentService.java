package auto.annotate.domain.document.service;

import auto.annotate.domain.document.entity.Document;
import auto.annotate.domain.folder.dto.request.SaveFolderRequest;
import auto.annotate.domain.user.dto.AuthUser;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface DocumentService {
   List<Document> save(List<MultipartFile> multipartFile, AuthUser authUser, SaveFolderRequest saveFolderRequest);

   Resource loadHighlightedFileAsResource(UUID documentId, int condition);

   Resource downloadExcelByCondition(UUID documentId, int condition);
}
