package co.nitida.identity.infrastructure;

import co.nitida.shared.web.ProblemResponses;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration
class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    /**
     * The session cookie, explicit on purpose: relying on the server.servlet.session.cookie.* mapping left it without
     * HttpOnly and SameSite (caught by OwaspSecurityTest). Secure stays configurable for plain-HTTP local runs.
     */
    @Bean
    DefaultCookieSerializer cookieSerializer(@Value("${server.servlet.session.cookie.secure:true}") boolean secure) {
        var serializer = new DefaultCookieSerializer();
        serializer.setCookieName("SESSION");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(secure);
        serializer.setSameSite("Lax");
        return serializer;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, WorkshopContextFilter workshopFilter) throws Exception {
        http
            // CSRF token in a cookie the frontend echoes in a header (section 10); plain handler so a SPA can read it.
            .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
            .addFilterAfter(workshopFilter, SecurityContextHolderFilter.class)
            .authorizeHttpRequests(a -> a
                    .requestMatchers("/actuator/health/**", "/actuator/health").permitAll()
                    .requestMatchers("/api/v1/public/**", "/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                    .requestMatchers("/api/v1/admin/**").hasRole("PLATFORM_ADMIN")
                    .requestMatchers("/api/**").authenticated()
                    .anyRequest().denyAll())
            .exceptionHandling(e -> e
                    .authenticationEntryPoint((req, res, ex) ->
                            ProblemResponses.write(res, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Inicie sesión para continuar"))
                    .accessDeniedHandler((req, res, ex) ->
                            ProblemResponses.write(res, HttpStatus.FORBIDDEN, "FORBIDDEN", "No tiene permiso para esta acción")))
            .logout(l -> l.logoutUrl("/api/v1/auth/logout")
                    .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
            .headers(h -> h
                    .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                    .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                    .contentSecurityPolicy(c -> c.policyDirectives("default-src 'none'; frame-ancestors 'none'")))
            .httpBasic(b -> b.disable())
            .formLogin(f -> f.disable())
            .requestCache(Customizer.withDefaults());
        return http.build();
    }
}
