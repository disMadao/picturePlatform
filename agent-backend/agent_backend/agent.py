from __future__ import annotations

import json
import logging
import re
from dataclasses import dataclass, field
from enum import Enum
from typing import Any, Dict, List, Optional
from concurrent.futures import ThreadPoolExecutor, as_completed

from .ark_video import create_task_and_wait_for_video_url
from .config import config
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
        - 查询词去空白后长度 < 6：固定 Java 关键词（backend）。
        - 否则 DeepSeek 路由；意图不明时由 deepseek_client 提示词默认偏向 vector_text。
        """
        # 1. 前端强制指定模式时，优先使用
        if mode in (SearchMode.BACKEND, SearchMode.VECTOR_TEXT, SearchMode.VECTOR_IMAGE):
            return mode

        # 2. 有图片 URL 且用户明显在说“相似图片”，优先走向量以图搜图
        if image_url:
            return SearchMode.VECTOR_IMAGE

        # 3. 查询词过短（去空白后长度 < 6）：走 Java 关键词更稳，避免向量语义过宽
        if query_text and len(query_text.strip()) < 6:
            return SearchMode.BACKEND

        # 4. 简单关键词启发：提到“语义”“风格”“相似”更像是向量文本搜图
        if query_text:
            lowered = query_text.lower()
            if any(kw in lowered for kw in ["语义", "风格", "相似", "类似", "推荐"]):
                return SearchMode.VECTOR_TEXT

        # 5. 调用 DeepSeek 做精细路由（如果配置了密钥）；意图不明时由提示词偏向 vector_text
        if query_text and deepseek_client.api_key:
            # print("调用 DeepSeek 进行搜索模式决策\n")
            decision = deepseek_client.decide_search_mode(
                query_text=query_text,
                has_image=bool(image_url),
            )
            return SearchMode(decision)

        # 6. 无 DeepSeek 时默认 Java 关键词
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
        top_k: int = 6,
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

    # --------------- 对话模式（图片搜索：不依赖 LLM 标记，直接搜图）---------------

    _SEARCH_MODE_CAPABILITY_TEXT = (
        "此 Agent 只能完成图片搜索与视频生成。"
        "当前为图片搜索模式，请描述你想找的图片内容或关键词；"
        "如需视频生成，请在上方切换到「视频生成」。"
    )

    _OFF_TOPIC_GREETING_ONLY = re.compile(
        r"^(?:\s*)(?:你好|您好|在吗|在不在|hi|hello|嗨|早上好|晚上好|下午好|谢谢|多谢|"
        r"再见|拜拜|嗯|哦|好|ok|OK|好的|行|收到|辛苦|👍|🙏|哈哈|哈哈哈)(?:[!.！。…\s，,])*$",
        re.I,
    )

    @staticmethod
    def _extract_search_query_from_user(user_message: str) -> str:
        """从自然语言里抽出关键词。"""
        s = user_message.strip()
        if not s:
            return ""
        s = re.sub(r"^(?:请|帮我|麻烦|能否|能不能|可以)?\s*", "", s)
        s = re.sub(r"^(?:查找|搜索|搜一?[搜下]?|找一?[找下]?|推荐)\s*", "", s, count=1)
        # 「薇尔莉特的图片」「马男波杰克里的图片」整段后缀去掉，避免只剩「马男波杰克里」
        s = re.sub(
            r"(?:的|里的)(?:图片|照片|壁纸|相片|照)(?:片)?\s*$",
            "",
            s,
        )
        s = re.sub(r"的?(?:图片|照片|壁纸|相片|照)(?:片)?\s*$", "", s)
        s = s.strip("，。！？!?；;、 ")
        return (s[:300] if s else user_message.strip()[:300]).strip()

    @classmethod
    def _is_off_topic_for_search_mode(cls, user_message: str) -> bool:
        """图片搜索模式下视为「与找图无关」的输入：寒暄、空内容、明显非找图闲聊等。"""
        t = (user_message or "").strip()
        if not t:
            return True
        if len(t) <= 40 and cls._OFF_TOPIC_GREETING_ONLY.match(t):
            return True
        # 明显与找图无关且未提及图/照片/壁纸
        if "图" not in t and "照片" not in t and "壁纸" not in t and "搜" not in t and "找" not in t:
            if len(t) < 100 and re.search(
                r"^(?:今天|明天|后天).{0,20}(?:天气|气温|下雨|下雪|台风)",
                t,
            ):
                return True
            if len(t) < 80 and re.search(
                r"(?:讲个笑话|说个笑话|股票|汇率|比特币|作业|论文|代码怎么写|bug|报错)",
                t,
            ):
                return True
        return False

    def _reply_search_mode_capability_only(self, conversation_id: int) -> Dict[str, Any]:
        conv_db.add_message(
            conversation_id=conversation_id,
            role="assistant",
            content=self._SEARCH_MODE_CAPABILITY_TEXT,
            content_type="text",
        )
        return {
            "role": "assistant",
            "contentType": "text",
            "content": self._SEARCH_MODE_CAPABILITY_TEXT,
            "extra": None,
        }

    def chat(
        self,
        conversation_id: int,
        user_message: str,
        user_id: int,
        image_url: Optional[str] = None,
        top_k: int = 10,
        session_intent: str = "search",
    ) -> Dict[str, Any]:
        """
        图片搜索入口（session_intent=search）：直接走搜索，不依赖 DeepSeek 的 [SEARCH_PICTURES] 标记。
        无关寒暄返回能力说明文案。
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

        # 3. 仅处理图片搜索模式（video 在 chat_video）
        if session_intent != "search":
            logger.warning("chat() 收到非 search 的 session_intent=%r，按 search 处理", session_intent)

        # 以图搜图：有图链则直接搜，不判断寒暄
        if image_url and str(image_url).strip():
            return self._do_search_and_save(
                conversation_id,
                "为你找到以下相似图片",
                None,
                str(image_url).strip(),
                top_k,
            )

        if self._is_off_topic_for_search_mode(user_message):
            return self._reply_search_mode_capability_only(conversation_id)

        q = self._extract_search_query_from_user(user_message)
        if not q:
            q = user_message.strip()
        reply_text = f"以下是「{q}」的搜索结果"
        return self._do_search_and_save(conversation_id, reply_text, q, None, top_k)

    def _do_search_and_save(
        self,
        conversation_id: int,
        reply_text: str,
        query_text: Optional[str],
        image_url: Optional[str],
        top_k: int,
    ) -> Dict[str, Any]:
        """执行搜索并将结果作为 assistant 消息存入数据库（模式由 _decide_mode + DeepSeek 决定）。"""
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

    def chat_video(
        self,
        conversation_id: int,
        user_message: str,
        user_id: int,
        space_id: Optional[int],
        first_frame_url: Optional[str] = None,
        last_frame_url: Optional[str] = None,
    ) -> Dict[str, Any]:
        """
        视频生成：方舟任务 -> Java 转存 COS + 落库 -> 返回 contentType=video。
        """
        user_extra: Dict[str, Any] = {
            "session_intent": "video",
            "space_id": space_id,
            "first_frame_url": first_frame_url,
            "last_frame_url": last_frame_url,
        }
        conv_db.add_message(
            conversation_id=conversation_id,
            role="user",
            content=user_message,
            content_type="text",
            extra=user_extra,
        )
        conv_db.touch_conversation(conversation_id)

        all_msgs = conv_db.list_messages(conversation_id)
        user_msgs = [m for m in all_msgs if m["role"] == "user"]
        if len(user_msgs) == 1:
            title = user_message[:50] if len(user_message) > 50 else user_message
            conv_db.update_conversation_title(conversation_id, f"[视频] {title}")

        if not space_id or space_id <= 0:
            err = "请先在界面选择要保存到的私有空间（space_id）。"
            conv_db.add_message(
                conversation_id=conversation_id,
                role="assistant",
                content=err,
                content_type="text",
            )
            return {
                "role": "assistant",
                "contentType": "text",
                "content": err,
                "extra": None,
            }

        if not (user_message or "").strip():
            err = "请填写视频生成提示词。"
            conv_db.add_message(
                conversation_id=conversation_id,
                role="assistant",
                content=err,
                content_type="text",
            )
            return {
                "role": "assistant",
                "contentType": "text",
                "content": err,
                "extra": None,
            }

        try:
            task_id, temp_url, _ = create_task_and_wait_for_video_url(
                user_message.strip(),
                first_frame_url=first_frame_url,
                last_frame_url=last_frame_url,
            )
            logger.info(
                "准备落库视频: JAVA_BASE_URL=%s user_id=%s space_id=%s",
                config.java_base_url,
                user_id,
                space_id,
            )
            vo = java_client.persist_agent_video(
                {
                    "tempVideoUrl": temp_url,
                    "userId": user_id,
                    "spaceId": space_id,
                    "name": "生成视频",
                    "introduction": user_message.strip()[:500],
                    "prompt": user_message.strip()[:2000],
                    "thumbnailUrl": first_frame_url,
                    "conversationId": conversation_id,
                    "arkTaskId": task_id,
                }
            )
        except Exception as e:
            logger.exception("视频生成或落库失败: %s", e)
            err = f"视频生成失败：{e}"
            conv_db.add_message(
                conversation_id=conversation_id,
                role="assistant",
                content=err,
                content_type="text",
            )
            return {
                "role": "assistant",
                "contentType": "text",
                "content": err,
                "extra": None,
            }

        video_url = vo.get("url") or ""
        extra = {
            "videoUrl": video_url,
            "videoRecordId": vo.get("id"),
            "thumbnailUrl": vo.get("thumbnailUrl"),
            "spaceId": space_id,
            "arkTaskId": task_id,
        }
        summary = "视频已生成并保存到你的空间。"
        conv_db.add_message(
            conversation_id=conversation_id,
            role="assistant",
            content=summary,
            content_type="video",
            extra=extra,
        )
        return {
            "role": "assistant",
            "contentType": "video",
            "content": summary,
            "extra": extra,
        }


picture_search_agent = PictureSearchAgent()


