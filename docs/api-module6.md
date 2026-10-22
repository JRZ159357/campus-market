# 模块 6 · 消息与通知 接口说明

> 依赖脚本：`sql/V7__chat_notice.sql`（新建 `chat_session` / `chat_message` / `notice` 三张表）
> 基础路径：`http://localhost:8080`，统一返回 `Result<T>`（`code=200` 成功）
> 权限：会话与消息接口需登录且必须是会话参与者；客服工作台接口对管理员与客服放开（全部 GET，天然只读）；公告发布与删除仅管理员
> 实时通道：`ws://localhost:8080/ws/chat?token=<JWT>`
> 相关配置：`app.ws.idle-timeout-ms`（连接空闲清理阈值，默认 90000 毫秒）

---

## 1. 数据库设计

### 1.1 `chat_session`（会话）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | 会话 ID |
| buyer_id | BIGINT | 咨询方（发起方） |
| seller_id | BIGINT | 被咨询方 |
| product_id | BIGINT NULL | 关联商品，可为空（如订单侧无商品上下文） |
| product_key | BIGINT 生成列 | `IFNULL(product_id, 0) STORED`，仅供唯一索引用 |
| last_message | VARCHAR(200) | 最后一条消息摘要，图片类自动转 `[图片]` |
| last_time | DATETIME | 最后消息时间，列表排序依据 |
| buyer_unread | INT | 咨询方未读数 |
| seller_unread | INT | 被咨询方未读数 |
| buyer_deleted / seller_deleted | TINYINT | 单向删除标记 |
| create_time / update_time | DATETIME | 通用时间戳 |

**索引**

- `uk_pair_product (buyer_id, seller_id, product_key)` —— 同一对买卖家在同一商品下只有一个会话。
  这里必须用生成列而不是直接对 `product_id` 建唯一索引：MySQL 的唯一索引把多个 `NULL` 视为互不相等，
  若不归一化，`product_id IS NULL` 的会话可以无限重复创建。`product_key` 把 NULL 折成 0，唯一性才成立。
- `idx_buyer_list (buyer_id, buyer_deleted, last_time)`、`idx_seller_list (seller_id, seller_deleted, last_time)` —— 支撑两侧列表查询与排序。
- `idx_product (product_id)` —— 支撑「我的发布 → 查看咨询」。

### 1.2 `chat_message`（消息）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | 自增，同时作为游标 |
| session_id | BIGINT | 所属会话 |
| sender_id / receiver_id | BIGINT | 发送方 / 接收方；系统消息的 sender_id 为 0 |
| msg_type | TINYINT | 1 文本 / 2 图片 / 3 系统 / 4 商品卡片 / 5 订单卡片 |
| content | TEXT | 文本内容、图片地址，或卡片 JSON |
| is_read | TINYINT | 0 未读 / 1 已读 |

**索引**

- `idx_session (session_id, id)` —— 游标分页主力索引。用 `id` 而不是 `create_time` 作第二列，是因为
  自增主键与写入时间同序，`WHERE session_id=? AND id<? ORDER BY id DESC LIMIT ?` 能直接走索引倒序扫描，不会出现同一毫秒多条消息导致的翻页跳条。
- `idx_receiver_unread (receiver_id, is_read)` —— 支撑未读统计。

### 1.3 `notice`（通知）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | 通知 ID |
| user_id | BIGINT | 接收人 |
| type | TINYINT | 1 订单 / 2 审核 / 3 公告 / 4 举报 |
| title | VARCHAR(60) | 标题 |
| content | VARCHAR(500) | 正文 |
| related_id | BIGINT NULL | 关联业务 ID（订单 ID / 商品 ID），用于详情页跳转 |
| is_read | TINYINT | 0 未读 / 1 已读 |

**索引**

- `idx_user_unread (user_id, is_read, create_time)` —— 未读优先列表 + 未读计数。
- `idx_type_time (type, create_time)` —— 公告聚合查询。

**公告为什么一人一行**：公告需要对每个接收人维护独立的已读状态，只能扇形写入（一个接收人一条记录）。
运营端要看到的却是"一次发布"，所以列表接口用 `GROUP BY title, content, create_time` 聚合成一行，
并用 `MIN(id)` 作为这一组的代表 ID。删除时先按代表 ID 回查该行拿到分组键，再整组删除。

---

## 2. WebSocket 协议

### 2.1 连接与鉴权

