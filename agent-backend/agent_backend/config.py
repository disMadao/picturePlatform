import os

from pydantic import BaseModel


class BackendConfig(BaseModel):
    """
    Agent 后端基础配置（可根据自己的环境修改）。
    """

    # Java 原有后端地址（用于调用图片查询等接口，等价于前端的 /api 前缀）
    # 注意：在 Docker 容器中，使用 127.0.0.1 可以让 Java 端识别为本地请求
    java_base_url: str = os.getenv("JAVA_BASE_URL", "http://127.0.0.1:8123/api")

    # 向量数据库服务地址（这里只是占位，不做真实连接）
    vector_base_url: str = os.getenv("VECTOR_BASE_URL", "http://localhost:9002")

    # DeepSeek API 配置：默认读取环境变量，避免把密钥写进代码
    deepseek_api_base: str = os.getenv("DEEPSEEK_API_BASE", "https://api.deepseek.com")
    deepseek_api_key: str | None = os.getenv("DEEPSEEK_API_KEY")
    deepseek_model: str = os.getenv("DEEPSEEK_MODEL", "deepseek-chat")

    # MySQL 数据库配置（默认与 Java 后端相同，可通过环境变量覆盖）
    db_host: str = os.getenv("MYSQL_HOST", "127.0.0.1")
    db_port: int = int(os.getenv("MYSQL_PORT", "3306"))
    db_user: str = os.getenv("MYSQL_USER", "root") # 这里暂时改成用本地的，后面上传记得改回线上的
    db_password: str = os.getenv("MYSQL_PASSWORD","root")
    # db_user: str = os.getenv("MYSQL_USER", "meizijun")
    # db_password: str = os.getenv("MYSQL_PASSWORD", "meizijun_mima_123")
    db_name: str = os.getenv("MYSQL_DB", "yu_picture")

    # 方舟视频生成（Seedance 等，需 pip install 'volcengine-python-sdk[ark]'）
    ark_base_url: str = os.getenv(
        "ARK_BASE_URL", "https://ark.cn-beijing.volces.com/api/v3"
    )
    # 与 doubao_sendance_test 中模型 ID 一致，可在控制台替换
    ark_video_model: str = os.getenv(
        "ARK_VIDEO_MODEL", "doubao-seedance-1-5-pro-251215"
    )
    ark_poll_interval_sec: float = float(os.getenv("ARK_POLL_INTERVAL_SEC", "3"))


config = BackendConfig()


