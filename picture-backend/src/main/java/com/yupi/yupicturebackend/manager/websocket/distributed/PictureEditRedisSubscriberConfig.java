package com.yupi.yupicturebackend.manager.websocket.distributed;

import cn.hutool.json.JSONUtil;
import com.yupi.yupicturebackend.manager.websocket.model.PictureEditResponseMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import javax.annotation.Resource;

/**
 * Redis Pub/Sub 订阅配置：监听编辑广播并转发给本机的 WebSocketSession。
 */
@Configuration
@Slf4j
@ConditionalOnProperty(name = "pictureEdit.broadcast", havingValue = "redis", matchIfMissing = true)
public class PictureEditRedisSubscriberConfig {

    @Resource
    private PictureEditSessionRegistry sessionRegistry;

    @Bean
    public RedisMessageListenerContainer pictureEditRedisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(pictureEditMessageListener(), new ChannelTopic(PictureEditRedisPublisher.CHANNEL));
        return container;
    }

    @Bean
    public MessageListener pictureEditMessageListener() {
        return new MessageListener() {
            private final StringRedisSerializer serializer = new StringRedisSerializer();

            /**
             * 收到redis的消息之后，调用单机实例的sessionRegistry通知本机的session
             * @param message
             * @param pattern
             */
            @Override
            public void onMessage(Message message, byte[] pattern) {
                try {
                    String body = serializer.deserialize(message.getBody());
                    if (body == null || body.isEmpty()) {
                        return;
                    }
                    PictureEditPubSubPayload payload = JSONUtil.toBean(body, PictureEditPubSubPayload.class);
                    if (payload == null || payload.getPictureId() == null) {
                        return;
                    }
                    PictureEditResponseMessage resp = payload.getMessage();
                    if (resp == null) {
                        return;
                    }
                    sessionRegistry.broadcast(payload.getPictureId(), resp, payload.getExcludeSessionId());
                } catch (Exception e) {
                    log.warn("consume redis ws broadcast failed: {}", e.getMessage());
                }
            }
        };
    }
}


