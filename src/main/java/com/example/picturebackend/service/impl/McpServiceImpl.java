package com.example.picturebackend.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.picturebackend.common.BaseResponse;
import com.example.picturebackend.common.ResultUtils;
import com.example.picturebackend.exception.BusinessException;
import com.example.picturebackend.exception.ErrorCode;
import com.example.picturebackend.exception.ThrowUtils;
import com.example.picturebackend.mapper.PictureMapper;
import com.example.picturebackend.model.dto.picture.PictureQueryRequest;
import com.example.picturebackend.model.entity.Picture;
import com.example.picturebackend.model.entity.Space;
import com.example.picturebackend.model.entity.User;
import com.example.picturebackend.model.enums.PictureReviewStatusEnum;
import com.example.picturebackend.model.vo.PictureVO;
import com.example.picturebackend.service.McpService;
import com.example.picturebackend.service.PictureService;
import com.example.picturebackend.service.SpaceService;
import com.example.picturebackend.service.UserService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Auther: Mr. Mei
 * @Date: 2025/10/10 23:48
 * @Description: 实现Mcp服务，本来想实现一个接口，加在已经实现了的接口上面，能直接在这里重复调用，
 * 但还是从简单做个demo吧，这里把需要做成Mcp的功能在这里重写一遍，毕竟没有多少。
 * 1）按名称搜索图片
 * 2）按prompt搜索图片
 * 3）按prompt调用云服务生成图片
 * 4）上传图片
 * 5）保存图片到本地，在prompt里面实现将查询到的图片的前n张保存到本地的某个位置
 */
