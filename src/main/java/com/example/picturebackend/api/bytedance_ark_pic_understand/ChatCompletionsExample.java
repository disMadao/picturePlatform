package com.example.picturebackend.api.bytedance_ark_pic_understand;

/**
 * @Auther: Mr. Mei
 * @Date: 2025/9/20 20:29
 * @Description:
 */


import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionContentPart;
import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionRequest;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessage;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessageRole;
import com.volcengine.ark.runtime.service.ArkService;

import java.util.ArrayList;
import java.util.List;

/**
 * 这是一个示例类，展示了如何使用ArkService来完成聊天功能。
 */
public class ChatCompletionsExample {
    public String getPictureIntroductionAndTags(String imageUrl) {
        //只需要传入这个就好了
//         imageUrl = "https://mei-1-1318186176.cos.ap-nanjing.myqcloud.com/public/XXXXXXXX";
        // 从环境变量中获取API密钥
        String apiKey = System.getenv("ARK_API_KEY");

        // 创建ArkService实例
        ArkService arkService = ArkService.builder().apiKey(apiKey).build();

        // 初始化消息列表
        List<ChatMessage> chatMessages = new ArrayList<>();

        // 创建用户消息
        List<ChatCompletionContentPart> multiContent = new ArrayList<>();
        ChatCompletionContentPart image = new ChatCompletionContentPart();
        image.setType("image_url");

        ChatCompletionContentPart.ChatCompletionContentPartImageURL image_url = new ChatCompletionContentPart.ChatCompletionContentPartImageURL();
        image_url.setUrl(imageUrl);
        image.setImageUrl(image_url);
        ChatCompletionContentPart text = new ChatCompletionContentPart();
        text.setType("text");
        text.setText("请不要输出其他内容，先输出图片的简介，然后输出一个英文分号 ; 作为分割，直接输出能代表这张图片主要内容的五个标签，示例格式：这是一张什么什么的图片，非常的图片。;[\"标签1\", \"标签2\"]，再次强调不要输出其他内容！");
        multiContent.add(image);
        multiContent.add(text);
        ChatMessage userMessage = ChatMessage.builder()
                .role(ChatMessageRole.USER) // 设置消息角色为用户
                // 设置消息内容

                .multiContent(multiContent)
                .build();

        // 将用户消息添加到消息列表
        chatMessages.add(userMessage);

        // 创建聊天完成请求
        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
                .model("doubao-seed-1-6-vision-250815")// 按需替换Model ID
                .messages(chatMessages) // 设置消息列表
                .build();
//        System.out.println("当前输出");
        // 发送聊天完成请求并打印响应
        try {
            return (String) arkService.createChatCompletion(chatCompletionRequest)
                    .getChoices().get(0).getMessage().getContent();
            // 获取响应并打印每个选择的消息内容
//            arkService.createChatCompletion(chatCompletionRequest)
//                    .getChoices()
//                    .forEach(choice -> System.out.println(choice.getMessage().getContent()));
        } catch (Exception e) {
            System.out.println("请求失败: " + e.getMessage());
        } finally {
            // 关闭服务执行器
            arkService.shutdownExecutor();
        }
        return "出错了";
    }

    public static void main(String[] args) {
        //只需要传入这个就好了
        String imageUrl = "https://ts4.tc.mm.bing.net/th/id/OIP-C.bSIh_vFw-SXCw7K2PteQHQHaLk?rs=1&pid=ImgDetMain&o=7&rm=3";
        ChatCompletionsExample cce = new ChatCompletionsExample();
        System.out.println(cce.getPictureIntroductionAndTags(imageUrl));
//        // 从环境变量中获取API密钥
//        String apiKey = System.getenv("ARK_API_KEY");
////        String apiKey = "asdfasdf";
////        System.out.println(System.getenv("ComSpec"));
////        System.out.println(apiKey);
//
//        // 创建ArkService实例
//        ArkService arkService = ArkService.builder().apiKey(apiKey).build();
//
//        // 初始化消息列表
//        List<ChatMessage> chatMessages = new ArrayList<>();
//
//        // 创建用户消息
//        List<ChatCompletionContentPart> multiContent = new ArrayList<>();
//        ChatCompletionContentPart image = new ChatCompletionContentPart();
//        image.setType("image_url");
//
//        ChatCompletionContentPart.ChatCompletionContentPartImageURL image_url = new ChatCompletionContentPart.ChatCompletionContentPartImageURL();
//        image_url.setUrl(imageUrl);
//        image.setImageUrl(image_url);
//        ChatCompletionContentPart text = new ChatCompletionContentPart();
//        text.setType("text");
//        text.setText("请不要输出其他内容，直接输出能代表这张图片主要内容的五个标签，示例格式：[\"标签1\", \"标签2\"]，再次强调不要输出其他内容！");
//        multiContent.add(image);
//        multiContent.add(text);
//        ChatMessage userMessage = ChatMessage.builder()
//                .role(ChatMessageRole.USER) // 设置消息角色为用户
//                // 设置消息内容
//
//                .multiContent(multiContent)
//                .build();
//
//        // 将用户消息添加到消息列表
//        chatMessages.add(userMessage);
//
//        // 创建聊天完成请求
//        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
//                .model("doubao-seed-1-6-vision-250815")// 按需替换Model ID
//                .messages(chatMessages) // 设置消息列表
//                .build();
////        System.out.println("当前输出");
//        // 发送聊天完成请求并打印响应
//        try {
//            // 获取响应并打印每个选择的消息内容
//            arkService.createChatCompletion(chatCompletionRequest)
//                    .getChoices()
//                    .forEach(choice -> System.out.println(choice.getMessage().getContent()));
//        } catch (Exception e) {
//            System.out.println("请求失败: " + e.getMessage());
//        } finally {
//            // 关闭服务执行器
//            arkService.shutdownExecutor();
//        }
    }
}