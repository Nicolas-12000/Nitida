package co.nitida.workshops.web;

import static org.springframework.web.servlet.function.RouterFunctions.route;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

@Configuration
class WorkshopRoutes {
    @Bean
    RouterFunction<ServerResponse> workshopEndpoints(WorkshopHandler h) {
        return route()
                .path("/api/v1", b -> b
                        .POST("/workshops", h::register)
                        .GET("/workshops/mine", h::mine)
                        .GET("/workshops/{id}", h::detail)
                        .PATCH("/workshops/{id}/settings", h::updateSettings)
                        .GET("/admin/workshops", h::adminList)
                        .POST("/admin/workshops/{id}/status", h::changeStatus)
                        .GET("/public/keys", h::publicKeys))
                .build();
    }
}
