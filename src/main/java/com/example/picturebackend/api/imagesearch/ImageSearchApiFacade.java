package com.example.picturebackend.api.imagesearch;

import com.example.picturebackend.api.imagesearch.model.ImageSearchResult;
import com.example.picturebackend.api.imagesearch.sub.GetImageFirstUrlApi;
import com.example.picturebackend.api.imagesearch.sub.GetImageListApi;
import com.example.picturebackend.api.imagesearch.sub.GetImagePageUrlApi;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * @Auther: Mr. Mei
 * @Date: 2025/4/29 22:01
 * @Description:
 */
@Slf4j
public class ImageSearchApiFacade {

    /**
     * 搜索图片
     *
     * @param imageUrl
     * @return
     */
    public static List<ImageSearchResult> searchImage(String imageUrl) {
        String imagePageUrl = GetImagePageUrlApi.getImagePageUrl(imageUrl);
        String imageFirstUrl = GetImageFirstUrlApi.getImageFirstUrl(imagePageUrl);
        List<ImageSearchResult> imageList = GetImageListApi.getImageList(imageFirstUrl);
        return imageList;
    }

    public static void main(String[] args) {
        // 测试以图搜图功能
        String imageUrl = "https://p11-ark-imagex.byteimg.com/tos-cn-i-51fv3nh8ci/4ef4ed86dd3144a7ab34d0859dbfd3d3~tplv-51fv3nh8ci-avif-cp:q90.avif";
        List<ImageSearchResult> resultList = searchImage(imageUrl);
        System.out.println("结果列表" + resultList);
    }
}
