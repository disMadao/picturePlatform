from __future__ import annotations

import json
import logging
from typing import Any, Dict, List, Optional

import pymysql

from .config import config

logger = logging.getLogger(__name__)


def _get_connection():
    return pymysql.connect(
        host=config.db_host,
        port=config.db_port,
        user=config.db_user,
        password=config.db_password,
        database=config.db_name,
        charset="utf8mb4",
        cursorclass=pymysql.cursors.DictCursor,
    )


# --------------- 对话管理 ---------------

def create_conversation(user_id: int, title: Optional[str] = None) -> int:
    conn = _get_connection()
    try:
        with conn.cursor() as cur:
            cur.execute(
                "INSERT INTO agent_conversation (userId, title) VALUES (%s, %s)",
                (user_id, title),
            )
        conn.commit()
        return cur.lastrowid  # type: ignore[return-value]
    finally:
        conn.close()


def list_conversations(user_id: int) -> List[Dict[str, Any]]:
    conn = _get_connection()
    try:
        with conn.cursor() as cur:
            cur.execute(
                """
                SELECT id, userId, title, createTime, updateTime
                FROM agent_conversation
                WHERE userId = %s AND isDelete = 0
                ORDER BY updateTime DESC
                """,
                (user_id,),
            )
            rows = cur.fetchall()
            for r in rows:
                for k in ("createTime", "updateTime"):
                    if r.get(k):
                        r[k] = str(r[k])
            return rows
    finally:
        conn.close()


def delete_conversation(user_id: int, conversation_id: int) -> bool:
    conn = _get_connection()
    try:
        with conn.cursor() as cur:
            affected = cur.execute(
                "UPDATE agent_conversation SET isDelete = 1 WHERE id = %s AND userId = %s",
                (conversation_id, user_id),
            )
        conn.commit()
        return affected > 0
    finally:
        conn.close()


def update_conversation_title(conversation_id: int, title: str) -> None:
    conn = _get_connection()
    try:
        with conn.cursor() as cur:
            cur.execute(
                "UPDATE agent_conversation SET title = %s WHERE id = %s",
                (title, conversation_id),
            )
        conn.commit()
    finally:
        conn.close()


def touch_conversation(conversation_id: int) -> None:
    """更新 updateTime，让最近活跃的对话排在前面。"""
    conn = _get_connection()
    try:
        with conn.cursor() as cur:
            cur.execute(
                "UPDATE agent_conversation SET updateTime = NOW() WHERE id = %s",
                (conversation_id,),
            )
        conn.commit()
    finally:
        conn.close()


# --------------- 消息管理 ---------------

def add_message(
    conversation_id: int,
    role: str,
    content: Optional[str] = None,
    content_type: str = "text",
    extra: Optional[Dict[str, Any]] = None,
) -> int:
    conn = _get_connection()
    try:
        extra_json = json.dumps(extra, ensure_ascii=False) if extra else None
        with conn.cursor() as cur:
            cur.execute(
                """
                INSERT INTO agent_message (conversationId, role, contentType, content, extra)
                VALUES (%s, %s, %s, %s, %s)
                """,
                (conversation_id, role, content_type, content, extra_json),
            )
        conn.commit()
        return cur.lastrowid  # type: ignore[return-value]
    finally:
        conn.close()


def list_messages(conversation_id: int) -> List[Dict[str, Any]]:
    conn = _get_connection()
    try:
        with conn.cursor() as cur:
            cur.execute(
                """
                SELECT id, conversationId, role, contentType, content, extra, createTime
                FROM agent_message
                WHERE conversationId = %s
                ORDER BY createTime ASC
                """,
                (conversation_id,),
            )
            rows = cur.fetchall()
            for r in rows:
                if r.get("createTime"):
                    r["createTime"] = str(r["createTime"])
                if isinstance(r.get("extra"), str):
                    try:
                        r["extra"] = json.loads(r["extra"])
                    except Exception:
                        pass
            return rows
    finally:
        conn.close()


def get_recent_messages(conversation_id: int, limit: int = 20) -> List[Dict[str, Any]]:
    """取最近 N 条消息，用于拼 LLM 上下文。返回按时间正序排列。"""
    conn = _get_connection()
    try:
        with conn.cursor() as cur:
            cur.execute(
                """
                SELECT role, contentType, content, extra
                FROM (
                    SELECT role, contentType, content, extra, createTime
                    FROM agent_message
                    WHERE conversationId = %s
                    ORDER BY createTime DESC
                    LIMIT %s
                ) t
                ORDER BY createTime ASC
                """,
                (conversation_id, limit),
            )
            rows = cur.fetchall()
            for r in rows:
                if isinstance(r.get("extra"), str):
                    try:
                        r["extra"] = json.loads(r["extra"])
                    except Exception:
                        pass
            return rows
    finally:
        conn.close()
