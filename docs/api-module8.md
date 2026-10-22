# 模块 8 · 个人中心与卖家主页 接口说明

> 依赖脚本：`sql/V9__user_profile.sql`（`sys_user` 增 `gender` / `intro` 两列与 `idx_user_create_time` 索引）
> 基础路径：`http://localhost:8080`，统一返回 `Result<T>`（`code=200` 成功；**异常同样以 HTTP 200 返回，真实结果码在 body.code**）
> 权限：个人中心与资料设置全部需登录；卖家主页与其商品列表免登录可读（携带 token 时返回关注状态）；管理端用户列表管理员与客服可看，写操作仅管理员
> 相关常量：`common/UserRule`（昵称/简介长度与注销口令）、`common/Gender`（性别取值）、`common/ProductStatus`（商品状态）

---

## 1. 数据库设计

本模块**不新增任何业务表**，只给 `sys_user` 补两个字段。

### 1.1 `sys_user` 新增字段

| 字段 | 类型 | 说明 |
|---|---|---|
| gender | TINYINT NOT NULL DEFAULT 0 | 性别：0 保密 / 1 男 / 2 女 |
| intro | VARCHAR(200) NULL | 个人简介，最长 100 字 |
| idx_user_create_time | INDEX | 索引，建在 `create_time` 上 |

**为什么性别与简介不拆表**：这两个都是「一对一、低频读写、几乎不参与查询条件」的附加属性。
拆成 `user_profile` 表会让每次读个人中心都得多一次 JOIN，而写头像/昵称这类高频操作
本来就要更新 `sys_user`——放一起反而少一次写。

**为什么 `intro` 列宽 200 而限制 100 字**：`UserRule.INTRO_MAX_LENGTH = 100` 是产品约束，
`INTRO_DB_MAX_LENGTH = 200` 是存储余量。两者分开定义，是为了以后放宽限制时
不用改表结构。`@Size(max = 100)` 与服务层的二次校验都以 100 为准。

**为什么 gender 用 tinyint 而不是 varchar('男'/'女')**：用枚举数字存、读出来再翻译成中文。
数据库里存可读中文的话，统计"男女比例"要 LIKE，中文枚举一旦要改名就是全表 UPDATE。

### 1.2 已有字段的复用

个人中心的六个统计数全部来自已有列与已有表，**没有新增冗余计数**：

| 统计数 | 来源 |
|---|---|
| 发布数 / 在售数 / 已售数 | `product` 表按 `seller_id` + `status` 分组统计 |
| 收藏数 / 关注数 / 粉丝数 | `sys_user.favorite_count` / `follow_count` / `fans_count`（模块 2、5 已冗余维护） |
| 待评价数 | `review` + `trade_order` 联查（与订单列表用同一套判定） |
| 各状态订单数 | `trade_order` 按 `buyer_id` + `status` 分组统计 |

---

## 2. 业务规则

### 2.1 聚合优先

个人中心首屏要展示发布数、已售数、在售数、收藏数、关注数、粉丝数、待评价数、
六个状态订单数。如果前端串 6~8 个接口：

1. 弱网下首屏会分好几批出现；
2. 各接口返回的时刻不同，用户会看到「收藏数已更新但订单数还是旧的」这种割裂。

所以 `GET /api/user/profile` 一次返回全部。代价是多几个 `COUNT`（走索引，成本很低），
换来的是首屏一次到位。

### 2.2 免登录与需登录的边界

| 场景 | 鉴权 | 理由 |
|---|---|---|
| 个人中心 `GET /api/user/profile` | 需登录 | 这些数字是「我的」，未登录时连「我是谁」都不知道 |
| 卖家主页 `GET /api/user/home/{userId}` | **免登录**（软登录） | 商品详情页、商品卡片都要能直接跳过去，不能要求先登录 |
| 卖家商品 `GET /api/user/products/{userId}` | **免登录** | 同上 |
| 改资料 / 改手机号 / 注销 | 需登录 | 只影响「我自己」 |

