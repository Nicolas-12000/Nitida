package co.nitida.motorcycles.web;

import static org.springframework.web.servlet.function.RouterFunctions.route;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

/** Owner portal (phase 2, partial): any signed-in user, no workshop context. */
@Configuration
class OwnerRoutes {
    @Bean
    RouterFunction<ServerResponse> ownerEndpoints(OwnerHandler h) {
        return route()
                .path("/api/v1/owner", b -> b
                        .POST("/claims", h::claim)
                        .GET("/motorcycles", h::mine)
                        .POST("/motorcycles/{id}/transfers", h::startTransfer)
                        .POST("/transfers/{code}/accept", h::acceptTransfer))
                .build();
    }
}
