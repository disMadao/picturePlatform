import json
from typing import Any, Dict, Optional

import pymysql

from .agent import AgentResult
from .config import config
from .schemas import AgentSearchRequest


def _get_connection():
    """
    获取到 yu_picture 数据库的连接。
    默认使用与 Java 后端一致的配置（可通过环境变量覆盖），
    不做连接池，便于理解和后期按需替换。
    """
    return pymysql.connect(
        host=config.db_host,
        port=config.db_port,
        user=config.db_user,
        password=config.db_password,
        database=config.db_name,
        charset="utf8mb4",
        cursorclass=pymysql.cursors.DictCursor,
    )


def log_search(
    user_id: Optional[int],
    request: AgentSearchRequest,
    result: AgentResult,
) -> None:
    """
    将本次 Agent 搜索记录到数据库：
    - agent_session 一条（当前简单地“一次请求 = 一次会话”）
    - agent_message 两条（user / assistant）
    """
    conn = _get_connection()
    try:
        with conn.cursor() as cursor:
            # 1. 创建会话
            title = (request.query_text or request.image_url or "").strip()
            if len(title) > 50:
                title = title[:50]
            cursor.execute(
                """
                INSERT INTO agent_session (userId, title, status)
                VALUES (%s, %s, %s)
                """,
                (user_id, title or None, 1),
            )
            session_id = cursor.lastrowid

            # 2. 写入用户消息
            user_content = json.dumps(
                {
                    "query_text": request.query_text,
                    "image_url": request.image_url,
                    "mode": request.mode.value,
                    "top_k": request.top_k,
                },
                ensure_ascii=False,
            )
            cursor.execute(
                """
                INSERT INTO agent_message (sessionId, userId, role, content)
                VALUES (%s, %s, %s, %s)
                """,
                (session_id, user_id, "user", user_content),
            )

            # 3. 写入 Agent 回复（只保存核心信息，步骤和模式等放在 extra 字段）
            extra: Dict[str, Any] = {
                "mode": result.mode.value,
                "steps": [step.__dict__ for step in result.steps],
                "picture_ids": [pic.get("id") for pic in (result.pictures or [])],
            }
            assistant_content = f"使用模式：{result.mode.value}，返回图片数量：{len(result.pictures)}"
            cursor.execute(
                """
                INSERT INTO agent_message (sessionId, userId, role, content, extra)
                VALUES (%s, %s, %s, %s, %s)
                """,
                (session_id, user_id, "assistant", assistant_content, json.dumps(extra, ensure_ascii=False)),
            )

        conn.commit()
    finally:
        conn.close()


