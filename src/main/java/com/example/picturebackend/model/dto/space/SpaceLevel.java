package com.example.picturebackend.model.dto.space;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @Auther: Mr. Mei
 * @Date: 2025/4/29 00:29
 * @Description:
 */
@Data
@AllArgsConstructor
public class SpaceLevel {

    private int value;

    private String text;

    private long maxCount;

    private long maxSize;
}

