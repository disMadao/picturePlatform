package com.yupi.yupicturebackend.api.bytedance_ark_pic_understand;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionContentPart;
import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionRequest;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessage;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessageRole;
import com.volcengine.ark.runtime.service.ArkService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 使用字节 Ark 视觉模型生成图片简介和标签
 */
@Slf4j
@Component
public class ArkPictureMetaApi {

    /**
     * 优先读取 Spring 配置；如果为空则兜底读取环境变量 ARK_API_KEY
     */
    @Value("${ark.apiKey:}")
    private String apiKey;

    @Value("${ark.model:doubao-seed-1-6-vision-250815}")
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
     * 根据图片 URL / 名称 / 分类等信息自动生成简介和标签
     *
     * @param imageUrl 图片地址（建议传公网可访问地址，便于模型读取）
     * @param name     图片名称
     * @param category 分类
     * @param exists   已有标签（可为空）
     * @return 生成结果，失败时返回 null
     */
    public PictureMeta generateMeta(String imageUrl, String name, String category, List<String> exists) {
        // 兜底从环境变量读取
        if (StrUtil.isBlank(apiKey)) {
            apiKey = System.getenv("ARK_API_KEY");
        }
        if (StrUtil.isBlank(apiKey)) {
            // 未配置密钥时直接跳过，避免影响正常上传 / 编辑
            log.warn("Ark apiKey 未配置，跳过图片元信息生成");
            return null;
        }
        // 简单的重试机制，最多尝试 3 次
        for (int i = 0; i < 3; i++) {
            try {
                String content = callLlm(imageUrl, name, category, exists);
                PictureMeta meta = parseContent(content);
                if (meta != null) {
                    return meta;
                }
            } catch (Exception e) {
                log.warn("调用 Ark 生成图片元信息失败，第 {} 次重试，错误：{}", i + 1, e.getMessage());
            }
        }
        return null;
    }

    /**
     * 调用 Ark Chat Completions 接口
     */
    private String callLlm(String imageUrl, String name, String category, List<String> exists) {
        ArkService arkService = ArkService.builder().apiKey(apiKey).build();
        try {
            List<ChatMessage> chatMessages = new ArrayList<>();

            List<ChatCompletionContentPart> multiContent = new ArrayList<>();

            // 图片内容
            if (StrUtil.isNotBlank(imageUrl)) {
                ChatCompletionContentPart image = new ChatCompletionContentPart();
                image.setType("image_url");
                ChatCompletionContentPart.ChatCompletionContentPartImageURL imageUrlPart =
                        new ChatCompletionContentPart.ChatCompletionContentPartImageURL();
                imageUrlPart.setUrl(imageUrl);
                image.setImageUrl(imageUrlPart);
                multiContent.add(image);
            }

            // 文本提示词
            StringBuilder prompt = new StringBuilder();
            prompt.append("你是图片元信息生成助手，请根据图片内容以及提供的文字信息，生成严格的 JSON：\n")
                    .append("{\n")
                    .append("  \"introduction\": \"一句简洁的中文图片简介，40~80 字\",\n")
                    .append("  \"tags\": [\"标签1\", \"标签2\", \"标签3\", \"标签4\", \"标签5\"]\n")
                    .append("}\n")
                    .append("要求：\n")
                    .append("1. 只返回 JSON，不要任何额外文字或注释；\n")
                    .append("2. tags 必须是 5 个简短的中文标签，每个不超过 8 个字；\n")
                    .append("3. 标签不要带编号、特殊符号（如#、-、.）；\n")
                    .append("4. introduction 不要超过 100 字。\n\n");

            if (StrUtil.isNotBlank(name)) {
                prompt.append("图片名称：").append(name).append("。\n");
            }
            if (StrUtil.isNotBlank(category)) {
                prompt.append("分类：").append(category).append("。\n");
            }
            if (CollUtil.isNotEmpty(exists)) {
                prompt.append("已有标签：").append(String.join("，", exists)).append("。\n");
            }
            prompt.append("请根据图片和以上信息，生成简介和 5 个标签，严格输出 JSON。");

            ChatCompletionContentPart text = new ChatCompletionContentPart();
            text.setType("text");
            text.setText(prompt.toString());
            multiContent.add(text);

            ChatMessage userMessage = ChatMessage.builder()
                    .role(ChatMessageRole.USER)
                    .multiContent(multiContent)
                    .build();
            chatMessages.add(userMessage);

            ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
                    .model(model)
                    .messages(chatMessages)
                    .build();

            return (String) arkService.createChatCompletion(chatCompletionRequest)
                    .getChoices()
                    .get(0)
                    .getMessage()
                    .getContent();
        } finally {
            arkService.shutdownExecutor();
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
            log.warn("解析 Ark 返回内容失败，content={}", content);
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


