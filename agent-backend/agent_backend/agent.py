from __future__ import annotations

import json
import logging
import re
from dataclasses import dataclass, field
from enum import Enum
from typing import Any, Dict, List, Optional
from concurrent.futures import ThreadPoolExecutor, as_completed

from .deepseek_client import deepseek_client
from .java_client import java_client
from .vector_store import VectorSearchResult, vector_client
from . import conversation as conv_db

logger = logging.getLogger(__name__)


class SearchMode(str, Enum):
    AUTO = "auto"
    BACKEND = "backend"
    VECTOR_TEXT = "vector_text"
    VECTOR_IMAGE = "vector_image"


@dataclass
class ReActStep:
    """
    ReAct 轨迹中的一个步骤，便于前端或调试查看 Agent 决策过程。
    """

    thought: str
    action: str
    observation: str


@dataclass
class AgentResult:
    """
    Agent 返回结果结构。
    """

    mode: SearchMode
    pictures: List[Dict[str, Any]] = field(default_factory=list)
    steps: List[ReActStep] = field(default_factory=list)


class PictureSearchAgent:
    """
    图片搜索 Agent，实现简单的 ReAct 风格决策：
    Thought -> Action (调用工具：Java 后端 / 向量库) -> Observation -> Final Answer。
    """

    def __init__(self) -> None:
        ...

    def _decide_mode(
        self,
        query_text: Optional[str],
        image_url: Optional[str],
        mode: SearchMode,
    ) -> SearchMode:
        """
        根据显式参数 + 启发式规则 + DeepSeek 决策，选择最终搜索模式。
        """
        # 1. 前端强制指定模式时，优先使用
        if mode in (SearchMode.BACKEND, SearchMode.VECTOR_TEXT, SearchMode.VECTOR_IMAGE):
            return mode

        # 2. 有图片 URL 且用户明显在说“相似图片”，优先走向量以图搜图
        if image_url:
            return SearchMode.VECTOR_IMAGE

        # 3. 简单关键词启发：提到“语义”“风格”“相似”更像是向量文本搜图
        if query_text:
            lowered = query_text.lower()
            if any(kw in lowered for kw in ["语义", "风格", "相似", "类似", "推荐"]):
                return SearchMode.VECTOR_TEXT

        # 4. 调用 DeepSeek 做精细路由（如果配置了密钥）
        if query_text and deepseek_client.api_key:
            # print("调用 DeepSeek 进行搜索模式决策\n")
            decision = deepseek_client.decide_search_mode(
                query_text=query_text,
                has_image=bool(image_url),
            )
            return SearchMode(decision)

        # 5. 默认退回原有后端关键词搜索
        return SearchMode.BACKEND

    def _tool_backend_search(self, query_text: str, top_k: int) -> AgentResult:
        """
        工具：调用 Java 后端的分页搜索接口进行普通关键词检索。
        作为 MCP 工具之一，供 Agent 使用。
        """
        steps: List[ReActStep] = []
        steps.append(
            ReActStep(
                thought="用户更像是在用关键词搜索图片，尝试调用 Java 后端的列表查询。",
                action=f"java_client.list_picture_vo_by_text(search_text={query_text!r}, top_k={top_k})",
                observation="等待后端返回图片列表。",
            )
        )
        pictures = java_client.list_picture_vo_by_text(search_text=query_text, page=1, page_size=top_k)
        steps.append(
            ReActStep(
                thought="已拿到 Java 后端返回的图片列表。",
                action="return",
                observation=f"共返回 {len(pictures)} 条结果。",
            )
        )
        return AgentResult(mode=SearchMode.BACKEND, pictures=pictures, steps=steps)

    def _load_pictures_from_vector_results(
        self,
        results: List[VectorSearchResult],
        steps: List[ReActStep],
    ) -> List[Dict[str, Any]]:
        """
        根据向量库返回的 picture_id 列表，调用 Java 后端补齐 PictureVO 信息。
        """
        pictures: List[Dict[str, Any]] = []
        if not results:
            return pictures

        # 并行回查 Java 后端，避免串行导致总耗时累加
        max_workers = min(8, len(results))
        with ThreadPoolExecutor(max_workers=max_workers) as executor:
            future_map = {
                executor.submit(java_client.get_picture_vo_by_id, item.picture_id): item
                for item in results
            }
            for future in as_completed(future_map):
                item = future_map[future]
                try:
                    pic = future.result()
                except Exception:
                    pic = None
                if pic:
                    pic["__vector_score__"] = item.score
                    pictures.append(pic)
        steps.append(
            ReActStep(
                thought="根据向量库返回的 picture_id，从 Java 后端加载完整的 PictureVO。",
                action=f"java_client.get_picture_vo_by_id in parallel (workers={max_workers})",
                observation=f"成功补全 {len(pictures)} 条图片信息。",
            )
        )
        return pictures

    def _tool_vector_text_search(self, query_text: str, top_k: int) -> AgentResult:
        """
        工具：向量数据库文本搜图。
        作为 MCP 工具之一，供 Agent 使用。
        """
        steps: List[ReActStep] = []
        steps.append(
            ReActStep(
                thought="用户想按语义或风格搜索图片，先调用向量库做文本向量检索。",
                action=f"vector_client.search_by_text(text={query_text!r}, top_k={top_k})",
                observation="等待向量数据库返回相似图片的 ID 列表。",
            )
        )
        print("现在在进行文本搜图\n")
        vector_results = vector_client.search_by_text(text=query_text, top_k=top_k)
        print("vector_results = ", vector_results)
        pictures = self._load_pictures_from_vector_results(vector_results, steps)
        print("查找结束了，pictures = ",pictures, "\n\n")
        return AgentResult(mode=SearchMode.VECTOR_TEXT, pictures=pictures, steps=steps)

    def _tool_vector_image_search(self, image_url: str, top_k: int) -> AgentResult:
        """
        工具：向量数据库以图搜图。
        作为 MCP 工具之一，供 Agent 使用。
        """
        steps: List[ReActStep] = []
        steps.append(
            ReActStep(
                thought="用户提供了一张图片，希望找相似图片，调用向量库做图片向量检索。",
                action=f"vector_client.search_by_image(image_url={image_url!r}, top_k={top_k})",
                observation="等待向量数据库返回相似图片的 ID 列表。",
            )
        )
        vector_results = vector_client.search_by_image(image_url=image_url, top_k=top_k)
        pictures = self._load_pictures_from_vector_results(vector_results, steps)
        return AgentResult(mode=SearchMode.VECTOR_IMAGE, pictures=pictures, steps=steps)

    def search(
        self,
        query_text: Optional[str],
        image_url: Optional[str],
        mode: SearchMode = SearchMode.AUTO,
        top_k: int = 20,
    ) -> AgentResult:
        """
        对外暴露的统一搜索入口，由 FastAPI /agent/search 调用。
        """
        steps: List[ReActStep] = []
        steps.append(
            ReActStep(
                thought="收到用户搜索请求，开始决定使用哪种搜索模式。",
                action="decide_mode",
                observation=f"输入 query_text={query_text!r}, image_url={image_url!r}, mode={mode.value!r}",
            )
        )

        final_mode = self._decide_mode(query_text=query_text, image_url=image_url, mode=mode)
        steps.append(
            ReActStep(
                thought=f"根据规则与（可选的）DeepSeek 决策，最终选择模式：{final_mode.value}",
                action="route_to_tool",
                observation="准备调用对应的搜索工具。",
            )
        )

        if final_mode == SearchMode.BACKEND:
            if not query_text:
                return AgentResult(
                    mode=final_mode,
                    pictures=[],
                    steps=steps
                    + [
                        ReActStep(
                            thought="后端关键词搜索需要 query_text，但当前为空。",
                            action="return",
                            observation="返回空结果。",
                        )
                    ],
                )
            result = self._tool_backend_search(query_text=query_text, top_k=top_k)
        elif final_mode == SearchMode.VECTOR_TEXT:
            if not query_text:
                return AgentResult(
                    mode=final_mode,
                    pictures=[],
                    steps=steps
                    + [
                        ReActStep(
                            thought="向量文本搜图需要 query_text，但当前为空。",
                            action="return",
                            observation="返回空结果。",
                        )
                    ],
                )
            result = self._tool_vector_text_search(query_text=query_text, top_k=top_k)
        else:  # VECTOR_IMAGE
            if not image_url:
                return AgentResult(
                    mode=final_mode,
                    pictures=[],
                    steps=steps
                    + [
                        ReActStep(
                            thought="以图搜图需要 image_url，但当前为空。",
                            action="return",
                            observation="返回空结果。",
                        )
                    ],
                )
            result = self._tool_vector_image_search(image_url=image_url, top_k=top_k)

        # 合并步骤轨迹，保证从决策到工具调用的完整 ReAct 链路
        result.steps = steps + result.steps
        return result

    # --------------- 对话模式 ---------------

    _CHAT_SYSTEM_PROMPT = (
        "你是一个图片搜索助手，运行在一个图片平台上。你可以：\n"
        "1. 和用户自然对话，回答关于图片、摄影、设计等方面的问题\n"
        "2. 帮用户搜索图片——当用户想搜索图片时，你必须在回复中包含一个特殊标记：\n"
        "   [SEARCH_PICTURES:搜索关键词]\n"
        "   例如用户说「帮我找几张夕阳的照片」，你应该回复类似：\n"
        "   好的，我来帮你搜索夕阳相关的图片。[SEARCH_PICTURES:夕阳风景照]\n"
        "3. 如果用户提供了图片链接想找相似图片，使用标记：\n"
        "   [SEARCH_BY_IMAGE:图片URL]\n"
        "\n"
        "规则：\n"
        "- 只有在用户明确想搜索图片时才使用 [SEARCH_PICTURES:...] 标记\n"
        "- 普通闲聊、提问不要触发搜索\n"
        "- 每次回复最多包含一个搜索标记\n"
        "- 搜索标记中的关键词应该是提炼后的有效搜索词，不要原样复制用户的长句子\n"
        "- 回复要简洁自然"
    )

    _SEARCH_PATTERN = re.compile(r"\[SEARCH_PICTURES:(.+?)\]")
    _IMAGE_SEARCH_PATTERN = re.compile(r"\[SEARCH_BY_IMAGE:(.+?)\]")

    def chat(
        self,
        conversation_id: int,
        user_message: str,
        user_id: int,
        image_url: Optional[str] = None,
        top_k: int = 3,
    ) -> Dict[str, Any]:
        """
        对话式入口。
        返回 {"role", "contentType", "content", "extra"} 形式的 assistant 消息。
        """
        # 1. 保存用户消息
        user_extra = {"image_url": image_url} if image_url else None
        conv_db.add_message(
            conversation_id=conversation_id,
            role="user",
            content=user_message,
            content_type="text",
            extra=user_extra,
        )
        conv_db.touch_conversation(conversation_id)

        # 2. 如果首条消息，自动设置对话标题
        all_msgs = conv_db.list_messages(conversation_id)
        user_msgs = [m for m in all_msgs if m["role"] == "user"]
        if len(user_msgs) == 1:
            title = user_message[:50] if len(user_message) > 50 else user_message
            conv_db.update_conversation_title(conversation_id, title)

        # 3. 拼 LLM 上下文
        recent = conv_db.get_recent_messages(conversation_id, limit=20)
        llm_messages: List[Dict[str, str]] = []
        for m in recent:
            role = m["role"]
            if role not in ("user", "assistant"):
                continue
            text = m.get("content") or ""
            if m.get("contentType") == "search_result" and m.get("extra"):
                extra = m["extra"] if isinstance(m["extra"], dict) else {}
                pic_count = len(extra.get("pictures", []))
                text = f"{text}"
            llm_messages.append({"role": role, "content": text})

        # 4. 调用 DeepSeek 对话
        if not deepseek_client.api_key:
            # 没有配置 LLM，走纯搜索兜底
            return self._fallback_search_chat(
                conversation_id, user_message, image_url, top_k
            )

        try:
            llm_reply = deepseek_client.chat_with_history(
                messages=llm_messages,
                system_prompt=self._CHAT_SYSTEM_PROMPT,
            )
        except Exception as e:
            logger.warning("DeepSeek 对话调用失败: %s", e)
            return self._fallback_search_chat(
                conversation_id, user_message, image_url, top_k
            )

        # 5. 解析 LLM 回复，看是否需要触发搜索
        search_match = self._SEARCH_PATTERN.search(llm_reply)
        image_search_match = self._IMAGE_SEARCH_PATTERN.search(llm_reply)

        if image_url and not image_search_match:
            # 用户提供了图片链接但 LLM 没输出标记，主动触发以图搜图
            clean_reply = llm_reply
            return self._do_search_and_save(
                conversation_id, clean_reply, None, image_url, top_k
            )

        if image_search_match:
            img_url = image_search_match.group(1).strip()
            clean_reply = self._IMAGE_SEARCH_PATTERN.sub("", llm_reply).strip()
            return self._do_search_and_save(
                conversation_id, clean_reply, None, img_url, top_k
            )

        if search_match:
            query = search_match.group(1).strip()
            clean_reply = self._SEARCH_PATTERN.sub("", llm_reply).strip()
            return self._do_search_and_save(
                conversation_id, clean_reply, query, None, top_k
            )

        # 6. 普通对话，直接保存文本回复
        conv_db.add_message(
            conversation_id=conversation_id,
            role="assistant",
            content=llm_reply,
            content_type="text",
        )
        return {
            "role": "assistant",
            "contentType": "text",
            "content": llm_reply,
            "extra": None,
        }

    def _do_search_and_save(
        self,
        conversation_id: int,
        reply_text: str,
        query_text: Optional[str],
        image_url: Optional[str],
        top_k: int,
    ) -> Dict[str, Any]:
        """执行搜索并将结果作为 assistant 消息存入数据库。"""
        result = self.search(
            query_text=query_text,
            image_url=image_url,
            mode=SearchMode.AUTO,
            top_k=top_k,
        )
        extra = {
            "mode": result.mode.value,
            "pictures": result.pictures,
            "steps": [s.__dict__ for s in result.steps],
        }
        content = reply_text or f"为你找到了 {len(result.pictures)} 张相关图片"
        conv_db.add_message(
            conversation_id=conversation_id,
            role="assistant",
            content=content,
            content_type="search_result",
            extra=extra,
        )
        return {
            "role": "assistant",
            "contentType": "search_result",
            "content": content,
            "extra": extra,
        }

    def _fallback_search_chat(
        self,
        conversation_id: int,
        user_message: str,
        image_url: Optional[str],
        top_k: int,
    ) -> Dict[str, Any]:
        """没有 LLM 时的兜底：直接走搜索。"""
        return self._do_search_and_save(
            conversation_id,
            "",
            user_message if not image_url else None,
            image_url,
            top_k,
        )


picture_search_agent = PictureSearchAgent()


