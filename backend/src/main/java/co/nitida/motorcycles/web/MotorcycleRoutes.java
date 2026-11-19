package co.nitida.motorcycles.web;

import static org.springframework.web.servlet.function.RouterFunctions.route;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

@Configuration
class MotorcycleRoutes {
    @Bean
    RouterFunction<ServerResponse> motorcycleEndpoints(MotorcycleHandler h) {
        return route()
                .path("/api/v1", b -> b
                        .GET("/motorcycles/lookup", h::lookup)
                        .POST("/customers", h::createCustomer)
                        .GET("/customers", h::searchCustomers)
                        .PATCH("/customers/{id}", h::updateCustomer)
                        .POST("/customers/{id}/erasure", h::eraseCustomer))
                .build();
    }
}
