package com.yupi.yupicturebackend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * Agent 生成视频（独立表，与 picture 字段风格对齐）
 */
@TableName(value = "generated_video")
@Data
public class GeneratedVideo implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String url;

    private String name;

    private String introduction;

    private String category;

    private String tags;

    private Long videoSize;

    private String videoFormat;

    private String thumbnailUrl;

    private Long userId;

    private Long spaceId;

    private Integer reviewStatus;

    private String reviewMessage;

    private Long reviewerId;

    private Date reviewTime;

    private String arkTaskId;

    private Long conversationId;

    private Date createTime;

    private Date editTime;

    private Date updateTime;

    @TableLogic
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
