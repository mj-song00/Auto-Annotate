package auto.annotate.domain.user.service;

import auto.annotate.common.config.PasswordEncoder;
import auto.annotate.common.exception.BaseException;
import auto.annotate.common.exception.ExceptionEnum;
import auto.annotate.common.jwt.JwtUtil;
import auto.annotate.common.jwt.TokenHashUtil;
import auto.annotate.domain.user.dto.request.LoginRequest;
import auto.annotate.domain.user.entity.User;
import auto.annotate.domain.user.reposotiry.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Value("${jwt.refresh.pepper}")
    private String refreshPepper;

    public String login(LoginRequest loginRequest) {
        User user = findByEmail(loginRequest.getEmail());
        validateUserNotDeleted(user);
        authenticateUser(user, loginRequest.getPassword());
        return generateAccessToken(user);
    }

    // AccessToken 재발급 (Refresh 회전 + RDS 해시 비교)
    @Transactional
    public String refreshAccessToken(String refreshToken, HttpServletResponse response) {
        User user = validateRefreshToken(refreshToken);

        String newRefreshToken = generateRefreshToken(user.getEmail());
        saveRefreshToken(user.getEmail(), newRefreshToken);
        setRefreshTokenCookie(response, newRefreshToken);

        return generateAccessToken(user);
    }

    @Transactional
    public void logout(String refreshToken, HttpServletResponse response) {
        try {
            if (refreshToken != null && jwtUtil.isTokenValid(refreshToken)) {
                Claims claims = jwtUtil.extractClaims(refreshToken);
                UUID userId = UUID.fromString(claims.getSubject());

                User user = userRepository.findById(userId)
                        .orElseThrow(() -> new BaseException(ExceptionEnum.USER_NOT_FOUND));

                user.clearRefreshToken();
            }
        } catch (Exception e) {
            log.warn("Logout refresh token cleanup failed", e);
        }

        // 쿠키 삭제
        ResponseCookie deleteCookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(true)
                .path("/")
                .sameSite("Strict")
                .maxAge(0)
                .build();

        response.setHeader(HttpHeaders.SET_COOKIE, deleteCookie.toString());
    }

    // 리프레시 토큰 쿠키 설정
    public void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        ResponseCookie cookie = ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge((long) 7 * 24 * 60 * 60)
                .sameSite("Strict")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    // 이메일로 사용자 조회
    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new BaseException(ExceptionEnum.USER_NOT_FOUND));
    }

    // 사용자 탈퇴 여부 확인
    public void validateUserNotDeleted(User user) {
        if (user.getDeletedAt() != null) {
            throw new BaseException(ExceptionEnum.ALREADY_DELETED);
        }
    }

    // 비밀번호 인증
    public void authenticateUser(User user, String rawPassword) {
        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new BaseException(ExceptionEnum.EMAIL_OR_PASSWORD_MISMATCH);
        }
    }

    // 액세스 토큰 생성
    private String generateAccessToken(User user) {
        return jwtUtil.createToken(user.getId(), user.getUserRole());
    }

    // 리프레시 토큰 생성
    public String generateRefreshToken(String email) {
        User user = findByEmail(email);
        return jwtUtil.createRefreshToken(user.getId());
    }

    // RDS에 refresh 해시 저장
    @Transactional
    public void saveRefreshToken(String email, String refreshToken) {
        User user = findByEmail(email);

        String hash = TokenHashUtil.sha256Base64(refreshToken, refreshPepper);

        Date exp = jwtUtil.extractClaims(refreshToken).getExpiration();
        LocalDateTime expiresAt =
                exp.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();

        user.updateRefreshToken(hash, expiresAt);
    }

    // RDS 해시 비교로 refresh 검증
    private User validateRefreshToken(String refreshToken) {
        if (refreshToken == null || !jwtUtil.isTokenValid(refreshToken)) {
            throw new BaseException(ExceptionEnum.INVALID_REFRESH_TOKEN);
        }

        Claims claims = jwtUtil.extractClaims(refreshToken);
        UUID userId = UUID.fromString(claims.getSubject());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(ExceptionEnum.USER_NOT_FOUND));

        String storedHash = user.getRefreshTokenHash();
        if (storedHash == null) {
            throw new BaseException(ExceptionEnum.INVALID_REFRESH_TOKEN);
        }

        String incomingHash = TokenHashUtil.sha256Base64(refreshToken, refreshPepper);
        if (!incomingHash.equals(storedHash)) {
            throw new BaseException(ExceptionEnum.INVALID_REFRESH_TOKEN);
        }

        LocalDateTime expiresAt = user.getRefreshTokenExpiresAt();
        if (expiresAt != null && expiresAt.isBefore(LocalDateTime.now())) {
            throw new BaseException(ExceptionEnum.INVALID_REFRESH_TOKEN);
        }

        return user;
    }
}