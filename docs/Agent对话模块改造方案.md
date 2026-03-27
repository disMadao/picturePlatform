# Agent 对话模块改造方案

## 一、现状分析

### 当前架构（agent_backend）

```
main.py                  → FastAPI，仅暴露 /agent/search（一次性搜索）
agent_backend/
  agent.py               → PictureSearchAgent，ReAct 风格路由决策（backend / vector_text / vector_image）
  deepseek_client.py     → DeepSeek LLM 客户端，仅用于路由决策（单轮，无上下文）
  java_client.py         → 调用 Java 后端的图片查询接口
  vector_store.py        → Milvus 向量检索 + sentence-transformers 文本编码
  history.py             → 每次搜索写一条 session + 两条 message（一次请求 = 一次会话，无多轮概念）
  schemas.py             → Pydantic 请求/响应模型
  config.py              → 配置
```

### 核心问题

| 问题 | 说明 |
|------|------|
| 无多轮对话 | 每次搜索都是独立请求，无上下文记忆 |
| 无会话管理 | 没有"新建 / 切换 / 列出 / 删除"对话的能力 |
| LLM 只做路由 | DeepSeek 仅用来判断走哪种搜索模式，不做自然语言对话 |
| 前端是搜索框 | AgentSearchPage.vue 是输入框 + 图片列表，不是聊天 UI |

---

## 二、开源项目对话存储方案调研

| 项目 | 存储方案 | 表结构 | 特点 |
|------|----------|--------|------|
| **Vercel ai-chatbot** | PostgreSQL + Drizzle | `Chat`(id, title, userId, visibility, createdAt) + `Message_v2`(id, chatId, role, parts, attachments, createdAt) | 最简洁，两表搞定，message 用 JSON 存结构化内容 |
| **LobeChat** | PostgreSQL + Drizzle | `sessions` → `topics` → `messages` → `message_groups` 四级层次 | 支持多模型并行回复、分组，过于复杂 |
| **Open WebUI** | SQLite/PostgreSQL | `chat` 表存所有消息（整个对话的 JSON blob） | 最简单但不可扩展，大对话性能差 |

### 结论

采用 **Vercel ai-chatbot 的两表模式**（conversation + message），这是最简化可运行的方案。理由：

1. 两张表即可覆盖"多对话 + 多轮消息"需求
2. message 的 `extra` 字段用 JSON 存储结构化数据（搜索结果、图片列表、ReAct 步骤），灵活可扩展
3. 用 `content_type` 区分消息类型（text / search_result / image / video），为后续视频生成预留

---

## 三、数据库设计

沿用项目已有的 **MySQL**（yu_picture 库），不引入新存储。

### 3.1 agent_conversation 表（替代原 agent_session）

```sql
CREATE TABLE IF NOT EXISTS agent_conversation (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '对话 ID',
    userId      BIGINT       NOT NULL             COMMENT '用户 ID',
    title       VARCHAR(128) DEFAULT NULL          COMMENT '对话标题（自动从首条消息生成）',
    createTime  DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updateTime  DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    isDelete    TINYINT      DEFAULT 0             NOT NULL COMMENT '软删除',
    INDEX idx_userId (userId),
    INDEX idx_updateTime (updateTime)
) COMMENT '对话会话表' COLLATE = utf8mb4_unicode_ci;
```

### 3.2 agent_message 表（改造原 agent_message）

```sql
CREATE TABLE IF NOT EXISTS agent_message (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '消息 ID',
    conversationId  BIGINT       NOT NULL             COMMENT '所属对话 ID',
    role            VARCHAR(16)  NOT NULL             COMMENT 'user / assistant / system',
    contentType     VARCHAR(32)  DEFAULT 'text'       NOT NULL COMMENT '消息类型：text / search_result / image / video',
    content         TEXT                              COMMENT '文本内容',
    extra           JSON         DEFAULT NULL          COMMENT '结构化数据（图片列表、ReAct 步骤、视频信息等）',
    createTime      DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    INDEX idx_conversationId (conversationId),
    INDEX idx_createTime (createTime)
) COMMENT '对话消息表' COLLATE = utf8mb4_unicode_ci;
```

### 字段说明

**contentType 取值及 extra 结构：**

