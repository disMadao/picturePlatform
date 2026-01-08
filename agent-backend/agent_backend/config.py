import os

from pydantic import BaseModel


class BackendConfig(BaseModel):
    """
    Agent 后端基础配置（可根据自己的环境修改）。
    """

    # Java 原有后端地址（用于调用图片查询等接口，等价于前端的 /api 前缀）
    java_base_url: str = "http://localhost:8123/api"

    # 向量数据库服务地址（这里只是占位，不做真实连接）
    vector_base_url: str = "http://localhost:9002"

    # DeepSeek API 配置：默认读取环境变量，避免把密钥写进代码
    deepseek_api_base: str = os.getenv("DEEPSEEK_API_BASE", "https://api.deepseek.com")
    deepseek_api_key: str | None = os.getenv("DEEPSEEK_API_KEY")
    deepseek_model: str = os.getenv("DEEPSEEK_MODEL", "deepseek-chat")

    # MySQL 数据库配置（默认与 Java 后端相同，可通过环境变量覆盖）
    db_host: str = os.getenv("MYSQL_HOST", "127.0.0.1")
    db_port: int = int(os.getenv("MYSQL_PORT", "3306"))
    db_user: str = os.getenv("MYSQL_USER", "root")
    db_password: str = os.getenv("MYSQL_PASSWORD", "root")
    db_name: str = os.getenv("MYSQL_DB", "yu_picture")


config = BackendConfig()


