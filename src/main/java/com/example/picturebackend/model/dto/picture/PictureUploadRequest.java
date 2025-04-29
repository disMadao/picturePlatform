package com.example.picturebackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * @Auther: Mr. Mei
 * @Date: 2025/4/20 11:47
 * @Description:
 */
@Data
public class PictureUploadRequest implements Serializable {

    /**
     * 图片 id（用于修改）
     */
    private Long id;
    /**
     * 文件地址
     */
    private String fileUrl;
    /**
     * 图片名称
     */
    private String picName;
    /**
     * 空间id
     */
    private Long spaceId;

    private static final long serialVersionUID = 1L;
}
