package com.yupi.yupicturebackend.manager.websocket.distributed.mq;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

/**
 * RabbitMQ 配置：
 * - Fanout Exchange：广播给所有实例
 * - 每个实例声明一个独立队列并绑定到 exchange
 *
 * 启用方式：pictureEdit.broadcast=mq
 */
@Configuration
@ConditionalOnProperty(name = "pictureEdit.broadcast", havingValue = "mq")
public class PictureEditRabbitMqConfig {

    public static final String EXCHANGE_NAME = "ws.picture.edit.fanout";

    @Bean
    public FanoutExchange pictureEditFanoutExchange() {
        // durable exchange：避免 broker 重启丢配置
        return new FanoutExchange(EXCHANGE_NAME, true, false);
    }

    @Bean
    public Queue pictureEditInstanceQueue() {
        // 每个实例一个队列：默认 auto-delete，便于开发环境；生产可改为 durable 并固定实例名
        String qName = "ws.picture.edit." + UUID.randomUUID();
        return QueueBuilder.nonDurable(qName).autoDelete().build();
    }

    @Bean
    public Binding pictureEditBinding(Queue pictureEditInstanceQueue, FanoutExchange pictureEditFanoutExchange) {
        return BindingBuilder.bind(pictureEditInstanceQueue).to(pictureEditFanoutExchange);
    }
}


