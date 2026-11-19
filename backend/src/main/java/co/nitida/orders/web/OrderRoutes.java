package co.nitida.orders.web;

import static org.springframework.web.servlet.function.RouterFunctions.route;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

@Configuration
class OrderRoutes {
    @Bean
    RouterFunction<ServerResponse> orderEndpoints(OrderHandler h) {
        return route()
                .path("/api/v1", b -> b
                        .POST("/orders", h::open)
                        .GET("/orders", h::list)
                        .GET("/orders/{id}", h::detail)
                        .PATCH("/orders/{id}", h::edit)
                        .POST("/orders/{id}/transitions", h::changeStatus)
                        .POST("/orders/{id}/parts", h::addPart)
                        .DELETE("/orders/{id}/parts/{partId}", h::removePart)
                        .POST("/orders/{id}/parts/{partId}/serial", h::registerSerial)
                        .POST("/orders/{id}/parts/{partId}/authenticity", h::authenticity)
                        .POST("/orders/{id}/photos", h::addPhoto)
                        .POST("/orders/{id}/payments", h::registerPayment)
                        .POST("/payments/{id}/void", h::voidPayment)
                        .POST("/orders/{id}/delivery", h::deliver))
                .build();
    }
}
