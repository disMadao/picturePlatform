package com.yupi.yupicturebackend.model.dto.video;

import lombok.Data;

/**
 * 由 Python Agent 调用：方舟临时视频 URL 转存 COS 并落库
 */
@Data
public class AgentVideoPersistRequest {

    private String tempVideoUrl;

    private Long userId;

    private Long spaceId;

    private String name;

    private String introduction;

    private String prompt;

    private String thumbnailUrl;

    private Long conversationId;

    private String arkTaskId;
}
