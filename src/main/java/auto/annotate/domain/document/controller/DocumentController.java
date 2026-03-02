package auto.annotate.domain.document.controller;


import auto.annotate.common.annotation.Auth;
import auto.annotate.common.exception.BaseException;
import auto.annotate.common.exception.ExceptionEnum;
import auto.annotate.common.response.ApiResponse;
import auto.annotate.common.response.ApiResponseEnum;
import auto.annotate.domain.document.repository.DocumentRepository;
import auto.annotate.domain.document.service.DocumentService;
import auto.annotate.domain.folder.dto.request.SaveFolderRequest;
import auto.annotate.domain.user.dto.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Tag(name = "Document", description = "PDF파일 업로드 및 분석결과 엑셀 다운로드 API")
@RestController
@RequiredArgsConstructor
@Slf4j
@SessionAttributes("uploadedFiles")
@RequestMapping("/api/v1/document")
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentRepository documentRepository;

    @Operation(summary = "PDF파일 업로드", description = "폴더 명과 파일을 업로드하여 분석을 진행합니다.")
    @PostMapping(value="/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Void>> fileUpLoad(
            @RequestPart("documents") List<MultipartFile> multipartFile,
            @Auth AuthUser authUser,
            @RequestPart("saveFolderRequest") SaveFolderRequest saveFolderRequest
            ){
        if (multipartFile.isEmpty()) {
            throw new BaseException(ExceptionEnum.DOCUMENT_NOT_FOUND);
        }

        documentService.save(multipartFile, authUser, saveFolderRequest);
        ApiResponse<Void> response = ApiResponse.successWithOutData(ApiResponseEnum.REGISTRATION_SUCCESS);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    /**
     * (현재 프론트에서 사용하지 않음)
     * 향후 하이라이트 PDF 확인 기능을 위해 유지 중.
     */
    @Operation(summary = "하이라이트 생성(현재 사용중이지 않음)", description = "조건에 맞는 하이라이트를 생성하여 PDF뷰어에 표시합니다. 현재는 사용되고 있지 않습니다.")
    @GetMapping("/{documentId}/highlighted")
    public ResponseEntity<Resource> getHighlightedDocument(
            @PathVariable UUID documentId,
            @RequestParam(name = "condition", defaultValue = "0") int condition,
            @RequestParam(name = "download", defaultValue = "false") boolean download
    ) {
        log.info("highlighted 요청 documentId={}, condition={}", documentId, condition);

        Resource resource = documentService.loadHighlightedFileAsResource(documentId, condition);

        if (!resource.exists()) {
            throw new BaseException(ExceptionEnum.DOCUMENT_NOT_FOUND);
        }

        String dispositionType = download ? "attachment" : "inline";

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        dispositionType + "; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }

    @Operation(summary = "분석된 파일 다운로드", description = "조건에 맞는 파일들을 다운로드 합니다.")
    @GetMapping("/{documentId}/excel")
    public ResponseEntity<Resource> downloadExcelByCondition(
            @PathVariable UUID documentId,
            @RequestParam int condition
    ) {
        Resource excel = documentService.downloadExcelByCondition(documentId, condition);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                ))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + excel.getFilename() + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(excel);
    }
}
