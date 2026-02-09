package auto.annotate.domain.folder.service;

import auto.annotate.domain.folder.dto.request.UpdateTitleRequest;
import auto.annotate.domain.folder.dto.response.FolderDocumentResponse;
import auto.annotate.domain.folder.dto.response.FolderResponse;
import auto.annotate.domain.user.dto.AuthUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface FolderService {
    Page<FolderResponse> getFolders(AuthUser authUser, Pageable pageable);

    void modifyTitle(AuthUser authUser, UUID id, UpdateTitleRequest request);

    void deleteTitle(AuthUser authUser, UUID id);

    List<FolderDocumentResponse> getDocuments(AuthUser authUser, UUID folderId);
}
