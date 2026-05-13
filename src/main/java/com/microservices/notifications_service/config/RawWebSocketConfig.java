package com.microservices.notifications_service.config;

import com.microservices.notifications_service.websocket.RawWebSocketHandler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
public class RawWebSocketConfig implements WebSocketConfigurer {

    private final RawWebSocketHandler kitchenWebSocketHandler;
    private final RawWebSocketHandler orderWebSocketHandler;
    private final RawWebSocketHandler managerWebSocketHandler;

    public RawWebSocketConfig(
            @Qualifier("kitchenWebSocketHandler") RawWebSocketHandler kitchenWebSocketHandler,
            @Qualifier("orderWebSocketHandler") RawWebSocketHandler orderWebSocketHandler,
            @Qualifier("managerWebSocketHandler") RawWebSocketHandler managerWebSocketHandler) {
        this.kitchenWebSocketHandler = kitchenWebSocketHandler;
        this.orderWebSocketHandler = orderWebSocketHandler;
        this.managerWebSocketHandler = managerWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Raw WebSocket endpoints consumed by frontend hooks
        registry.addHandler(kitchenWebSocketHandler, "/ws/kitchen/*")
                .setAllowedOrigins("*");

        registry.addHandler(orderWebSocketHandler, "/ws/orders/*")
                .setAllowedOrigins("*");

        registry.addHandler(managerWebSocketHandler, "/ws/manager/*")
                .setAllowedOrigins("*");
    }
}