```
ws://localhost:8080/ws/chat?token=<JWT>
```

握手阶段完成鉴权，`ChatHandshakeInterceptor` 依次校验：

1. 取 token：优先 `?token=` 查询参数，其次 `Authorization` 请求头；
2. JWT 签名与有效期；
3. Redis 登录白名单（与 HTTP 接口同一套，退出登录后旧 token 立即失效）；
4. 用户存在且 `status=0`（未封禁）。

任一步失败返回 `401` 拒绝握手。**注意**：MVC 的 `HandlerInterceptor` 不作用于 WebSocket 握手，
鉴权必须放在 `HandshakeInterceptor` 里，否则连接会带着无效身份建立成功。

握手成功后把 `userId` 写入 `session.attributes`，后续帧不再重复校验身份（长连接每帧查库代价过高）。
真正有副作用的操作仍走 REST，由拦截器完整校验，所以"WS 身份弱校验 + REST 强校验"是安全的。

### 2.2 帧格式

统一信封，前端只按 `type` 分发，业务字段一律在 `data` 里：

```json
{ "type": "message", "data": { ... }, "timestamp": 1690000000000 }
```

| 方向 | type | data | 说明 |
|---|---|---|---|
| 下行 | `message` | `ChatMessageVO` | 新聊天消息（按接收方视角生成，`self=false`） |
| 下行 | `read` | `{ sessionId, readerId }` | 对方已读回执 |
| 下行 | `typing` | `{ sessionId, userId }` | 对方正在输入 |
| 下行 | `notice` | `NoticeVO` | 新系统通知 |
| 下行 | `unread` | `{ chatUnread, unreadSessions, noticeUnread, total }` | 未读数变化 |
| 下行 | `pong` | `{ serverTime }` | 心跳响应 |
| 下行 | `error` | `{ msg }` | 格式或业务错误提示 |
| 上行 | `ping` | — | 心跳，建议 30 秒一次 |
| 上行 | `read` | `{ sessionId }` | 标记已读并给对方推回执 |
| 上行 | `typing` | `{ sessionId }` | 正在输入（纯转发，不落库） |
| 上行 | `message` | `{ sessionId, msgType, content }` | 兼容 H5 端单连接发消息，与 REST 走同一 Service |

**连接建立后服务端会主动推一帧 `unread`**，前端角标无需额外请求。

### 2.3 心跳与断线重连

- 服务端：`WsSessionManager` 用 `@Scheduled` 每 30 秒扫描一次，关闭超过 90 秒无活动的连接。
  收到任意帧都会 `touch(session)` 续期，因此 30 秒一次的 `ping` 足以保活。
- 客户端（`miniprogram/utils/ws.js`）：`PING_INTERVAL=30000`，发送 `ping` 并等待 `pong`；
  断线后按 `min(2000 * 2^retryCount, 30000)` 指数退避重连，最多 5 次；
  超过 5 次仍失败则标记 `status='failed'` 并启动 10 秒轮询降级（`POLL_INTERVAL=10000`），
  只轮询未读数，聊天页仍可通过下拉刷新拉历史消息。

### 2.4 离线消息

不引入离线队列，采用**落库 + 上线拉取**：消息发送时无论对方是否在线都先写 `chat_message`，
在线则额外推一帧 `message`。对方上线后进入聊天页走 `GET /api/chat/session/{id}` 或
`GET /api/chat/message/{sessionId}` 拉取，连接建立时再补一帧 `unread` 让角标立刻正确。

这样做的代价是未读数需要独立维护（见第 4 节），收益是：消息不依赖长连接的可靠性，
服务端不需要为离线用户保存队列，也不会出现"推送失败就丢消息"的问题。

---

## 3. 接口清单

### 3.1 会话（5 个）

| 方法 | 路径 | 说明 | 鉴权 |
|---|---|---|---|
| GET | `/api/chat/session/list` | 我的会话列表（买家侧 + 卖家侧并集，按最后消息时间倒序） | 需登录 |
| GET | `/api/chat/session/{id}` | 会话详情：头信息 + 最近 50 条消息（时间正序）+ `hasMore` | 会话参与者 |
| POST | `/api/chat/session` | 创建或获取会话（幂等），返回会话 VO | 需登录 |
| DELETE | `/api/chat/session/{id}` | 删除会话（只从自己列表移除，对方仍可见） | 会话参与者 |
| GET | `/api/chat/session/by-product/{productId}` | 某商品下我作为卖家收到的咨询会话列表 | 商品归属卖家 |

