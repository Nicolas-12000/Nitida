package co.nitida.shared.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Swagger UI needs a relaxed CSP and public GET access; production and test keep these routes closed (OwaspSecurityTest).
 */
@Configuration
@ConditionalOnProperty(name = "nitida.openapi.enabled", havingValue = "true")
class OpenApiSecurityConfiguration {

    @Bean
    @Order(0)
    SecurityFilterChain openApiSecurity(HttpSecurity http) throws Exception {
        return http.securityMatcher(
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/openapi.yaml",
                        "/v3/api-docs",
                        "/v3/api-docs.yaml",
                        "/v3/api-docs/**")
                .authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .csrf(c -> c.disable())
                .headers(h -> h.contentSecurityPolicy(c -> c.policyDirectives(
                        "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; "
                                + "img-src 'self' data:; font-src 'self' data:; connect-src 'self'")))
                .build();
    }
}