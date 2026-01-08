# WebSocket 分布式广播：用 MQ 替代 Redis Pub/Sub（面试/实现版）

### 1. 为什么要替代 Redis Pub/Sub？

Redis Pub/Sub 的核心问题是**“在线订阅才能收到”**：

- 订阅者进程重启 / 网络抖动 / Redis 连接短暂断开时，消息会**直接丢失**（没有队列缓存、没有 ACK 重试）
- 对“协同编辑动作”这种高频事件来说，短暂丢消息在体验上会放大（状态不同步、用户以为卡顿）

所以我们把跨实例广播升级为 MQ（RabbitMQ）：

- **有队列**：消息先进入队列
- **有 ACK**：消费者确认消费
- **可重试/可扩展**：后续可加死信、重试策略

---

### 2. 选型：RabbitMQ Fanout Exchange（广播）

目标是“每条编辑消息发给所有实例”，典型实现是：

- **Fanout Exchange**：发布一次，路由到所有绑定队列
- **每个应用实例一个队列**：实例启动时声明一个队列并绑定到 exchange

这样每个实例都能收到同一条消息，并转发给本机维护的 WebSocketSession。

---

### 3. 项目里的落地方式（简洁可运行）

我们新增了 3 个组件（只在 `pictureEdit.broadcast=mq` 时启用）：

- `PictureEditRabbitMqConfig`
  - 声明 `ws.picture.edit.fanout` fanout exchange
  - 每个实例声明一个 auto-delete 队列并绑定到 exchange
- `PictureEditRabbitPublisher`
  - 将广播消息投递到 fanout exchange
  - MQ 不可用时降级为本机广播（保证服务可运行）
- `PictureEditRabbitListener`
  - 监听本实例队列，消费后调用 `PictureEditSessionRegistry.broadcast(...)` 推送给本机用户

并且引入了统一抽象：

- `PictureEditBroadcastPublisher`
  - `redis`/`mq` 两种实现通过配置切换

---

### 4. 开关配置

`application.yml`：

```yaml
pictureEdit:
  broadcast: mq   # mq / redis
```

默认仍为 `redis`，确保没有 RabbitMQ 环境也能跑；需要更可靠时再切到 `mq`。

---

### 5. 面试官追问点（可直接背）

- **Q：MQ 一定不丢消息吗？**
  - **A**：比 Pub/Sub 更可靠，因为有队列和 ACK；但要做到“强可靠”还要配合 durable queue、publisher confirm、以及持久化消息。

- **Q：为什么队列用了 auto-delete？**
  - **A**：第一版为了简洁和本地联调方便；生产环境可以用“固定队列名 + durable queue”或“实例注册中心 + 队列生命周期管理”来增强可靠性。

- **Q：消息很频繁，会不会压垮 MQ？**
  - **A**：协同编辑动作可以合并/采样（例如只保留最新状态），也可以分级：关键事件走 MQ，非关键事件走 WebSocket 直推或本机缓存。