`POST /api/chat/session` 请求体：

```json
{ "sellerId": 9, "productId": 41 }
```

`productId` 可为空。三个行为要点：

1. **角色归一化**：表的语义是 `buyer_id=发起方 / seller_id=被咨询方`，但当卖家从订单侧点「联系买家」时，
   传进来的 `sellerId` 其实是买家。服务层判断"当前用户是否是商品卖家"，若是则把两边对调，
   否则会生成 `(seller→buyer)` 的反向重复会话，两个人在列表里各看到一条。
2. **并发幂等**：两人同时点「联系卖家」可能同时通过"查不到"判断，插入时靠唯一索引拦下第二个，
   服务层捕获 `DuplicateKeyException` 后回查已有会话返回，前端无感知。
3. **首次带商品卡片**：仅当会话是新建的、且发起方是买家时，自动补一条 `msgType=4` 的商品卡片消息，
   让对方一进来就知道在聊哪件商品。

### 3.2 消息（3 个）

| 方法 | 路径 | 说明 | 鉴权 |
|---|---|---|---|
| GET | `/api/chat/message/{sessionId}?cursor=&size=` | 历史消息，游标分页，倒序返回 | 会话参与者 |
| POST | `/api/chat/message` | 发送消息，落库后推送对方 | 会话参与者 |
| PUT | `/api/chat/message/read/{sessionId}` | 标记会话已读 | 会话参与者 |

`POST /api/chat/message` 请求体：

```json
{ "sessionId": 7, "msgType": 1, "content": "在的，随时可以来看车" }
```

- `msgType=1` 文本，`content` 限 1000 字；
- `msgType=4/5` 卡片消息，客户端**只传业务 ID**（`productId` 或 `orderNo`），
  服务端重新查库生成卡片 JSON 存入 `content` —— 卡片上的标题、价格、状态不能由前端决定，否则可伪造"9.9 元成交"的假卡片；
- `msgType=3` 系统消息禁止客户端发送，只能由订单流程内部调用 `appendSystemMessage` 写入。

### 3.3 未读（1 个）

| 方法 | 路径 | 说明 | 鉴权 |
|---|---|---|---|
| GET | `/api/chat/unread` | 未读汇总，返回 `{ chatUnread, unreadSessions, noticeUnread, total }` | 需登录 |

返回体与 WebSocket 的 `unread` 帧完全一致，前端两条路径共用同一个角标逻辑。

### 3.4 系统通知（5 个 + 管理端 3 个）

| 方法 | 路径 | 说明 | 鉴权 |
|---|---|---|---|
| GET | `/api/notice/list?pageNum=&pageSize=&type=&onlyUnread=` | 我的通知列表，未读优先 | 需登录 |
| GET | `/api/notice/{id}` | 通知详情（越权返回"通知不存在"） | 通知归属人 |
| PUT | `/api/notice/read/{id}` | 标记单条已读 | 通知归属人 |
| PUT | `/api/notice/read-all` | 全部标记已读，返回影响条数 | 需登录 |
| GET | `/api/notice/unread-count` | 未读汇总（与 `/api/chat/unread` 同结构） | 需登录 |
| GET | `/api/notice/admin/page` | 已发布公告列表（按发布批次聚合） | 管理员、客服 |
| POST | `/api/notice/admin/publish` | 发布公告 | 仅管理员 |
| DELETE | `/api/notice/admin/{id}` | 删除公告（整组删除） | 仅管理员 |

列表排序为 `ORDER BY is_read ASC, create_time DESC`：未读永远浮在最上面，同组内按时间倒序。
这个排序在 `(user_id, is_read, create_time)` 索引上可以完全走索引，不需要额外排序。

`POST /api/notice/admin/publish` 请求体：

```json
{ "title": "国庆假期客服安排", "content": "10 月 1 日至 3 日客服在线时间调整为 10:00-18:00。", "scope": 1 }
{ "title": "账号异常提醒", "content": "请及时修改密码。", "scope": 2, "userIds": [5, 9] }
```

