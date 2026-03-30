# 02 - Java 端改造（参考示例）

以下代码为 **落地参考**，包名、类名需与 `picture-backend` 实际工程一致；**不要**在未评审时直接覆盖现有 Controller / Filter。

## 1. Maven 依赖（示例）

在 `picture-backend/pom.xml` 中增加（版本号请按当前 Spring Boot 与社区实践选择）：

```xml
<!-- gRPC BOM 可选，用于统一版本 -->
<dependency>
    <groupId>io.grpc</groupId>
    <artifactId>grpc-netty-shaded</artifactId>
    <version>${grpc.version}</version>
</dependency>
<dependency>
    <groupId>io.grpc</groupId>
    <artifactId>grpc-protobuf</artifactId>
    <version>${grpc.version}</version>
</dependency>
<dependency>
    <groupId>io.grpc</groupId>
    <artifactId>grpc-stub</artifactId>
    <version>${grpc.version}</version>
</dependency>
<dependency>
    <groupId>com.google.protobuf</groupId>
    <artifactId>protobuf-java</artifactId>
    <version>${protobuf.version}</version>
</dependency>
```

或使用 **`net.devh:grpc-server-spring-boot-starter`** 简化与 Spring 的集成（需查阅其文档配置端口与拦截器）。

## 2. 构建：从 .proto 生成 Java

使用 `protobuf-maven-plugin` + `os-maven-plugin`，在 `src/main/proto` 放置 `.proto`，构建时生成 `target/generated-sources/protobuf/java`。

`pom.xml` 片段（示例）：

```xml
<build>
    <extensions>
        <extension>
            <groupId>kr.motd.maven</groupId>
            <artifactId>os-maven-plugin</artifactId>
            <version>1.7.1</version>
        </extension>
    </extensions>
    <plugins>
        <plugin>
            <groupId>org.xolstice.maven.plugins</groupId>
            <artifactId>protobuf-maven-plugin</artifactId>
            <version>0.6.1</version>
            <configuration>
                <protocArtifact>com.google.protobuf:protoc:3.25.1:exe:${os.detected.classifier}</protocArtifact>
                <pluginId>grpc-java</pluginId>
                <pluginArtifact>io.grpc:protoc-gen-grpc-java:1.62.2:exe:${os.detected.classifier}</pluginArtifact>
            </configuration>
            <executions>
                <execution>
                    <goals>
                        <goal>compile</goal>
                        <goal>compile-custom</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

## 3. .proto 片段（示例）

文件路径示例：`src/main/proto/picture_agent/v1/picture_agent.proto`

```protobuf
syntax = "proto3";

package picture.agent.v1;

option java_multiple_files = true;
option java_package = "com.yupi.yupicturebackend.grpc.v1";
option java_outer_classname = "PictureAgentProto";

service PictureAgentService {
  rpc ListPictures (ListPicturesRequest) returns (ListPicturesResponse);
  rpc GetPicture (GetPictureRequest) returns (GetPictureResponse);
  rpc PersistAgentVideo (PersistAgentVideoRequest) returns (PersistAgentVideoResponse);
}

message ListPicturesRequest {
  int32 current = 1;
  int32 page_size = 2;
  string search_text = 3;
}

message ListPicturesResponse {
  int32 code = 1;
  string message = 2;
  repeated PictureVo records = 3;
}

message GetPictureRequest {
  int64 id = 1;
}

message GetPictureResponse {
  int32 code = 1;
  string message = 2;
  PictureVo data = 3;
}

message PictureVo {
  int64 id = 1;
  // ... 与现有 VO 对齐的字段
}

message PersistAgentVideoRequest {
  // 与 AgentVideoPersistRequest 对齐；此处仅示意
  string temp_video_url = 1;
  int64 space_id = 2;
}

message PersistAgentVideoResponse {
  int32 code = 1;
  string message = 2;
  int64 generated_video_id = 3;
}
```

实际字段请以 `AgentVideoPersistRequest`、`PictureVO` 等为准补全。

## 4. ServerInterceptor：校验内部 Token

```java
package com.yupi.yupicturebackend.grpc;

import io.grpc.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class InternalTokenGrpcInterceptor implements ServerInterceptor {

    public static final Metadata.Key<String> X_INTERNAL_TOKEN =
            Metadata.Key.of("x-internal-token", Metadata.ASCII_STRING_MARSHALLER);

    @Value("${agent.internalToken:}")
    private String expectedToken;

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        String token = headers.get(X_INTERNAL_TOKEN);
        if (expectedToken == null || expectedToken.isEmpty()
                || token == null || !expectedToken.equals(token)) {
            call.close(Status.UNAUTHENTICATED.withDescription("invalid X-Internal-Token"), new Metadata());
            return new ServerCall.Listener<>() {};
        }
        return next.startCall(call, headers);
    }
}
```

若还需「等价于 Filter 中的管理员身份」，可在此处在 `Context` 中挂载 `userId`（需与业务层约定读取方式）。

## 5. gRPC 服务实现：委托现有 Service（示例）

```java
package com.yupi.yupicturebackend.grpc;

import com.yupi.yupicturebackend.grpc.v1.PictureAgentServiceGrpc;
import com.yupi.yupicturebackend.grpc.v1.*;
import com.yupi.yupicturebackend.service.GeneratedVideoService;
import com.yupi.yupicturebackend.service.PictureService;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 示例：若使用 grpc-spring-boot-starter，可用 @GrpcService。
 * 实现内调用现有 PictureService / GeneratedVideoService，逻辑与 REST Controller 对齐。
 */
@GrpcService
public class PictureAgentGrpcService extends PictureAgentServiceGrpc.PictureAgentServiceImplBase {

    @Autowired
    private PictureService pictureService;

    @Autowired
    private GeneratedVideoService generatedVideoService;

    @Override
    public void listPictures(ListPicturesRequest request, StreamObserver<ListPicturesResponse> responseObserver) {
        try {
            // 将 request 转为现有 DTO，调用 pictureService 分页方法，再组装 ListPicturesResponse
            ListPicturesResponse.Builder b = ListPicturesResponse.newBuilder().setCode(0);
            // b.addAllRecords(...);
            responseObserver.onNext(b.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void getPicture(GetPictureRequest request, StreamObserver<GetPictureResponse> responseObserver) {
        // 类似：pictureService 按 id 查询，填充 PictureVo
    }

    @Override
    public void persistAgentVideo(PersistAgentVideoRequest request, StreamObserver<PersistAgentVideoResponse> responseObserver) {
        try {
            // 构建 AgentVideoPersistRequest，调用 generatedVideoService.persistFromAgentTempUrl(...)
            PersistAgentVideoResponse resp = PersistAgentVideoResponse.newBuilder()
                    .setCode(0)
                    .build();
            responseObserver.onNext(resp);
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }
}
```

## 6. 配置（示例）

`application.yml`：

```yaml
grpc:
  server:
    port: 9090

agent:
  internalToken: ${AGENT_INTERNAL_TOKEN:}
```

## 7. 与现有代码的关系

- **不要删除** 现有 `VideoController`、`AgentInternalAuthFilter`，在过渡期内保留 HTTP。
- gRPC 实现应 **复用** `GeneratedVideoService`、`PictureService` 等，避免两套业务逻辑。
- 若业务代码从 `RequestContextHolder` / Session 取用户，需为 gRPC 请求增加 **统一的「内部调用用户」注入**（例如在拦截器里设置 ThreadLocal，再在 Service 中读取）。
