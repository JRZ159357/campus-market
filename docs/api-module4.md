# 模块 4 · 收藏与关注 接口说明

> 依赖脚本：`sql/V5__favorite_follow.sql`（新建 `favorite` / `follow` 表，`sys_user` 增三个冗余计数）
> 基础路径：`http://localhost:8080`，统一返回 `Result<T>`（`code=200` 成功）
> 权限：所有接口均需登录；客服（customer_service）只能 GET，写操作统一返回 `403 客服账号无操作权限，请联系管理员`

---

## 1. 接口清单

### 1.1 收藏

| 方法 | 路径 | 说明 | 鉴权 |
|---|---|---|---|
| POST | `/api/favorite/{productId}` | 收藏 / 取消收藏（同一接口切换） | 需登录 |
| GET | `/api/favorite/my` | 我的收藏（游标分页，带商品信息） | 需登录 |
| GET | `/api/favorite/check/{productId}` | 是否已收藏该商品 | 需登录 |

### 1.2 关注

| 方法 | 路径 | 说明 | 鉴权 |
|---|---|---|---|
| POST | `/api/follow/{userId}` | 关注 / 取消关注（同一接口切换） | 需登录 |
| GET | `/api/follow/following` | 我关注的人（分页，含在售商品数） | 需登录 |
| GET | `/api/follow/followers` | 关注我的人（粉丝列表，含是否回关） | 需登录 |
| GET | `/api/follow/check/{userId}` | 是否已关注该用户 | 需登录 |

---

## 2. 收藏

### 2.1 收藏 / 取消收藏

`POST /api/favorite/{productId}`

无请求体。已收藏则删除收藏记录并减计数，未收藏则新增记录并加计数。

```json
{
  "code": 200,
  "msg": "收藏成功",
  "data": { "favorited": true, "favoriteCount": 12 }
}
```

| 场景 | code | msg |
|---|---|---|
| 未登录 | 401 | 未登录，请先登录 |
| 客服账号调用 | 403 | 客服账号无操作权限，请联系管理员 |
| 商品不存在 / 已删除 | 500 | 商品不存在或已被删除 |

**联动计数**（同一事务内）：

- `product.favorite_count` ±1；
- `sys_user.favorite_count`（我的收藏数）±1；
- Redis 集合 `favorite:ids:{userId}` SADD / SREM。

并发重复点击由唯一索引 `uk_user_product` 兜底：`DuplicateKeyException` 被捕获并视为「已收藏」，不会重复加计数。

### 2.2 我的收藏

