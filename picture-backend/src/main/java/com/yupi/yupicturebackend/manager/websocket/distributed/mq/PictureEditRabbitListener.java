package com.yupi.yupicturebackend.manager.websocket.distributed.mq;

import cn.hutool.json.JSONUtil;
import com.yupi.yupicturebackend.manager.websocket.distributed.PictureEditPubSubPayload;
import com.yupi.yupicturebackend.manager.websocket.distributed.PictureEditSessionRegistry;
import com.yupi.yupicturebackend.manager.websocket.model.PictureEditResponseMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * MQ 广播消费者：收到消息后推送到本机的 WebSocketSession。
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "pictureEdit.broadcast", havingValue = "mq")
public class PictureEditRabbitListener {

    @Resource
    private PictureEditSessionRegistry sessionRegistry;

    @RabbitListener(queues = "#{pictureEditInstanceQueue.name}")
    public void onMessage(String body) {
        try {
            PictureEditPubSubPayload payload = JSONUtil.toBean(body, PictureEditPubSubPayload.class);
            if (payload == null || payload.getPictureId() == null) {
                return;
            }
            PictureEditResponseMessage msg = payload.getMessage();
            if (msg == null) {
                return;
            }
            sessionRegistry.broadcast(payload.getPictureId(), msg, payload.getExcludeSessionId());
        } catch (Exception e) {
            log.warn("consume rabbit ws broadcast failed: {}", e.getMessage());
        }
    }
}


