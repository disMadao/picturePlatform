package com.yupi.yupicturebackend.controller;

import com.yupi.yupicturebackend.common.BaseResponse;
import com.yupi.yupicturebackend.common.ResultUtils;
import com.yupi.yupicturebackend.manager.upload.FileVideoUpload;
import com.yupi.yupicturebackend.manager.upload.UrlVideoUpload;
import com.yupi.yupicturebackend.model.dto.file.UploadVideoResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;

@Slf4j
@RestController
@RequestMapping("/video")
public class VideoController {

    @Resource
    private FileVideoUpload fileVideoUpload;

    @Resource
    private UrlVideoUpload urlVideoUpload;

    /**
     * 上传本地视频文件
     *
     * @param file       视频文件
     * @param pathPrefix 上传路径前缀（如用户ID/空间ID）
     * @return 上传结果（包含视频URL）
     */
    @PostMapping("/upload")
    public BaseResponse<UploadVideoResult> uploadVideo(
            @RequestPart("file") MultipartFile file,
            @RequestParam("pathPrefix") String pathPrefix) {
        UploadVideoResult result = fileVideoUpload.uploadVideo(file, pathPrefix);
        return ResultUtils.success(result);
    }

    /**
     * 通过URL上传视频
     *
     * @param url        视频URL地址
     * @param pathPrefix 上传路径前缀
     * @return 上传结果（包含视频URL）
     */
    @PostMapping("/upload/url")
    public BaseResponse<UploadVideoResult> uploadVideoByUrl(
            @RequestParam("url") String url,
            @RequestParam("pathPrefix") String pathPrefix) {
        UploadVideoResult result = urlVideoUpload.uploadVideo(url, pathPrefix);
        return ResultUtils.success(result);
    }
}