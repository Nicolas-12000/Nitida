package co.nitida.catalog.web;

import static org.springframework.web.servlet.function.RouterFunctions.route;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

@Configuration
class CatalogRoutes {
    @Bean
    RouterFunction<ServerResponse> catalogEndpoints(CatalogHandler h) {
        return route()
                .path("/api/v1", b -> b
                        .GET("/catalog/products/{code}", h::scan)
                        .POST("/catalog/products", h::propose)
                        .POST("/serials/{id}/retire", h::retire))
                .build();
    }
}
