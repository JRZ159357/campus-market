# 模块 7 · 评价与信誉分 接口说明

> 依赖脚本：`sql/V8__review_credit.sql`（新建 `review` / `credit_log` 两张表，`trade_order` 增 `buyer_reviewed` / `seller_reviewed`）
> 基础路径：`http://localhost:8080`，统一返回 `Result<T>`（`code=200` 成功；**异常同样以 HTTP 200 返回，真实结果码在 body.code**）
> 权限：评价写入需登录且必须是订单参与方；商品/用户评价列表、信誉分详情免登录可读；管理端列表管理员与客服可看，删除评价与调整信誉分仅管理员
> 相关常量：`common/CreditRule`（初始分与加分规则）、`common/CreditLevel`（等级区间）、`common/ReviewRule`（追评窗口与内容上限）

---

## 1. 数据库设计

### 1.1 `review`（评价）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | 评价 ID |
| order_id | BIGINT | 订单 ID |
| product_id | BIGINT | 商品 ID（从订单冗余，便于按商品查评价） |
| order_no | VARCHAR(40) | 订单号（冗余，管理端可直接展示与模糊搜索） |
| reviewer_id | BIGINT | 评价人 |
| reviewee_id | BIGINT | 被评价人 |
| review_type | TINYINT | 1 买家评卖家 / 2 卖家评买家 |
| stars | TINYINT | 综合星级（各维度平均后四舍五入） |
| desc_stars | TINYINT NULL | 商品描述相符（仅方向 1 填） |
| ship_stars | TINYINT NULL | 发货速度（仅方向 1 填） |
| attitude_stars | TINYINT NULL | 沟通态度（两个方向都填） |
| pay_stars | TINYINT NULL | 付款速度（仅方向 2 填） |
| content | VARCHAR(500) | 文字评价，可为空 |
| images | VARCHAR(1000) | 图片 JSON 数组，最多 3 张；空值统一存 `"[]"` |
| is_anonymous | TINYINT | 0 实名 / 1 匿名 |
| reply_content | VARCHAR(500) NULL | 卖家回复 |
| reply_time | DATETIME NULL | 回复时间 |
| follow_content | VARCHAR(500) NULL | 追评内容 |
| follow_images | VARCHAR(1000) NULL | 追评图片 |
| follow_time | DATETIME NULL | 追评时间 |
| deleted | TINYINT | 逻辑删除标记 |
| create_time / update_time | DATETIME | 时间戳 |

**索引**

- `uk_order_type (order_id, review_type)` —— 同一订单的同一方向只能有一条评价。
  这是防重复评价的最终防线：服务层的 `countByOrderType` 只是为了给出友好提示，
  并发下真正兜住的是这个唯一索引（捕获 `DuplicateKeyException` 后转成同样的提示）。
- `idx_reviewee_time (reviewee_id, create_time)` —— 「我收到的评价」列表与被评价人统计。
- `idx_reviewer_time (reviewer_id, create_time)` —— 「我发出的评价」。
- `idx_product_time (product_id, create_time)` —— 商品评价列表。
- `idx_stars_time (stars, create_time)` —— 管理端按星级区间筛选。

**为什么维度列用 NULL 而不是 0**：管理端统计平均分用 `AVG(stars)`，但维度平均分如果把"不适用"填 0，
`AVG` 会被这些 0 拉低。例如卖家评买家只有两个维度，描述维度填 0 后平均值就永远偏低。填 NULL 才能被 `AVG` 正确忽略。

**为什么 `images` 空值存 `"[]"` 而不是 NULL**：SQL 判空统一写成 `images <> '[]'`，
若混用 NULL / `''` / `'[]'` 三种空值表达，判断条件就要写三遍，漏一处就统计错。
统一成一种写法后，判断条件只有一句 `images IS NOT NULL AND images <> '[]' AND images <> ''`。

### 1.2 `credit_log`（信誉分流水）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | 流水 ID |
| user_id | BIGINT | 被影响用户 |
| change_score | INT | 本次实际生效的变动（截断后可能小于名义值） |
| before_score / after_score | INT | 变动前后快照，账目可对账 |
| type | TINYINT | 1 评价 / 2 举报违规 / 3 管理员调整 / 4 评价被删除 |
| reason | VARCHAR(255) | 变动原因（对用户可见） |
| related_id | BIGINT NULL | 关联业务 ID（评价 ID 等） |
| operator_id | BIGINT NULL | 操作人，评价类为空 |
| create_time | DATETIME | 发生时间 |

