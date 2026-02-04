package auto.annotate.common.response;

import lombok.Getter;

@Getter
public enum ApiResponseEnum {
    REGISTRATION_SUCCESS("파일 저장 완료"),
    SIGNUP_SUCCESS("회원가입 완료"),
    PROFILE_RETRIEVED_SUCCESS("프로필 조회가 완료"),
    PASSWORD_CHANGED_SUCCESS("비밀번호 변경 완료"),
    NICKNAME_CHANGED_SUCCESS("닉네임 변경 완료"),
    USER_DELETED_SUCCESS("회원 탈퇴가 완료"),
    GET_FOLDER_SUCCESS("폴더 조회 완료"),
    FOLDER_UPDATE_SUCCESS("폴더 수정 완료");

    private final String message;

    ApiResponseEnum(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
