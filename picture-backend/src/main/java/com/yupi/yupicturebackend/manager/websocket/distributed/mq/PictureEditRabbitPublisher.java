package com.yupi.yupicturebackend.manager.websocket.distributed.mq;

import cn.hutool.json.JSONUtil;
import com.yupi.yupicturebackend.manager.websocket.distributed.PictureEditBroadcastPublisher;
import com.yupi.yupicturebackend.manager.websocket.distributed.PictureEditPubSubPayload;
import com.yupi.yupicturebackend.manager.websocket.distributed.PictureEditSessionRegistry;
import com.yupi.yupicturebackend.manager.websocket.model.PictureEditResponseMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * MQ 广播发布者：使用 RabbitMQ fanout exchange。
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "pictureEdit.broadcast", havingValue = "mq")
public class PictureEditRabbitPublisher implements PictureEditBroadcastPublisher {

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Resource
    private PictureEditSessionRegistry sessionRegistry;

    @Override
    public void publish(Long pictureId, PictureEditResponseMessage message, String excludeSessionId) {
        PictureEditPubSubPayload payload = new PictureEditPubSubPayload();
        payload.setPictureId(pictureId);
        payload.setExcludeSessionId(excludeSessionId);
        payload.setMessage(message);
        String json = JSONUtil.toJsonStr(payload);
        try {
            rabbitTemplate.convertAndSend(PictureEditRabbitMqConfig.EXCHANGE_NAME, "", json);
        } catch (Exception e) {
            // 降级：MQ 不可用时仅本机广播（保证能跑）
            log.warn("Rabbit publish failed, fallback to local broadcast, err={}", e.getMessage());
            sessionRegistry.broadcast(pictureId, message, excludeSessionId);
        }
    }
}


