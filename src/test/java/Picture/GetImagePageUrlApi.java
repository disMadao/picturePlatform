package Picture;

/**
 * @Auther: Mr. Mei
 * @Date: 2025/9/19 22:27
 * @Description:
 */


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
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @Auther: Mr. Mei
 * @Date: 2025/4/29 21:07
 * @Description:
 */
@Slf4j
public class GetImagePageUrlApi {

    /**
     * 获取图片页面地址
     * 百度修改了请求方式，需要知道凭借在请求url里面的sign，这个和 imageUrl 是有关系的，但不知道它是怎么加密的，网页代码里面找不到加密代码。
     *
     * @param imageUrl
     * @return
     */
    public static String getImagePageUrl(String imageUrl) {
        // 1. 准备请求参数
//        Map<String, Object> formData = new HashMap<>();
//        formData.put("image", imageUrl);
//        formData.put("tn", "pc");
//        formData.put("from", "pc");
//        formData.put("image_source", "PC_UPLOAD_URL");
//        formData.put("Cookie","PSTM=1735303929; BAIDUID=96D33E596DF9111806BAF5537B04D7CB:FG=1; BIDUPSID=DEBCA99D8DBDD740FB086562739C882A; BAIDUID_BFESS=96D33E596DF9111806BAF5537B04D7CB:FG=1; ZFY=HHh6yT2MHTj95w3L4p78nanrx2D:ATT2ziqdTvHXE32g:C; H_PS_PSSID=61027_60853_61391_61429_61445_61464_61459_61492_61509_61518_61529_61573_61631; ab_jid=e599ab0849fa5568b6e13d30abdc07928ce8; ab_jid_BFESS=e599ab0849fa5568b6e13d30abdc07928ce8; ab_bid=ab0849fa5568b6e13d30abdc07928ce9ce81; ab_sr=1.0.1_ZmIzODQ2NWNkNzc2MjgzMGFkYjUwNDhlNzQ2ZDhkZWRkZTNmNzUwOWI4MTg0OTBiM2ZjNDcwNjlmNWNjN2I2ZGViNGIwYjk3MWRiNGQ4ZDA3MTE0ODgyMDllNTM2NGJkYTNkMzI3NjA4ZWFjYmEzYjU5MjIyNjViZTYwODRjYmFlYmQzODMyNGJmODgyMjMzMWNlMDdhMjYwZDJjYmQwNw==");
//        formData.put("User-Agent","Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/104.0.0.0 Safari/537.36");

        // 请求地址
        String url = "https://graph.baidu.com/ajax/pcsimi?carousel=503&entrance=GENERAL&extUiData[isLogoShow]=1&inspire=general_pc&limit=30&next=2&render_type=card&sign=126b36c6caa88da46168301758293284&tk=20025&tpl_from=pc&page=1&";

        try {
            // 2. 发送 GET 请求到百度接口
            HttpResponse response = HttpRequest.get(url)
                    .header("y", "graph.baidu.com")
                    .header("accept", "*/*")
//                    .header("accept-encoding", "gzip, deflate, br")
                    .header("accept-language", "zh-CN,zh;q=0.9")
                    .header("content-type", "application/json; charset=UTF-8")
//                    .header("cookie", "PSTM=1735303929; BAIDUID=96D33E596DF9111806BAF5537B04D7CB:FG=1; BIDUPSID=DEBCA99D8DBDD740FB086562739C882A; BAIDUID_BFESS=96D33E596DF9111806BAF5537B04D7CB:FG=1; ZFY=HHh6yT2MHTj95w3L4p78nanrx2D:ATT2ziqdTvHXE32g:C; H_PS_PSSID=61027_60853_61391_61429_61445_61464_61459_61492_61509_61518_61529_61573_61631; antispam_key_id=23; H_WISE_SIDS=110085_644682_646538_646559_650476_652588_656456_655951_660624_658259_660926_661763_662202_662209_662216_662994_664617_665190_665223_665265_666425_666166_666654_666666_666669_666665_666604_666610_666612_666856_666372_666464_666557_666581_667432_667683_667677_668048_668158_662823_668345_668382_668390_667868_668516_668508_668522_668777_668646_668829_668868_668760_668757_668908_668900_668894_668893_668896_668965_668872_668980_669113_669112_669110_669065_667928_669166_669195_669268_667196_667207_667212_669314_667884_669378_669402_669397_667566_669420_669526_669555_669558_669528_669539_669517_669509_669515_663788_669503_669628_667053_669732_669738_669857_669825_669963_665292_670035_669685_669681_670043_670069_670077_670067_670078_670074_670072_670070_669812; H_WISE_SIDS_BFESS=110085_644682_646538_646559_650476_652588_656456_655951_660624_658259_660926_661763_662202_662209_662216_662994_664617_665190_665223_665265_666425_666166_666654_666666_666669_666665_666604_666610_666612_666856_666372_666464_666557_666581_667432_667683_667677_668048_668158_662823_668345_668382_668390_667868_668516_668508_668522_668777_668646_668829_668868_668760_668757_668908_668900_668894_668893_668896_668965_668872_668980_669113_669112_669110_669065_667928_669166_669195_669268_667196_667207_667212_669314_667884_669378_669402_669397_667566_669420_669526_669555_669558_669528_669539_669517_669509_669515_663788_669503_669628_667053_669732_669738_669857_669825_669963_665292_670035_669685_669681_670043_670069_670077_670067_670078_670074_670072_670070_669812; BA_HECTOR=ag2g040hakah810101808l0la5alaj1kcqr7s24; BDORZ=AE84CDB3A529C0F8A2B9DCDD1D18B695; SE_LAUNCH=5%3A29304887; rsv_i=b5eeW6VckXULteMu8CapuU1gYsEnbMq9AQW79aBf8eoJD1WYpdxdfVl22PjZgap7cQDRGzIsBRNE9fe+QhKBq90PaiXxAjg; antispam_data=4af1321309105255df53e1c07c4539bcf81ffcc82822f08906d248593b977c9376fa04148ecea1eefecd53642338e60bc6a38cc3e3cf2eaf625aa1e54566bd52fd79ccf6f77531c2f7e20509119529a2; ab_sr=1.0.1_YzI3YjM2OWJiZDAyZTZkNzQ5MTcyZjYyM2YwNWUzZmUwY2IxNDkyMWE0MTJmYTJhOTgzZDNjMzEwODVhNjRkYjc2NjMyYzM4YTQ5ZTg2NmEwMjc5YWQ3MWFhMjllYTAxYTM5ZjFhMDQ5MDlkNTBhMTg3YmY4NTBiZTM3YTI2Y2YxODFhMzc4ZGQ1NWIwMDQyY2RhZGMyMzA0NWJlOGIxYw==")
//                    .header("referer", "https://graph.baidu.com/s?card_key=&entrance=GENERAL&extUiData%5BisLogoShow%5D=1&f=all&isLogoShow=1&session_id=6036889207950321274&sign=126b36c6caa88da46168301758293284&tpl_from=pc")
                    .header("sec-ch-ua", "\"Chromium\";v=\"104\", \" Not A;Brand\";v=\"99\", \"Google Chrome\";v=\"104\"")
                    .header("sec-ch-ua-mobile", "?0")
                    .header("sec-ch-ua-platform", "\"Windows\"")
                    .header("sec-fetch-dest", "empty")
                    .header("sec-fetch-mode", "cors")
                    .header("sec-fetch-site", "same-origin")
                    .header("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/104.0.0.0 Safari/537.36")
                    .timeout(10000)
                    .execute();

            // 判断响应状态
            if (HttpStatus.HTTP_OK != response.getStatus()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "接口调用失败");
            }
            // 解析响应
            String responseBody = response.body();
//            System.out.println(response.getStatus());
//            System.out.println(response.charset());
//
//            System.out.println(responseBody);

            Map<String, Object> result = JSONUtil.toBean(responseBody, Map.class);
//            for (String i : result.keySet()) {
//                System.out.println(i+", "+result.get(i));
//            }

            // 3. 处理响应结果
            if (result == null || !Integer.valueOf(0).equals(result.get("status"))) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "接口调用失败");
            }
            Map<String, Object> data = (Map<String, Object>) result.get("data");
