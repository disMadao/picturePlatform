package com.example.picturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.picturebackend.model.dto.space.SpaceAddRequest;
import com.example.picturebackend.model.dto.space.SpaceQueryRequest;
import com.example.picturebackend.model.entity.Space;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.picturebackend.model.entity.User;
import com.example.picturebackend.model.vo.SpaceVO;

import javax.servlet.http.HttpServletRequest;

/**
 * @author 枚子君
 * @description 针对表【space(空间)】的数据库操作Service
 * @createDate 2025-04-28 20:49:51
 */
public interface SpaceService extends IService<Space> {

    /**
     * 校验空间数据的方法，增加 add 参数用来区分是创建数据时校验还是编辑时校验，
     *
     * @param space
     * @param add
     */
    void validSpace(Space space, boolean add);

    /**
     * 创建或更新空间时，需要根据空间级别自动填充限额数据
     *
     * @param space
     */
    void fillSpaceBySpaceLevel(Space space);

    /**
     * 新增空间
     *
     * @param spaceAddRequest
     * @param loginUser
     * @return
     */
    long addSpace(SpaceAddRequest spaceAddRequest, User loginUser);

    void checkSpaceAuth(User loginUser, Space space);

    SpaceVO getSpaceVO(Space space, HttpServletRequest request);

    QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest);

    Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage, HttpServletRequest request);
}
