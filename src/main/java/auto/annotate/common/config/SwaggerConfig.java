package auto.annotate.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {
    @Bean
    public OpenAPI customOpenAPI() {

        SecurityScheme securityScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .in(SecurityScheme.In.HEADER)
                .name("Authorization");

        SecurityRequirement securityRequirement = new SecurityRequirement().addList("BearerAuth");

        return new OpenAPI()
                .info(new Info()
                        .title("Gathering API")
                        .version("0.2")
                        .description("""
                                ### 인증 및 권한 관리
                                - **JWT를 사용한 인증/인가 시스템**
                                - 액세스 토큰은 `Authorization` 헤더에 `Bearer <JWT>` 형식으로 포함하여 전송.
                                - 리프레시 토큰은 **HTTP-Only 쿠키**에 저장되며, 액세스 토큰 만료 시 재발급에 사용.
                                
                                ### 인증 흐름
                                1. **로그인**
                                   - `/api/auth/login`에서 이메일과 비밀번호로 로그인.
                                   - 응답으로 액세스 토큰(JWT)과 리프레시 토큰(HTTP-Only 쿠키)을 발급받습니다.
                                
                                2. **액세스 토큰 만료 시 재발급**
                                   - `/api/auth/refresh-token` 엔드포인트를 호출.
                                   - 리프레시 토큰은 자동으로 쿠키에서 읽혀 서버에서 검증.
                                   - 새 액세스 토큰을 발급받아 클라이언트에서 사용합니다.
                                
                                3. **로그아웃**
                                   - `/api/auth/logout` 호출 시 리프레시 토큰은 블랙리스트 처리되며 쿠키에서 삭제됩니다.
                                
                                ### 주의 사항
                                - **액세스 토큰 만료 시간**: 30분 (JWT_EXPIRATION_TIME)
                                - **리프레시 토큰 만료 시간**: 24시간 (JWT_REFRESH_EXPIRATION_TIME)
                                - 클라이언트는 액세스 토큰 만료 시 자동으로 `/refresh-token`을 호출하여 갱신해야 합니다.
                                
                                ### 테스트 방법
                                - 로그인 후 발급받은 액세스 토큰을 스웨거의 `Authorize` 버튼을 통해 설정.
                                - 리프레시 토큰 관련 기능은 쿠키 기반이므로 스웨거에서 직접 테스트할 수 없습니다.
                                """)
                        .contact(new Contact()
                                .name("Funding Market Repository")
                                .url("https://github.com/mj-song00/Auto-Annotate.git ")
                        )
                )
                .addSecurityItem(securityRequirement)
                .schemaRequirement("BearerAuth", securityScheme);
    }
}
