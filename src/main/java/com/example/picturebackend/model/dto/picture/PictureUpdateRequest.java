package com.example.picturebackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @Auther: Mr. Mei
 * @Date: 2025/4/21 00:00
 * @Description:
 */
@Data
public class PictureUpdateRequest implements Serializable {

    /**
     * id
     */
    private Long id;


    /**
     * 图片名称
     */
    private String name;

    /**
     * 简介
     */
    private String introduction;

    /**
     * 分类
     */
    private String category;

    /**
     * 标签
     */
    private List<String> tags;

    private static final long serialVersionUID = 1L;
}

