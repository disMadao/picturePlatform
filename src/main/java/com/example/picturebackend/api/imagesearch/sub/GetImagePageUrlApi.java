package com.example.picturebackend.api.imagesearch.sub;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.json.JSONUtil;
import com.example.picturebackend.exception.BusinessException;
import com.example.picturebackend.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * @Auther: Mr. Mei
 * @Date: 2025/4/29 21:07
 * @Description:
 */
@Slf4j
public class GetImagePageUrlApi {

    /**
     * 获取以图搜图页面地址
     *
     * @param imageUrl
     * @return
     */
    public static String getImagePageUrl(String imageUrl) {
        // image: https%3A%2F%2Fwww.codefather.cn%2Flogo.png
        //tn: pc
        //from: pc
        //image_source: PC_UPLOAD_URL
        //sdkParams:
        // 1. 准备请求参数
        Map<String, Object> formData = new HashMap<>();
        formData.put("image", imageUrl);
        formData.put("tn", "pc");
        formData.put("from", "pc");
        formData.put("image_source", "PC_UPLOAD_URL");
        // 获取当前时间戳
        long uptime = System.currentTimeMillis();
        // 请求地址
        String url = "https://graph.baidu.com/upload?uptime=" + uptime;
        String acsToken = "1758341900649_1758356298844_g5yRtVH3FRk5j2FIE5Qxtvjg5GvhFnoxhFOQaB+UBFrLe+l3CqJbj89GgFSCwAtkrpBQSKkcWFJgaI7oJRE5Y8G10PE3phvndg7R+EZxRnERmVKfMZllQ7a1/wqHm7XZC6XNHekhEBs443RZMqGEO5OSDxruF+TFXmztLMchGLxE68M/f3xuM0VL1dE6LAdDkoEaUbutB3v/qyXom+tQQnZpcuG1cJcoQyfyyBitoTeCNJ7wF8noAKiSz/I/33QpXgHC2Xl3vh7b/Ekjl2CAqXZlPJ7wMwd5l/5NRTwRa8aLRB4Dwdaxtpm/nVm0nITo/DkYrA8vXtHMY8stWbJ09MLopDd4SakdivdTDEqu52FT5yqchICQqFmjfF4s0sTY90UFynf3ZTMiHPqL/R9tejVj6O04shrGbVUW/5lrA4OKGbD7A93h+9ggJHSleIdCndqTcGcQBo0pEQkjJp18fw==";
        try {
            // 2. 发送请求
            HttpResponse httpResponse = HttpRequest.post(url)
                    .form(formData)
                    .header("Acs-Token", RandomUtil.randomString(1))
                    .timeout(5000)
                    .execute();
            System.out.println("httpResponse: " + httpResponse);
            System.out.println("httpResponse.getStatus(): " + httpResponse.getStatus());
//            System.out.println(httpResponse.body());
            if (httpResponse.getStatus() != HttpStatus.HTTP_OK) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "接口调用失败");
            }
            // 解析响应
            // {"status":0,"msg":"Success","data":{"url":"https://graph.baidu.com/sc","sign":"1262fe97cd54acd88139901734784257"}}
            String body = httpResponse.body();
            Map<String, Object> result = JSONUtil.toBean(body, Map.class);
            System.out.println("body: " + body);
            // 3. 处理响应结果
            if (result == null || !Integer.valueOf(0).equals(result.get("status"))) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "接口调用失败");
            }
            Map<String, Object> data = (Map<String, Object>) result.get("data");
            // 对 URL 进行解码
            String rawUrl = (String) data.get("url");
            String searchResultUrl = URLUtil.decode(rawUrl, StandardCharsets.UTF_8);
            // 如果 URL 为空
            if (StrUtil.isBlank(searchResultUrl)) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "未返回有效的结果地址");
            }
            return searchResultUrl;
        } catch (Exception e) {
            log.error("调用百度以图搜图接口失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "搜索失败");
        }
    }

    public static void main(String[] args) {
        // 测试以图搜图功能
        String imageUrl = "https://www.codefather.cn/logo.png";
        String searchResultUrl = getImagePageUrl(imageUrl);
        System.out.println("搜索成功，结果 URL：" + searchResultUrl);
    }
}


