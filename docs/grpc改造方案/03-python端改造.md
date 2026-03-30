# 03 - Python 端改造（参考示例）

以下代码为 **落地参考**，用于 `agent-backend`；**不要**在未评审时直接替换现有 `java_client.py`，建议新建模块并通过配置开关切换。

## 1. 依赖

在 `agent-backend` 的依赖文件（如 `requirements.txt`）中增加：

```
grpcio>=1.62.0
grpcio-tools>=1.62.0
```

安装：

```bash
pip install grpcio grpcio-tools
```

## 2. 从 .proto 生成 Python 代码

将 **与 Java 同一份** `.proto` 放到例如 `agent-backend/proto/picture_agent/v1/`。

在项目根目录执行（路径按实际调整）：

```bash
python -m grpc_tools.protoc \
  -I agent-backend/proto \
  --python_out=agent-backend/agent_backend/grpc_gen \
  --grpc_python_out=agent-backend/agent_backend/grpc_gen \
  agent-backend/proto/picture_agent/v1/picture_agent.proto
```

会生成 `picture_agent_pb2.py` 与 `picture_agent_pb2_grpc.py`（具体文件名取决于 `package` 与 `option`）。将 `grpc_gen` 作为包使用时，注意 **`__init__.py`** 与 `PYTHONPATH`。

## 3. 客户端封装（示例）

新建文件示例：`agent_backend/grpc_java_client.py`（**新建**，不覆盖 `java_client.py`）

```python
from __future__ import annotations

import os
from typing import Any, Dict, List, Optional

import grpc

# 假设生成代码位于 agent_backend.grpc_gen
from agent_backend.grpc_gen.picture_agent.v1 import picture_agent_pb2 as pb2
from agent_backend.grpc_gen.picture_agent.v1 import picture_agent_pb2_grpc as pb2_grpc


def _metadata() -> tuple[tuple[str, str], ...]:
    token = os.getenv("AGENT_INTERNAL_TOKEN", "").strip()
    if not token:
        raise RuntimeError("AGENT_INTERNAL_TOKEN is not set")
    return (("x-internal-token", token),)


class GrpcJavaBackendClient:
    """与 JavaBackendClient 能力对齐的 gRPC 版本（示例）。"""

    def __init__(self, target: str | None = None) -> None:
        # 例：JAVA_GRPC_TARGET=127.0.0.1:9090
        self._target = target or os.getenv("JAVA_GRPC_TARGET", "127.0.0.1:9090")
        self._channel = grpc.insecure_channel(self._target)
        self._stub = pb2_grpc.PictureAgentServiceStub(self._channel)

    def close(self) -> None:
        self._channel.close()

    def list_picture_vo_by_text(
        self,
        search_text: str,
        page: int = 1,
        page_size: int = 20,
    ) -> List[Dict[str, Any]]:
        req = pb2.ListPicturesRequest(
            current=page,
            page_size=page_size,
            search_text=search_text,
        )
        resp = self._stub.ListPictures(req, metadata=_metadata(), timeout=10.0)
        if resp.code != 0:
            raise RuntimeError(resp.message or "ListPictures failed")
        # 将 protobuf Message 转为 dict 列表，视 PictureVo 字段而定
        return [self._picture_vo_to_dict(p) for p in resp.records]

    def get_picture_vo_by_id(self, picture_id: int | str) -> Optional[Dict[str, Any]]:
        req = pb2.GetPictureRequest(id=int(picture_id))
        resp = self._stub.GetPicture(req, metadata=_metadata(), timeout=10.0)
        if resp.code != 0:
            return None
        if not resp.HasField("data"):
            return None
        return self._picture_vo_to_dict(resp.data)

    def persist_agent_video(self, body: Dict[str, Any]) -> Dict[str, Any]:
        # 将 body 映射到 PersistAgentVideoRequest；字段名与 proto 一致
        req = pb2.PersistAgentVideoRequest(
            temp_video_url=body.get("tempVideoUrl") or body.get("temp_video_url", ""),
            space_id=int(body.get("spaceId") or body.get("space_id", 0)),
        )
        resp = self._stub.PersistAgentVideo(
            req,
            metadata=_metadata(),
            timeout=600.0,  # 与现有 HTTP 长超时一致
        )
        if resp.code != 0:
            raise RuntimeError(resp.message or "PersistAgentVideo failed")
        return {"generatedVideoId": resp.generated_video_id}

    @staticmethod
    def _picture_vo_to_dict(vo: pb2.PictureVo) -> Dict[str, Any]:
        # 示例：可用 MessageToDict（google.protobuf.json_format）简化
        from google.protobuf.json_format import MessageToDict
        return MessageToDict(vo, preserving_proto_field_name=True)
```

> 注意：`PersistAgentVideoRequest` 的字段必须与 Java 侧 `AgentVideoPersistRequest` 及 proto 定义一致；上面仅为演示，**需按真实 JSON body 调整**。

## 4. 与 `java_client` 切换（配置开关，示例）

不修改原类逻辑时，可用环境变量在 **入口** 选择实现：

```python
import os

USE_GRPC_JAVA = os.getenv("USE_GRPC_JAVA", "").lower() in ("1", "true", "yes")

if USE_GRPC_JAVA:
    from agent_backend.grpc_java_client import GrpcJavaBackendClient
    java_client = GrpcJavaBackendClient()
else:
    from agent_backend.java_client import JavaBackendClient
    java_client = JavaBackendClient()
```

实际工程中建议放在单独模块，避免循环导入。

## 5. TLS（可选，生产）

内网若启用 **mTLS**，需将 `grpc.insecure_channel` 换为 `grpc.secure_channel`，并传入 `grpc.ssl_channel_credentials` 与客户端证书。文档级别仅提示，具体证书管理由运维约定。

## 6. 联调检查清单

- `JAVA_GRPC_TARGET` 指向 Java gRPC 监听地址。
- `AGENT_INTERNAL_TOKEN` 与 Java `agent.internalToken` 一致。
- `persist` 类调用 **deadline ≥ 600s**（或与业务一致）。
- 对比同一条请求在 HTTP 与 gRPC 下的返回数据是否一致。
