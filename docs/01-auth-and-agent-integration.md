# 登录鉴权方案：Java 后端 & Python Agent 联动（面试版）

### 1. 我们的真实现状（代码层面）

后端登录态是“双轨制”：

- **HttpSession（Spring Session + Redis）**：登录时写 `request.getSession().setAttribute(USER_LOGIN_STATE, user)`，鉴权时用 `request.getSession().getAttribute(...)` 判断是否登录。
- **Sa-Token（Space 账号体系）**：登录时 `StpKit.SPACE.login(userId)`，空间权限校验用 `@SaSpaceCheckPermission` / `StpKit.SPACE.hasPermission(...)`。

一句话总结：**“是否登录”主要看 Session；“是否有空间权限”主要看 Sa-Token SPACE 会话**。

---

### 2. 面试官常问：为什么不用 JWT？为什么用 Session？

- **Session 优点**：
  - **后端可控**：服务端能主动失效（踢下线、改密码后强制重新登录）。
  - **适配复杂权限**：尤其是“空间/团队权限”这种动态权限，Session/Server-side 状态更自然。
  - **多端一致**：浏览器天然带 Cookie，前端接入成本低。
- **Session 缺点**：
  - **跨服务共享要做治理**：单机内存 session 不可扩展，所以我们用 **Spring Session + Redis** 做集中存储。
  - **CSRF 风险**：需要 SameSite/CSRF token 等配套（项目里 Cookie 有配置，业务上可补 CSRF）。

如果用 JWT：
- 优点是无状态、易扩展；但需要额外设计 token 续期/撤销、权限变更一致性、以及安全存储（前端存储策略）等。

---

### 3. Python Agent 调用 Java 接口，为什么会“未登录”？

Agent 是一个独立的服务（FastAPI），它调用 Java 的 `/api/picture/get/vo` 等接口。

**Java 的登录态依赖 Cookie（SESSION/JSESSIONID 等）**，而 Agent 默认不会携带浏览器的 Cookie，所以 Java 看到的是“匿名请求”，自然返回 `40100 未登录`。

---

### 4. 方案对比（我们选什么，为什么）

#### 方案 A：Agent 里保存管理员账号密码，先调用 `/user/login` 拿 cookie，再请求业务接口

- **做法**：Agent 维护一个 http client（带 cookie jar），401 时自动登录并重试。
- **优点**：实现非常快、100% 复用现有鉴权链路。
- **缺点**：Agent 侧需要保存账号密码（哪怕是环境变量），面试时容易被追问“凭证泄露怎么办”。

#### 方案 B：让 Python 直接读 Redis 里的 Session，伪造登录态（你提出的思路）

- **听起来很美**，但实践上**不可取/成本高**：
  - Redis Session 里存的是 Java 侧的序列化对象（`User` 等），Python 难以可靠反序列化并构造出 Java 能认可的状态。
  - **最终仍然要靠 cookie 里的 sessionId 绑定请求**，你“直接写 Redis”本质是在“伪造服务端状态”，安全风险更大、可维护性更差。

结论：**不推荐**。

#### 方案 C（推荐）：内部服务鉴权（Internal Token / Service-to-Service Auth）

- **做法**：
  - Agent 请求 Java 时带一个 `X-Internal-Token`。
  - Java 端增加一个 Filter：校验 token + 限制来源（只允许本机/白名单），通过后在服务端“注入管理员登录态”（写入 HttpSession + Sa-Token SPACE token）。
- **优点**：
  - **Agent 不存账号密码**（只存一个可轮换 token）。
  - **最小侵入**：不动核心业务鉴权，只加一个“内部入口”。
  - **可治理**：token 可轮换、可加 IP 白名单、可加审计日志。
- **缺点**：
  - 需要额外配置和安全边界（token 泄露、来源限制、仅内网可用）。

我们在项目里采用的方向就是 **方案 C**：简单、可控、便于面试讲清楚“工程化权衡”。

---

### 5. 面试官追问清单（可直接背）

- **Q：内部 token 泄露怎么办？**
  - **A**：token 只在内网/本机使用 + IP 白名单；定期轮换；加日志审计；必要时加 mTLS。

- **Q：为什么不直接把 Java 接口改成公开接口？**
  - **A**：公开接口意味着业务权限失控；内部调用也必须有明确的“服务间身份”，否则就是安全漏洞。

- **Q：方案 C 会不会绕过权限？**
  - **A**：它是**明确的内部通道**，只用于 Agent 这类服务间调用；对外仍然走原登录态与权限模型。并且我们可以把 token 权限限制到“只读接口”，进一步缩小攻击面。

---

### 6. 一句话故事（面试收尾）

“我们用 **Spring Session Redis** 解决 session 分布式问题，用 **Sa-Token** 做空间权限体系；当引入 Python Agent 作为独立服务后，为避免保存管理员密码，我们设计了 **Internal Token 的服务间鉴权**，在 Java 端通过 Filter 注入管理员态，既复用了现有权限模型，又把安全边界控制在内网可治理范围内。”


