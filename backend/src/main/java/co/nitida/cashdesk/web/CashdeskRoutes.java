package co.nitida.cashdesk.web;

import static org.springframework.web.servlet.function.RouterFunctions.route;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

@Configuration
class CashdeskRoutes {
    @Bean
    RouterFunction<ServerResponse> cashdeskEndpoints(CashdeskHandler h) {
        return route()
                .path("/api/v1/cashdesk", b -> b
                        .GET("/counts/{day}", h::count)
                        .POST("/counts/{day}/close", h::close)
                        .POST("/counts/{day}/reopen", h::reopen)
                        .GET("/alerts", h::alerts)
                        .POST("/alerts/{id}/resolve", h::resolve))
                .build();
    }
}
