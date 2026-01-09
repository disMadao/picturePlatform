## 忆存云图

**忆存云图**是一个面向企业/个人的智能图片存储与管理平台后端项目，聚焦“海量存储 + 快速检索 + 协同编辑 + Agent 智能搜索”。本仓库包含两套后端：

- **`picture-backend/`（Java）**：业务后端，负责图片上传/处理、权限与空间体系、检索与协同编辑等核心能力。
- **`agent-backend/`（Python）**：Agent 服务，把搜索能力包装为可编排的工具调用链，并接入 Milvus 向量库，提供更智能的检索入口。

> 本 README **不介绍前端**，也**不介绍 `picture-backend-ddd`**；只围绕 `picture-backend` 与 `agent-backend` 的结构与技术要点。

---

## 项目亮点（和源码对应）

### 1）腾讯 COS 海量存储 + 数据万象（CI）图片处理

- **对象存储接入**：`picture-backend/src/main/java/.../config/CosClientConfig.java` 通过 `cos.client.*` 配置创建 `COSClient`。
- **上传即处理**：`.../manager/CosManager.java` 在上传时使用 `PicOperations`：
  - 将原图转为 **webp 压缩**（`imageMogr2/format/webp`）
  - 按条件生成 **缩略图**（`imageMogr2/thumbnail/<w>x<h>>`）
- **上传模板化**：`.../manager/upload/PictureUploadTemplate.java` 抽象“校验 -> 落地临时文件 -> 上传 -> 解析 CI 返回信息 -> 清理临时文件”的通用流程，便于扩展文件/URL 等多种来源。

### 2）Redis + Caffeine 两级缓存（提升读性能与体验）

在图片列表查询中落地了**本地缓存（Caffeine）+ 分布式缓存（Redis）**的两级缓存策略，并加入随机过期时间以降低缓存雪崩风险：

- `picture-backend/src/main/java/.../controller/PictureController.java`
  - `LOCAL_CACHE`：Caffeine 本地缓存，`expireAfterWrite(5 min)`
  - Redis 缓存：`StringRedisTemplate`，写入时 `300~600s` 随机过期

> 目前该接口标注为 `@Deprecated`（保留实现用于对比与面试说明）；主业务接口仍可按需继续演进统一缓存层。

### 3）ShardingSphere 分库分表：按 `spaceId` 动态路由图片表

- **分片规则**：`application.yml` 中的 `spring.shardingsphere.rules.sharding.tables.picture` 使用自定义分片算法（`CLASS_BASED`）。
- **自定义分片算法**：`.../manager/sharding/PictureShardingAlgorithm.java`
  - 以 `spaceId` 作为分片键，路由到 `picture_{spaceId}`（如果存在）
  - `spaceId` 为空时回退逻辑表 `picture`
- **可选的动态分表管理器（实验/预留）**：`.../manager/sharding/DynamicShardingManager.java`
  - 包含“创建分表 + 动态更新 `actual-data-nodes`”的思路（当前类上 `@Component` 注释掉，属于可选方案/预留能力）。

### 4）WebSocket 实时协同编辑：分布式锁 + 跨实例广播 + Disruptor 异步化

协同编辑链路按“连接 -> 消息 -> 异步处理 -> 广播”分层，重点解决**多实例一致性**与**高并发消息吞吐**：

- **WebSocket 接入**：`.../manager/websocket/WebSocketConfig.java` 注册 `/ws/picture/edit`
- **分布式处理器**：`.../manager/websocket/DistributedPictureEditHandler.java`
  - **编辑锁**：`PictureEditDistributedLockService` 使用 Redis `SETNX + TTL`，并在 Redis 不可用时降级为本机内存锁
  - **跨实例广播**：`PictureEditBroadcastPublisher` 抽象广播能力
    - 默认 **Redis Pub/Sub**：`PictureEditRedisPublisher`（失败时降级为本机广播）
    - 可选 **RabbitMQ fanout**：`.../distributed/mq/*`，每个实例声明独立队列，消费者收到后下发到本机 session
  - **Disruptor**：`.../manager/websocket/disruptor/PictureEditEventDisruptorConfig.java` 使用环形队列把 WS 消息处理异步化，提升高并发下的协同流畅度

