package auto.annotate.domain.document.controller;


import auto.annotate.common.exception.BaseException;
import auto.annotate.common.exception.ExceptionEnum;
import auto.annotate.common.response.ApiResponse;
import auto.annotate.common.response.ApiResponseEnum;
import auto.annotate.domain.document.repository.DocumentRepository;
import auto.annotate.domain.document.service.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Slf4j
@SessionAttributes("uploadedFiles")
@RequestMapping("/document")
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentRepository documentRepository;

    @PostMapping(value="/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Void>> fileUpLoad(
            @RequestPart("documents") List<MultipartFile> multipartFile
            ){
        if (multipartFile.isEmpty()) {
            throw new BaseException(ExceptionEnum.DOCUMENT_NOT_FOUND);
        }

        documentService.save(multipartFile);
        ApiResponse<Void> response = ApiResponse.successWithOutData(ApiResponseEnum.REGISTRATION_SUCCESS);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }


    @GetMapping("/{documentId}/highlighted")
    public ResponseEntity<Resource> getHighlightedDocument(
            @PathVariable UUID documentId,
            @RequestParam(name = "condition", defaultValue = "0") int condition,
            @RequestParam(name = "download", defaultValue = "false") boolean download
    ) {
        log.info("🔥 highlighted 요청 documentId={}, condition={}", documentId, condition);

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

    @GetMapping
    public List<Map<String, Object>> getAllDocumentIds() {
        return documentRepository.findAll()
                .stream()
                .map(doc -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", doc.getId());
                    map.put("filePath", doc.getFileUrl());
                    map.put("originalFileName", doc.getOriginalFileName());
                    return map;
                })
                .toList();
    }

    @GetMapping("/bundles/{bundleKey}/highlighted")
    public ResponseEntity<Resource> getHighlightedByBundle(
            @PathVariable String bundleKey,
            @RequestParam int condition
    ) {
        Resource resource = documentService.loadHighlightedByBundle(bundleKey, condition);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"highlighted.pdf\"")
                .body(resource);
    }
}
