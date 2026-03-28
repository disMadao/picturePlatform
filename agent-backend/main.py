import os
import logging

from dotenv import load_dotenv

# 先于其它模块加载，使 JAVA_BASE_URL / AGENT_INTERNAL_TOKEN 等来自 agent-backend/.env
load_dotenv()

from fastapi import FastAPI, Query
from fastapi.middleware.cors import CORSMiddleware

from agent_backend.agent import picture_search_agent
from agent_backend.schemas import (
    AgentSearchRequest,
    AgentSearchResponse,
    BaseResponse,
    CreateConversationRequest,
    SendMessageRequest,
    SessionIntent,
)
from agent_backend.history import log_search
from agent_backend import conversation as conv_db

logger = logging.getLogger(__name__)

app = FastAPI(title="Picture Agent Backend")

# ---- CORS：只允许你自己的前端域名 ----
ALLOWED_ORIGINS = [
    o.strip()
    for o in os.getenv(
        "CORS_ORIGINS",
        "http://localhost:5173,http://127.0.0.1:5173,http://localhost:8123,http://118.195.165.9",
    ).split(",")
    if o.strip()
]

app.add_middleware(
    CORSMiddleware,
    allow_origins=ALLOWED_ORIGINS,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)



# --------------- 对话管理 ---------------

@app.post("/agent/conversation/create", response_model=BaseResponse)
def create_conversation(req: CreateConversationRequest) -> BaseResponse:
    cid = conv_db.create_conversation(user_id=req.user_id)
    # 与列表接口一致：id 用字符串，避免前端 JSON 大整数精度丢失
    return BaseResponse(code=0, data={"id": str(cid)})


@app.get("/agent/conversation/list", response_model=BaseResponse)
def list_conversations(user_id: int = Query(...)) -> BaseResponse:
    rows = conv_db.list_conversations(user_id=user_id)
    return BaseResponse(code=0, data=rows)


@app.post("/agent/conversation/delete", response_model=BaseResponse)
def delete_conversation(
    user_id: int = Query(...),
    conversation_id: int = Query(...),
) -> BaseResponse:
    ok = conv_db.delete_conversation(user_id=user_id, conversation_id=conversation_id)
    if not ok:
        return BaseResponse(code=40400, message="对话不存在或无权限")
    return BaseResponse(code=0)


@app.get("/agent/conversation/messages", response_model=BaseResponse)
def list_messages(conversation_id: int = Query(...)) -> BaseResponse:
    msgs = conv_db.list_messages(conversation_id=conversation_id)
    return BaseResponse(code=0, data=msgs)


# --------------- 对话式聊天（核心入口） ---------------

@app.post("/agent/chat", response_model=BaseResponse)
def agent_chat(req: SendMessageRequest) -> BaseResponse:
    if req.session_intent == SessionIntent.VIDEO:
        result = picture_search_agent.chat_video(
            conversation_id=req.conversation_id,
            user_message=req.content,
            user_id=req.user_id,
            space_id=req.space_id,
            first_frame_url=req.first_frame_url or req.image_url,
            last_frame_url=req.last_frame_url,
        )
    else:
        result = picture_search_agent.chat(
            conversation_id=req.conversation_id,
            user_message=req.content,
            user_id=req.user_id,
            image_url=req.image_url,
            session_intent=req.session_intent.value,
        )
    return BaseResponse(code=0, data=result)


# --------------- 兼容旧版搜索接口 ---------------

@app.post("/agent/search", response_model=BaseResponse)
def agent_search(req: AgentSearchRequest) -> BaseResponse:
    result = picture_search_agent.search(
        query_text=req.query_text,
        image_url=req.image_url,
        mode=req.mode,
        top_k=req.top_k,
    )
    resp_data = AgentSearchResponse(
        mode=result.mode,
        pictures=result.pictures,
        steps=[step.__dict__ for step in result.steps],
    )
    try:
        log_search(user_id=req.user_id, request=req, result=result)
    except Exception:
        pass
    return BaseResponse(code=0, data=resp_data.dict(), message="ok")


@app.get("/health")
def health() -> BaseResponse:
    return BaseResponse(code=0, data={"status": "ok"}, message="ok")


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        app,
        host="0.0.0.0",
        port=int(os.getenv("AGENT_PORT", "9002")),
        log_level="info",
    )