### 5）Python Agent 模块：工具化封装 + Milvus 向量库（以文搜图）+ 与 Java 后端联动

Agent 服务提供统一搜索入口，并按 ReAct 思路把“决策/调用/观测”过程结构化返回，便于调试与展示：

- **对外 API**：`agent-backend/main.py` 暴露 `/agent/search`
- **Agent 路由与工具调用**：`agent-backend/agent_backend/agent.py`
  - `mode=auto` 时：启发式规则 +（可选）DeepSeek 决策，路由到
    - Java 后端关键词检索（`backend`）
    - Milvus 文本向量检索（`vector_text`）
    - Milvus 以图搜图（`vector_image`，当前为占位实现）
- **向量库客户端**：`agent-backend/agent_backend/vector_store.py`
  - 使用 `pymilvus` 连接 Milvus；文本向量检索默认支持 `sentence-transformers`（未安装则返回空结果，保证服务可启动）
- **MySQL -> Milvus 同步脚本**：`agent-backend/scripts/sync_milvus_from_mysql.py`
  - 从 `picture` 表抽取 `name/category/introduction/tags` 拼接文本做 embedding
  - 写入 Milvus `picture_vectors` 集合（`picture_id + embedding`）
- **与 Java 后端联动（补齐 PictureVO）**：`agent-backend/agent_backend/java_client.py`
  - 向量库返回 `picture_id` 后，通过 Java `/picture/get/vo` 补齐图片详情
  - 通过 `X-Internal-Token`（环境变量 `AGENT_INTERNAL_TOKEN`）进行**内部服务鉴权**，避免在 Agent 侧保存管理员账号密码
- **鉴权落点（Java）**：`picture-backend/src/main/java/.../config/AgentInternalAuthFilter.java`
  - 校验 `X-Internal-Token`，并限制仅本机来源
  - 在服务端注入管理员登录态（`HttpSession` + `Sa-Token SPACE`），以兼容空间权限校验

更详细的联动与鉴权说明见：`docs/01-auth-and-agent-integration.md`

---

## 目录结构（后端）

### `picture-backend/`（Spring Boot）

- **`controller/`**：REST 接口（图片/空间/用户等）
- **`manager/`**：核心能力封装
  - **`CosManager`**：COS 上传/下载/删除 + CI 图片处理
  - **`upload/`**：上传模板方法（支持不同输入源）
  - **`sharding/`**：分库分表与分片算法
  - **`websocket/`**：协同编辑（分布式锁/广播/Disruptor）
- **`service/`**：业务服务层（配合 MyBatis-Plus）
- **`config/`**：COS、内部鉴权 Filter 等基础配置

### `agent-backend/`（FastAPI）

- **`main.py`**：HTTP 入口
- **`agent_backend/agent.py`**：Agent 决策与工具调用（ReAct 轨迹）
- **`agent_backend/java_client.py`**：调用 Java 后端（内部 token 鉴权）
- **`agent_backend/vector_store.py`**：Milvus 向量检索客户端
- **`scripts/sync_milvus_from_mysql.py`**：MySQL 同步到 Milvus 的离线脚本
- **`docker-compose.yml`**：Milvus（含 etcd/minio）一键启动

---

## 快速启动（仅后端/本地联调）

### 1）启动 `picture-backend`

按 `picture-backend/src/main/resources/application.yml` 配置 MySQL、Redis 等依赖后启动 Spring Boot 即可。

### 2）启动 Milvus（用于向量检索）

在 `agent-backend/` 目录下：

```bash
docker compose up -d
```

### 3）启动 `agent-backend`

```bash
pip install -r agent-backend/requirements.txt
python agent-backend/main.py
```

> 向量“以文搜图”需要额外安装 `sentence-transformers`（脚本与向量客户端里有提示）；未安装时服务仍可运行，只是向量检索会返回空结果。

---

## 相关文档

- `docs/01-auth-and-agent-integration.md`：Java 后端与 Python Agent 的鉴权联动
