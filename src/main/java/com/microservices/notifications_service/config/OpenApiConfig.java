package com.microservices.notifications_service.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI notificationServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Notification Service API")
                        .description("Real-time customer and staff notifications via WebSocket. " +
                                "Consumes order events from Kafka and pushes updates to connected clients.")
                        .version("1.0.0"))
                .servers(List.of(
                        new Server().url("http://54.235.78.18:8080/api").description("Production (Live API Gateway)"),
                        new Server().url("http://localhost:8080/api").description("API Gateway (local dev)"),
                        new Server().url("http://localhost:8087/api").description("Direct (local dev)")))
                .components(new Components()
                        .addSecuritySchemes("Bearer Authentication",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }
}
