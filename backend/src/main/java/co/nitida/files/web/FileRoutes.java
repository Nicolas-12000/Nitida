package co.nitida.files.web;

import static org.springframework.web.servlet.function.RouterFunctions.route;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

@Configuration
class FileRoutes {
    @Bean
    RouterFunction<ServerResponse> fileEndpoints(FileHandler h) {
        return route()
                .path("/api/v1/files", b -> b
                        .POST("", h::upload)
                        .GET("/{id}", h::download))
                .build();
    }
}
