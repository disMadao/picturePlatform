package com.yupi.yupicturebackend.manager.upload;

import cn.hutool.core.io.FileUtil;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * 本地文件视频上传
 */
@Service
public class FileVideoUpload extends VideoUploadTemplate {

    @Override
    protected void validVideo(Object inputSource) {
        MultipartFile multipartFile = (MultipartFile) inputSource;
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件不能为空");
        }

        // 文件大小限制（100MB）
        long maxSize = 100 * 1024 * 1024L;
        if (multipartFile.getSize() > maxSize) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件大小不能超过 100MB");
        }

        // 校验文件后缀
        String fileSuffix = FileUtil.getSuffix(multipartFile.getOriginalFilename()).toLowerCase();
        List<String> allowedFormats = Arrays.asList("mp4", "avi", "mov", "wmv", "flv", "mkv", "webm");
        if (!allowedFormats.contains(fileSuffix)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的文件格式，仅支持: " + allowedFormats);
        }
    }

    @Override
    protected String getOriginalFilename(Object inputSource) {
        MultipartFile multipartFile = (MultipartFile) inputSource;
        return multipartFile.getOriginalFilename();
    }

    @Override
    protected void processFile(Object inputSource, File file) throws Exception {
        MultipartFile multipartFile = (MultipartFile) inputSource;
        multipartFile.transferTo(file);
    }
}