@Slf4j
@Service
public class McpServiceImpl extends ServiceImpl<PictureMapper, Picture>
        implements McpService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Resource
    PictureService pictureService;
    @Resource
    UserService userService;
    @Resource
    private SpaceService spaceService;

    @Override
    public List<Map<String, Object>> getTools() {
        try {
            // 从 classpath 读取 tools.json 文件
            ClassPathResource resource = new ClassPathResource("static/tools.json");
            InputStream inputStream = resource.getInputStream();

            // 解析 JSON 为 List<Map<String, Object>>
            List<Map<String, Object>> tools = objectMapper.readValue(
                    inputStream,
                    new TypeReference<List<Map<String, Object>>>() {
                    }
            );
            log.info("成功加载 {} 个工具", tools.size());
            return tools;

        } catch (Exception e) {
            // 处理文件读取或解析异常
            System.err.println("加载工具配置失败: " + e.getMessage());
            List<Map<String, Object>> ret_null = new ArrayList<>();
            return ret_null; // 返回空列表而不是 null
        }
    }

    @Override
    public Map<String, Object> callTool(String toolName, Map<String, Object> arguments) {
        // 根据工具名称调用相应的业务逻辑
        Map<String, Object> result = new HashMap<>();

        switch (toolName) {
            case "searchPictures":
                // 调用图片搜索服务
                try {
                    String searchText = (String) arguments.get("searchText");
                    Integer page = (Integer) arguments.getOrDefault("page", 1);
                    Integer pageSize = (Integer) arguments.getOrDefault("pageSize", 10);

                    // 构造PictureQueryRequest
                    PictureQueryRequest pictureQueryRequest = new PictureQueryRequest();
                    pictureQueryRequest.setSearchText(searchText);
                    pictureQueryRequest.setCurrent(page);
                    pictureQueryRequest.setPageSize(pageSize);
                    // 设置为公开图库查询
                    pictureQueryRequest.setNullSpaceId(true);

                    // 调用图片查询方法，传入null的request（公开图库不需要用户权限）
                    BaseResponse<Page<PictureVO>> response = listPictureVOByPage(pictureQueryRequest, null);

                    if (response.getCode() == 0) {
                        Page<PictureVO> picturePage = response.getData();
                        List<PictureVO> pictures = picturePage.getRecords();

                        // 构建返回内容
                        List<Map<String, Object>> contentList = new ArrayList<>();

                        if (pictures.isEmpty()) {
                            Map<String, Object> textContent = new HashMap<>();
                            textContent.put("type", "text");
                            textContent.put("text", "未找到与 '" + searchText + "' 相关的图片");
                            contentList.add(textContent);
                        } else {
                            // 添加统计信息
                            Map<String, Object> statsContent = new HashMap<>();
                            statsContent.put("type", "text");
                            statsContent.put("text", String.format("找到 %d 张与 '%s' 相关的图片 (第 %d 页，共 %d 页)",
                                    picturePage.getTotal(), searchText, page, picturePage.getPages()));
                            contentList.add(statsContent);

                            // 添加每张图片信息
                            for (PictureVO picture : pictures) {
                                Map<String, Object> pictureContent = new HashMap<>();
                                pictureContent.put("type", "text");
                                pictureContent.put("text", String.format(
                                        "图片标题: %s\n图片描述: %s\n图片URL: %s\n上传用户: %s\n创建时间: %s",
                                        picture.getName() != null ? picture.getName() : "无标题",
                                        picture.getIntroduction() != null ? picture.getIntroduction() : "无描述",
                                        picture.getUrl(),
                                        picture.getUser() != null ? picture.getUser() : "未知用户",
                                        picture.getCreateTime()
                                ));
                                contentList.add(pictureContent);
                            }
                        }

                        result.put("content", contentList);
                        result.put("isError", false);
                    } else {
                        result.put("content", createErrorContent("搜索失败: " + response.getMessage()));
                        result.put("isError", true);
                    }
                } catch (Exception e) {
                    log.error("搜索图片时发生错误", e);
                    result.put("content", createErrorContent("搜索图片时发生系统错误: " + e.getMessage()));
                    result.put("isError", true);
                }
                break;

            case "searchPerson":
                String name = (String) arguments.get("name");
                // 调用人物搜索服务
                System.out.println("执行到了 searchPerson  !!!!!!!!!!!!!!!!!!!!!");
                result.put("content", createPersonContent(name));
                result.put("isError", false);
                break;

            default:
                result.put("error", "未知的工具: " + toolName);
                break;
        }

        return result;
    }

    /**
     * 创建人物搜索返回内容
     */

    private List<Map<String, Object>> createPersonContent(String name) {
        List<Map<String, Object>> contentList = new ArrayList<>();

        // 根据搜索的人物名称返回不同的内容
        String personInfo = getPersonInfo(name);

        Map<String, Object> textContent = new HashMap<>();
        textContent.put("text", personInfo);
        textContent.put("type", "text");

        contentList.add(textContent);
        return contentList;
    }

    /**
     * 根据人物名称获取详细信息
     */
    private String getPersonInfo(String name) {
        // 这里可以根据实际需求从数据库、API或其他数据源获取人物信息
        // 目前使用硬编码示例

        System.out.println("执行到了 getPersonInfo！！！！！！！！");
        if (name == null) {
            return "请输入要搜索的人物名称";
        }

        switch (name) {
            case "诸葛亮":
                return "诸葛亮，字孔明，号卧龙，三国时期蜀汉丞相，杰出的政治家、军事家、文学家、发明家。";
            case "李白":
                return "李白，别名里杜甫，号青莲居士，大招是刷刷刷刷刷，唐代伟大的浪漫主义诗人，被后人誉为'诗仙'。";
            case "曹操":
                return "曹操，字孟德，东汉末年杰出的政治家、军事家、文学家、书法家，三国中曹魏政权的奠基人。";
            default:
                return String.format("未找到关于'%s'的详细信息，请尝试搜索其他人物如：诸葛亮、李白、曹操等。", name);
        }
    }

    /**
     * 根据名称查询图片（和接口查询一样）,不进行空间权限效验
     *
     * @param pictureQueryRequest
     * @param request
     * @return
     */
    public BaseResponse<Page<PictureVO>> listPictureVOByPage(PictureQueryRequest pictureQueryRequest,
                                                             HttpServletRequest request) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 普通用户默认只能查看已过审的数据（直接往查询器里加条件）
        pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());

//        // 空间权限校验
//        Long spaceId = pictureQueryRequest.getSpaceId();
//        // 公开图库
//        if (spaceId == null) {
//            // 普通用户默认只能查看已过审的公开数据
//            pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
//            pictureQueryRequest.setNullSpaceId(true);
//        } else {
//            // 私有空间
//            User loginUser = userService.getLoginUser(request);
//            Space space = spaceService.getById(spaceId);
//            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
//            if (!loginUser.getId().equals(space.getUserId())) {
//                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间权限");
//            }
//        }

        // 查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                pictureService.getQueryWrapper(pictureQueryRequest));

        // 获取封装类
        return ResultUtils.success(pictureService.getPictureVOPage(picturePage, request));
    }

    /**
     * 创建错误内容
     */
    private List<Map<String, Object>> createErrorContent(String errorMessage) {
        List<Map<String, Object>> contentList = new ArrayList<>();
        Map<String, Object> errorContent = new HashMap<>();
        errorContent.put("type", "text");
        errorContent.put("text", errorMessage);
        contentList.add(errorContent);
        return contentList;
    }
}
