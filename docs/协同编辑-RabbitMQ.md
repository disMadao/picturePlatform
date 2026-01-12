# 协同编辑-RabbitMQ

[toc]

大部分内容都在上篇里面讲过了，不在赘述，这里重点介绍 RabbitMQ。

## 第一部分：RabbitMQ 是什么？

简单来说，RabbitMQ 是一个 **“消息代理” (Message Broker)**，你可以把它想象成一个 **“超级邮局”**。

在没有 RabbitMQ 时，服务 A 想要给服务 B 发数据，必须直接调用服务 B 的接口。如果服务 B 挂了，或者服务 A 想同时发给 B、C、D、E，代码就会变得非常复杂且脆弱。

RabbitMQ 的作用就是解耦：

1. **生产者 (Publisher)**：写信的人（你的代码）。把信（消息）丢进邮筒，不用管谁来收。
2. **交换机 (Exchange)**：邮局的分拣中心。它决定这封信是发给某一个人，还是复印几份发给所有人。
3. **队列 (Queue)**：具体的信箱。
4. **消费者 (Consumer)**：收信的人。从信箱里取信处理。

## 第二部分：项目中的相关代码实现

只需要三个类（Listener、Publisher、Config），需要用MQ实现的功能只有在 Handler 中将各种处理事件的消息广播给其他用户，具体的消息有：

```txt
"用户 %s 加入编辑"
"用户 %s 开始编辑图片"
"%s 执行 %s"
"用户 %s 退出编辑图片"
"用户 %s 离开编辑"
```

MQ和之前的Redis一样，进行分发消息和接收消息的处理。

### 1）分发消息（Publisher）- 生产者

类似redis，rabbitMQ也有一个类似的注入对象进行操作：

```java
private RabbitTemplate rabbitTemplate;
```

广播消息的的方法一样，因为MQ的实现和redis的的实现都实现的同一个接口，通过Spring注解配合yaml配置文件进行灵活选择。而且内部的传输方法名都和redis中的一样，都叫 `convertAndSend`：

```java
public void publish(Long pictureId, PictureEditResponseMessage message, String excludeSessionId) {
    PictureEditPubSubPayload payload = new PictureEditPubSubPayload();
    payload.setPictureId(pictureId);
    payload.setExcludeSessionId(excludeSessionId);
    payload.setMessage(message);
    String json = JSONUtil.toJsonStr(payload);
    try {
        rabbitTemplate.convertAndSend(PictureEditRabbitMqConfig.EXCHANGE_NAME, "", json);
    } catch (Exception e) {
        // 降级：MQ 不可用时仅本机广播（保证能跑）
        log.warn("Rabbit publish failed, fallback to local broadcast, err={}", e.getMessage());
        sessionRegistry.broadcast(pictureId, message, excludeSessionId);
    }
}
```

### 2）接受消息（Listener） - 消费者

通过注解和消息生产者中的 Queue 关联，实现订阅关系。

```java
@RabbitListener(queues = "#{pictureEditInstanceQueue.name}")
public void onMessage(String body) {
        PictureEditPubSubPayload payload = JSONUtil.toBean(body, PictureEditPubSubPayload.class);
        PictureEditResponseMessage msg = payload.getMessage();
        sessionRegistry.broadcast(payload.getPictureId(), msg, payload.getExcludeSessionId());
    
}
```



### 3）配置类

注入 `FanoutExchange`, `Queue`, `Binding`。

现在看的话，好像已经完善了，生产者发消息，消费者从订阅的Queue中消费消息。好像不需要别的了？这个类还在做写什么？

这个exchange就是消息中间件的路由机制，如果没有这个的话，那就只能一对一私聊了。特别是在当前代码中，可以看到我的 qName = "ws.picture.edit." + UUID.randomUUID();

同一个机器还好，能从Spring配置类中直接读到，不同机器就完全不知道对方的 qName 是什么了。这就轮到 Exchange(交换机) 登场了。它的作用就是 **"解耦发送者和接收者"**。

在我的代码中，Exchange 是 **Fanout（扇形/广播）** 类型的。

- **Publisher（发布者）说**：*“我不管谁在听，反正我有新消息了，我只负责把消息扔给 **'广播台' (Exchange)**。”*
- **Exchange（广播台）说**：*“收到了。我看下现在谁连着我...哦，有 Queue-UUID1、Queue-UUID2、Queue-UUID3。好，我把这条消息 **复印 3 份**，分别塞进这 3 个队列里。”*
- **Queue（队列）说**：*“我有新消息了，Listener 快来拿。”*

**这就是为什么要 Binding：** `Binding` 就是 **“订阅动作”**。 每台服务器启动时，创建了自己的随机 Queue，然后通过 Binding 告诉 Exchange：*“哪怕我是个临时工（随机队列），但我也是这个集体的一员，以后有广播记得分我一份。”*

### MQ广播机制

但是还有问题！为什么我的代码中需要 Listener 中 ”写死 “Queue的名字？而不是写 Exchange？

```java
@RabbitListener(queues = "#{pictureEditInstanceQueue.name}")
public void onMessage(String body) {}
```

**这是因为 Listener 必须绑定特定的Queue，不直接和 exchange 交互，和exchange 直接交互的是 发布者！发布者无需知道现在有哪些Queue**，回看代码，发送的时候有写了Queue吗？没有！只写了 exchange_name。这就是解耦。

```java
rabbitTemplate.convertAndSend(PictureEditRabbitMqConfig.EXCHANGE_NAME, "", json);
```



**终极比喻：大楼广播系统**

- **Publisher**：是**播音员**。
- **Exchange**：是**广播总台的线路**。
- **Queue**：是每个房间墙上的**喇叭**。
- **Listener**：是**坐在房间里的人**（就是这里的 `@RabbitListener`）。