**软登录**（`@PublicApi`）与硬登录的区别：软登录拿到有效 token 就写入上下文、返回关注状态，
token 无效也只是静默忽略，**绝不阻断请求**。所以 `followed` / `isSelf` 在未登录时是 `false`，而不是报错。

### 2.3 封禁用户的对外可见性

卖家主页是免登录接口，走的是软登录。`AuthInterceptor` 里那条「封禁账号禁止访问」的判断
管的是**当前请求者**，对**被查看者**不生效。

所以 `UserCenterServiceImpl` 额外提供 `requireVisibleUser()`：被封禁的用户，
主页与商品列表一律拒绝（`code=403`「该用户已被封禁，主页暂不可访问」）。

不这样处理的话，封禁一个用户后：商品确实被下架了，但主页还亮着，
名字、信誉分、粉丝数、「已认证学生」标签全都还在——前后端状态对不上。

### 2.4 封禁与角色变更的联动

| 管理端操作 | 联动效果 |
|---|---|
| 封禁用户 | 同步下架其全部在架商品（PENDING / ON_SALE）+ 站内通知 + 清用户缓存 |
| 解封用户 | 清用户缓存，恢复访问 |
| 角色改为 `student` | `student_verified=1`、`publish_limit=20` |
| 角色改为其它 | `student_verified=0`、`publish_limit=0` |

**为什么降级普通用户要回收发布额度**：不回收的话，一个认证学生被降级后仍残留 20 件发布额度，
等于绕过「仅认证学生可发布」这条产品规则——`@RequireRole(STUDENT)` 只看角色，
不看额度，两条路都能发。

**为什么只下架 PENDING / ON_SALE**：`offlineAllBySeller` 只处理这两个状态，
已售出（3）与违规下架（4）保持不变。已售出的商品对应着已完成订单，
改了状态会让订单失去追溯对象；违规下架是违规证据，不能被一次封禁洗掉。

**为什么通知失败不阻断封禁**：`notifyQuietly()` 只记 warn 日志。
封禁是安全动作，通知是锦上添花；因为通知服务抖动就回滚封禁，
等于让一个恶意用户在通知故障期间畅通无阻。

### 2.5 角色变更的权限约束

- 管理员**不能封禁自己**，也不能**修改自己的角色**——否则可以把最后一个管理员降成普通用户；
- 客服与管理员账号**不能自助注销**（`DELETE /api/user/account` 返回 403）：
  他们的账号挂着管理权限，必须由另一位管理员先把角色降下来；
- 客服账号在管理端是**只读**的：拦截器对客服放行一切 GET，拦截一切非 GET。

### 2.6 手机号不可前端直改

`PUT /api/user/phone` 收的是微信 `getPhoneNumber` 的授权码，不是手机号字符串。
服务端用 `WechatApiClient.getPhoneNumber(code)` 换取真实号码，再校验：

1. 与当前手机号不同（相同直接报错，避免无意义写入）；
2. 与其它账号不重复（唯一性校验）。

如果前端直接传手机号字符串，任何人都能把账号手机号改成自己的——
之后「用手机号找回账号」就成了把账号送人的漏洞。

### 2.7 注销账号的三重确认

| 检查 | 不通过时的提示 |
|---|---|
| 角色不是 admin / customer_service | 客服与管理员账号不能自助注销，请联系管理员移除角色后再注销 |
| `confirmText` 等于「注销账号」 | 请输入「注销账号」以确认注销 |
| 已设置密码且密码正确 | 当前账号尚未设置密码，请先在「账号与安全」中设置密码后再注销 |

全部通过后按顺序执行：

1. `offlineAllBySeller(userId)` —— 下架全部在架商品（账号没了，商品还挂在平台上被点是直接的资损）；
2. `removeById(userId)` —— 软删除（`@TableLogic` 只置 `deleted=1`，历史订单与评价的 `user_id` 仍指向这条记录，账目不丢）；
3. `redis.delete(LOGIN_TOKEN + userId)` —— 强制下线，否则用户手上的 token 在过期前仍能通过鉴权；
4. `evictCache(userId)` —— 清 30 分钟的用户信息缓存。

