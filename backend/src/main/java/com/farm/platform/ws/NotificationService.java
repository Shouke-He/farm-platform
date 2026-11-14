package com.farm.platform.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 推送站：维护在线连接表 + 按 userId 定向推送（人不在线就静默丢弃、不落库；
 *  到点提醒另有「上线补推」兜底，见 TaskDueNotifyScheduler.onUserOnline） */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final ObjectMapper objectMapper;
    /** 上线事件的发布者：让「错过也能补一次」这类逻辑挂到连接建立这个时机上 */
    private final ApplicationEventPublisher publisher;

    /** 在线会话表：key 是用户 id，value 是他当前的连接（一个用户只保留最新一条） */
    private final ConcurrentHashMap<Long, WebSocketSession> sessions = new ConcurrentHashMap<>();

    /** 登记新连接；同一用户已有连接时把旧的关掉 */
    public void register(Long userId, WebSocketSession session) {
        // put 返回被顶替下来的旧连接（原本没值就是 null）
        WebSocketSession old = sessions.put(userId, session);
        if (old != null && old.isOpen() && !old.getId().equals(session.getId())) {
            try {
                old.close();
            } catch (Exception ignored) {
            }
        }
        log.info("用户 {} 上线，当前在线 {}", userId, sessions.size());
        // 会话已经登记进在线表，此刻发事件：监听方（到点提醒）直接就能把消息推出去
        publisher.publishEvent(new UserOnlineEvent(userId));   // 【上线补推】
    }

    /** 注销连接；用「key+value 一起比对」的删法，避免旧连接断开时误删新连接 */
    public void unregister(Long userId, WebSocketSession session) {
        sessions.remove(userId, session);
        log.info("用户 {} 下线，当前在线 {}", userId, sessions.size());
    }

    /** 推一条消息：{type,title,content,time} 四个字段与前端约定好，转 JSON 文本帧发出 */
    public void sendToUser(Long userId, String type, String title, String content) {
        WebSocketSession session = sessions.get(userId);   // 【实时推】
        if (session == null || !session.isOpen()) {   // 【追问·无主提醒】
            return;
        }
        try {
            Map<String, Object> msg = Map.of(
                    "type", type, "title", title, "content", content, "time", LocalDateTime.now());
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(msg)));
        } catch (Exception e) {
            log.warn("推送消息失败 userId={}: {}", userId, e.getMessage());
        }
    }
}
