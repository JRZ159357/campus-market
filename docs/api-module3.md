# 模块 3 · 首页与商品浏览 接口说明

> 依赖脚本：`sql/V3__banner_home.sql`（banner 表 + product 两个查询索引）
> 基础路径：`http://localhost:8080`，统一返回 `Result<T>`（`code=200` 成功）
> 本模块接口均为免登录（`@PublicApi`，携带 token 时为"软登录"），管理端接口需 `admin` 角色

---

## 1. 首页（免登录）

### 1.1 首页聚合

`GET /api/home/index`

一次返回轮播图、分类、猜你喜欢、最新发布与热门搜索词，减少首屏请求数。

| 字段 | 说明 |
|---|---|
| `banners` | 启用且处于有效期内的轮播图，按 `sort` 升序 |
| `categories` | 启用的分类，按 `sort` 升序 |
| `guessYouLike` | 已上架商品，认证学生优先，最多 10 条 |
| `latest` | 已上架商品，按 id 倒序（等价发布时间倒序），取 4 条 |
| `hotKeywords` | 热门搜索词（当前为运营预置 10 个） |

响应示例（截断）：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "banners": [
      { "id": 1, "title": "开学季教材专场", "imageUrl": "/uploads/2026/10/a.png",
        "linkType": 2, "linkTypeLabel": "分类页", "linkValue": "2",
        "sort": 1, "status": 1, "effective": true }
    ],
    "categories": [{ "id": 1, "name": "数码电子", "icon": "📱", "sort": 1, "status": 1 }],
    "guessYouLike": [
      { "id": 46, "title": "九成新自行车", "price": 260.00, "originalPrice": 899.00,
        "cover": "/uploads/2026/10/b.png", "isVerifiedSeller": 1,
        "sellerNickname": "小林", "viewCount": 12 }
    ],
    "latest": [],
    "hotKeywords": ["自行车", "教材", "考研资料"]
  }
}
```

### 1.2 轮播图列表

`GET /api/home/banner/list` — 只返回「启用 + 在时间窗口内」的轮播图，按 `sort` 升序。

### 1.3 热门搜索词

`GET /api/home/hot-keywords` — 返回 `string[]`。搜索页单独使用，避免为 10 个词拉整个首页数据。

---

## 2. 商品浏览（免登录）

### 2.1 商品列表（offset 分页）

`GET /api/product/page`

| 参数 | 类型 | 说明 |
|---|---|---|
| `pageNum` / `pageSize` | int | 默认 1 / 10，pageSize 上限 100 |
| `categoryId` | long | 分类筛选 |
| `keyword` | string | 标题或描述模糊匹配 |
| `minPrice` / `maxPrice` | decimal | 价格区间，min > max 时报 400 |
| `sort` | string | `default` 综合（认证学生优先）/ `new` 最新 / `priceAsc` / `priceDesc` / `hot` |

### 2.2 关键词搜索

`GET /api/product/search`

参数与列表一致。搜索与列表共用同一套过滤与排序实现，避免两处规则漂移；`keyword` 为空时退化为普通列表。

### 2.3 热门商品

`GET /api/product/hot`

- 排序权重：`view_count + favorite_count * 3`（收藏的购买意愿更强，权重取 3）
- 取前 10 条，Redis 缓存 5 分钟；商品审核通过 / 下架 / 强制下架 / 删除时主动失效缓存
- 返回 `ProductListVO[]`

### 2.4 游标分页（瀑布流）

`GET /api/product/feed?cursor=&size=&categoryId=`

| 参数 | 说明 |
|---|---|
| `cursor` | 上一页最后一条的商品 id，首页不传 |
| `size` | 每页条数，默认 10，上限 50 |
| `categoryId` | 可选 |

响应：

```json
{ "code": 200, "data": { "records": [ ... ], "nextCursor": 44, "hasMore": true } }
```

`hasMore=false` 时 `nextCursor` 为 `null`。

### 2.5 浏览量计数规则

- 访客访问详情：Redis `product:view:delta` 执行 `HINCRBY`，不写 MySQL
- 卖家查看自己的商品：不计数
- 详情返回 `viewCount = 库中值 + 未回写增量`，卖家与访客看到同一个数字
- 定时任务（默认 60 秒，`app.view-count.flush-interval-ms`）批量回写：先累加 MySQL，再从 Redis 减去已回写量

---

## 3. 轮播图管理（admin）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/banner/page` | 分页，支持 `status`、`keyword`（标题） |
| POST | `/api/banner` | 新增 |
| PUT | `/api/banner/{id}` | 编辑 |
| DELETE | `/api/banner/{id}` | 删除（逻辑删除） |
| PUT | `/api/banner/{id}/status?status=0\|1` | 启用/禁用 |

