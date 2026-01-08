package com.yupi.yupicturebackend.manager.websocket.distributed;

import cn.hutool.json.JSONUtil;
import com.yupi.yupicturebackend.manager.websocket.model.PictureEditResponseMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * WebSocket 广播消息发布到 Redis（跨实例同步）
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "pictureEdit.broadcast", havingValue = "redis", matchIfMissing = true)
public class PictureEditRedisPublisher implements PictureEditBroadcastPublisher {

    public static final String CHANNEL = "ws:picture:edit:broadcast";

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private PictureEditSessionRegistry sessionRegistry;

    /**
     * 发布广播消息到 Redis；若 Redis 不可用则降级为本机广播（保证可运行）。
     */
    @Override
    public void publish(Long pictureId, PictureEditResponseMessage message, String excludeSessionId) {
        PictureEditPubSubPayload payload = new PictureEditPubSubPayload();
        payload.setPictureId(pictureId);
        payload.setExcludeSessionId(excludeSessionId);
        payload.setMessage(message);
        String json = JSONUtil.toJsonStr(payload);
        try {
            stringRedisTemplate.convertAndSend(CHANNEL, json);
        } catch (Exception e) {
            // 降级：Redis 不可用时仅本机广播（单机模式仍可使用）
            log.warn("Redis publish failed, fallback to local broadcast, err={}", e.getMessage());
            sessionRegistry.broadcast(pictureId, message, excludeSessionId);
        }
    }
}


