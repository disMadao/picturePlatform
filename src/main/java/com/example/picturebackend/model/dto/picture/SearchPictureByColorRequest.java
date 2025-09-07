package com.example.picturebackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * @Auther: Mr. Mei
 * @Date: 2025/4/29 22:43
 * @Description:
 */
@Data
public class SearchPictureByColorRequest implements Serializable {

    /**
     * 图片主色调
     */
    private String picColor;

    /**
     * 空间 id
     */
    private Long spaceId;

    private static final long serialVersionUID = 1L;
}