请求体字段：

| 字段 | 必填 | 校验 |
|---|---|---|
| `title` | 是 | ≤ 80 字 |
| `imageUrl` | 是 | 上传接口返回的相对地址或 http(s) 地址 |
| `linkType` | 是 | 1 商品详情 / 2 分类页 / 3 外部链接 |
| `linkValue` | 视类型 | 1、2 必须为正整数 ID；3 必须以 `http://` 或 `https://` 开头 |
| `sort` | 否 | 不传自动排到末尾 |
| `status` | 否 | 0 禁用 / 1 启用，默认 1 |
| `startTime` / `endTime` | 否 | `yyyy-MM-dd HH:mm:ss`，end 必须晚于 start |

返回的 `BannerVO` 额外带 `linkTypeLabel` 与 `effective`（当前是否在展示期），管理端表格据此显示"当前不展示"。

---

## 4. 设计说明（可写入论文）

### 4.1 首页聚合减少请求数

首屏原本需要轮播图、分类、推荐、最新发布 4 个请求；现在合成 1 个聚合接口，弱网下不会因为多个请求串行而逐块白屏。聚合接口内部复用各模块 Service，不重写状态判断，保证"只取已上架""只取启用分类"等规则只有一处实现。

### 4.2 游标分页解决深分页

瀑布流会连续翻十几页，`LIMIT 100000, 10` 需要扫描并丢弃前 10 万行。改用 `id < cursor ORDER BY id DESC LIMIT n+1`：

- 走 `idx_status_id(status, id)` 索引直接定位，代价与页深无关
- 多取一条判断 `hasMore`，省掉一次 `COUNT(*)`
- 首页复用聚合接口的 `latest`（同样按 id 倒序）作为第一页，因此首屏只发 1 个请求，翻页也不会重复

### 4.3 浏览量写 Redis + 定时回写

详情页是高频写热点，每访问一次就 `UPDATE product` 会让同一行产生行锁竞争与大量 binlog。改为 Redis Hash 计数 + 定时批量回写：

- 读：`库中值 + 待回写增量`，保证展示实时
- 写：`HINCRBY`，无锁、无事务
- 回写：先累加 MySQL 再 `HINCRBY -delta`，期间新到的访问会留在 Hash 等下一轮，不会丢计数
- Redis 不可用时降级为直接写库，功能不中断

### 4.4 热门商品缓存

首页、分类页都会请求热门列表，直接打库会重复扫描大表。结果缓存 5 分钟；商品状态变化（审核通过/下架/删除）时主动删除缓存，避免首页继续展示已下架商品。

### 4.5 自定义 tabBar 的原因

需求要求 tabBar 含"发布"，但发布页有两个特殊之处：

1. 未认证用户点击要先弹窗引导认证，不能直接进页面；
2. 编辑商品需要 `navigateTo('/pages/publish/index?id=xx')`，而原生 tabBar 页面不允许 `navigateTo`。

因此使用 `custom-tab-bar`：4 个真实 tab 走 `switchTab`，"发布"是组件内的按钮，由 JS 决定是弹窗、跳认证还是打开发布页。

---

## 5. 测试记录

`tests/smoke_module3.py`：**54/54 通过**（后端启动时设置 `VIEW_FLUSH_INTERVAL_MS=2000` 便于验证回写）。

覆盖：首页聚合字段、分类启用过滤、热词、未登录 401 与普通用户 403、关键词/价格/分类筛选、价格升降序、游标分页不重不漏、热门加权与缓存命中、浏览量三次递增、卖家不计数、Redis 增量回写 MySQL、轮播图增删改查、五类参数校验、sort 排序、定时上下架、禁用/启用即时生效、商品下架后热门缓存失效。

回归：模块 1 `22/22`、模块 2 `81/81` 全部通过；规范自检 `通过 81 项 / 警告 1 项 / 失败 0 项`（警告为 FileController 的 IOException 捕获，属合理用法）。
