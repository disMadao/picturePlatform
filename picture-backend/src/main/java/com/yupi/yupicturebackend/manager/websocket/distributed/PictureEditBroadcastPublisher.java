package com.yupi.yupicturebackend.manager.websocket.distributed;

import com.yupi.yupicturebackend.manager.websocket.model.PictureEditResponseMessage;

/**
 * 广播抽象：用于把协同编辑消息“跨实例广播”出去。
 */
public interface PictureEditBroadcastPublisher {

    void publish(Long pictureId, PictureEditResponseMessage message, String excludeSessionId);
}