**索引**：`idx_user_time (user_id, create_time)`、`idx_type_time (type, create_time)`。

**流水表只追加不修改**：实体上没有 `@TableUpdate`、Mapper 上没有 UPDATE/DELETE 方法。
信誉分是"可申诉"的业务数据，流水本身就是证据链；管理员删评价也只是再追加一条 type=4 的 0 变动流水，
而不是去改历史记录。

### 1.3 `trade_order` 新增字段

| 字段 | 类型 | 说明 |
|---|---|---|
| buyer_reviewed | TINYINT DEFAULT 0 | 买家是否已评价（方向 1） |
| seller_reviewed | TINYINT DEFAULT 0 | 卖家是否已评价（方向 2） |

这两个标记用于两处：

1. **待评价列表查询**：`status=3 AND ((buyer_id=? AND buyer_reviewed=0) OR (seller_id=? AND seller_reviewed=0))`，
   一次 SQL 拿到"我要评的订单"，不用在内存里过滤。
2. **重复评价的 CAS 防线**：更新时带 `WHERE buyer_reviewed = 0`，受影响行数为 0 说明并发请求已经抢先，
   整个事务回滚。

---

## 2. 业务规则

### 2.1 评价规则

| 规则 | 实现 |
|---|---|
| 只有已完成订单可评价 | `order.status != 3` 直接抛「只有已完成的订单才能评价」 |
| 双向独立评价 | 买评卖、卖评买各一条，`uk_order_type (order_id, review_type)` 保证互不冲突 |
| 评价后不可修改 | 无任何 UPDATE 评价正文的接口；追评只能追加，且不重新打分 |
| 追评一次 | `follow_content IS NULL` 条件更新，并发下只有一次能成功 |
| 追评窗口 7 天 | 按**订单 `finish_time`** 计算，不是按评价时间 |
| 匿名可选（默认实名） | `is_anonymous` 默认 0，前端开关默认关闭 |

**方向由订单角色决定，不信任前端传值**：前端传 `reviewType` 只用于核对，
真正的方向取自"当前用户在订单里是买家还是卖家"。否则恶意传 `reviewType=1`
就能让卖家给自己加分——这是本模块最重要的一个安全设计。

**追评窗口为什么按订单完成时间算**：如果按评价时间算，用户拖到第 6 天才评价，
追评窗口就变成"完成后的第 6~13 天"，与需求"交易完成后 7 天内"不符。
按 `finish_time` 算，窗口永远是"完成后的 7 天内"，与用户什么时候评价无关。

**追评不重新打分**：星级代表这次交易的整体感受，允许事后改分等于给了
"不满意就威胁改一星"的手段，对卖家不公平。所以追评只追加文字与图片。

### 2.2 评价维度

| 方向 | 维度 | 综合星级算法 |
|---|---|---|
| 1 买家评卖家 | 商品描述相符、发货速度、沟通态度 | 三者平均后四舍五入 |
| 2 卖家评买家 | 付款速度、沟通态度 | 二者平均后四舍五入 |

文字 ≤ 500 字，图片 ≤ 3 张（前后端都校验）。

### 2.3 信誉分算法

初始分 100，下限 0，上限 150。

| 综合星级 | 变动 | 说明 |
|---|---|---|
| 5 星 | +2 | 非常满意 |
| 4 星 | +1 | 满意 |
| 3 星 | 0 | 一般，不加分也不扣分 |
| 2 星 | −2 | 不满意 |
| 1 星 | −5 | 非常差 |
| 举报违规 | −10 | `CreditLogType.REPORT` |

**等级区间**

| 分数区间 | 等级 | 颜色 |
|---|---|---|
| 0 ~ 59 | 差 | 红 `#F56C6C` |
| 60 ~ 79 | 一般 | 橙 `#E6A23C` |
| 80 ~ 99 | 良好 | 蓝 `#409EFF` |
| 100 ~ 119 | 优秀 | 绿 `#67C23A` |
| 120 ~ 150 | 极佳 | 金 `#D4A017` |

**唯一加分入口 `CreditService.addScore()`**，所有加分（评价、举报、管理员调整）都必须走它：