### 2.8 值的长度约束

| 项 | 限制 | 常量 |
|---|---|---|
| 昵称 | ≤ 20 字 | `UserRule.NICKNAME_MAX_LENGTH` |
| 个人简介 | ≤ 100 字 | `UserRule.INTRO_MAX_LENGTH` |
| 头像 URL | ≤ 500 字 | `UserRule.AVATAR_MAX_LENGTH` |
| 注销确认口令 | 固定为「注销账号」 | `UserRule.ACCOUNT_DELETE_CONFIRM` |

这些数字集中在一个类里，是为了让「前后端各写一份」这种漂移无处可藏：
前端 `maxlength` 直接引用同一套数值，后端 `@Size` 也用同一套数值。

---

## 3. 接口清单

### 3.1 个人中心与卖家主页（7 个）

| 方法 | 路径 | 说明 | 鉴权 |
|---|---|---|---|
| GET | `/api/user/profile` | 个人中心聚合数据 | 需登录 |
| GET | `/api/user/home/{userId}` | 卖家主页数据 | 免登录（软登录） |
| GET | `/api/user/products/{userId}` | 卖家在售商品列表（分页） | 免登录 |
| PUT | `/api/user/profile` | 修改个人资料 | 需登录 |
| PUT | `/api/user/phone` | 修改手机号（微信授权验证） | 需登录 |
| POST | `/api/user/logout` | 退出登录 | 需登录 |
| DELETE | `/api/user/account` | 注销账号（软删除，商品全部下架） | 需登录 |

**`GET /api/user/profile` 响应**（`UserProfileVO`）

```json
{
  "code": 200,
  "data": {
    "userId": 5,
    "nickname": "张三",
    "avatar": "/uploads/a.png",
    "gender": 1,
    "genderLabel": "男",
    "intro": "大三在读",
    "phone": "13800001234",
    "username": "zhangsan",
    "hasAccount": true,
    "role": "student",
    "roleLabel": "认证学生",
    "studentVerified": 1,
    "studentNo": "20230101",
    "creditScore": 120,
    "creditLevelLabel": "优秀",
    "creditLevelColor": "#67C23A",
    "creditProgress": 60,
    "publishCount": 3,
    "soldCount": 2,
    "onSaleCount": 1,
    "favoriteCount": 5,
    "followCount": 4,
    "fansCount": 6,
    "pendingReviewCount": 2,
    "orderCount": {
      "pendingPay": 1, "pendingShip": 2, "pendingReceive": 3, "finished": 4,
      "canceled": 0, "refunding": 1, "refunded": 0, "afterSale": 1, "total": 12
    },
    "createTime": "2026-01-01 10:00:00",
    "lastLoginTime": "2026-10-21 10:00:00"
  }
}
```

`studentNo` 只在**认证已通过**时有值。申请了但还在审核中不返回——
学号属于个人敏感信息，没通过审核就不该出现在任何界面。

**`GET /api/user/home/{userId}` 响应**（`UserHomeVO`）

```json
{
  "code": 200,
  "data": {
    "userId": 88,
    "nickname": "李四",
    "avatar": "/uploads/s.png",
    "genderLabel": "女",
    "intro": "只卖闲置",
    "roleLabel": "认证学生",
    "studentVerified": true,
    "creditScore": 95,
    "creditLevelLabel": "良好",
    "creditLevelColor": "#E6A23C",
    "onSaleCount": 2,
    "soldCount": 7,
    "reviewCount": 5,
    "goodRate": 80.0,
    "avgStars": 4.6,
    "fansCount": 12,
    "createTime": "2026-02-01 10:00:00",
    "followed": false,
    "isSelf": false,
    "recentReviews": []
  }
}
```

与个人中心的区别：卖家主页是「别人的」，只能看 + 关注 + 私信，
所以不含订单数、收藏数这类私有数据；但要展示「好评率」——
这是促成交易的信任指标，所以必须给出评价总数与好评率，而不只是信誉分。

