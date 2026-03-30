# gRPC 改造方案（文档索引）

本目录说明在 **picturePlatform** 中，如何将 **agent-backend（Python）** 与 **picture-backend（Java）** 之间的内部调用从 HTTP 逐步迁移到 gRPC，**不替代**对外 REST API。

文档按部分拆分，便于评审与分阶段实施：

| 文档 | 内容 |
|------|------|
| [01-整体设计.md](./01-整体设计.md) | 范围、架构、端口、鉴权、风险与决策 |
| [02-java端改造.md](./02-java端改造.md) | Maven 依赖、proto、拦截器、gRPC 服务实现（示例代码） |
| [03-python端改造.md](./03-python端改造.md) | 依赖、代码生成、Stub 调用与 Metadata（示例代码） |

说明：文中代码均为 **落地参考示例**，粘贴到工程前需与当前包名、类名、Service 方法签名对齐；**请勿**在未评审的情况下直接覆盖现有业务类。
