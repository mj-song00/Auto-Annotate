package auto.annotate.domain.folder.controller;

import auto.annotate.common.annotation.Auth;
import auto.annotate.common.response.ApiResponse;
import auto.annotate.common.response.ApiResponseEnum;
import auto.annotate.domain.folder.dto.request.UpdateTitleRequest;
import auto.annotate.domain.folder.dto.response.FolderDocumentResponse;
import auto.annotate.domain.folder.dto.response.FolderPageResponse;
import auto.annotate.domain.folder.dto.response.FolderResponse;
import auto.annotate.domain.folder.service.FolderService;
import auto.annotate.domain.user.dto.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Folder", description = "PDF파일을 담은 폴더를 생성합니다.")
@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/v1/folder")
public class FolderController {
    private final FolderService folderService;

    @Operation(summary = "폴더조회", description = "모든 폴더를 조회합니다.")
    @GetMapping("")
    public ResponseEntity<ApiResponse<FolderPageResponse>> getFolders(
            @Auth AuthUser authUser,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ){
        Pageable pageable = PageRequest.of(page - 1, size);

        Page<FolderResponse> result = folderService.getFolders(authUser, pageable);

        FolderPageResponse response = new FolderPageResponse(
                result.getContent(),
                result.getNumber(),
                result.getTotalElements(),
                result.getTotalPages()
        );


        ApiResponse<FolderPageResponse> apiResponse =
                ApiResponse.successWithData(response, ApiResponseEnum.GET_FOLDER_SUCCESS);
        return ResponseEntity.status(HttpStatus.OK).body(apiResponse);
    }

    /**
     * 폴더 제목을 수정합니다. 필요한 값으로는
     * @param authUser (로그인 확인용)
     * @param folderId (폴더 id)
     * @param request 를 받습니다.
     * @return void 입니다.
     */
    @Operation(summary = "폴더 수정", description = "폴더 이름을 수정합니다.")
    @PutMapping("/{folderId}")
    public ResponseEntity<ApiResponse<Void>>  modfiyTitle(
            @Auth AuthUser authUser,
            @PathVariable UUID folderId,
            @RequestBody UpdateTitleRequest request

    ){
        folderService.modifyTitle(authUser, folderId, request);
        ApiResponse<Void> response = ApiResponse.successWithOutData(
                ApiResponseEnum.FOLDER_UPDATE_SUCCESS);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    /**
     * 사용자가 임의로 폴더를 삭제합니다.
     * 폴더는 3일뒤 자정에 자동으로 삭제됩니다.
     */
    @Operation(summary = "폴더 삭제", description = "폴더를 임의로 삭제합니다.")
    @DeleteMapping("/{folderId}")
    public ResponseEntity<ApiResponse<Void>> deleteTitle(
            @Auth AuthUser authUser,
            @PathVariable UUID folderId
    ){
        folderService.deleteTitle(authUser, folderId);
        ApiResponse<Void> response = ApiResponse.successWithOutData(
                ApiResponseEnum.FOLDER_DELETE_SUCCESS);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @Operation(summary = "모든 documents 불러오기", description = "각 폴더에 속해있는 documentId들을 요청합니다.")
    @GetMapping("/{folderId}/documents")
    public ResponseEntity<ApiResponse<List<FolderDocumentResponse>>> getDocuments(
            @Auth AuthUser authUser,
            @PathVariable UUID folderId
    ){
        List<FolderDocumentResponse> result = folderService.getDocuments(authUser, folderId);
        ApiResponse<List<FolderDocumentResponse>> response =
                ApiResponse.successWithData(result, ApiResponseEnum.GET_FOLDER_SUCCESS);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
