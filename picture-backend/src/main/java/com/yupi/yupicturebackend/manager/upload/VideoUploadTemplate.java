package com.yupi.yupicturebackend.manager.upload;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import com.qcloud.cos.model.PutObjectResult;
import com.yupi.yupicturebackend.config.CosClientConfig;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.manager.CosManager;
import com.yupi.yupicturebackend.model.dto.file.UploadVideoResult;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Resource;
import java.io.File;
import java.util.Date;

/**
 * 视频上传模板（最简版，仅重命名上传）
 */
@Slf4j
public abstract class VideoUploadTemplate {

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private CosManager cosManager;

    /**
     * 上传视频（模板方法）
     *
     * @param inputSource      输入源（文件或URL）
     * @param uploadPathPrefix 上传路径前缀
     * @return 上传结果
     */
    public UploadVideoResult uploadVideo(Object inputSource, String uploadPathPrefix) {
        // 1. 校验视频
        validVideo(inputSource);

        // 2. 生成上传路径（重命名）
        String originalFilename = getOriginalFilename(inputSource);
        String uuid = RandomUtil.randomString(16);
        String uploadFilename = String.format("%s_%s.%s",
                DateUtil.formatDate(new Date()), uuid, FileUtil.getSuffix(originalFilename));
        String uploadPath = String.format("/%s/%s", uploadPathPrefix, uploadFilename);

        File tempFile = null;
        try {
            // 3. 创建临时文件并写入内容
            // 3. 创建临时文件并写入内容
            String prefix = "video_" + uuid + "_";
            String suffix = "." + FileUtil.getSuffix(originalFilename);
            System.out.println(",,,,,,,,,,,   "+prefix+".\n,,,,,,,,,,,     " + suffix);
            tempFile = File.createTempFile(prefix, suffix);
            System.out.println("aaaaaaaaaaaaaaaaaaa");
            processFile(inputSource, tempFile);

            // 4. 上传到 COS（普通上传，不进行媒体处理）
            PutObjectResult putObjectResult = cosManager.putObject(uploadPath, tempFile);

            // 5. 封装返回结果
            return buildResult(originalFilename, tempFile, uploadPath);
        } catch (Exception e) {
            log.error("视频上传失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        } finally {
            // 6. 清理临时文件
            deleteTempFile(tempFile);
        }
    }

    /**
     * 校验视频（子类实现）
     */
    protected abstract void validVideo(Object inputSource);

    /**
     * 获取原始文件名（子类实现）
     */
    protected abstract String getOriginalFilename(Object inputSource);

    /**
     * 处理输入源，写入本地临时文件（子类实现）
     */
    protected abstract void processFile(Object inputSource, File file) throws Exception;

    /**
     * 封装返回结果
     */
    private UploadVideoResult buildResult(String originalFilename, File file, String uploadPath) {
        UploadVideoResult result = new UploadVideoResult();
        result.setOriginalName(originalFilename);
        result.setUrl(cosClientConfig.getHost() + "/" + uploadPath);
        result.setFileSize(file.length());
        return result;
    }

    /**
     * 删除临时文件
     */
    private void deleteTempFile(File file) {
        if (file != null && file.exists()) {
            boolean deleted = file.delete();
            if (!deleted) {
                log.warn("临时文件删除失败: {}", file.getAbsolutePath());
            }
        }
    }
}