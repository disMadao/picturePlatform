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
        // 获取到可以防止事件的位置
        long next = ringBuffer.next();
        PictureEditEvent pictureEditEvent = ringBuffer.get(next);
        pictureEditEvent.setPictureEditRequestMessage(pictureEditRequestMessage);
        pictureEditEvent.setSession(session);
        pictureEditEvent.setUser(user);
        pictureEditEvent.setPictureId(pictureId);
        // 发布事件，然后disruptor后台线程会自动处理，也就是PictureEditEventDisruptorCOnfig中的 disruptor.start();启动的
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