`goodRate` / `avgStars` / `recentReviews` 直接复用 `CreditService.detail(userId)` 的同一份数据，
免得「主页好评率」和「信誉分页好评率」两处算法不一致。
`recentReviews` 已在服务层脱敏（匿名评价的昵称是「匿名用户」），前端直接展示即可。

**`GET /api/user/products/{userId}` 查询参数**

| 参数 | 说明 |
|---|---|
| pageNum | 页码，默认 1 |
| pageSize | 每页条数，默认 10，最大 100 |

只返回**已上架**（status=1）商品。待审核 / 已下架 / 已售出都属于卖家自己的内部状态，
不该出现在给别人看的页面上。

**`PUT /api/user/profile` 请求体**

```json
{ "nickname": "张三", "avatar": "/uploads/a.png", "gender": 1, "intro": "大三在读" }
```

全部字段「**非空即更新**」：只有传了的字段才会被改，传 null 视为不改。
传空字符串等于「清空该字段」（昵称除外，空昵称会被拒）。
一个字段都没传时返回 `code=400`「没有需要修改的内容」。

**`PUT /api/user/phone` 请求体**：`{ "code": "微信 getPhoneNumber 回调返回的授权码" }`

**`DELETE /api/user/account` 请求体**：`{ "password": "123456", "confirmText": "注销账号" }`

### 3.2 管理端用户管理（6 个）

| 方法 | 路径 | 说明 | 鉴权 |
|---|---|---|---|
| GET | `/api/admin/user/page` | 用户列表（分页 + 多条件筛选） | 管理员 / 客服（只读） |
| GET | `/api/admin/user/{userId}` | 用户详情 | 管理员 / 客服（只读） |
| PUT | `/api/admin/user/{userId}` | 编辑用户资料 | 仅管理员 |
| PUT | `/api/admin/user/{userId}/status` | 封禁 / 解封 | 仅管理员 |
| PUT | `/api/admin/user/{userId}/role` | 分配角色 | 仅管理员 |
| PUT | `/api/admin/user/{userId}/student-auth` | 学生认证通过 / 驳回 | 仅管理员 |

**`GET /api/admin/user/page` 查询参数**

| 参数 | 说明 |
|---|---|
| pageNum / pageSize | 分页 |
| keyword | 昵称 / 账号 / 手机号 / 用户ID / 学号 模糊 |
| role | 角色：`user` / `student` / `customer_service` / `admin` |
| status | 0 正常 / 1 封禁 |
| studentVerified | 0 未认证 / 1 已认证 |
| minCredit / maxCredit | 信誉分区间 |
| startTime / endTime | 注册时间区间 |

`keyword` 的实现是**子查询**而不是 JOIN：

```sql
OR EXISTS (SELECT 1 FROM student_auth sk
           WHERE sk.user_id = u.id AND sk.status = 1 AND sk.deleted = 0
             AND sk.student_no LIKE CONCAT('%', #{q.keyword}, '%'))
```

学号在 `student_auth` 表里，如果用 JOIN，一个有 3 条认证记录的用户会把主表放大 3 倍，
分页的 `total` 就会虚高、同一页里也会出现重复用户。`EXISTS` 子查询只做存在性判断，
不改变行数。

`studentNo` 子查询取**最近一条 status=1** 的认证记录（`ORDER BY id DESC LIMIT 1`），
保证一个用户只显示一个学号。

**`GET /api/admin/user/{userId}` 响应**（`AdminUserDetailVO`）

包含四块内容：

- `user`：基本信息（`AdminUserVO`）——昵称、账号、手机号、角色、认证状态、信誉分与等级色、发布数、注册时间；
- `creditLogs`：信誉分变动记录（最近 20 条）；
- `products`：该用户发布的商品；
- `orders`：该用户作为买家或卖家的订单；
- `reviews`：该用户收到的评价（**管理端不脱敏**，管理员处理举报时需要看到实名）。

