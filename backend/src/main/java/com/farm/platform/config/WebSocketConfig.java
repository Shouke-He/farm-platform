package com.farm.platform.config;

import com.farm.platform.util.JwtUtil;
import com.farm.platform.ws.NotificationWebSocketHandler;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/** WebSocket 配置：注册 /ws 端点，握手时用 query 中的 token 校验 JWT 并携带 userId */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final JwtUtil jwtUtil;
    private final NotificationWebSocketHandler notificationWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(notificationWebSocketHandler, "/ws")
                .addInterceptors(tokenInterceptor())
                .setAllowedOriginPatterns("*");
    }

    private HandshakeInterceptor tokenInterceptor() {
        return new HandshakeInterceptor() {
            @Override
            public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                           WebSocketHandler wsHandler, Map<String, Object> attributes) {
                String token = request instanceof ServletServerHttpRequest sshr
                        ? sshr.getServletRequest().getParameter("token") : null;
                // 没带 token 的连接不放行：连上了也不知道该把消息推给谁
                if (token == null || token.isBlank()) {
                    return false;
                }
                try {
                    Claims claims = jwtUtil.parse(token);
                    attributes.put(NotificationWebSocketHandler.ATTR_USER_ID, Long.valueOf(claims.getSubject()));
                    return true;
                } catch (Exception e) {
                    // token 过期、签名不符、格式不对全部拒绝握手（握手阶段没法回 HTTP 错误码，前端只会看到连接失败）
                    return false;
                }
            }

            @Override
            public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                       WebSocketHandler wsHandler, Exception exception) {
            }
        };
    }
}
