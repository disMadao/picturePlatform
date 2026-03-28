package com.yupi.yupicturebackend.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import com.yupi.yupicturebackend.manager.upload.UrlVideoUpload;
import com.yupi.yupicturebackend.mapper.GeneratedVideoMapper;
import com.yupi.yupicturebackend.model.dto.file.UploadVideoResult;
import com.yupi.yupicturebackend.model.dto.video.AgentVideoPersistRequest;
import com.yupi.yupicturebackend.model.entity.GeneratedVideo;
import com.yupi.yupicturebackend.model.entity.Space;
import com.yupi.yupicturebackend.model.enums.PictureReviewStatusEnum;
import com.yupi.yupicturebackend.model.vo.GeneratedVideoVO;
import com.yupi.yupicturebackend.service.GeneratedVideoService;
import com.yupi.yupicturebackend.service.SpaceService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;

@Service
public class GeneratedVideoServiceImpl extends ServiceImpl<GeneratedVideoMapper, GeneratedVideo>
        implements GeneratedVideoService {

    @Resource
    private UrlVideoUpload urlVideoUpload;

    @Resource
    private SpaceService spaceService;

    @Override
    public GeneratedVideoVO persistFromAgentTempUrl(AgentVideoPersistRequest req) {
        ThrowUtils.throwIf(StrUtil.isBlank(req.getTempVideoUrl()), ErrorCode.PARAMS_ERROR, "临时视频地址为空");
        ThrowUtils.throwIf(req.getUserId() == null || req.getUserId() <= 0, ErrorCode.PARAMS_ERROR, "userId 无效");
        ThrowUtils.throwIf(req.getSpaceId() == null || req.getSpaceId() <= 0, ErrorCode.PARAMS_ERROR, "spaceId 无效");

        Space space = spaceService.getById(req.getSpaceId());
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        ThrowUtils.throwIf(!space.getUserId().equals(req.getUserId()), ErrorCode.NO_AUTH_ERROR, "无权写入该空间");

        String pathPrefix = String.format("space/%s/video", req.getSpaceId());
        UploadVideoResult upload = urlVideoUpload.uploadVideo(req.getTempVideoUrl(), pathPrefix);

        GeneratedVideo gv = new GeneratedVideo();
        gv.setUrl(upload.getUrl());
        gv.setName(StrUtil.isNotBlank(req.getName()) ? req.getName() : "生成视频");
        String intro = StrUtil.isNotBlank(req.getIntroduction()) ? req.getIntroduction() : req.getPrompt();
        gv.setIntroduction(intro);
        gv.setVideoSize(upload.getFileSize());
        gv.setVideoFormat("mp4");
        gv.setThumbnailUrl(req.getThumbnailUrl());
        gv.setUserId(req.getUserId());
        gv.setSpaceId(req.getSpaceId());
        gv.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
        gv.setArkTaskId(req.getArkTaskId());
        gv.setConversationId(req.getConversationId());
        Date now = new Date();
        gv.setCreateTime(now);
        gv.setEditTime(now);
        gv.setUpdateTime(now);
        boolean ok = this.save(gv);
        ThrowUtils.throwIf(!ok, ErrorCode.OPERATION_ERROR, "保存视频记录失败");
        return toVo(gv);
    }

    private static GeneratedVideoVO toVo(GeneratedVideo gv) {
        GeneratedVideoVO vo = new GeneratedVideoVO();
        BeanUtils.copyProperties(gv, vo);
        return vo;
    }
}
