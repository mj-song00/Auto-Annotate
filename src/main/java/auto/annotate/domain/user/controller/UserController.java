package auto.annotate.domain.user.controller;

import auto.annotate.common.annotation.Auth;
import auto.annotate.common.response.ApiResponse;
import auto.annotate.common.response.ApiResponseEnum;
import auto.annotate.domain.user.dto.AuthUser;
import auto.annotate.domain.user.dto.request.ChangeNickNameRequest;
import auto.annotate.domain.user.dto.request.ChangePasswordRequest;
import auto.annotate.domain.user.dto.request.SignupRequest;
import auto.annotate.domain.user.dto.response.UserProfileResponse;
import auto.annotate.domain.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "User", description = "인증 API")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {
    private final UserService userService;

    @Operation(summary = "회원가입", description = "회원가입을 진행합니다.")
    @PostMapping("/sign-up")
    public ResponseEntity<ApiResponse<Void>> signup(
            @Valid @RequestBody SignupRequest signupRequest) {
        userService.createUser(signupRequest);
        ApiResponse<Void> response =
                ApiResponse.successWithOutData(ApiResponseEnum.SIGNUP_SUCCESS);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }


    @Operation(summary = "내 프로필 조회", description = "본인의 프로필을 조회합니다.")
    @GetMapping("/me/profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getUserProfile(
            @Auth AuthUser authUser) {
        UserProfileResponse profile = userService.getUserProfile(authUser);
        ApiResponse<UserProfileResponse> response =
                ApiResponse.successWithData(profile, ApiResponseEnum.PROFILE_RETRIEVED_SUCCESS);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "비밀번호 변경", description = "본인의 비밀번호를 변경합니다.")
    @PatchMapping("/me/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Auth AuthUser authUser,
            @Valid @RequestBody ChangePasswordRequest changePasswordRequest) {
        userService.changePassword(
                authUser,
                changePasswordRequest.getOldPassword(),
                changePasswordRequest.getNewPassword());
        ApiResponse<Void> response =
                ApiResponse.successWithOutData(ApiResponseEnum.PASSWORD_CHANGED_SUCCESS);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "닉네임 변경", description = "본인의 닉네임을 변경합니다.")
    @PatchMapping("/me/nickname")
    public ResponseEntity<ApiResponse<Void>> changeNickName(
            @Auth AuthUser authUser,
            @Valid @RequestBody ChangeNickNameRequest changeNickNameRequest) {
        userService.changeNickName(
                authUser,
                changeNickNameRequest.getNewNickName());
        ApiResponse<Void> response = ApiResponse.successWithOutData(ApiResponseEnum.NICKNAME_CHANGED_SUCCESS);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "회원 탈퇴", description = "본인의 계정을 탈퇴합니다.")
    @PatchMapping("/me/delete")
    public ResponseEntity<ApiResponse<Void>> deleteUser(
            @Auth AuthUser authenticatedUser,
            @CookieValue(value = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response) {
        userService.deleteUser(authenticatedUser, refreshToken, response);
        ApiResponse<Void> responseBody =
                ApiResponse.successWithOutData(ApiResponseEnum.USER_DELETED_SUCCESS);
        return ResponseEntity.ok(responseBody);
    }
}
