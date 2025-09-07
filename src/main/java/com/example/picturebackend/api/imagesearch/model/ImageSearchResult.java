package com.example.picturebackend.api.imagesearch.model;

import lombok.Data;

import java.util.ArrayList;

/**
 * @Auther: Mr. Mei
 * @Date: 2025/4/29 21:06
 * @Description:
 */
@Data
public class ImageSearchResult {

    /**
     * 缩略图地址
     */
    private String thumbUrl;

    /**
     * 来源地址
     */
    private String fromUrl;

}

