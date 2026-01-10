# WebSocket 协同编辑（分布式版）：Redis 锁 + Redis Pub/Sub（面试/实现笔记）

### 1. 为什么要做分布式改造？

单机版协同编辑通常会用内存结构：

- `Map<pictureId, editingUserId>`：编辑锁
- `Map<pictureId, Set<WebSocketSession>>`：在线会话集合

但当服务水平扩容为多实例时：

- **编辑锁不一致**：A 实例以为没人编辑，B 实例也以为没人编辑 → 冲突
- **广播不一致**：A 实例只知道自己机器上的 WebSocketSession，无法通知 B 实例上的用户

所以需要“跨实例的共享状态”和“跨实例广播通道”。

---

### 2. 目标（第一版可落地、可运行）

第一版分布式方案选择最小闭环：

- **编辑锁**：Redis `SETNX + TTL`
- **广播通道**：Redis `Pub/Sub`
- **兼容性**：Redis 不可用时自动降级为单机逻辑（保证能跑）

> 这版解决“锁一致 + 广播一致”，不追求 OT/CRDT 那种精细协同。

---

### 3. 编辑锁：Redis SETNX + TTL

核心点：

- key：`ws:picture:edit:lock:{pictureId}`
- value：`userId`
- 语义：同一时刻只有一个用户能拿到锁
- TTL：例如 5 分钟（防止断线未释放导致永久占用）

#### 进入编辑（ENTER_EDIT）

- `SET key userId NX EX ttl` 成功 ⇒ 获得编辑权
- 失败 ⇒ 说明已有编辑者（可给当前用户返回提示）

#### 编辑动作（EDIT_ACTION）

只有持锁者才允许广播动作：

- `GET key == userId` 才算当前编辑者
- 期间可 `EXPIRE key ttl` 做续期，避免长时间编辑锁过期被抢

#### 退出编辑/断开连接（EXIT_EDIT / disconnect）

只允许持锁者释放：

- `GET key == userId` ⇒ `DEL key`

> 更严谨的做法是 Lua 脚本原子释放；第一版为了“最简洁可运行”先用 get+del。

---

### 4. 广播：Redis Pub/Sub

每个应用实例：

- 维护自己本机的 `WebSocketSession` 集合
- 订阅一个 Redis channel（例如 `ws:picture:edit:broadcast`）

当某个实例需要广播消息时：

1) 把消息发布到 Redis channel  
2) 所有实例收到后，向各自本机的 sessions 广播

发布载荷示例：

- `pictureId`
- `excludeSessionId`：仅用于“发起者本机实例”避免重复推送给自己
- `message`：业务消息体（type/message/user/editAction）

---

### 5. 代码结构（本项目落地）

分布式相关类（新增）：

- `PictureEditDistributedLockService`：Redis 锁（带降级）
- `PictureEditRedisPublisher`：发布广播到 Redis（发布失败降级本机广播）
- `PictureEditRedisSubscriberConfig`：订阅 Redis channel，收到后广播到本机 session
- `PictureEditSessionRegistry`：本机 session 管理 + 广播能力

WebSocket Handler（新增）：

- `DistributedPictureEditHandler`：替代原 `PictureEditHandler`，实现：
  - 连接建立/关闭：注册/清理 session，并发布 INFO 消息
  - ENTER_EDIT / EXIT_EDIT / EDIT_ACTION：使用 Redis 锁 + Pub/Sub 广播

---

### 6. 面试官常问（准备回答）

- **Q: 什么情况下会出现丢失消息，为什么会出现丢失消息的情况？**
 - **A**：但订阅者和redis服务器断开连接的时候，redis集群发生网络分区，redis重启/崩溃的上班，都会出现消息丢失。这是因为Pub/Sub没有消息持久化机制。断开期间的消息也不会补发。

- **Q：为什么不用 MQ（Kafka/Rabbit）？**
  - **A**：第一版追求最小成本落地，Redis 已经是系统基础组件；后续消息量很大再迁移 MQ。

- **Q：Redis Pub/Sub 不可靠（丢消息）怎么办？**
  - **A**：协同编辑的动作同步允许“短暂不一致”，下一次动作会覆盖；如果要强一致，可用 Redis Stream / MQ 或引入版本号 + 补偿拉取。

- **Q：锁 TTL 到期会不会误释放？**
  - **A**：会，所以需要续期（编辑动作时 refresh TTL）。更严谨可加心跳消息或后台定时续期。


