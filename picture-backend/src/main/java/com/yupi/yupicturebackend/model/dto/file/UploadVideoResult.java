package com.yupi.yupicturebackend.model.dto.file;

import lombok.Data;

/**
 * 上传视频结果
 */
@Data
public class UploadVideoResult {
    /**
     * 视频可访问 URL
     */
    private String url;

    /**
     * 原始文件名
     */
    private String originalName;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;
}