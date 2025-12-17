package auto.annotate.domain.document.controller;


import auto.annotate.common.exception.BaseException;
import auto.annotate.common.exception.ExceptionEnum;
import auto.annotate.common.response.ApiResponse;
import auto.annotate.common.response.ApiResponseEnum;
import auto.annotate.domain.document.repository.DocumentRepository;
import auto.annotate.domain.document.service.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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


    @GetMapping("/{documentId}/highlihted")
    public ResponseEntity<Resource> getHighlightedDocument(@PathVariable UUID documentId){
        Resource resource = documentService.loadHighlightedFileAsResource(documentId);

        String contentType = "application/pdf";
        String headerValue = "inline; filename=\"" + resource.getFilename() + "\"";

        if (!resource.exists()) {
            throw new RuntimeException("PDF 파일이 존재하지 않습니다: " + documentId);
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, headerValue)
                .body(resource);
    }

    @GetMapping
    public List<Map<String, Object>> getAllDocumentIds() {
        return documentRepository.findAll()
                .stream()
                .map(doc -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", doc.getId());
                    map.put("originalFileName", doc.getOriginalFileName());
                    return map;
                })
                .toList();
    }
}
