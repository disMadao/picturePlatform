package com.example.picturebackend.model.dto.space;

import lombok.Data;

import java.io.Serializable;

/**
 * @Auther: Mr. Mei
 * @Date: 2025/4/28 20:54
 * @Description:
 */
@Data
public class SpaceEditRequest implements Serializable {

    /**
     * 空间 id
     */
    private Long id;

    /**
     * 空间名称
     */
    private String spaceName;

    private static final long serialVersionUID = 1L;
}