| contentType | content | extra |
|-------------|---------|-------|
| `text` | 用户的文本 / LLM 的自然语言回复 | null |
| `search_result` | LLM 的总结文案 | `{"mode": "vector_text", "pictures": [...], "steps": [...]}` |
| `image` | 描述文字 | `{"url": "...", "pictureId": 123}` |
| `video` | （未来）描述文字 | `{"url": "...", "videoId": 123, "status": "generating"}` |

> 设计 `contentType` 和 `extra` 而不是拆更多表，是因为这种做法最简单，且与未来视频生成模块天然兼容——新增一种 contentType 即可，不用改表结构。

---

## 四、后端改造（agent_backend）

### 4.1 需要改的文件 & 新增文件

```
agent_backend/
  conversation.py  ← 【新增】对话 & 消息的 CRUD（MySQL 操作）
  agent.py         ← 【改造】支持多轮上下文，chat() 方法替代 search()
  deepseek_client.py ← 【改造】支持带历史消息的对话补全
  history.py       ← 【废弃】其功能合并到 conversation.py
  schemas.py       ← 【改造】新增对话相关的请求/响应模型
main.py            ← 【改造】新增对话管理 + 聊天的 API 路由
```

### 4.2 conversation.py（新增）

核心功能，每个函数对应一个最小操作：

```python
# 对话管理
create_conversation(user_id) -> conversation_id     # 创建新对话
list_conversations(user_id) -> List[ConversationVO]  # 列出用户所有对话（按 updateTime 倒序）
delete_conversation(user_id, conversation_id)        # 软删除对话
update_conversation_title(conversation_id, title)    # 更新标题

# 消息管理
add_message(conversation_id, role, content_type, content, extra) -> message_id
list_messages(conversation_id) -> List[MessageVO]    # 获取某对话的所有消息（按时间正序）
get_recent_messages(conversation_id, limit=20) -> List[MessageVO]  # 最近 N 条（用于拼 LLM 上下文）
```

### 4.3 agent.py 改造

将现有的 `search()` 方法重构为 `chat()` 方法：

```python
class PictureSearchAgent:
    def chat(self, conversation_id: int, user_message: str, user_id: int) -> AgentChatResult:
        """
        对话式入口，替代原来的 search()。
        流程：
        1. 把用户消息存入 agent_message
        2. 拉取最近 N 条历史消息，拼成 LLM 上下文
        3. 调用 DeepSeek 对话补全：
           - 如果 LLM 判断用户想搜图 → 调用原有的搜索工具链，把搜索结果也存为一条 assistant 消息
           - 如果是普通对话 → 直接返回 LLM 的文本回复
        4. 把 assistant 回复存入 agent_message
        5. 返回结果
        """
```

关键：保留原有的 `_decide_mode`、`_tool_backend_search`、`_tool_vector_text_search` 等方法不动，只是在外层包一层对话逻辑。

### 4.4 deepseek_client.py 改造

新增方法，支持带上下文的对话：

```python
def chat_with_history(self, messages: List[dict], system_prompt: str) -> str:
    """
    带历史消息的对话补全。
    messages: [{"role": "user"/"assistant", "content": "..."}]
    返回 LLM 的文本回复。
    """
```

system_prompt 中需要描述 Agent 的能力边界（能搜图、能聊天，未来能生成视频），让 LLM 自行决定是否触发工具调用。

### 4.5 schemas.py 新增模型

```python
class CreateConversationRequest(BaseModel):
    user_id: int

class SendMessageRequest(BaseModel):
    user_id: int
    conversation_id: int
    content: str                    # 用户输入的文本
    image_url: Optional[str] = None # 可选的图片（以图搜图）

class ConversationVO(BaseModel):
    id: int
    title: Optional[str]
    createTime: str
    updateTime: str

class MessageVO(BaseModel):
    id: int
    role: str
    contentType: str
    content: Optional[str]
    extra: Optional[dict]
    createTime: str
```

### 4.6 main.py 新增路由

```
POST   /agent/conversation/create       → 创建对话
GET    /agent/conversation/list          → 列出用户对话
DELETE /agent/conversation/delete        → 删除对话
GET    /agent/conversation/messages      → 获取某对话所有消息
POST   /agent/chat                       → 发送消息（核心入口）
GET    /health                           → 保留
POST   /agent/search                     → 保留（兼容旧前端，内部转发到 chat）
```

