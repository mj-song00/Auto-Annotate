package auto.annotate.domain.file.controller;


import auto.annotate.common.exception.BaseException;
import auto.annotate.common.exception.ExceptionEnum;
import auto.annotate.common.response.ApiResponse;
import auto.annotate.common.response.ApiResponseEnum;
import auto.annotate.domain.file.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@Slf4j
@SessionAttributes("uploadedFiles")
public class FileController {

    private final FileService fileService;


    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<Void>> fileUpLoad(
            @RequestParam("files")MultipartFile multipartFile
            ){
        if (multipartFile.isEmpty()) {
            throw new BaseException(ExceptionEnum.FILE_NOT_FOUND);
        }

        fileService.save(multipartFile);
        ApiResponse<Void> response = ApiResponse.successWithOutData(ApiResponseEnum.REGISTRATION_SUCCESS);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
