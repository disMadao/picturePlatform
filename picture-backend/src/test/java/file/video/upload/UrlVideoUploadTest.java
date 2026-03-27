package file.video.upload;

import cn.hutool.http.HttpUtil;
import com.qcloud.cos.model.PutObjectResult;
import com.yupi.yupicturebackend.config.CosClientConfig;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.manager.CosManager;
import com.yupi.yupicturebackend.manager.upload.UrlVideoUpload;
import com.yupi.yupicturebackend.model.dto.file.UploadVideoResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UrlVideoUploadTest {

    @Mock
    private CosClientConfig cosClientConfig;

    @Mock
    private CosManager cosManager;

    @InjectMocks
    private UrlVideoUpload urlVideoUpload;

    @Test
    void uploadVideo_Success() throws Exception {
        // 模拟静态方法 HttpUtil.downloadFile（避免真实下载）
        try (MockedStatic<HttpUtil> mockedHttp = mockStatic(HttpUtil.class)) {
            mockedHttp.when(() -> HttpUtil.downloadFile(anyString(), any(File.class)))
                    .thenAnswer(invocation -> {
                        File file = invocation.getArgument(1);
                        file.createNewFile(); // 模拟创建临时文件
                        return null;
                    });

            // 使用你实际的 TOS 域名
            String realHost = "https://ark-content-generation-cn-beijing.tos-cn-beijing.volces.com";
            when(cosClientConfig.getHost()).thenReturn(realHost);

            PutObjectResult mockResult = new PutObjectResult();
            when(cosManager.putObject(anyString(), any(File.class))).thenReturn(mockResult);

            // 使用你提供的视频链接作为输入源
            String videoUrl = "这里git居然把seedance的视频连接识别成了api_key?!";

            UploadVideoResult result = urlVideoUpload.uploadVideo(videoUrl, "test/prefix");

            assertNotNull(result);
            // 验证返回的 URL 以你的域名开头
            assertTrue(result.getUrl().startsWith(realHost + "/test/prefix/"));
            // 验证原始文件名（从 URL 中提取）
            assertEquals("02177289687650200000000000000000000ffffac191d36eedd28.mp4", result.getOriginalName());
            verify(cosManager, times(1)).putObject(anyString(), any(File.class));
        }
    }

    @Test
    void uploadVideo_InvalidUrl_ThrowsException() {
        String invalidUrl = "not a url";
        assertThrows(BusinessException.class, () -> {
            urlVideoUpload.uploadVideo(invalidUrl, "test/prefix");
        });
    }
}