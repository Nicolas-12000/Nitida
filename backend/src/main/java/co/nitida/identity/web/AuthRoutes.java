package co.nitida.identity.web;

import static org.springframework.web.servlet.function.RouterFunctions.route;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

@Configuration
class AuthRoutes {
    @Bean
    RouterFunction<ServerResponse> identityEndpoints(AuthHandler auth, MemberHandler members) {
        return route()
                .path("/api/v1", b -> b
                        .POST("/auth/register", auth::register)
                        .POST("/auth/login", auth::login)
                        .GET("/auth/me", auth::me)
                        .POST("/invitations/{token}/accept", members::accept)
                        .POST("/workshops/{id}/invitations", members::invite)
                        .GET("/workshops/{id}/members", members::list)
                        .DELETE("/workshops/{id}/members/{userId}", members::remove))
                .build();
    }
}