```
1. SELECT credit_score FROM sys_user WHERE id=? FOR UPDATE   -- 拿行锁，读到的是真实起点
2. after  = clamp(before + delta)                            -- 截断到 0~150
3. actual = after - before                                   -- 反推实际生效的变动
4. 写 credit_log（before/after 快照 + actual）
5. UPDATE sys_user SET credit_score = after
6. userService.evictCache(userId)                            -- 用户信息有 30 分钟 Redis 缓存
```

三个关键点：

1. **`FOR UPDATE` 行锁**保证并发评价不会算错起点。不用锁的话两个并发评价都读到 100，
   各自 +2 后一个被覆盖，总分只加了 2 而非 4。
2. **`actual = after - before`** 反推实际变动。149 分再 +2 实际只到 150，流水记 +1 而不是 +2，
   账目必须平。触边界且 `delta != 0` 时仍然写一条 0 变动流水并注明原因，
   让人能从流水看出"这次加分没生效"；`delta == 0`（3 星评价）则完全不写流水，避免刷屏。
3. **`evictCache` 必做**。`sys_user.credit_score` 会随用户信息一起进 30 分钟 Redis 缓存，
   改分后不清缓存，个人中心会一直显示旧分。

### 2.4 匿名脱敏

脱敏在**服务端**完成（`service/support/ReviewAssembler`），不在前端判断：

- 用户端接口（商品评价、用户评价、订单评价）传 `maskAnonymous = true`，
  匿名评价的 `reviewerNickname` 返回「匿名用户」、`reviewerAvatar` 返回空；
- 管理端接口传 `maskAnonymous = false`，保留真实昵称——管理员判违规时必须能看到是谁写的；
- `isAnonymous` 标记始终返回，前端用它决定是否显示"匿名"角标。

服务端脱敏的原因：前端脱敏等于"把真实昵称发给客户端再藏起来"，抓包就能看到，
匿名就失去了意义。

---

## 3. 接口清单

### 3.1 评价（11 个）

| 方法 | 路径 | 说明 | 鉴权 |
|---|---|---|---|
| POST | `/api/review` | 提交评价（同时更新对方信誉分） | 需登录，订单参与方 |
| POST | `/api/review/follow` | 追评一次 | 需登录，评价人本人 |
| POST | `/api/review/reply` | 卖家回复买家评价 | 需登录，被评价的卖家 |
| GET | `/api/review/{id}` | 评价详情 | 免登录 |
| GET | `/api/review/product/{productId}` | 商品评价列表（只含方向 1） | 免登录 |
| GET | `/api/review/product/{productId}/stat` | 商品评价统计 | 免登录 |
| GET | `/api/review/user/{userId}` | 某用户收到的评价 | 免登录 |
| GET | `/api/review/order/{orderId}` | 订单评价详情（双方评价 + 能否评价/追评） | 需登录 |
| GET | `/api/review/my/pending` | 我待评价的订单 | 需登录 |
| GET | `/api/review/my/pending/count` | 我待评价的订单数（角标） | 需登录 |
| GET | `/api/review/admin/page` | 管理端评价列表 | 管理员 / 客服（只读） |
| DELETE | `/api/review/{id}` | 删除违规评价 | 仅管理员 |

**`POST /api/review` 请求体**

```json
{
  "orderId": 43,
  "reviewType": 1,
  "descStars": 5,
  "shipStars": 5,
  "attitudeStars": 4,
  "content": "车况很好，卖家很爽快",
  "images": ["/uploads/r1.png"],
  "isAnonymous": false
}
```

- `reviewType`：1 买家评卖家 / 2 卖家评买家，**只用于核对**，实际方向以后端判定的订单角色为准；
- 方向 1 必填 `descStars` / `shipStars` / `attitudeStars`，方向 2 必填 `payStars` / `attitudeStars`；
- `content` ≤ 500 字，`images` ≤ 3 张；
- 返回 `ReviewVO`，含被评价人最新信誉等级。

**`POST /api/review/follow` 请求体**：`{ "reviewId": 7, "content": "追评内容", "images": [] }`，内容与图片至少一项。

**`POST /api/review/reply` 请求体**：`{ "reviewId": 7, "content": "感谢支持" }`。
只能回复方向 1（买家评卖家）的评价，且只能由被评价的卖家回复一次。

