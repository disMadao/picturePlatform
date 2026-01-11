package com.yupi.yupicturebackend.manager.websocket.disruptor;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.yupi.yupicturebackend.manager.websocket.model.PictureEditRequestMessage;
import com.yupi.yupicturebackend.model.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;

/**
 * 图片编辑事件生产者。
 * 
 * 整个Disruptor的优化可以只看这个类，这个类和外界交互，优化是因为 WebSocket的每个客户端之间的单个连接处理方式是同步的，这里把放事件到队列中和处理队列中的事件异步化了，
 * 这里的publishEvent负责将事件发布到 disruptor 的无锁环形队列中，然后会被早就启动好的后台线程自动处理。
 * 
 */
@Component
@Slf4j
public class PictureEditEventProducer {

    @Resource
    private Disruptor<PictureEditEvent> pictureEditEventDisruptor;//这里不要随便改名字，在PictureEditEventDisruptorConfig中注入的是这个名字

    /**
     * 发布事件
     *
     * @param pictureEditRequestMessage
     * @param session
     * @param user
     * @param pictureId
     */
    public void publishEvent(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) {
        RingBuffer<PictureEditEvent> ringBuffer = pictureEditEventDisruptor.getRingBuffer();
        // 1. 获取环形缓冲区中的下一个位置
        long next = ringBuffer.next();  // 拿到一个空的slot索引

        // 2. 通过索引获取缓冲区中已存在的空事件对象
        PictureEditEvent pictureEditEvent = ringBuffer.get(next);
        // 注意：这里不是new一个新对象，而是拿到缓冲区预先分配的对象

        // 3. 填充这个事件对象的数据
        pictureEditEvent.setPictureEditRequestMessage(pictureEditRequestMessage);
        pictureEditEvent.setSession(session);
        pictureEditEvent.setUser(user);
        pictureEditEvent.setPictureId(pictureId);

        // 4.发布事件（告诉消费者这个位置的数据已准备好），然后disruptor后台线程会自动处理，也就是PictureEditEventDisruptorConfig中的 disruptor.start();启动的
        ringBuffer.publish(next);
    }

    /**
     * 优雅停机
     */
    @PreDestroy
    public void destroy() {
        pictureEditEventDisruptor.shutdown();
    }
}

