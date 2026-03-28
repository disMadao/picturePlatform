---
name: picture-platform-dev
description: Development workflow for the picturePlatform monorepo (picture-backend Java/Spring, picture-frontend Vue, agent-backend Python). Use when working in this repository, debugging, or when the user pastes logs or stack traces from these services.
---

# picturePlatform 开发约定

## 1. 用户粘贴报错日志时（必须遵守）

1. **不要立刻改代码**。先根据日志（含异常类型、`Caused by`、业务栈里 `com.yupi` 等包名）说明**可能原因**与**依据**。
2. **说明结论后**再询问用户：**是否需要我直接修改代码**；未确认前不要擅自提交修复。
3. **若信息不足以判断根因**（如堆栈被截断、只贴了过滤器链、缺少关键一行 `at com.yupi...`）：**明确说无法确定**，并列出还缺什么（例如完整堆栈顶部、`Caused by`、复现步骤、当前 profile、相关配置片段）。**禁止编造原因**。
4. **若需要本地无法自行获取的信息**（例如线上环境变量、真实 DB/Redis 地址、用户未打开的文件）：**向用户说明**，请其补充，不要猜测填充。

## 2. 仓库结构（便于定位）

- `picture-backend`：Spring Boot，接口前缀多为 `/api`（见 `server.servlet.context-path`）。
- `picture-frontend`：Vue 前端。
- `agent-backend`：Python FastAPI，智能检索等。

改代码时**只动与任务相关的文件**，避免无关重构；用户未要求时不要新增文档。

## 3. 与用户沟通语言

- 中文回复，除非用户要求英文。

## 4. 数据库建表与 DDL

- 开发中若涉及**新建表、改表结构、执行迁移 SQL** 等，Agent **可能没有权限**在目标库上直接执行。
- 做法：在代码/方案里可写 **MyBatis 实体、Mapper、业务逻辑**；**DDL 脚本或迁移说明**整理给用户，由**用户自行在库中建表或执行**。
- 不要假设「已帮用户建好表」；若缺表会导致运行失败，应**明确列出**需要执行的 SQL 或变更点，方便用户操作。
