package auto.annotate.domain.folder.controller;

import auto.annotate.common.annotation.Auth;
import auto.annotate.common.response.ApiResponse;
import auto.annotate.common.response.ApiResponseEnum;
import auto.annotate.domain.folder.dto.response.FolderResponse;
import auto.annotate.domain.folder.service.FolderService;
import auto.annotate.domain.user.dto.AuthUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/v1/folder")
public class FolderController {
    private final FolderService folderService;

    @GetMapping("")
    public ResponseEntity<ApiResponse<List<FolderResponse>>> getFolders(
            @Auth AuthUser authUser
    ){
        List<FolderResponse> result = folderService.getFolders(authUser);
        ApiResponse<List<FolderResponse>> response =
                ApiResponse.successWithData(result, ApiResponseEnum.GET_FOLDER_SUCCESS);
        return ResponseEntity.ok(response);
    }
}
