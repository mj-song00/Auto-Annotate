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
    FILE_SAVE_FAILED(HttpStatus.BAD_REQUEST, "FILE_SAVE_FAILED","저장이 실패하였습니다." ),

    USER_NOT_FOUND(HttpStatus.BAD_REQUEST,"USER_NOT_FOUND" , "유저를 확인할 수 없습니다." ),
    USER_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "HttpStatus.BAD_REQUEST","이미 존재하는 계정입니다."),
    UNAUTHORIZED_USER(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED_USER", "인증되지 않은 사용자입니다."),
    ALREADY_DELETED(HttpStatus.BAD_REQUEST, "ALREADY_DELETED", "탈퇴된 사용자입니다."),
    EMAIL_OR_PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "EMAIL_PASSWORD_MISMATCH", "이메일 혹은 비밀번호가 일치하지 않습니다."),
    PASSWORD_SAME_AS_OLD(HttpStatus.BAD_REQUEST, "PASSWORD_SAME_AS_OLD", "새 비밀번호가 기존 비밀번호와 동일합니다."),
    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "PASSWORD_MISMATCH", "비밀번호가 일치하지 않습니다."),
    NICKNAME_SAME_AS_OLD(HttpStatus.BAD_REQUEST, "NICKNAME_SAME_AS_OLD", "새로운 닉네임을 입력해주세요."),


    // 리프레시 토큰 관련
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "잘못된 리프레시 토큰입니다.");
    private final HttpStatus status;
    private final String errorCode;
    private final String message;

    ExceptionEnum(HttpStatus status, String errorCode, String message) {
        this.status = status;
        this.errorCode = errorCode;
        this.message = message;
    }
}
