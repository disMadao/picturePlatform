from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from agent_backend.agent import picture_search_agent
from agent_backend.schemas import AgentSearchRequest, AgentSearchResponse, BaseResponse
from agent_backend.history import log_search

app = FastAPI(title="Picture Agent Backend")

# 允许前端本地联调（可按需收紧）
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    # Agent 前端请求不依赖 cookie，关闭 credentials，避免与 "*" origin 组合导致浏览器拦截
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.post("/agent/search", response_model=BaseResponse)
def agent_search(req: AgentSearchRequest) -> BaseResponse:
    """
    Agent 统一搜索入口。
    - 支持纯文本搜索（关键词 / 语义）
    - 支持以图搜图（通过 image_url）
    - mode=auto 时自动在原有后端 / 向量文本 / 向量图片之间做路由（ReAct 风格决策）
    """
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
    # 将本次对话（搜索请求 + Agent 决策）记录到数据库
    try:
        log_search(user_id=req.user_id, request=req, result=result)
    except Exception:
        # 记录失败不影响主流程，后续可按需加日志
        pass
    return BaseResponse(code=0, data=resp_data.dict(), message="ok")


@app.get("/health")
def health() -> BaseResponse:
    """
    健康检查接口，便于前后端联动自测。
    """
    return BaseResponse(code=0, data={"status": "ok"}, message="ok")


# 不生成启动脚本，具体启动命令可由使用者自行决定：
# uvicorn main:app --host 0.0.0.0 --port 9002


# 在 main.py 文件末尾添加
if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        app, 
        host="0.0.0.0", 
        port=int(__import__("os").getenv("AGENT_PORT", "9002")),
        log_level="info"
    )