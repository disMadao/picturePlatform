# WebSocket 协同编辑：团队空间图片编辑通道（面试版）

### 1. 业务目标（我怎么讲给面试官听）

“团队空间里，多人可能同时打开同一张图片做编辑（裁剪、旋转等）。我们需要一个低延迟的实时通道来同步编辑状态，并且要严格控制权限，避免非团队成员/无权限用户接入。”

---

### 2. 核心设计概览

这个模块主要由 3 层组成：

- **连接层**：`WebSocketConfig` 注册 WebSocket 路由 `/ws/picture/edit`
- **握手鉴权层**：`WsHandshakeInterceptor` 在建立连接前做登录 + 权限校验
- **消息处理层**：`PictureEditHandler` 接收消息后投递到 **Disruptor** 环形队列异步消费，最终广播给同图的其他在线用户

---

### 3. 连接怎么建立（入口）

- WebSocket 路由：`/ws/picture/edit`
- 前端连接时会带参数：`pictureId`
- 服务端通过 `WebSocketConfig` 注册 handler + interceptor，并允许跨域（开发阶段）：
  - `registry.addHandler(pictureEditHandler, "/ws/picture/edit").addInterceptors(wsHandshakeInterceptor)...`

> 面试可强调：WebSocket 是长连接，建立连接的“前置校验”非常关键，否则后续每条消息再校验会很慢、也更容易出错。

---

### 4. 握手阶段做了哪些安全校验（重点）

`WsHandshakeInterceptor.beforeHandshake(...)` 主要做：

- **参数校验**：必须有 `pictureId`
- **登录校验**：用 `userService.getLoginUser(request)`（依赖 Session）
- **资源存在校验**：图片必须存在
- **空间类型校验**：如果图片在空间里，必须是 **团队空间**（非团队空间拒绝）
- **权限校验**：通过 `spaceUserAuthManager.getPermissionList(space, loginUser)` 判断是否有 `PICTURE_EDIT`

校验通过后，会把关键数据写入 WebSocket Session attributes：

- `user` / `userId` / `pictureId`

> 面试可强调：把 user/pictureId 缓存在 session attributes，后续处理消息时不需要反复查库。

---

### 5. 消息协议（很适合面试讲“可扩展性”）

请求消息：`PictureEditRequestMessage`

- `type`：`ENTER_EDIT` / `EXIT_EDIT` / `EDIT_ACTION`
- `editAction`：具体动作，如 `ZOOM_IN` / `ROTATE_LEFT` 等

响应消息：`PictureEditResponseMessage`

- `type`：`INFO` / `ERROR` / `ENTER_EDIT` / `EXIT_EDIT` / `EDIT_ACTION`
- `message`：提示文本
- `editAction`：动作回传
- `user`：操作者信息（UserVO）

> 扩展性：未来加“光标位置/选区/撤销重做”，只需要扩展 message 字段与枚举，不影响连接层。

---

### 6. “谁能编辑”的并发控制（简化版锁）

`PictureEditHandler` 内存维护两个结构：

- `pictureEditingUsers: Map<pictureId, userId>`：同一时刻只允许一个用户进入“编辑状态”
- `pictureSessions: Map<pictureId, Set<WebSocketSession>>`：同一张图的所有在线会话集合（用于广播）

流程：

- 连接建立：广播 `INFO`（“用户 X 加入编辑”）
- `ENTER_EDIT`：
  - 如果当前没有编辑者：设置 `pictureEditingUsers[pictureId]=userId`，广播 `ENTER_EDIT`
- `EDIT_ACTION`：
  - 只有“当前编辑者”发出的动作才会生效
  - 广播给同图其他用户（排除自己），避免重复应用
- `EXIT_EDIT` / 断连：
  - 如果是编辑者：清掉编辑锁并广播 `EXIT_EDIT`
  - 清理 sessionSet，必要时移除 pictureId 维度的集合

> 面试官可能追问：这是单机内存锁，横向扩展怎么办？  
回答：第一版优先满足单机协同；若要多实例，可把编辑锁迁移到 Redis（SETNX + TTL），广播迁移到 MQ 或 WS 网关。

---

### 7. 为什么引入 Disruptor（性能故事点）

`handleTextMessage` 不直接处理业务，而是：

- 解析 JSON → `PictureEditRequestMessage`
- 把事件投递到 `PictureEditEventProducer` → Disruptor RingBuffer

消费者 `PictureEditEventWorkHandler` 再根据 `type` 分发到：

- `handleEnterEditMessage`
- `handleExitEditMessage`
- `handleEditActionMessage`

好处（面试版表达）：

- **削峰**：避免 WebSocket IO 线程被业务处理阻塞（尤其多人同时发消息）
- **低 GC / 高吞吐**：Disruptor 的环形队列模型相比普通阻塞队列开销更低
- **结构清晰**：生产者负责“收消息”，消费者负责“执行业务”，方便扩展更多消息类型

---

### 8. 工程细节（加分项）

- **Long 精度问题**：广播时用 Jackson 把 Long/long 序列化成 String（避免 JS 端精度丢失）
- **广播排除自身**：`EDIT_ACTION` 广播时排除发起者，避免前端重复应用编辑动作

---

### 9. 面试官追问清单（可直接背）

- **Q：为什么握手时就做权限校验？**
  - **A**：长连接一旦建立，消息频繁；把鉴权前置可以降低每条消息成本，并且防止未授权用户占连接资源。

- **Q：同时多人编辑怎么解决冲突？**
  - **A**：第一版采用“编辑锁”（同一时刻只允许一个编辑者），其他人只能观看动作同步；后续可升级为 OT/CRDT 细粒度协同。

- **Q：Disruptor 的收益是什么？为什么不用普通线程池？**
  - **A**：消息量大时，普通线程池+队列易出现锁竞争和 GC 压力；Disruptor 更适合高频事件分发、吞吐更稳。


