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
import auto.annotate.domain.user.service.UserServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class FolderServiceImpl implements FolderService {

    private final FolderRepository folderRepository;
    private final DocumentRepository documentRepository;
    private final UserServiceImpl userService;

    @Override
    public Page<FolderResponse> getFolders(AuthUser authUser, Pageable pageable) {
        User user = userService.findByIdOrThrow(authUser.getId());

        Page<Folder> folders = folderRepository.findByUserAndDeletedAtIsNullOrderByCreatedAtDesc(user, pageable);

        return folders.map(folder -> FolderResponse.of(folder.getId(), folder.getName()));
    }

    @Override
    public void modifyTitle(AuthUser authUser, UUID id, UpdateTitleRequest request) {
        Folder folder = getFolder(authUser, id);
        folder.update(request.getName());
        folderRepository.save(folder);
    }

    @Override
    @Transactional
    public void deleteTitle(AuthUser authUser, UUID id) {
        Folder folder = getFolder(authUser, id);

        LocalDateTime now = LocalDateTime.now();

        folder.delete();
        folderRepository.save(folder);

        documentRepository.softDeleteByFolderId(id, now);
    }

    @Override
    public List<FolderDocumentResponse> getDocuments(AuthUser authUser, UUID folderId) {
        User user = userService.findByIdOrThrow(authUser.getId());

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

    @Transactional
    public void deleteExpiredFolders() {
        long t0 = System.nanoTime();
        LocalDateTime cutoff = LocalDateTime.now().minusDays(3);

        List<Folder> targets =
                folderRepository.findAllByCreatedAtBeforeAndDeletedAtIsNull(cutoff);

        for (Folder folder : targets) {
            folder.delete(); // soft delete
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        log.info("FOLDER_CLEANUP deleted={} elapsedMs={}", targets.size(), ms);
    }

    private Folder getFolder(AuthUser authUser, UUID id) {
        userService.findByIdOrThrow(authUser.getId());

        return folderRepository.findById(id)
                .orElseThrow(() -> new BaseException(ExceptionEnum.FOLDER_NOT_FOUND));
    }
}
