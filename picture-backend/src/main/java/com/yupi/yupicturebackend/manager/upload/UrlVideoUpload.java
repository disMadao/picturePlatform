package com.yupi.yupicturebackend.manager.upload;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.http.HttpUtil;
import cn.hutool.http.Method;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

/**
 * URL 视频上传
 */
@Service
public class UrlVideoUpload extends VideoUploadTemplate {

    @Override
    protected void validVideo(Object inputSource) {
        String fileUrl = (String) inputSource;
        ThrowUtils.throwIf(StrUtil.isBlank(fileUrl), ErrorCode.PARAMS_ERROR, "文件地址为空");

        // 校验 URL 格式
        try {
            new URL(fileUrl);
        } catch (MalformedURLException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件地址格式不正确");
        }

        // 校验协议
        ThrowUtils.throwIf(!fileUrl.startsWith("http://") && !fileUrl.startsWith("https://"),
                ErrorCode.PARAMS_ERROR, "仅支持 HTTP 或 HTTPS 协议");

        // 发送 HEAD 请求验证文件是否存在及大小
        HttpResponse response = null;
        try {
            response = HttpUtil.createRequest(Method.HEAD, fileUrl).execute();
            if (response.getStatus() != HttpStatus.HTTP_OK) {
                // 如果 HEAD 请求失败，不阻止上传（可能服务器不支持 HEAD），但可以记录
                return;
            }

            // 校验 Content-Type 是否为视频格式（可选）
            String contentType = response.header("Content-Type");
            if (StrUtil.isNotBlank(contentType)) {
                // 常见视频 MIME 类型
                List<String> allowedContentTypes = Arrays.asList(
                        "video/mp4", "video/x-msvideo", "video/quicktime",
                        "video/x-ms-wmv", "video/x-flv", "video/x-matroska", "video/webm"
                );
                // 这里只做简单校验，如果不匹配也不强制失败
                if (!allowedContentTypes.contains(contentType.toLowerCase())) {
                    // 可根据需要抛出异常或仅警告
                    // throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件类型错误");
                }
            }

            // 校验文件大小（不超过 100MB）
            String contentLengthStr = response.header("Content-Length");
            if (StrUtil.isNotBlank(contentLengthStr)) {
                try {
                    long contentLength = Long.parseLong(contentLengthStr);
                    long maxSize = 100 * 1024 * 1024L;
                    if (contentLength > maxSize) {
                        throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件大小不能超过 100MB");
                    }
                } catch (NumberFormatException e) {
                    // 忽略解析错误
                }
            }
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    @Override
    protected String getOriginalFilename(Object inputSource) {
        String fileUrl = (String) inputSource;
        // 使用 Hutool 获取 URL 中的文件名（可能带参数）
        String fullName = FileUtil.getName(fileUrl);
        // 去除 ? 及之后的所有参数
        int queryIndex = fullName.indexOf('?');
        if (queryIndex > 0) {
            fullName = fullName.substring(0, queryIndex);
        }
        return fullName;
    }

    @Override
    protected void processFile(Object inputSource, File file) throws Exception {
        String fileUrl = (String) inputSource;
        // 下载文件到临时文件
        HttpUtil.downloadFile(fileUrl, file);
    }
}