**`PUT /api/admin/user/{userId}` 请求体**：`{ "nickname": "...", "avatar": "...", "gender": 1, "intro": "..." }`
与用户端改资料同一个 DTO，同样是「非空即更新」。

**`PUT /api/admin/user/{userId}/status` 请求体**：`{ "status": 1, "reason": "发布违禁品" }`
`status`：0 正常 / 1 封禁。设置成当前相同状态会报错（「该账号未被封禁」/「该账号已被封禁」）。

**`PUT /api/admin/user/{userId}/role` 请求体**：`{ "role": "customer_service" }`

**`PUT /api/admin/user/{userId}/student-auth` 请求体**：`{ "status": 1, "reason": "驳回原因" }`
`status`：1 通过 / 2 驳回。内部复用 `StudentAuthService.audit()`，
保证认证状态、信誉分、发布额度的联动逻辑只有一份实现。

---

## 4. 数据一致性设计小结

| 风险点 | 处理 |
|---|---|
| 封禁后商品还在架 | `offlineAllBySeller` 统一下架 PENDING / ON_SALE |
| 封禁后主页还能看 | `requireVisibleUser` 拒绝被封禁用户的对外主页 |
| 降级后仍能发布 | 降级时同步 `student_verified=0` / `publish_limit=0` |
| 注销后 token 仍可用 | 删除 Redis 中的 token 白名单 |
| 注销后商品还在架 | 注销流程第一步就是下架商品 |
| 改资料后个人中心显示旧值 | 改完 `evictCache(userId)`（用户信息有 30 分钟缓存） |
| 角色变更后鉴权还用旧角色 | 角色变更后同样 `evictCache`（拦截器按缓存鉴权） |
| 通知服务抖动导致封禁回滚 | `notifyQuietly` 只记日志不回滚 |
| 管理端最后一个管理员把自己降级 | 不能修改自己的角色 |
| 用户列表 total 虚高 | 学号与发布数用子查询而非 JOIN |
| 匿名评价泄露实名 | 脱敏在服务层完成，前端拿到的就是「匿名用户」 |

---

## 5. 测试

### 5.1 接口冒烟

```bash
python tests/smoke_module8.py
```

覆盖 89 个用例：权限（未登录 401 / 客服只读 403 / 管理员与客服能力边界）->
个人中心聚合字段完整性与统计数一致性 -> 卖家主页免登录与软登录 ->
只展示已上架商品 -> 资料修改（长度 / 性别 / 空更新）->
手机号（授权码换取、相同拒绝、唯一性）-> 管理端筛选与详情 ->
封禁联动下架 -> 角色变更联动额度 -> 认证审核 -> 注销三重确认 -> 退出登录 -> 数据清理。

### 5.2 小程序逻辑离线校验

```bash
node tests/mp_profile_check.cjs
```

覆盖 79 个用例：个人中心（一次聚合、角标口径、统计行分流、宫格路由、未登录态）、
编辑资料（回填、长度实时提示、校验不通过不发请求）、
卖家主页（左右两列瀑布流、评价 Tab、关注切换、私信建会话、本人不能关注自己）、
设置页（缓存大小、清除缓存保留登录态、退出登录、注销三重确认）、
账号与安全（手机号脱敏、认证状态、改绑手机号、密码校验）、
我的发布（状态 Tab 跳转、已售出不可删、已下架可重新上架）。

### 5.3 演示数据与截图

```bash
python tests/seed_module8_demo.py          # 播种（普通/认证学生/客服/管理员/已封禁各若干）
python tests/seed_module8_demo.py --clean  # 清理
python tests/shot_admin.py --out docs/screenshots/module8 --token <JWT> \
    --user-info '{"role":"admin","roleLabel":"管理员","username":"admin"}' \
    --url http://127.0.0.1:5173 --shots "user-list=user:3500"
```

截图见 `docs/screenshots/module8/`：用户列表、认证学生标签、已封禁标签、用户详情弹窗、编辑资料弹窗。
