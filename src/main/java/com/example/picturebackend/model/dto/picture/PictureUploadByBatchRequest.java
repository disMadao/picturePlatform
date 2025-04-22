package com.example.picturebackend.model.dto.picture;

import lombok.Data;

/**
 * @Auther: Mr. Mei
 * @Date: 2025/4/22 13:50
 * @Description:
 */
@Data
public class PictureUploadByBatchRequest {

    /**
     * 搜索词
     */
    private String searchText;

    /**
     * 抓取数量
     */
    private Integer count = 10;
    /**
     * 名称前缀
     */
    private String namePrefix;

}
