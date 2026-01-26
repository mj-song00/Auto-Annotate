package auto.annotate.common.filter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FilterConfig {
    @Bean
    public FilterRegistrationBean<JwtFilter> JwtFilter(@Autowired JwtFilter jwtAuthenticationFilter) {
        FilterRegistrationBean<JwtFilter> registrationBean = new FilterRegistrationBean<>();

        registrationBean.setFilter(jwtAuthenticationFilter);
        registrationBean.addUrlPatterns("/*"); // 필터가 적용될 URL 패턴 설정

        return registrationBean;
    }
}

