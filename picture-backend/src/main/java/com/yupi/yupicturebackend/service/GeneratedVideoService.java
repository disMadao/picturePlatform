package com.yupi.yupicturebackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yupi.yupicturebackend.model.dto.video.AgentVideoPersistRequest;
import com.yupi.yupicturebackend.model.entity.GeneratedVideo;
import com.yupi.yupicturebackend.model.vo.GeneratedVideoVO;

public interface GeneratedVideoService extends IService<GeneratedVideo> {

    /**
     * 将方舟临时视频 URL 转存 COS 并写入 generated_video
     */
    GeneratedVideoVO persistFromAgentTempUrl(AgentVideoPersistRequest request);
}
