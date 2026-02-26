package auto.annotate.common.filter;

import auto.annotate.common.jwt.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.*;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class JwtFilter implements Filter {
    private final JwtUtil jwtUtil;
    private static final List<String> SWAGGER_WHITELIST = List.of(
            "/swagger-ui", "/swagger-ui.html", "/v3/api-docs",
            "/api-docs", "/v3/api-docs/swagger-config", "/swagger-ui/**"
    );


    public JwtFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String url = httpRequest.getRequestURI();

        // Swagger 경로는 JWT 검증 없이 통과
        if (SWAGGER_WHITELIST.stream().anyMatch(url::startsWith)) {
            chain.doFilter(request, response);
            return;
        }

        //가입, 로그인은 jwt 체크 불필요
        if (    !url.startsWith("/api/") ||
                url.startsWith("/swagger-ui") ||
                url.startsWith("/v3/api-docs") ||
                url.startsWith("/api/v1/users/auth/sign-in") ||
                url.startsWith("/api/v1/users/sign-up") ||
                url.startsWith("/api/v1/users/auth/refresh-token") || // refreshToken API
                url.startsWith("/api/v1/users/auth/logout")

        ) {
            chain.doFilter(request, response);
            return;
        }

        // 정적 리소스에 대한 요청은 JWT 검증을 생략
        if (url.startsWith("/images/") || url.startsWith("/css/") || url.startsWith("/js/") || url.startsWith("/webjars/")) {
            chain.doFilter(request, response);
            return;
        }


        // 헤더에서 Authorization 토큰을 가져옵니다.
        String authorizationHeader = httpRequest.getHeader("Authorization");

        // refreshToken 재발급 API인 경우 쿠키에서 refreshToken 꺼내기
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            if (httpRequest.getCookies() != null) {
                for (Cookie cookie : httpRequest.getCookies()) {
                    if ("refreshToken".equals(cookie.getName())) {
                        authorizationHeader = "Bearer " + cookie.getValue();
                        break;
                    }
                }
            }
        }

        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, "JWT 토큰이 필요합니다.");
            return;
        }

        // 헤더에서 JWT 추출
        String token = jwtUtil.getJwtFromHeader(httpRequest);
        try {
            Claims claims = jwtUtil.extractClaims(token);
            // 토큰을 검증하고, 유효한 경우 필터 체인을 타고 다음 필터로 이동합니다.
            if (jwtUtil.validateToken(token)) {
                // 필요하다면 요청에 사용자 정보를 추가할 수 있습니다.
                httpRequest.setAttribute("id", UUID.fromString(claims.getSubject()));
                httpRequest.setAttribute("username", (claims.get("username", String.class)));
                httpRequest.setAttribute("role", claims.get("role", String.class));

                chain.doFilter(request, response);
            } else {
                httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, "JWT 토큰이 유효하지 않습니다.");
            }
        } catch (Exception e) {
            httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, "JWT 토큰 검증 중 오류가 발생했습니다.");
        }
    }
}