- `title` ≤ 60 字，`content` ≤ 500 字；
- `scope=1` 全员：查 `sys_user WHERE status=0` 拿全部正常用户；
- `scope=2` 指定用户：用 `userIds`；
- 批量插入按 500 条一批，避免单条 SQL 过大；
- 发布时间用同一个 `LocalDateTime` 常量传给所有行，否则 `GROUP BY create_time` 会把一次发布拆成多组。

### 3.5 客服工作台（2 个）

| 方法 | 路径 | 说明 | 鉴权 |
|---|---|---|---|
| GET | `/api/chat/admin/session/list?keyword=&pageNum=&pageSize=` | 全平台会话分页，支持按昵称搜索 | 管理员、客服 |
| GET | `/api/chat/admin/message/{sessionId}?size=50` | 查看指定会话的聊天记录 | 管理员、客服 |

两个接口都是 GET，与拦截器"客服非 GET 一律 403"的规则自然一致，因此**不存在客服发消息的接口**。
客服端查聊天记录时 `self` 恒为 `false`，界面靠 `fromBuyer` 区分左右（买家在左、卖家在右），
避免"当前登录的是客服，所有人都成了别人"的视角错乱。

`keyword` 匹配用户昵称：先查 `sys_user` 拿匹配的 ID 集合，再 `IN` 到会话查询；
匹配不到时直接返回空页，不发起大表扫描。

---

## 4. 未读数一致性设计

### 4.1 DB 是唯一事实来源

- 会话级：`chat_session.buyer_unread / seller_unread` 冗余计数，发消息时 `+1`，标记已读时归零；
- 通知级：`notice.is_read`，未读数用 `COUNT(*) WHERE is_read=0` 实时统计。

Redis 只缓存**标量总数**（`chat:unread:{userId}`、`notice:unread:{userId}`），TTL 1 天，
不参与任何业务判断，丢了最多多查一次库。

### 4.2 写后失效，而不是在缓存上加减

任何改变未读数的操作完成后调用 `unreadService.refresh(userId)`，实现是**删除缓存 key + 推送最新汇总**。

为什么不在缓存上直接 `INCR`：两个并发请求都基于同一个旧值做加法，结果会少算一次。
删除是幂等的，删错了最多导致下一次读回源一次库，代价可控；而加法算错是静默的数据错误。

Redis 不可用时 `cachedCount()` 内部降级为直查 DB，接口不报 500。

### 4.3 计数更新的原子性

会话未读直接用 SQL 表达式自增，不做"读-改-写"：

```java
update.setSql(receiverIsBuyer ? "buyer_unread = buyer_unread + 1" : "seller_unread = seller_unread + 1")
```

同时把双方的 `buyer_deleted / seller_deleted` 置 0 —— 用户删过会话后对方又发来消息，
会话应当重新回到列表，而不是留在"已删除"状态里收不到。

### 4.4 系统消息不计未读

订单状态变更写入的系统消息（`msgType=3`）不增加 `seller_unread/buyer_unread`。
理由是同一笔订单往往同时产生通知与系统消息，两边都计数会让角标虚高；通知才是"需要处理"的信号。

---

## 5. 通知触发点

统一入口在 `NoticeService`：`notifyOrder / notifyAudit / notifyReport`，
全部标注 `@Transactional(propagation = REQUIRES_NEW)` 并包 try-catch。

**为什么要 REQUIRES_NEW**：订单状态已经改成功了，通知写失败不应该让订单回滚。
用独立事务 + 吞异常，保证"主业务一定能提交，通知尽力而为"，
最坏情况是用户下次打开看到状态变了但没有收到通知，比订单操作直接失败好得多。

| 触发场景 | 通知标题 | 接收方 | type | relatedId |
|---|---|---|---|---|
| 下单成功 | 收到新订单 | 卖家 | 1 | 订单 ID |
| 买家付款 | 买家已付款 | 卖家 | 1 | 订单 ID |
| 卖家发货 | 卖家已发货 | 买家 | 1 | 订单 ID |
| 买家收货 | 买家已确认收货 | 卖家 | 1 | 订单 ID |
| 买家取消 | 买家取消了订单 | 卖家 | 1 | 订单 ID |
| 申请退款 | 收到退款申请 | 卖家 | 1 | 订单 ID |
| 同意退款 | 退款已完成 | 买家 | 1 | 订单 ID |
| 拒绝退款 | 退款申请被拒绝 | 买家 | 1 | 订单 ID |
| 撤销退款 | 买家撤销了退款申请 | 卖家 | 1 | 订单 ID |
| 管理员强制取消 / 强制退款 | 订单已被平台关闭 / 订单已被平台退款 | 买卖双方各一条 | 1 | 订单 ID |
| 订单超时 | 订单超时已取消 | 买家 | 1 | 订单 ID |
| 商品审核通过 / 驳回 | 商品审核通过 / 商品审核未通过 | 卖家 | 2 | 商品 ID |
| 学生认证通过 / 驳回 | 认证审核通过 / 认证审核未通过 | 用户 | 2 | 无 |
| 举报处理完成 | 举报处理结果 | 举报人 | 4 | 举报 ID |

