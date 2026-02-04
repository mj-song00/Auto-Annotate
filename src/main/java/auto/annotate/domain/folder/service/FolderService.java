package auto.annotate.domain.folder.service;

import auto.annotate.domain.folder.dto.request.UpdateTitleRequest;
import auto.annotate.domain.folder.dto.response.FolderResponse;
import auto.annotate.domain.user.dto.AuthUser;

import java.util.List;
import java.util.UUID;

public interface FolderService {
    List<FolderResponse> getFolders(AuthUser authUser);

    void modifyTitle(AuthUser authUser, UUID id, UpdateTitleRequest request);
}