---

## 五、前端改造要点（picture-frontend）

### 5.1 文件变动

```
src/
  api/agentController.ts          ← 【改造】新增对话管理 + 聊天 API
  pages/AgentSearchPage.vue       ← 【改造】→ 对话式 UI（或新建 AgentChatPage.vue）
  components/ChatMessage.vue      ← 【新增】单条消息渲染组件（支持文本 / 图片列表 / 视频）
  components/ConversationList.vue ← 【新增】左侧对话列表
```

### 5.2 页面布局

```
┌──────────────┬─────────────────────────────────────────┐
│  对话列表     │  对话区域                                │
│  (侧边栏)    │  ┌─────────────────────────────────────┐│
│              │  │ [assistant] 你好，我可以帮你搜索图片 ││
│  ● 对话 1    │  │ [user] 帮我找夕阳风格的风景照       ││
│  ● 对话 2    │  │ [assistant] 已找到 12 张相关图片：   ││
│  ● 对话 3    │  │   🖼️ 🖼️ 🖼️ 🖼️ ...（图片网格）      ││
│              │  │ [user] 再找几张海边的                ││
│  [+ 新对话]  │  │ [assistant] 又找到 8 张：            ││
│              │  │   🖼️ 🖼️ 🖼️ ...                     ││
│              │  └─────────────────────────────────────┘│
│              │  ┌─────────────────────────────────────┐│
│              │  │ 输入消息...                   [发送] ││
│              │  └─────────────────────────────────────┘│
└──────────────┴─────────────────────────────────────────┘
```

---

## 六、与未来"视频生成"模块的兼容性

开发要求 2 的核心诉求：用户在对话中搜索图片风格 → 生成视频 → 视频在对话中展示 → 可收藏到空间。

当前方案已预留的设计：

| 设计点 | 如何兼容视频生成 |
|--------|-----------------|
| `contentType` 枚举 | 新增 `video` 类型，前端按类型渲染不同组件 |
| `extra` JSON 字段 | 存视频 URL、生成状态、参数等，无需改表 |
| 对话模型 | 视频生成请求和结果都是对话中的消息，天然融入 |
| 收藏到空间 | 独立接口：从 message.extra 取 videoId/pictureId → 调 Java 后端写入 space |
| Agent 工具链 | agent.py 的工具列表中新增 `_tool_generate_video()`，与搜索工具平级 |

**不会冲突的原因：** 对话模型是通用的（conversation + message），搜索和视频只是不同的 contentType 和不同的工具调用，互不影响。

---

## 七、实现顺序（MVP 最小可运行路径）

### 第一步：数据库

1. 在 MySQL 中执行建表 SQL（agent_conversation + agent_message）
2. 原 agent_session / agent_message 表暂时保留不动，不影响线上

### 第二步：后端（agent_backend）

按依赖顺序实现：

1. `conversation.py` — 对话和消息的 CRUD 操作
2. `schemas.py` — 新增请求/响应模型
3. `deepseek_client.py` — 新增 `chat_with_history()` 方法
4. `agent.py` — 新增 `chat()` 方法（保留原 `search()` 兼容）
5. `main.py` — 新增路由
6. 本地测试跑通

### 第三步：前端（picture-frontend）

1. `agentController.ts` — 新增 API
2. `ConversationList.vue` — 对话列表组件
3. `ChatMessage.vue` — 消息渲染组件
4. `AgentChatPage.vue` — 整合页面
5. 路由注册

---

## 八、对话存储总结

| 维度 | 决策 | 理由 |
|------|------|------|
| 存储引擎 | MySQL（已有） | 项目已用 MySQL，不引入新依赖 |
| 表设计 | conversation + message 两表 | 参考 Vercel ai-chatbot，最简可运行 |
| 消息内容 | content(TEXT) + extra(JSON) | 文本走 content，结构化数据走 extra，前端按 contentType 渲染 |
| 上下文管理 | 取最近 N 条消息拼入 LLM prompt | 简单有效，避免 token 爆炸 |
| 对话标题 | 首条消息自动截取 / LLM 生成 | MVP 先用截取，后续可让 LLM 生成更好的标题 |
| 多对话 | 每个用户可创建多个 conversation | conversation 表按 userId 索引 |
