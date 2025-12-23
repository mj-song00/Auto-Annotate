package auto.annotate.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ExceptionEnum {
    DATA_INTEGRITY_VIOLATION(HttpStatus.BAD_REQUEST, "DATA_INTEGRITY_VIOLATION",
            "데이터 처리 중 문제가 발생했습니다. 요청을 확인하고 다시 시도해주세요"),
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "INVALID_INPUT_VALUE", "요청 값이 올바르지 않습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR",
            "서버에서 문제가 발생하였습니다."),


    DOCUMENT_NOT_FOUND(HttpStatus.BAD_REQUEST,"DOCUMENT_NOT_FOUND","첨부파일을 확인해 주세요"),
    FILE_READ_ERROR(HttpStatus.BAD_REQUEST, "FILE_READ_ERROR", "pdf를 읽는 중 오류가 발생하였습니다"),
    FILE_WRITE_ERROR(HttpStatus.BAD_REQUEST, "FILE_WRITE_ERROR","pdf를 수정하던중 오류가 발생하였습니다" ),
    FILE_NOT_FOUND(HttpStatus.BAD_REQUEST,"FILE_NOT_FOUND", "file을 찾지 못했습니다" ),
    FILE_SAVE_FAILED(HttpStatus.BAD_REQUEST," ILE_SAVE_FAILED","저장이 실패하였습니다." );


    private final HttpStatus status;
    private final String errorCode;
    private final String message;

    ExceptionEnum(HttpStatus status, String errorCode, String message) {
        this.status = status;
        this.errorCode = errorCode;
        this.message = message;
    }
}
