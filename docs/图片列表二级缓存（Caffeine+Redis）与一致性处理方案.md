### 图片列表二级缓存（Caffeine + Redis）与一致性处理方案（picture-backend）

本说明面向 `picture-backend` 的“图片列表分页查询缓存”，重点解释：当前项目哪里用了二级缓存、现状不足、常见解决方案、以及本项目更适合的“版本号命名空间（namespace）”方案。

---

### 1. 项目中用到了的地方（真实代码）

#### 1) 后端：仅一个列表接口做了二级缓存（且已废弃）

- 位置：`picture-backend/src/main/java/com/yupi/yupicturebackend/controller/PictureController.java`
- 接口：`POST /picture/list/page/vo/cache`
- 标记：`@Deprecated`
- 缓存策略：
  - **一级缓存（本地）**：Caffeine `LOCAL_CACHE`（5 分钟过期）
  - **二级缓存（分布式）**：Redis（5–10 分钟随机过期，防雪崩）
  - key：`yupicture:listPictureVOByPage:{md5(queryJson)}`

#### 2) 前端：实际页面没有使用带缓存的接口

- 首页列表：`picture-frontend/src/pages/HomePage.vue` 调用的是 `POST /api/picture/list/page/vo`
- 空间详情页：`picture-frontend/src/pages/SpaceDetailPage.vue` 调用的也是 `POST /api/picture/list/page/vo`
- `POST /api/picture/list/page/vo/cache` 只在 OpenAPI 生成的 `picture-frontend/src/api/pictureController.ts` 中出现“函数定义”，没有被页面调用。

> 结论：当前“二级缓存”更像是演示/试验接口，而不是主链路。

---

### 2. 现状不足（为什么会被废弃/为什么没做写后失效）

- **一致性缺失**：只有读路径写入缓存，没有“写后失效/更新”的配套流程；数据库更新后，列表缓存会在 TTL 内返回旧数据（最终一致）。
- **key 不可枚举**：缓存 key 基于“查询条件 JSON → MD5”，上传/编辑/删除/审核会影响大量条件组合的列表结果，几乎无法精确删除所有相关 key。
- **逻辑与新版接口不一致**：新版 `/list/page/vo` 有“公开图库 nullSpaceId/空间权限校验”等逻辑，而缓存接口写死了 `reviewStatus=PASS`，容易跟业务规则漂移。
- **反序列化类型不稳**：`JSONUtil.toBean(cachedValue, Page.class)` 会丢失 `Page<PictureVO>` 的泛型信息，复杂字段变化时更容易出问题。

---

### 3. 常见解决方案（每种 1–2 句话）

- **Cache-Aside + 精确 key 失效**：写库成功后删除对应缓存 key（适合 `picture:{id}` 这种单对象缓存；不适合“多条件列表”）。
- **延迟双删（double delete）**：先删缓存→写库→延迟再删一次，降低并发下的脏读概率（仍要求 key 可定位）。
- **订阅/消息驱动失效（PubSub/MQ）**：写库后发布“失效事件”，各节点清理本地缓存，Redis 侧可删前缀或 bump 版本（适合多实例）。
- **Write-Through / Write-Behind**：把缓存当主写入口（或异步回写 DB），一致性更强但改造大、对列表场景不友好。
- **版本号命名空间（namespace / generation）**：维护一个“列表版本号”，key 拼 version；写操作只需 `INCR version`，旧 key 自动失效（非常适合多条件列表缓存）。

---

### 4. 推荐方案：版本号命名空间（重点）

#### 4.1 适用原因（对本项目最匹配）

- 本项目列表缓存 key 为“条件 md5”，**无法逐 key 删除**；版本号方案用一次 `INCR` 就能整体切换到新命名空间。
- 适配二级缓存：Redis 和本地 Caffeine 都用相同的 key，**写后自然“换 key”**，无需对 Caffeine 做复杂清理。

#### 4.2 核心规则

- **读列表**：先拿 `version`，再生成
  - `cacheKey = yupicture:listPictureVOByPage:v{version}:{md5(queryJson)}`
- **写操作**（上传/编辑/删除/审核等影响列表展示）：只做
  - `INCR yupicture:picture:list:version`

#### 4.3 核心代码（精简版，可直接落到 Controller/Service）

**(1) 版本号 key**

```java
private static final String LIST_CACHE_VERSION_KEY = "yupicture:picture:list:version";
private static final String LIST_CACHE_KEY_PREFIX = "yupicture:listPictureVOByPage";
```

**(2) 获取版本号 + 生成缓存 key**

```java
private long getListCacheVersion() {
    String v = stringRedisTemplate.opsForValue().get(LIST_CACHE_VERSION_KEY);
    if (v == null) {
        stringRedisTemplate.opsForValue().setIfAbsent(LIST_CACHE_VERSION_KEY, "1");
        return 1L;
    }
    return Long.parseLong(v);
}

private String buildListCacheKey(PictureQueryRequest req) {
    String queryJson = JSONUtil.toJsonStr(req);
    String md5 = DigestUtils.md5DigestAsHex(queryJson.getBytes());
    long version = getListCacheVersion();
    return LIST_CACHE_KEY_PREFIX + ":v" + version + ":" + md5;
}
```

**(3) 写后失效：只 bump 版本**

```java
private void bumpListCacheVersion() {
    stringRedisTemplate.opsForValue().increment(LIST_CACHE_VERSION_KEY);
}
```

#### 4.4 “写后失效”应该挂在哪些场景？

只要会影响“列表页能看到什么/列表字段变化”的写操作，就应该 bump：

- **上传图片**：`/picture/upload`、`/picture/upload/url`
- **编辑图片**：`/picture/edit`、`/picture/update`（管理员更新）
- **删除图片**：`/picture/delete`
- **审核图片**：`/picture/review`（reviewStatus 影响是否出现在公开列表）
- （可选）**批量编辑**：`/picture/edit/batch`

> 触发时机：建议在“数据库事务成功提交之后”再 bump（至少要保证写库成功再失效缓存）。

#### 4.5 更细粒度（可选）：按 spaceId 做版本号

如果担心一次写操作导致全站列表缓存全部失效，可以改成：

- 公共图库：`yupicture:picture:list:version:public`
- 空间列表：`yupicture:picture:list:version:space:{spaceId}`

这样上传/编辑某个空间的图片只影响该空间的列表缓存。

---

### 5. 落地建议（结合当前代码）

- 由于 `/list/page/vo/cache` 已 `@Deprecated` 且前端未使用，若要真正上线列表缓存，建议：
  - 在主接口 `/picture/list/page/vo` 引入缓存（或新增一个非废弃的缓存版接口），并采用“版本号命名空间”方案；
  - 写接口成功后统一 bump version（可抽成 `PictureCacheManager`/`CacheVersionService`，避免 Controller 里散落逻辑）。