通知详情页的跳转目标由 `targetOf()` 决定：

- `type=1` 且有 `relatedId` → `order`，跳 `/pages/order/detail?orderNo=`
- `type=2` 且有 `relatedId` → `product`，跳 `/pages/product/detail?id=`
- 其余 → `none`，不显示跳转按钮

同时订单状态变更还会调用 `ChatService.appendSystemMessage()`，在买卖双方已存在的会话里插入一条系统消息。
会话不存在时静默跳过 —— 用户没聊过天，没必要为了插一条系统消息凭空造一个会话出来。

---

## 6. 前端要点

### 6.1 消息视图必须按"谁看"生成

同一条消息推给接收方时，`self` 必须重新计算。`sendMessage` 里生成两份 VO：

```java
ChatMessageVO senderView   = toMessageVO(message, session, userId);
ChatMessageVO receiverView = toMessageVO(message, session, receiverId);
wsSessionManager.push(receiverId, WsFrame.of(WsFrame.TYPE_MESSAGE, receiverView));
return senderView;
```

早期实现直接复用发送方的 VO 推送，接收方收到后 `self=true`，消息被渲染到自己的右侧 ——
这是联调阶段由 `tests/ws_check.cjs` 抓到的真实 bug，靠单元测试很难发现。

### 6.2 小程序消息中心

`pages/message/index`：消息 / 通知双 Tab，各自带未读角标；会话卡片显示对方头像（首字）、昵称、
最后消息摘要、相对时间、未读圆点（超 99 显示 `99+`）、关联商品缩略条；
长按会话弹出 actionSheet（标记已读 / 删除）；下拉刷新 + 上拉加载；同时订阅 WS 的
`message / notice / unread / status` 四类事件。

### 6.3 小程序聊天页

- 背景 `#F5F5F5`，气泡圆角 12px，自己的气泡橙色渐变 `#FF6B35 → #FF8C5A` + 白字，对方白底带阴影；
- 时间分隔：与上一条间隔超过 5 分钟才渲染，避免每条消息都顶着时间戳；
- 进入页面即调用已读接口，并通过 WS 发一帧 `read` 让对方看到"已读"；
- 输入框 1.5 秒节流发送 `typing`，对方界面显示"正在输入"；
- 图片圆角 8px，点击 `wx.previewImage` 放大；
- 顶部显示对方昵称 + 关联商品标题，右上角"更多"可查看关联商品；
- 状态小绿点 / 红点反映 WS 连接状态。

### 6.4 tabBar 角标

`custom-tab-bar/index` 在 `attached` 时订阅 WS 的 `unread` 事件，收到后 `refresh()` 更新角标，
超出 99 显示 `99+`。各页 `onShow` 里调 `this.getTabBar().refresh()`，保证从二级页返回时角标同步。

---

## 7. 测试覆盖

| 脚本 | 用例数 | 覆盖内容 |
|---|---|---|
| `tests/smoke_module6.py` | 108 | 权限 401/403、会话创建幂等与角色反转、商品卡片自动消息、发消息/未读/已读、卡片消息、系统消息禁发、非参与者拒绝、游标分页无重复、详情 50 条 + hasMore、单向删除与复现、订单/审核通知触发、公告发布与删除、客服工作台、DB 落库校验 |
| `tests/ws_check.cjs` | 26 | 握手鉴权（错误 token 拒连）、连上即收 unread 帧、实时 message 帧（含 `self=false`）、unread 变化帧、read 回执、typing、ping/pong、notice 帧、离线消息落库与重连 |
| `tests/mp_chat_check.cjs` | 56 | 消息首页双 Tab 与角标、会话卡片与长按菜单、通知卡片与全部已读、聊天页时间分隔/气泡/已读/正在输入/游标加载/卡片与图片发送、通知详情跳转 |
