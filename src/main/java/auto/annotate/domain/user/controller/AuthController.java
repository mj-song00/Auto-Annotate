package auto.annotate.domain.user.controller;

import auto.annotate.domain.user.dto.request.LoginRequest;
import auto.annotate.domain.user.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "인가 API")
@RestController
@RequestMapping("/api/v1/users/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {
    private final AuthService authService;

    // 일반 로그인
    @Operation(summary = "로그인", description = "일반 사용자의 로그인을 진행합니다.")
    @PostMapping("/sign-in")
    public ResponseEntity<String> login(@Valid @RequestBody LoginRequest loginRequest, HttpServletResponse response) {
        // 로그인 후 토큰 발급
        String accessToken = authService.login(loginRequest);
        String refreshToken = authService.generateRefreshToken(loginRequest.getEmail());

        // 액세스 토큰 redis 저장
        authService.saveRefreshToken(loginRequest.getEmail(), refreshToken);

        // 리프레시 토큰을 HTTP-Only 쿠키로 설정
        authService.setRefreshTokenCookie(response, refreshToken);

        return ResponseEntity.status(HttpStatus.CREATED).body(accessToken);
    }

    // 리프레시 토큰으로 액세스 토큰 재발급
    @Operation(summary = "토큰 재발급", description = "리프레시 토큰을 사용하여 새로운 액세스 토큰을 발급합니다.")
    @PostMapping("/refresh-token")
    public ResponseEntity<String> refreshToken(
            @CookieValue(value = "refreshToken", required = false) String refreshToken, HttpServletResponse response) {
        String newAccessToken = authService.refreshAccessToken(refreshToken, response);
        return ResponseEntity.ok(newAccessToken);
    }

    // 로그아웃
    @Operation(summary = "로그아웃", description = "사용자가 로그아웃합니다.")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(value = "refreshToken", required = false) String refreshToken, HttpServletResponse response) {
        authService.logout(refreshToken, response);
        return ResponseEntity.ok().build();
    }
}
