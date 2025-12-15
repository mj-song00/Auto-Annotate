package auto.annotate.common.response;

import lombok.Getter;

@Getter
public enum ApiResponseEnum {
    REGISTRATION_SUCCESS("파일 저장 완료");

    private final String message;

    ApiResponseEnum(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
