package com.yupi.yupicturebackend.api.deepseek;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 调用 DeepSeek 生成图片简介和标签
 */
@Slf4j
@Component
public class DeepSeekPictureMetaApi {

    @Value("${deepseek.apiKey:}")
    private String apiKey;

    @Value("${deepseek.apiBase:https://api.deepseek.com}")
    private String apiBase;

    @Value("${deepseek.model:deepseek-chat}")
    private String model;

    /**
     * 元信息结构
     */
    @Data
    public static class PictureMeta {
        private String introduction;
        private List<String> tags;
    }

    /**
     * 根据图片名称 / 分类等信息自动生成简介和标签
     *
     * @param name     图片名称
     * @param category 分类
     * @param exists   已有标签（可为空）
     * @return 生成结果，失败时返回 null
     */
    public PictureMeta generateMeta(String name, String category, List<String> exists) {
        if (StrUtil.isBlank(apiKey)) {
            // 未配置密钥时直接跳过，避免影响正常上传
            log.warn("DeepSeek apiKey 未配置，跳过图片元信息生成");
            return null;
        }
        // 简单的重试机制，最多尝试 3 次
        for (int i = 0; i < 3; i++) {
            try {
                String content = callLlm(name, category, exists);
                PictureMeta meta = parseContent(content);
                if (meta != null) {
                    return meta;
                }
            } catch (Exception e) {
                log.warn("调用 DeepSeek 生成图片元信息失败，第 {} 次重试，错误：{}", i + 1, e.getMessage());
            }
        }
        return null;
    }

    /**
     * 调用 DeepSeek 的 chat completions 接口
     */
    private String callLlm(String name, String category, List<String> exists) {
        String url = apiBase;
        if (!url.endsWith("/chat/completions")) {
            url = url.replaceAll("/+$", "") + "/chat/completions";
        }
        String systemPrompt = "你是图片元信息生成助手，只输出严格的 JSON，格式如下：\n" +
                "{\n" +
                "  \"introduction\": \"一句简洁的中文图片简介，40~80 字\",\n" +
                "  \"tags\": [\"标签1\", \"标签2\", \"标签3\", \"标签4\", \"标签5\"]\n" +
                "}\n" +
                "要求：\n" +
                "1. 只返回 JSON，不要任何额外文字或注释；\n" +
                "2. tags 必须是 5 个简短的中文标签，每个不超过 8 个字；\n" +
                "3. 标签不要带编号、特殊符号（如#、-、.）；\n" +
                "4. introduction 不要超过 100 字。";

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("图片名称：").append(StrUtil.blankToDefault(name, "未命名图片")).append("。\n");
        if (StrUtil.isNotBlank(category)) {
            userPrompt.append("分类：").append(category).append("。\n");
        }
        if (exists != null && !exists.isEmpty()) {
            userPrompt.append("已有标签：").append(String.join("，", exists)).append("。\n");
        }
        userPrompt.append("请根据以上信息，生成简介和 5 个标签。");

        JSONObject payload = new JSONObject();
        payload.set("model", model);
        JSONArray messages = new JSONArray();
        messages.add(JSONUtil.createObj()
                .set("role", "system")
                .set("content", systemPrompt));
        messages.add(JSONUtil.createObj()
                .set("role", "user")
                .set("content", userPrompt.toString()));
        payload.set("messages", messages);
        payload.set("temperature", 0);

        HttpRequest request = HttpRequest.post(url)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(payload.toString());

        try (HttpResponse response = request.execute()) {
            if (!response.isOk()) {
                log.error("DeepSeek 请求失败：{}", response.body());
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "生成图片元信息失败");
            }
            JSONObject json = JSONUtil.parseObj(response.body());
            JSONArray choices = json.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "生成图片元信息结果为空");
            }
            JSONObject message = choices.getJSONObject(0).getJSONObject("message");
            String content = message.getStr("content");
            if (StrUtil.isBlank(content)) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "生成图片元信息结果为空");
            }
            return content;
        }
    }

    /**
     * 解析并校验 LLM 返回的 JSON 字符串
     */
    private PictureMeta parseContent(String content) {
        if (StrUtil.isBlank(content)) {
            return null;
        }
        // 有些模型可能会返回 ```json 包裹的内容，这里简单裁剪
        String jsonStr = content.trim();
        int start = jsonStr.indexOf('{');
        int end = jsonStr.lastIndexOf('}');
        if (start >= 0 && end > start) {
            jsonStr = jsonStr.substring(start, end + 1);
        }
        JSONObject obj;
        try {
            obj = JSONUtil.parseObj(jsonStr);
        } catch (Exception e) {
            log.warn("解析 DeepSeek 返回内容失败，content={}", content);
            return null;
        }
        String introduction = obj.getStr("introduction");
        if (StrUtil.isBlank(introduction)) {
            return null;
        }
        // 简单截断，防止超出字段长度
        introduction = StrUtil.sub(introduction, 0, 200);

        JSONArray tagArray = obj.getJSONArray("tags");
        if (tagArray == null || tagArray.isEmpty()) {
            return null;
        }
        Set<String> tagSet = new LinkedHashSet<>();
        for (Object o : tagArray) {
            if (o == null) {
                continue;
            }
            String tag = o.toString();
            tag = StrUtil.trim(tag);
            if (StrUtil.isBlank(tag)) {
                continue;
            }
            // 去除特殊前缀
            tag = tag.replaceAll("^[#\\-\\d\\.\\s]+", "");
            if (StrUtil.isBlank(tag)) {
                continue;
            }
            tagSet.add(tag);
            if (tagSet.size() >= 5) {
                break;
            }
        }
        if (tagSet.isEmpty()) {
            return null;
        }
        List<String> tags = new ArrayList<>(tagSet);
        // 保证最多 5 个
        if (tags.size() > 5) {
            tags = tags.subList(0, 5);
        }
        PictureMeta meta = new PictureMeta();
        meta.setIntroduction(introduction);
        meta.setTags(tags);
        return meta;
    }
}