//            System.out.println(data.get("list"));
//            System.out.println(data.get("list").getClass());
            String tgStr = JSONUtil.toJsonStr(data.get("list"));
            tgStr = tgStr.substring(1, tgStr.length() - 1); //去掉两个中括号，这里的代码都要结合那个响应格式来看才能看懂
//            System.out.println("tgStr = "+tgStr);
            String[] arr_tgStr = tgStr.split("},\\{");
//            System.out.println(arr_tgStr.length);
//            System.out.println(arr_tgStr[0]);
//            System.out.println("----------");
            String rawUrl = "";
            for (String tgStr1 : arr_tgStr) {
//                System.out.println(tgStr1);

                String thumbUrl = tgStr1.substring(tgStr1.indexOf("\"thumbUrl\":") + 12, tgStr1.indexOf("\",\"hoverUrl\":\"\""));
                System.out.println(thumbUrl);
                if (rawUrl.equals("")) {
                    rawUrl = thumbUrl;//这里只需要一张，所以返回第一张
                }
            }
//            System.out.println("-----------------");
//            for (String i : picList.keySet()) {
//                System.out.println(i+", "+picList.get(i));
//            }
//            String rawUrl = arr_tgStr[0];//这里只返回第一张
            // 对 URL 进行解码
            String searchResultUrl = URLUtil.decode(rawUrl, StandardCharsets.UTF_8);
            // 如果 URL 为空
            if (searchResultUrl == null) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "未返回有效结果");
            }
            return searchResultUrl;
        } catch (Exception e) {
            log.error("搜索失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "搜索失败");
        }
    }

    public static void main(String[] args) {
        // 测试以图搜图功能
        String imageUrl = "https://www.codefather.cn/logo.png";
        String result = getImagePageUrl(imageUrl);
        System.out.println("搜索成功，结果 URL：" + result);
    }
}