`GET /api/favorite/my?cursor=&size=10`

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `cursor` | long | 否 | 上一页最后一条**收藏记录ID**（响应里的 `nextCursor`），首页不传 |
| `size` | int | 否 | 每页条数，默认 10，最大 50 |

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "records": [
      {
        "favoriteId": 98,
        "id": 41,
        "sellerId": 12,
        "sellerNickname": "李同学",
        "sellerAvatar": "/uploads/avatar.png",
        "categoryId": 2,
        "title": "高等数学 第七版",
        "price": 25.00,
        "originalPrice": 59.80,
        "cover": "/uploads/2026/10/x.png",
        "status": 1,
        "statusLabel": "已上架",
        "viewCount": 36,
        "favoriteCount": 4,
        "isVerifiedSeller": 1,
        "favoriteTime": "2026-10-21 14:32:10"
      }
    ],
    "nextCursor": 98,
    "hasMore": true
  }
}
```

说明：

- 排序为**收藏时间倒序**（`favorite.id DESC`），`id` 自增等价于收藏先后；
- 游标分页：SQL 为 `id < cursor`，走 `idx_user_id_id`，不产生 `OFFSET` 深分页；
- 商品被删除时不返回该条（避免空卡片）；已售出 / 已下架仍返回，由前端加灰色蒙层提示；
- 列表字段与商品列表 VO 对齐，小程序可直接复用 `product-card` 组件。

### 2.3 检查收藏状态

`GET /api/favorite/check/{productId}` → `{"code":200,"data":true}`

实现：`SISMEMBER favorite:ids:{userId} {productId}`，O(1)。集合不存在时一次性回源 `SELECT product_id FROM favorite WHERE user_id=?` 并回填（含哨兵 `0`，用于区分「已加载但为空」与「未加载」），TTL 7 天；Redis 异常时降级为一次 SQL 查询。

---

## 3. 关注

### 3.1 关注 / 取消关注

`POST /api/follow/{userId}`

```json
{
  "code": 200,
  "msg": "关注成功",
  "data": { "followed": true, "fansCount": 8 }
}
```

| 场景 | code | msg |
|---|---|---|
| 关注自己 | 400 | 不能关注自己 |
| 用户不存在 | 500 | 用户不存在或已注销 |
| 目标账号被封禁 | 500 | 该用户已被封禁，无法关注 |
| 客服账号调用 | 403 | 客服账号无操作权限，请联系管理员 |

联动：`follower.follow_count` ±1、`following.fans_count` ±1，与关系记录同事务。

### 3.2 我关注的人

`GET /api/follow/following?pageNum=1&pageSize=10`

```json
{
  "code": 200,
  "data": {
    "records": [
      { "userId": 12, "nickname": "李同学", "avatar": "/uploads/a.png",
        "studentVerified": true, "creditScore": 100, "onSaleCount": 5,
        "fansCount": 8, "followed": true, "followTime": "2026-10-21 14:40:02" }
    ],
    "total": 1, "current": 1, "size": 10, "pages": 1
  }
}
```

### 3.3 关注我的人（粉丝）

`GET /api/follow/followers?pageNum=1&pageSize=10`

字段与 3.2 相同，`followed` 表示**当前登录用户是否已关注该粉丝**（用于「回关 / 已关注」按钮切换）；`followTime` 为该粉丝关注我的时间。

### 3.4 检查关注状态

`GET /api/follow/check/{userId}` → `{"code":200,"data":true}`；自己对自己恒返回 `false`。

---

## 4. 商品详情的联动字段

`GET /api/product/{id}`（免登录，携带 token 时按登录用户计算状态）

新增字段：

| 字段 | 位置 | 说明 |
|---|---|---|
| `favorited` | 详情根节点 | 当前访问者是否已收藏；未登录 / 访客恒 `false` |
| `favoriteCount` | 详情根节点 | 商品收藏数（已有字段，收藏后实时变化） |
| `seller.followed` | 卖家卡片 | 当前访问者是否已关注该卖家；卖家本人 / 未登录恒 `false` |
| `seller.fansCount` | 卖家卡片 | 卖家粉丝数（读 `sys_user.fans_count`） |

---

## 5. 数据结构与索引

### 5.1 favorite

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | bigint | 主键，自增顺序即收藏顺序（游标依据） |
| `user_id` | bigint | 收藏用户 |
| `product_id` | bigint | 被收藏商品 |
| `create_time` | datetime | 收藏时间 |

索引：`uk_user_product(user_id, product_id)` 唯一、`idx_user_id_id(user_id, id)` 游标分页、`idx_product_id(product_id)` 反查。

### 5.2 follow

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | bigint | 主键，自增顺序即关注顺序 |
| `follower_id` | bigint | 关注者 |
| `following_id` | bigint | 被关注者（卖家） |
| `create_time` | datetime | 关注时间 |

索引：`uk_follower_following(follower_id, following_id)` 唯一、`idx_follower_id(follower_id, id)`、`idx_following_id(following_id, id)`；`CHECK (follower_id <> following_id)` 数据库层兜底禁止自关注。

### 5.3 sys_user 新增冗余计数

| 字段 | 说明 |
|---|---|
| `favorite_count` | 我收藏的商品数 |
| `follow_count` | 我关注的人数 |
| `fans_count` | 粉丝数 |

三者均为读多写少的冗余字段，随收藏/关注切换同事务增减，个人中心与详情页直接读取，不做 `COUNT(*)`；更新语句使用 `GREATEST(x + delta, 0)` 兜底，避免并发下计数为负。

---

## 6. 性能设计摘要（论文可写点）

| 问题 | 方案 |
|---|---|
| 详情页每次打开都要判断收藏态 | Redis Set 存用户收藏的商品ID，`SISMEMBER` O(1)；未命中一次性回源并回填，哨兵 `0` 区分空集合 |
| 收藏数 / 关注数 / 粉丝数需要实时展示 | 冗余计数 + 同事务增减，列表与个人中心零 `COUNT` |
| 收藏列表深分页 | 游标分页 `id < cursor ORDER BY id DESC`，走 `(user_id, id)` 组合索引，无 `OFFSET` |
| 关注列表 N+1 查询 | 一次分页查关系 + 一次批量查用户 + 一次分组查在售数，一页固定 3 次 SQL |
| 唯一性 / 并发重复点击 | 联合唯一索引 + `DuplicateKeyException` 捕获，重复点击不重复计数 |
