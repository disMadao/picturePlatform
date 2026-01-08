from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum
from typing import Any, Dict, List, Optional

from .deepseek_client import deepseek_client
from .java_client import java_client
from .vector_store import VectorSearchResult, vector_client


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
        for item in results:
            pic = java_client.get_picture_vo_by_id(item.picture_id)
            if pic:
                pic["__vector_score__"] = item.score
                pictures.append(pic)
        steps.append(
            ReActStep(
                thought="根据向量库返回的 picture_id，从 Java 后端加载完整的 PictureVO。",
                action="java_client.get_picture_vo_by_id for each result",
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
        print("result = ", result, "\n\n")
        return result


picture_search_agent = PictureSearchAgent()


