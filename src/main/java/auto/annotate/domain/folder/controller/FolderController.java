package auto.annotate.domain.folder.controller;

import auto.annotate.common.annotation.Auth;
import auto.annotate.common.response.ApiResponse;
import auto.annotate.common.response.ApiResponseEnum;
import auto.annotate.domain.folder.dto.request.UpdateTitleRequest;
import auto.annotate.domain.folder.dto.response.FolderResponse;
import auto.annotate.domain.folder.service.FolderService;
import auto.annotate.domain.user.dto.AuthUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

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
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    /**
     * 폴더 제목을 수정합니다. 필요한 값으로는
     * @param authUser (로그인 확인용)
     * @param id (폴더 id)
     * @param request 를 받습니다.
     * @return void 입니다.
     */
    @PutMapping("/{folderId}")
    public ResponseEntity<ApiResponse<Void>>  modfiyTitle(
            @Auth AuthUser authUser,
            @PathVariable UUID id,
            @RequestBody UpdateTitleRequest request

    ){
        folderService.modifyTitle(authUser, id, request);
        ApiResponse<Void> response = ApiResponse.successWithOutData(
                ApiResponseEnum.FOLDER_UPDATE_SUCCESS);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
