package com.yupi.yupicturebackend.model.vo;

import lombok.Data;

import java.util.Date;

@Data
public class GeneratedVideoVO {

    private Long id;

    private String url;

    private String name;

    private String thumbnailUrl;

    private Long videoSize;

    private String videoFormat;

    private Long userId;

    private Long spaceId;

    private Long conversationId;

    private Date createTime;
}
