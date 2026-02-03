package auto.annotate.domain.folder.service;

import auto.annotate.common.exception.BaseException;
import auto.annotate.common.exception.ExceptionEnum;
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

@Service
@Slf4j
@RequiredArgsConstructor
public class FolderServiceImpl implements FolderService{

    private final UserRepository userRepository;
    private final FolderRepository folderRepository;

    @Override
    public List<FolderResponse> getFolders(AuthUser authUser) {
        User user = getUser(authUser.getId());

        List<Folder> folders = folderRepository.findByUserId(user.getId());
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

    private User getUser(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BaseException(ExceptionEnum.USER_NOT_FOUND));
    }
}
