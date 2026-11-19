package co.nitida.intake.web;

import static org.springframework.web.servlet.function.RouterFunctions.route;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

@Configuration
class IntakeRoutes {
    @Bean
    RouterFunction<ServerResponse> intakeEndpoints(IntakeHandler h) {
        return route()
                .path("/api/v1/intakes", b -> b
                        .POST("", h::register)
                        .GET("", h::list)
                        .POST("/{id}/discard", h::discard))
                .PATCH("/api/v1/motorcycles/{id}/plate", h::changePlate)
                .build();
    }
}
