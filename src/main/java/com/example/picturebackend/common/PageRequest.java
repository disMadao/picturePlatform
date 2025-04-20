package com.example.picturebackend.common;

import lombok.Data;

/**
 * @Auther: Mr. Mei
 * @Date: 2025/4/14 16:35
 * @Description:
 */
@Data
public class PageRequest {

    /**
     * 当前页号
     */
    private int current = 1;

    /**
     * 页面大小
     */
    private int pageSize = 10;

    /**
     * 排序字段
     */
    private String sortField;

    /**
     * 排序顺序（默认降序）
     */
    private String sortOrder = "descend";
}
