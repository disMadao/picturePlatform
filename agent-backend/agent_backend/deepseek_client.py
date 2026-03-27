from __future__ import annotations

from typing import Any, Dict, List, Literal, Optional

import httpx

from .config import config


class DeepSeekClient:
    """
    DeepSeek LLM 简单封装（仅用于路由决策等 Agent 场景）。
    默认不在代码中硬编码密钥，请在运行时通过环境变量或配置注入 config.deepseek_api_key。
    """

    def __init__(
        self,
        api_base: str | None = None,
        api_key: str | None = None,
        model: str | None = None,
    ) -> None:
        self.api_base = api_base or config.deepseek_api_base
        self.api_key = api_key or config.deepseek_api_key
        self.model = model or config.deepseek_model
        self._client = httpx.Client(timeout=20.0)

    def _headers(self) -> Dict[str, str]:
        if not self.api_key:
            raise RuntimeError("DeepSeek API key is not configured")
        return {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json",
        }

    def decide_search_mode(
        self,
        query_text: str,
        has_image: bool,
    ) -> Literal["backend", "vector_text", "vector_image"]:
        """
        使用 DeepSeek 基于自然语言自动判断应走哪种搜索方式。
        - backend: 使用原有 Java 后端的关键词检索
        - vector_text: 使用向量库文本搜图
        - vector_image: 使用向量库以图搜图
        """
        system_prompt = (
            "你是图片搜索路由助手，只回答 backend / vector_text / vector_image 之一。\n"
            "backend：用户想按标题、简介等普通字段搜索；\n"
            "vector_text：用户想按语义、风格、场景等更智能的方式文本搜图；\n"
            "vector_image：用户上传或引用图片，希望根据图片找相似图。\n"
            "注意：如果 has_image 为 true，更优先考虑 vector_image。"
        )
        user_prompt = f"用户查询：{query_text!r}。has_image={has_image}。只输出一个单词：backend 或 vector_text 或 vector_image。"

        payload: Dict[str, Any] = {
            "model": self.model,
            "messages": [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt},
            ],
            "temperature": 0,
        }
        # 这里只是占位实现，实际调用时请确保已经在运行环境中配置好 API Key。
        resp = self._client.post(
            f"{self.api_base.rstrip('/')}/chat/completions",
            headers=self._headers(),
            json=payload,
        )
        resp.raise_for_status()
        data = resp.json()
        content: str = data["choices"][0]["message"]["content"].strip()
        if "vector_image" in content:
            return "vector_image"
        if "vector_text" in content:
            return "vector_text"
        return "backend"


    def chat_with_history(
        self,
        messages: List[Dict[str, str]],
        system_prompt: str,
    ) -> str:
        """
        带历史消息的对话补全。
        messages: [{"role": "user"/"assistant", "content": "..."}]
        返回 LLM 的文本回复。
        """
        full_messages = [{"role": "system", "content": system_prompt}] + messages
        payload: Dict[str, Any] = {
            "model": self.model,
            "messages": full_messages,
            "temperature": 0.7,
        }
        resp = self._client.post(
            f"{self.api_base.rstrip('/')}/chat/completions",
            headers=self._headers(),
            json=payload,
            timeout=30.0,
        )
        resp.raise_for_status()
        data = resp.json()
        return data["choices"][0]["message"]["content"].strip()


deepseek_client = DeepSeekClient()


