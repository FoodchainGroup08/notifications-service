package com.microservices.notifications_service.websocket;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebSocketHandlerRegistry {

    @Bean
    public RawWebSocketHandler kitchenWebSocketHandler() {
        return new RawWebSocketHandler("kitchen");
    }

    @Bean
    public RawWebSocketHandler orderWebSocketHandler() {
        return new RawWebSocketHandler("order");
    }

    @Bean
    public RawWebSocketHandler managerWebSocketHandler() {
        return new RawWebSocketHandler("manager");
    }
}
