package auto.annotate.domain.folder.service;

import auto.annotate.domain.folder.dto.response.FolderResponse;
import auto.annotate.domain.user.dto.AuthUser;

import java.util.List;

public interface FolderService {
    List<FolderResponse> getFolders(AuthUser authUser);
}
