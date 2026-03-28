---
name: picture-platform-backend-agent
description: Starts or restarts the picture-backend (Spring Boot) and agent-backend (FastAPI) services for the picturePlatform repo. Use when the user asks to start the backend, start the agent, run Java API, run Python agent, restart services, or verify local dev servers on ports 8123 and 9002.
---

# picturePlatform：后端 + Agent 启动与重启

## 端口与依赖关系

| 服务 | 目录 | 默认端口 | 说明 |
|------|------|----------|------|
| picture-backend | `picture-backend/` | **8123**，`context-path: /api` | Java；完整 API 根路径形如 `http://127.0.0.1:8123/api` |
| agent-backend | `agent-backend/` | **9002**（`AGENT_PORT`） | Python FastAPI；Agent 通过 `JAVA_BASE_URL` 调 Java，**应先保证 Java 已监听** |

Agent 需要连库/向量等时，按各环境 `application-*.yml` 与 `.env` 自行就绪（MySQL、Redis、Milvus 等）。

## 启动前检查

1. **释放端口**（若上次未正常退出）：在对应系统结束占用 **8123** / **9002** 的进程，或先结束旧终端里的服务。
2. **Agent 环境变量**（`agent-backend/.env` 或当前 shell）：至少 `JAVA_BASE_URL=http://127.0.0.1:8123/api`（与 `server.servlet.context-path` 一致）；需要内部鉴权时配置 `AGENT_INTERNAL_TOKEN`，与 Java 侧 `agent.internalToken` 一致。

## 启动顺序（推荐）

1. 启动 **picture-backend**（先起 Java，再起 Agent）。
2. 启动 **agent-backend**。

## Windows PowerShell（本仓库已验证可用）

在仓库根目录 `picturePlatform` 下执行。

**终端 1 — Java 后端（两种方式任选）**

首选（简单）：

```powershell
Set-Location picture-backend
mvn -DskipTests spring-boot:run
```

若在 Windows 上出现 **`CreateProcess error=206` / 文件名或扩展名太长**（依赖多、命令行类路径过长），改用 **打 fat jar 再运行**（已在本机验证可启动 `Tomcat started on port(s): 8123`）：

```powershell
Set-Location picture-backend
mvn -DskipTests package
java -jar target/picture-backend-0.0.1-SNAPSHOT.jar
```

（版本号以 `pom.xml` 里 `<version>` 为准，jar 名一般为 `picture-backend-<version>.jar`。）

**终端 2 — Python Agent**

```powershell
Set-Location agent-backend
python main.py
```

若使用虚拟环境，先 `.\venv\Scripts\Activate.ps1`（路径以本机为准）再运行 `python main.py`。

**本机一次成功联调结果**（供对照）：`GET http://127.0.0.1:8123/api/health` 返回 `{"code":0,"data":"ok",...}`；`GET http://127.0.0.1:9002/health` 返回 `code:0` 且 `data.status` 为 `ok`。

## 启动成功判定

在**第三个**终端或请求工具中验证（服务起来后再测）：

- Java：`GET http://127.0.0.1:8123/api/health` 应返回业务包装的成功响应（`MainController` 的 `/health` 挂在 context-path 下）。
- Agent：`GET http://127.0.0.1:9002/health` 应返回 JSON，`code` 为 0。

PowerShell 示例：

```powershell
Invoke-WebRequest -Uri "http://127.0.0.1:8123/api/health" -UseBasicParsing
Invoke-WebRequest -Uri "http://127.0.0.1:9002/health" -UseBasicParsing
```

## 重启时的习惯做法（供 Agent 执行）

1. 查看是否已有终端在跑 `spring-boot:run` 或 `python main.py`；若有，可在该终端 **Ctrl+C** 停掉再启动，或结束对应进程。
2. 按上表顺序重新执行两条启动命令；长耗时任务使用后台运行并读取终端输出文件直至健康检查通过。
3. Maven 首次拉依赖较慢属正常；若 Java 编译报 Lombok/JDK 问题，以本仓库 **`picture-backend/pom.xml`** 中已配置的 Lombok 与 `maven-compiler-plugin` 为准，使用本机 **JDK 17+** 运行 Maven 一般可编过。

## 常见变量速查

- Agent 端口：`AGENT_PORT`（默认 9002）。
- CORS：`agent-backend` 中 `CORS_ORIGINS`，需包含前端源（如 `http://localhost:5173`）。
