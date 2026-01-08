package com.yupi.yupicturebackend.manager.websocket.distributed;

import cn.hutool.core.collection.CollUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.yupi.yupicturebackend.manager.websocket.model.PictureEditResponseMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理每张图片对应的 WebSocketSession 集合，并提供广播能力。
 *
 * 分布式模式下：每个应用实例只维护“本机连接”；跨实例广播由 Redis Pub/Sub 完成。
 */
@Component
@Slf4j
public class PictureEditSessionRegistry {

    // key: pictureId, value: 用户会话集合
    private final Map<Long, Set<WebSocketSession>> pictureSessions = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;

    public PictureEditSessionRegistry() {
        this.objectMapper = new ObjectMapper();
        // 将 Long 类型转为 String，避免前端精度丢失
        SimpleModule module = new SimpleModule();
        module.addSerializer(Long.class, ToStringSerializer.instance);
        module.addSerializer(Long.TYPE, ToStringSerializer.instance);
        this.objectMapper.registerModule(module);
    }

    public void addSession(Long pictureId, WebSocketSession session) {
        pictureSessions.putIfAbsent(pictureId, ConcurrentHashMap.newKeySet());
        pictureSessions.get(pictureId).add(session);
    }

    public void removeSession(Long pictureId, WebSocketSession session) {
        Set<WebSocketSession> sessionSet = pictureSessions.get(pictureId);
        if (sessionSet == null) {
            return;
        }
        sessionSet.remove(session);
        if (sessionSet.isEmpty()) {
            pictureSessions.remove(pictureId);
        }
    }

    public int countSessions(Long pictureId) {
        Set<WebSocketSession> sessionSet = pictureSessions.get(pictureId);
        return sessionSet == null ? 0 : sessionSet.size();
    }

    public void broadcast(Long pictureId, PictureEditResponseMessage msg, String excludeSessionId) {
        Set<WebSocketSession> sessionSet = pictureSessions.get(pictureId);
        if (CollUtil.isEmpty(sessionSet)) {
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(msg);
            TextMessage textMessage = new TextMessage(payload);
            for (WebSocketSession s : sessionSet) {
                if (excludeSessionId != null && excludeSessionId.equals(s.getId())) {
                    continue;
                }
                if (s.isOpen()) {
                    s.sendMessage(textMessage);
                }
            }
        } catch (IOException e) {
            log.warn("broadcast failed, pictureId={}, err={}", pictureId, e.getMessage());
        }
    }
}


