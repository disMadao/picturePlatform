from __future__ import annotations

from enum import Enum
from typing import Any, Dict, List, Optional

from pydantic import BaseModel, Field


class SearchMode(str, Enum):
  AUTO = "auto"
  BACKEND = "backend"
  VECTOR_TEXT = "vector_text"
  VECTOR_IMAGE = "vector_image"


class AgentSearchRequest(BaseModel):
  """
  Agent 搜索请求入参。
  - query_text: 文本描述（可选）
  - image_url: 以图搜图时的图片地址（可选）
  - mode: 搜索模式（默认 auto，由 Agent 自动路由）
  """

  user_id: Optional[int] = Field(default=None, description="发起请求的用户 ID")
  query_text: Optional[str] = Field(default=None, description="文本查询内容")
  image_url: Optional[str] = Field(default=None, description="图片 URL，用于以图搜图")
  mode: SearchMode = Field(default=SearchMode.AUTO, description="搜索模式，默认自动路由")
  top_k: int = Field(default=20, description="返回的最大图片数量")


class AgentSearchResponse(BaseModel):
  """
  Agent 搜索响应。
  - mode: 最终使用的搜索模式
  - pictures: 图片列表（结构尽量贴近 Java 后端的 PictureVO）
  - steps: ReAct 轨迹，便于调试 / 前端展示 Agent 思考过程
  """

  mode: SearchMode
  pictures: List[Dict[str, Any]]
  steps: List[Dict[str, Any]]


class BaseResponse(BaseModel):
  """
  与 Java 后端保持一致的响应包裹结构，方便前端直接对接。
  """

  code: int = 0
  data: Optional[Any] = None
  message: str = "ok"


# --------------- 对话模块 ---------------

class CreateConversationRequest(BaseModel):
  user_id: int

class SendMessageRequest(BaseModel):
  user_id: int
  conversation_id: int
  content: str = Field(description="用户输入的文本")
  image_url: Optional[str] = Field(default=None, description="可选的图片 URL（以图搜图）")

class ConversationVO(BaseModel):
  id: int
  title: Optional[str] = None
  createTime: str
  updateTime: str

class MessageVO(BaseModel):
  id: int
  conversationId: int
  role: str
  contentType: str
  content: Optional[str] = None
  extra: Optional[Dict[str, Any]] = None
  createTime: str