**`GET /api/review/admin/page` 查询参数**

| 参数 | 说明 |
|---|---|
| `productKeyword` | 商品标题模糊 |
| `reviewerKeyword` | 评价人昵称模糊 |
| `revieweeKeyword` | 被评价人昵称模糊 |
| `orderNo` | 订单号模糊 |
| `reviewType` | 方向 |
| `minStars` / `maxStars` | 星级区间（好评 4-5 / 中评 3 / 差评 1-2） |
| `hasImage` / `hasFollow` / `hasReply` | 是否有图 / 追评 / 回复 |

昵称与标题的模糊搜索**先查小表拿 ID 集合再 `IN` 进评价表**，而不是在评价表上 JOIN 用户表再 LIKE：
JOIN 会让评价表的索引失效，而 `IN` 走的是 `idx_reviewee_time` / `idx_product_time`。
查不到 ID 时直接返回空页，不生成 `IN ()` 去全表扫描。

**商品评价只统计方向 1**：一笔订单会产出两行评价（买评卖 + 卖评买），两行都带同一个 `product_id`。
不过滤方向的话，商品页的平均分会把"卖家对买家的付款速度打分"也算进去，分数直接失真。
`GET /api/review/product/{id}` 与 `/stat` 都强制 `review_type = 1`。

### 3.2 信誉分（4 个）

| 方法 | 路径 | 说明 | 鉴权 |
|---|---|---|---|
| GET | `/api/credit/{userId}` | 信誉分详情（分数、等级、进度、最近评价） | 免登录 |
| GET | `/api/credit/{userId}/logs?type=` | 信誉分流水（`type=3` 只看管理员调整） | 需登录 |
| GET | `/api/credit/admin/users?keyword=` | 管理端用户信誉分列表 | 管理员 / 客服（只读） |
| PUT | `/api/credit/{userId}/adjust` | 管理员调整信誉分 | 仅管理员 |

**`PUT /api/credit/{userId}/adjust` 请求体**：`{ "changeScore": -10, "reason": "举报核实存在恶意砍价" }`

- `changeScore` 范围 −100 ~ 100，不能为 0；
- `reason` 必填，≤ 200 字，写入流水可追溯；
- 调整同样走 `addScore`，因此一样受 0~150 边界截断；
- 已经在边界值上时直接拒绝（返回「该用户信誉分已在边界值上，本次调整未生效」），
  而不是静默写一条 0 变动流水让管理员以为改过了。

### 3.3 订单详情扩展

`GET /api/order/{orderNo}` 的返回体新增：

| 字段 | 说明 |
|---|---|
| `buyerReviewed` / `sellerReviewed` | 两侧是否已评价 |
| `myReviewId` | 当前用户发出的那条评价 ID |
| `canReview` | 当前用户是否可评价 |
| `canFollowReview` | 当前用户是否可追评 |

订单详情页据此渲染三态按钮：可评价 → 「评价」；已评价且可追评 → 「追评」；否则 → 「查看评价」。

---

## 4. 数据一致性设计小结

| 风险 | 防线 |
|---|---|
| 并发重复评价 | 预检查提示 + `uk_order_type` 唯一索引 + 订单标记 CAS，三层 |
| 卖家给自己加分 | 方向由订单角色判定，不信任前端 `reviewType` |
| 并发加分算错起点 | `SELECT ... FOR UPDATE` 行锁 |
| 分数溢出边界 | `clamp` 截断 + `actual` 反推实际变动，流水与分数永远对得上 |
| 改分后前端仍显示旧分 | `userService.evictCache(userId)` |
| 恶意删除评价 | 仅管理员可删；逻辑删除保留原文供申诉；删除不回退分数但写 type=4 审计流水 |
| 匿名被前端绕过 | 服务端脱敏，用户端与管理端策略不同 |

---

## 5. 测试

| 文件 | 覆盖 | 用例数 |
|---|---|---|
| `tests/smoke_module7.py` | 权限、评价前置校验、双向评价与信誉分增减、追评、回复、匿名脱敏、列表筛选、管理端删除、管理员调分、DB 落库校验、清理 | 90 |
| `tests/mp_review_check.cjs` | 评价页维度与星级、追评页、评价列表筛选与分页、待评价列表、星级文案与信誉分增减映射 | 65 |
