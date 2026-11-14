package com.farm.platform.ws;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** WebSocket 连接处理器：把浏览器连上来的 WebSocket 变成 NotificationService 在线表里的一条记录。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    // session attributes 里存用户 id 用的 key，握手拦截器写、本类读，两边靠它对齐
    public static final String ATTR_USER_ID = "userId";

    private final NotificationService notificationService;

    /** 连接建立后触发：从 session attributes 里取出握手时放进去的 userId，登记到在线表。 */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {   // 【浏览器收】
        // userId 是握手拦截器校验 token 通过后写进来的，类型是 Long
        Object uid = session.getAttributes().get(ATTR_USER_ID);
        if (uid instanceof Long userId) {
            notificationService.register(userId, session);
        } else {
            log.warn("非法连接（无 userId）：{}", session.getId());
            try {
                // 1003 NOT_ACCEPTABLE：告诉对方这类连接不被接受
                session.close(CloseStatus.NOT_ACCEPTABLE);
            } catch (Exception ignored) {
            }
        }
    }

    /** 连接关闭后触发：把这条连接从在线表里注销。 */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object uid = session.getAttributes().get(ATTR_USER_ID);
        if (uid instanceof Long userId) {
            // 注销时比对的还是这条连接本身，不会误删用户后来重连建立的新连接
            notificationService.unregister(userId, session);
        }
    }

    /** 客户端消息忽略（本项目为服务端单向推送）；空实现避免 WARN 日志 */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
    }
}
