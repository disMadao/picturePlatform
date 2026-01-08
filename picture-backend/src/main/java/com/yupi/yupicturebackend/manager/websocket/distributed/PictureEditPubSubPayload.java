package com.yupi.yupicturebackend.manager.websocket.distributed;

import com.yupi.yupicturebackend.manager.websocket.model.PictureEditResponseMessage;
import lombok.Data;

/**
 * Redis Pub/Sub 广播载荷
 */
@Data
public class PictureEditPubSubPayload {

    /**
     * 图片 id
     */
    private Long pictureId;

    /**
     * 排除的 sessionId（仅用于“发起者本机实例”避免重复发送给自己）
     */
    private String excludeSessionId;

    /**
     * 广播消息
     */
    private PictureEditResponseMessage message;
}


