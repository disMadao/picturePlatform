package com.yupi.yupicturebackend.manager.websocket;

import cn.hutool.json.JSONUtil;
import com.yupi.yupicturebackend.manager.websocket.disruptor.PictureEditEventProducer;
import com.yupi.yupicturebackend.manager.websocket.distributed.PictureEditBroadcastPublisher;
import com.yupi.yupicturebackend.manager.websocket.distributed.PictureEditDistributedLockService;
import com.yupi.yupicturebackend.manager.websocket.distributed.PictureEditSessionRegistry;
import com.yupi.yupicturebackend.manager.websocket.model.PictureEditActionEnum;
import com.yupi.yupicturebackend.manager.websocket.model.PictureEditMessageTypeEnum;
import com.yupi.yupicturebackend.manager.websocket.model.PictureEditRequestMessage;
import com.yupi.yupicturebackend.manager.websocket.model.PictureEditResponseMessage;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.annotation.Resource;
import java.io.IOException;

/**
 * 分布式版图片编辑 WebSocket 处理器
 *
 * - 编辑锁：Redis SETNX + TTL（不可用时降级单机内存）
 * - 广播：可插拔（Redis Pub/Sub 或 MQ），不可用时降级本机广播
 *
 * 注意：跨实例广播的“最终下发”发生在订阅端（subscriber）；
 * publisher 只负责 publish（失败时才本机广播兜底）。
 */
@Component
@Slf4j
public class DistributedPictureEditHandler extends TextWebSocketHandler {

    @Resource
    private UserService userService;

    @Resource
    @Lazy
    private PictureEditEventProducer pictureEditEventProducer;//仅用于广播事件到 disruptor中，交给里面的线程异步处理事件

    @Resource
    private PictureEditSessionRegistry sessionRegistry;

    @Resource
    private PictureEditDistributedLockService lockService;

    @Resource
    private PictureEditBroadcastPublisher broadcastPublisher;//用于广播所有通知类别消息，可能是PictureEditRedisPublisher或者PictureEDitRabbitPublisher，看yml配置

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        super.afterConnectionEstablished(session);
        User user = (User) session.getAttributes().get("user");
        Long pictureId = (Long) session.getAttributes().get("pictureId");
        sessionRegistry.addSession(pictureId, session);

        PictureEditResponseMessage resp = new PictureEditResponseMessage();
        resp.setType(PictureEditMessageTypeEnum.INFO.getValue());
        resp.setMessage(String.format("用户 %s 加入编辑", user.getUserName()));
        resp.setUser(userService.getUserVO(user));
        // 分布式广播
        broadcastPublisher.publish(pictureId, resp, null);
    }

    /**
     * 优化之前是这里直接根据事件类型调用对应的 handle 方法处理，现在只需要将事件放到 disruptor队列中，处理交给 disruptor中的线程
     * @param session
     * @param message
     * @throws Exception
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        super.handleTextMessage(session, message);
        PictureEditRequestMessage req = JSONUtil.toBean(message.getPayload(), PictureEditRequestMessage.class);
        User user = (User) session.getAttributes().get("user");
        Long pictureId = (Long) session.getAttributes().get("pictureId");
        pictureEditEventProducer.publishEvent(req, session, user, pictureId);//根据req中事件调用下面三个方法
    }

    /**
     * ENTER_EDIT：尝试获取分布式锁
     */
    public void handleEnterEditMessage(PictureEditRequestMessage req, WebSocketSession session, User user, Long pictureId) throws IOException {
        boolean ok = lockService.tryLock(pictureId, user.getId());
        if (!ok) {
            // 给当前用户一个轻量提示（不广播）
            PictureEditResponseMessage resp = new PictureEditResponseMessage();
            resp.setType(PictureEditMessageTypeEnum.INFO.getValue());
            resp.setMessage("当前已有其他用户正在编辑，请稍后再试");
            resp.setUser(userService.getUserVO(user));
            session.sendMessage(new TextMessage(JSONUtil.toJsonStr(resp)));
            return;
        }
        PictureEditResponseMessage resp = new PictureEditResponseMessage();
        resp.setType(PictureEditMessageTypeEnum.ENTER_EDIT.getValue());
        resp.setMessage(String.format("用户 %s 开始编辑图片", user.getUserName()));
        resp.setUser(userService.getUserVO(user));
        broadcastPublisher.publish(pictureId, resp, null);
    }

    /**
     * EDIT_ACTION：仅编辑锁持有者可广播动作
     */
    public void handleEditActionMessage(PictureEditRequestMessage req, WebSocketSession session, User user, Long pictureId) throws IOException {
        String editAction = req.getEditAction();
        PictureEditActionEnum actionEnum = PictureEditActionEnum.getEnumByValue(editAction);
        if (actionEnum == null) {
            log.warn("invalid editAction={}", editAction);
            return;
        }
        if (!lockService.isOwner(pictureId, user.getId())) {
            return;
        }
        // 续期，避免长编辑锁过期
        lockService.refreshIfOwner(pictureId, user.getId());

        PictureEditResponseMessage resp = new PictureEditResponseMessage();
        resp.setType(PictureEditMessageTypeEnum.EDIT_ACTION.getValue());
        resp.setMessage(String.format("%s 执行 %s", user.getUserName(), actionEnum.getText()));
        resp.setEditAction(editAction);
        resp.setUser(userService.getUserVO(user));
        // 排除发起者本机 session，避免重复应用
        broadcastPublisher.publish(pictureId, resp, session.getId());
    }

    /**
     * EXIT_EDIT：释放分布式锁
     */
    public void handleExitEditMessage(PictureEditRequestMessage req, WebSocketSession session, User user, Long pictureId) throws IOException {
        // 只允许持锁者释放
        if (!lockService.isOwner(pictureId, user.getId())) {
            return;
        }
        lockService.unlockIfOwner(pictureId, user.getId());

        PictureEditResponseMessage resp = new PictureEditResponseMessage();
        resp.setType(PictureEditMessageTypeEnum.EXIT_EDIT.getValue());
        resp.setMessage(String.format("用户 %s 退出编辑图片", user.getUserName()));
        resp.setUser(userService.getUserVO(user));
        broadcastPublisher.publish(pictureId, resp, null);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        super.afterConnectionClosed(session, status);
        User user = (User) session.getAttributes().get("user");
        Long pictureId = (Long) session.getAttributes().get("pictureId");

        try {
            // 断连时尝试释放锁（如果是持锁者）
            lockService.unlockIfOwner(pictureId, user.getId());
        } catch (Exception ignored) {
        }

        sessionRegistry.removeSession(pictureId, session);

        PictureEditResponseMessage resp = new PictureEditResponseMessage();
        resp.setType(PictureEditMessageTypeEnum.INFO.getValue());
        resp.setMessage(String.format("用户 %s 离开编辑", user.getUserName()));
        resp.setUser(userService.getUserVO(user));
        broadcastPublisher.publish(pictureId, resp, null);
    }
}


