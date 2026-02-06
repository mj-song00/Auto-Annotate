package auto.annotate.domain.folder.service;

import auto.annotate.common.exception.BaseException;
import auto.annotate.common.exception.ExceptionEnum;
import auto.annotate.domain.document.entity.Document;
import auto.annotate.domain.document.repository.DocumentRepository;
import auto.annotate.domain.folder.dto.request.UpdateTitleRequest;
import auto.annotate.domain.folder.dto.response.FolderDocumentResponse;
import auto.annotate.domain.folder.dto.response.FolderResponse;
import auto.annotate.domain.folder.entity.Folder;
import auto.annotate.domain.folder.repository.FolderRepository;
import auto.annotate.domain.user.dto.AuthUser;
import auto.annotate.domain.user.entity.User;
import auto.annotate.domain.user.reposotiry.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class FolderServiceImpl implements FolderService {

    private final UserRepository userRepository;
    private final FolderRepository folderRepository;
    private final DocumentRepository documentRepository;

    @Override
    public List<FolderResponse> getFolders(AuthUser authUser) {
        User user = getUser(authUser.getId());

        List<Folder> folders = folderRepository.findByUserAndDeletedAtIsNull(user);
        List<FolderResponse> responses = new ArrayList<>();

        for (Folder folder : folders) {
            responses.add(
                    FolderResponse.of(
                            folder.getId(),
                            folder.getName()
                    )
            );
        }
        return responses;
    }

    @Override
    public void modifyTitle(AuthUser authUser, UUID id, UpdateTitleRequest request) {
        Folder folder = getFolder(authUser, id);
        folder.update(request.getName());
        folderRepository.save(folder);
    }

    @Override
    public void deleteTitle(AuthUser authUser, UUID id) {
        Folder folder = getFolder(authUser, id);
        folder.delete();
        folderRepository.save(folder);
    }

    @Override
    public List<FolderDocumentResponse> getDocuments(AuthUser authUser, UUID folderId) {
        User user = getUser(authUser.getId());

        Folder folder = folderRepository.findByIdAndDeletedAtIsNull(folderId)
                .orElseThrow(() -> new BaseException(ExceptionEnum.FOLDER_NOT_FOUND));


        if (!folder.getUser().getId().equals(user.getId())) {
            throw new BaseException(ExceptionEnum.USER_NOT_FOUND);
        }

        List<Document> documents = documentRepository.findALLByFolderId(folderId);
        return documents.stream()
                .map(FolderDocumentResponse::of)
                .collect(Collectors.toList());
    }

    private User getUser(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BaseException(ExceptionEnum.USER_NOT_FOUND));
    }

    private Folder getFolder(AuthUser authUser, UUID id) {
        getUser(authUser.getId());

        return folderRepository.findById(id)
                .orElseThrow(() -> new BaseException(ExceptionEnum.FOLDER_NOT_FOUND));
    }
}
