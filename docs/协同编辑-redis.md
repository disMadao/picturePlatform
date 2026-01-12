# 协同编辑-redis

[toc]



这篇文档用于理清整个项目中的使用了redis的分布式协同编辑方案。

模块的文件目录如下：

```txt
websocket
├── disruptor                        // LMAX Disruptor 高性能队列相关
│   ├── PictureEditEvent             // 编辑事件定义
│   ├── PictureEditEventDisruptorConfig // Disruptor 配置
│   ├── PictureEditEventProducer     // 事件生产者
│   └── PictureEditEventWorkHandler  // 事件消费者/处理器
├── distributed                      // 分布式相关功能
│   └── mq                           // 消息队列/Redis PubSub
│   ├── PictureEditBroadcastPublisher  (Interface) // 广播发布接口
│   ├── PictureEditDistributedLockService // 分布式锁服务
│   ├── PictureEditPubSubPayload       // 发布订阅消息载体
│   ├── PictureEditRedisPublisher      // Redis 消息发布者
│   ├── PictureEditRedisSubscriberConfig // Redis 订阅配置
│   └── PictureEditSessionRegistry     // 分布式会话注册表
├── model                            // 数据模型 (折叠中)
├── DistributedPictureEditHandler    // 分布式图片编辑处理器
├── PictureEditHandler               // 本地图片编辑处理器
├── WebSocketConfig                  // WebSocket 全局配置
└── WsHandshakeInterceptor           // WebSocket 握手拦截器
```





## WebSocket

从最上层开始看，这里只有一个 WebSocketConfig 类实现 `WebSocketConfigurer`接口：

里面只有两个对象：

```java
//建立连接后的所有通信逻辑
@Autowired
private DistributedPictureEditHandler pictureEditHandler;

//负责验证和筛选连接请求
@Resource
private WsHandshakeInterceptor wsHandshakeInterceptor;
```

然后一个专门的注册方法 WebSocketHandlers的方法：

```java
@Override
public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(pictureEditHandler, "/ws/picture/edit")
        .addInterceptors(wsHandshakeInterceptor)
        .setAllowedOrigins("*");
}
```

这个 Interceptor 是用于验证参数的，只有两个方法`beforeHandshake`和`afterHandshake`。

其中的`beforeHandshake`处理主要逻辑：

> 比如是否登录，图片是否存在，空间是否存在，权限是否有，之类的。还会把登录信息和图片id保存在 WebSocket 的会话中，这个会话具体就是一个在方法中一个参数` Map<String, Object> attributes` 里面。

这个对象比较简单，主要逻辑都在 `Handler`中。



## Handler

Handler通过继承 `TextWebSocketHandler` 实现。继承而来的方法有三个：

```java
  void afterConnectionEstablished(WebSocketSession session)
  void handleTextMessage(WebSocketSession session, TextMessage message)
  void afterConnectionClosed(WebSocketSession session, CloseStatus status) 
```

重要的数据有 下面几个：

```java
//仅用于广播事件到 disruptor中，交给里面的线程异步处理事件， disruptor后面会介绍
private PictureEditEventProducer pictureEditEventProducer;

//封装的一个自定义类，内部用Map<Long, Set<WebSocketSession>>管理和广播到session
private PictureEditSessionRegistry sessionRegistry;

//redis实现的分布式锁，进入时setIfAbsent，退出时del 
private PictureEditDistributedLockService lockService;

//用于广播所有通知类别消息
private PictureEditBroadcastPublisher broadcastPublisher;
```

`afterConnectionEstablished`方法：

- 调用 `sessionRegistry` 将新的用户加入到对应图片的编辑组中，返回一个消息 "用户 %s 加入编辑"
- 这个消息交给`broadcastPublisher` 进行广播到其他正在编辑的用户。

`handleTextMessage` 方法：

- 获取 `TextMessage` 和此时的session，对从`TextMessage`中收到的事件进行处理。
- 原版本是直接就在这里进行处理的，但WebSocket虽然对每个用户分开session进行管理，但每个session的连接本身还是单线程的，这里可以将收到事件和处理事件进行异步化，这也就是后面的 `disruptor` 模块的作用，其实也可以用线程池处理，disruptor更高效。

`afterConnectionClosed` 方法：

- 从  `sessionRegistry` 中移除 session
- 发送 "用户 %s 离开编辑" 到全局用户





## redis 的pub/sub机制

1）pub

也就是上面的 Handler中的PictureEditBroadcastPublisher类，实际上这是个接口，这里使用redis实现了一个类叫做 `PictureEditRedisPublisher`。

专门用来实现 pub 操作，核心只有一行代码：

```java
stringRedisTemplate.convertAndSend(CHANNEL, json);//CHANNEL = "ws:picture:edit:broadcast";
```

2）sub

通过一个配置类，内部实现两个返回xxContainer和xxListner的方法。

Listner中监听消息，反序列化之后通过 `sessionRegistry` 广播到本机的用户中。



## redis的分布式锁

这里采用了线程安全的 ConcurrentHashMap 来存储图片id对应的用户id键值对，用于降级才会被用到的本地锁。

正常情况下不会使用，全用stringRedisTemplate，核心代码如下：

```java
//尝试加锁同时判断可见
Boolean ok = stringRedisTemplate.opsForValue().setIfAbsent(key(pictureId), String.valueOf(userId), LOCK_TTL);
//判断是否拥有
String v = stringRedisTemplate.opsForValue().get(key(pictureId));
//删除锁
stringRedisTemplate.delete(key(pictureId));
```



## disruptor

原本的处理逻辑很简单，根据对应的事件，封装几个处理方法，然后switch即可。

现在会稍微复杂点，需要采用一个生产者消费者的模式来处理。

三个核心类：

- PictureEditEventDisruptorConfig：图片编辑事件 Disruptor 配置
- PictureEditEventProducer：图片编辑事件生产者。
- PictureEditEventWorkHandler：图片编辑事件处理器（消费者）

1）PictureEditEventDisruptorConfig

创建 disruptor并设置消费者、ringBuffer大小，启动 disruptor这个异步线程之后返回Disruptor这个Bean。

2）PictureEditEventWorkHandler

内置 `DistributedPictureEditHandler` ，这里因为之前处理逻辑就在这个类里面，属于是直接复用现有逻辑了。

这里的 `onEvent`方法监听消息，然后通过switch判断之后交给对应的方法处理。

3）PictureEditEventProducer

`publishEvent` 方法被 之前说过的 `DistributedPictureEditHandler` 用于在处理消息的时候发送事件。在这个方法内部，通过 `pictureEditEventDisruptor` 拿到 `disruptor`内部的ringBuffer ,获取现有空数据块填充后通知消费者这个位置已准备好了。

Disruptor的工作模式是：

```
生产者：准备数据 → 通知缓冲区（发布位置）
消费者：监听缓冲区 → 发现新位置 → 读取数据
```

所以其实生产者最终通知的是一个位置数字，生产者做的就是填充这个位置对应的块的数据。