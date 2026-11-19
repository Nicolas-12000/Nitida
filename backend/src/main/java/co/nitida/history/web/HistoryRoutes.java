package co.nitida.history.web;

import static org.springframework.web.servlet.function.RouterFunctions.route;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

@Configuration
class HistoryRoutes {
    @Bean
    RouterFunction<ServerResponse> historyEndpoints(HistoryHandler h) {
        return route()
                .path("/api/v1", b -> b
                        .GET("/motorcycles/{id}/history", h::view)
                        .POST("/motorcycles/{id}/history/amendments", h::amend)
                        .POST("/motorcycles/{id}/history/odometer-changes", h::odometerChange)
                        .POST("/motorcycles/{id}/links", h::createLink)
                        .DELETE("/links/{id}", h::revokeLink)
                        .GET("/public/v/{token}", h::viewPublic))
                .build();
    }
}
