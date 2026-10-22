# 模块 12 · 首页推荐位配置接口说明

> 对应版本：V12__home_config.sql
> 代码位置：`backend/src/main/java/com/campus/market/{controller,service,service/impl,mapper,dto,vo,common}`
> 管理端：`admin-web/src/views/home/index.vue`（5 个 Tab 合并在一个页面）
> 小程序：`miniprogram/pages/index/*`

## 1. 模块定位

前十一个模块里，首页是**硬编码**的：轮播图能改（模块 3 建了 banner 表），公告、金刚区、
热门推荐、新品推荐全是写死的逻辑。运营想让「考研季」把教材类顶上来，只能改代码重新部署。

模块 12 把首页拆成 5 个可配置区块，全部收敛到**一个**聚合接口：

| 区块 | 数据来源 | 可配置项 |
|---|---|---|
| 轮播图 | `banner`（复用，补 `position`） | 图片、标题、跳转类型与目标、排序、启停、生效时间 |
| 公告栏 | `home_config.notice_*` | 文案、链接、是否显示 |
| 金刚区 | `category`（复用，补 `is_home_show`） | 显示哪些分类、顺序 |
| 热门推荐 | `home_config.hot_*` | 手动选品（最多 20 件）或按热度自动（1-20 件） |
| 新品推荐 | `home_config.new_count` | 取最新 N 件（1-20） |

设计上有一句话贯穿全程：

> **配置决定「推什么」，不决定「什么能上首页」。**

运营可以决定推哪件商品、推几件、排第几；但「只有已上架商品能出现在首页」
这条底线写在代码里，不开放给配置。否则迟早有人把一件待审核商品推到首页头条。

## 2. 接口清单

### 2.1 轮播图 `/api/banner`（`/api/home/banner` 为等价别名）

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/page` | admin / customer_service | 分页，支持状态、关键词筛选 |
| POST | `/` | admin | 新增（自动置 `position=1`、未填排序时取 max+1） |
| PUT | `/{id}` | admin | 编辑 |
| DELETE | `/{id}` | admin | 删除（逻辑删除） |
| PUT | `/{id}/status` | admin | 启用 / 禁用（`?status=0\|1`） |
| PUT | `/sort` | admin | 批量排序，拖拽后一次保存 |
| GET | `/api/home/banner/list` | 免登录 | 小程序用：启用中且在展示期内，按 sort 升序 |

> **一个 `@RequestMapping` 挂两个前缀**。`/api/banner` 是模块 3 定的老路径，
> 已经出现在交付文档、小程序说明和模块 3 的回归测试里。为了「目录整齐」全部改掉，
> 收益只是好看，风险是某个没被搜到的调用方悄悄 404。两套都保留，成本为零。

### 2.2 首页配置 `/api/home`

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/config` | admin / customer_service | 读取 7 项配置（KV 已铺平为结构化对象） |
| PUT | `/config` | admin | 保存 7 项配置，整份提交 |
| GET | `/category/sort` | admin / customer_service | 金刚区分类列表（含 `isHomeShow`、`sort`、在售数） |
| PUT | `/category/sort` | admin | 保存金刚区：哪些显示 + 顺序 |
| GET | `/product/picked?ids=1,2,3` | admin / customer_service | 按 ID 预览手动选品（仅已上架，保持传入顺序） |

### 2.3 小程序读接口

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/home` | 免登录 | **首页聚合**：公告 + 轮播图 + 金刚区 + 热门推荐 + 新品推荐 + 热搜词 |
| GET | `/api/home/index` | 免登录 | 模块 3 留下的别名，内容完全一致 |
| GET | `/api/home/banner/list` | 免登录 | 单独取轮播图（兼容旧版小程序） |
| GET | `/api/home/hot-keywords` | 免登录 | 热门搜索词 |

> 别名同样是为「已经发过版的小程序」保留的。一个 `@GetMapping({"", "/index"})`
> 解决两件事，没有第二套实现，也就不会出现两份逻辑慢慢跑偏。

## 3. 数据模型

### 3.1 `banner` 补两处

```sql
ALTER TABLE `banner`
  ADD COLUMN `position` tinyint NOT NULL DEFAULT 1
      COMMENT '展示位置:1首页轮播 2分类页顶部(预留)' AFTER `link_value`,
  ADD INDEX `idx_position_status_sort` (`position`, `status`, `sort`);

ALTER TABLE `banner`
  MODIFY COLUMN `link_type` tinyint NOT NULL DEFAULT 1
      COMMENT '跳转类型:1商品详情 2分类页 3外部链接 4无跳转';
```

- **`position` 现在只有一个取值**，看着像过度设计。但轮播图天然会出现在多个页面
  （首页、分类页顶部、活动页），预留位置维度后，将来加位置只是加一个常量值，
  不必再动一次表结构 + 改一次所有查询。
- **`link_type` 补 4（无跳转）**：原先只有 1/2/3，运营想放一张纯品牌海报
  只能挂一个假链接——点进去是 404，比不能点更糟。
- 索引 `(position, status, sort)` 对应 `listActive()` 的完整 WHERE + ORDER BY，
  首页每次读都不必回表排序。

### 3.2 `category` 补一列

```sql
ALTER TABLE `category`
  ADD COLUMN `is_home_show` tinyint NOT NULL DEFAULT 1
      COMMENT '是否显示在首页金刚区:0否 1是' AFTER `sort`,
  ADD INDEX `idx_home_show` (`is_home_show`, `sort`);
```

**不另建「金刚区表」**：金刚区是分类的一个视图属性，不是独立实体。
拆表之后，分类改名要同步两张表、分类删除要处理孤儿行、排序要维护两套，
换来的只是「表名更贴合业务」。默认值取 1 —— 新分类刚建出来就是要用的，
想下掉某个分类就在配置里关掉开关，而不是删分类（删分类会让历史商品变成无分类）。

### 3.3 `home_config`（新增，KV 结构）

```sql
CREATE TABLE `home_config` (
  `id`           bigint       NOT NULL AUTO_INCREMENT,
  `config_key`   varchar(50)  NOT NULL COMMENT '配置键',
  `config_value` varchar(2000) NOT NULL DEFAULT '' COMMENT '配置值（结构化数据存 JSON）',
  `remark`       varchar(100) DEFAULT NULL COMMENT '说明',
  `create_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`)
);
```

预置 7 项：

| config_key | 默认值 | 含义 |
|---|---|---|
| `notice_text` | 校园二手交易平台正式上线，认证学生发布更放心 | 公告文字 |
| `notice_link` | （空） | 公告点击跳转 |
| `notice_enabled` | 1 | 公告是否显示 |
| `hot_mode` | auto | `manual` 手动选品 / `auto` 按热度 |
| `hot_product_ids` | `[]` | 手动选品 ID（JSON 数组，有序） |
| `hot_count` | 10 | 自动模式取前 N 件 |
| `new_count` | 10 | 新品推荐取最新 N 件 |

**为什么是 KV 而不是 7 个列**：首页可配置项还会继续加（活动倒计时、客服二维码、
开学季专题位……）。每加一项就 `ALTER TABLE` 一次，在毕设阶段还能接受，
真上线就是「每次运营提需求都要发一次版」。

**但接口不是 KV**：给前端的仍然是 `HomeConfigVO` 的 7 个结构化字段。
让前端拼 `config_key` 等于把数据库设计直接暴露成接口契约，
以后想加一项配置要同时改前端和数据库两处，KV 的唯一好处也就没了。
铺平/收拢的逻辑全在 `HomeConfigService` 内部。

### 3.4 预置轮播图 3 张

脚本用 `NOT EXISTS (SELECT 1 FROM banner WHERE title = ...)` 保证可重复执行，
图片指向 `backend/uploads/2026/10/seed-banner-{1,2,3}.png`（脚本生成的 750×420 渐变图）。

## 4. 关键设计

### 4.1 默认值写在代码里，不是只写在 SQL 里

`HomeConfigKey.defaultValue(key)` 为 7 个 key 各给了一个兜底值。原因是三条几乎必然发生的路径：

- 新环境部署，忘了跑 V12 脚本；
- 运维误删了 `home_config` 里的一行；
- 演示前清库重来，配置表空了。

这三种情况下，`get()` 都应该返回一套能用的默认值，让**首页照常打开**。
首页打不开是这个系统里最贵的事故，严重程度远高于「公告文案不对」。

### 4.2 读配置做防御性收敛

`get()` 读出来的是数据库里的字符串，可能是历史脏数据、被手改过的 JSON、
或者旧版本写入的非法值。所以每一步都收敛而不是信任：

| 脏数据 | 处理 |
|---|---|
| `hot_count = 999` | 截到 1-20，非法/缺失回落 10 |
| `hot_mode = "hot"` | 回落 `auto` |
| `hot_product_ids = "not json"` / `[1,"x",null]` | 当作空数组，逐个校验为 `Long` |
| `notice_enabled = "true"` | 非 1 一律按 0 |

代价是十几行 `if`，收益是「配置表被写坏，首页也不会 500」。

### 4.3 手动选品：允许「选了但商品已下架」

运营选了 20 件商品，几个月后它们陆续卖掉、下架、被强制下架。
此时有两种错误做法：

- 报错 / 返回空区块 —— 首页开一个空白区，是明确的 bug；
- 只按 ID 查，不过滤状态 —— 把违规下架的商品重新推上首页，是事故。

正确做法是：**读取时按已上架过滤，全部失效则回落自动模式**。

```java
if (HomeConfigKey.MODE_MANUAL.equals(config.getHotMode())) {
    List<ProductListVO> picked = productService.listOnSaleByIds(config.getHotProductIds());
    if (!picked.isEmpty()) return picked;
    log.warn("热门推荐为手动模式但所选商品均失效，回落按热度自动取 {} 件", config.getHotCount());
}
return productService.hotTop(config.getHotCount());
```

判定依据是「**结果为空**」而不是「**配置为空**」。部分失效就少推几件，
全部失效才回落——推荐策略退化，总好过首页开天窗。

> 管理端对应做了一处提示：`missingPickCount` 计算已选 ID 里查不回来的数量，
> 在热门 Tab 上提示「有 N 件已下架，保存后将不展示」。运营看得见，才不会以为是自己点错了。

### 4.4 `IN` 查询的返回顺序不可靠

`listOnSaleByIds` 用 `LinkedHashMap` 按传入 ID 顺序重排结果。
`WHERE id IN (...)` 不保证顺序，直接 `stream().map(toVO)` 的话，
运营精心排在第一位的商品会变成随机一件——这种 bug 只在小数据量下偶发，
测出来也像「排序没保存成功」，很难定位。

### 4.5 缓存：一个 key，5 分钟，写后失效

```java
private static final long INDEX_TTL_SECONDS = 300L;
```

- **整份 `HomeIndexVO` 缓存成一个 key `home:index`**。拆成 banner / config / hot 多个 key
  只会让失效逻辑变复杂：改一张轮播图要判断「要不要连带清商品缓存」，
  而首页的数据量本来就小（几十条），拆开没有收益。
- **运营的写操作主动失效**，不受 TTL 影响：保存配置、拖排序、启停轮播图、删除轮播图
  都会立即 `evictIndex()`，运营点完保存刷新小程序就能看到。
- **5 分钟是给「用户侧」的**：热门推荐按浏览量+收藏量加权，这个值本来就不需要秒级准确；
  缓存 5 分钟把首页的数据库压力从「每次打开一次聚合查询」降到「每 5 分钟一次」。

失效统一走 `HomeCacheService`，它在 `TransactionSynchronizationManager` 的
`afterCommit` 里删 key——事务回滚了就不该清缓存，否则清完发现数据没变，
下一次读又把旧值写回去，白折腾一趟。

**为什么抽一个 `HomeCacheService`**：轮播图 / 配置 / 金刚区三处的写操作都要清首页缓存，
而 `HomeServiceImpl` 又依赖这三个 Service。直接在业务 Service 里注入 `HomeService`
会形成循环依赖。`HomeCacheService` 只依赖 Redis + Jackson，
业务 Service 单向依赖它，依赖图是一棵树而不是一个环。

### 4.6 商品变更的缓存失效收拢到一个方法

模块 8 起，商品写操作要清「数据看板」缓存；模块 12 起，还要清「首页」缓存。
原先 8 个调用点各写一行 `dashboardCacheService.evictOnProductChange()`，
现在统一换成 `evictProductCaches()`：

```java
private void evictProductCaches() {
    dashboardCacheService.evictOnProductChange();
    homeCacheService.evictIndex();
}
```

8 个调用点只需要说「商品变了」，具体清哪些缓存由一处决定。
新增一种缓存时改一行就够——漏掉一处就是一个只在特定操作后复现的脏数据。

### 4.7 批量排序：一次事务写完

拖拽排序最容易写错的地方是「前端循环发 N 个 `PUT /{id}`」。问题是：
第 3 个请求失败时，前 2 个已经改了，用户看到的顺序是「一半新一半旧」，
而每失败一次就刷新一次页面，越刷越乱。

`PUT /sort` 和 `PUT /category/sort` 都是**一次请求、一个事务、全量覆盖**：

```java
long exists = count(in(ids));
if (exists != ids.size()) {
    throw new BusinessException(PARAM_ERROR, "存在已被删除的轮播图，请刷新后重试");
}
```

先校验所有 ID 都还在，再逐条更新。拖拽期间别的管理员刚好删了一张图，
这里会明确报错而不是静默跳过——静默跳过的结果是「保存成功」变成一句假话。

## 5. 请求 / 响应示例

### 5.1 `GET /api/home`

```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "notice": {
      "text": "校园二手交易平台正式上线，认证学生发布更放心",
      "link": null,
      "enabled": 1
    },
    "banners": [
      { "id": 4, "title": "开学季·二手教材专区", "imageUrl": "/uploads/2026/10/seed-banner-1.png",
        "linkType": 2, "linkValue": "2", "linkTypeLabel": "分类页", "sort": 1, "status": 1,
        "position": 1, "startTime": null, "endTime": null }
    ],
    "categories": [
      { "id": 1, "name": "数码电子", "icon": "📱", "sort": 1, "isHomeShow": 1, "productCount": 10 }
    ],
    "hotProducts": [
      { "id": 814, "title": "考研数学全套教材 无笔记", "price": 45.00, "originalPrice": 168.00,
        "cover": "/uploads/2026/10/8517....png", "viewCount": 512, "favoriteCount": 96,
        "isVerifiedSeller": 1, "createTime": "2026-10-22 15:04:11" }
    ],
    "newProducts": [ /* 同上结构，按发布时间倒序 */ ],
    "hotKeywords": ["自行车", "教材", "考研资料", "耳机", "显示器"]
  }
}
```

**`notice` 永远是对象，不会是 `null`**。小程序端不必写 `notice && notice.enabled`
这种双重判空；关闭时返回 `enabled: 0`，前端一个 `wx:if` 就够。
「该返回空对象还是 null」这种问题，统一一次，前端就不用各自猜。

### 5.2 `PUT /api/home/config`

```json
{
  "noticeText": "国庆假期平台正常交易，注意当面验货",
  "noticeLink": "https://example.com/activity",
  "noticeEnabled": 1,
  "hotMode": "manual",
  "hotProductIds": [814, 815, 813],
  "hotCount": 10,
  "newCount": 10
}
```

响应：`{"code":200,"msg":"配置已保存，首页已刷新","data":null}`

### 5.3 `PUT /api/home/category/sort`

```json
{
  "items": [
    { "id": 2, "isHomeShow": 1, "sort": 1 },
    { "id": 1, "isHomeShow": 1, "sort": 2 },
    { "id": 7, "isHomeShow": 0, "sort": 3 }
  ]
}
```

全量覆盖：传了哪些分类就更新哪些，`sort` 同时是**分类的全局排序**，
所以金刚区配置和分类管理页看到的是同一套顺序——不会出现「分类管理里排好了、
首页还是老样子」的困惑。

## 6. 校验与错误返回

| 场景 | 结果 |
|---|---|
| 公告超过 100 字 | 400「公告最多100字」（`@Size` + Service 双保险） |
| 公告链接不以 `http://` / `https://` 开头 | 400「公告链接需以 http:// 或 https:// 开头」 |
| `hotMode` 不是 manual / auto | 400「热门推荐模式取值为 manual 或 auto」 |
| `manual` 模式但一件都没选 | 400「手动选品模式下至少选择一件商品」 |
| 手动选品超过 20 件 | 400「手动选品最多20件」 |
| `hotCount` / `newCount` 越界 | 400（`@Min/@Max`）；Service 侧再 `clamp` 到 1-20 |
| 轮播图 `linkType=1` / `2` 但 `linkValue` 不是正整数 | 400「商品ID必须是正整数」/「分类ID必须是正整数」 |
| 轮播图 `linkType=3` 但链接不以 `http(s)://` 开头 | 400「外部链接需以 http:// 或 https:// 开头」 |
| 轮播图 `linkType` 不在 1-4 内 | 400「跳转类型取值为1商品 2分类 3外部链接 4无跳转」 |
| 轮播图 `endTime` 早于 `startTime` | 400「失效时间必须晚于生效时间」 |
| 批量排序含已删除 ID | 400「存在已被删除的轮播图/分类，请刷新后重试」 |
| 客服调用任何写接口 | 403（`@RequireRole(ADMIN)` + 客服只读拦截器） |

> **手动模式没选品为什么不静默回落自动？** 这里和 4.3 的处理刻意不同：
> 4.3 是「运营选过、但商品后来失效了」，属于不可控；这里是「运营点了手动模式
> 却一件没选」，属于操作没做完。前者应该兜底，后者应该报错——否则运营会以为
> 自己选的东西生效了，实际看到的是推荐策略，排查半天。

## 7. 管理端：一个页面、五个 Tab

`admin-web/src/views/home/index.vue`（约 1500 行），侧边栏「首页配置」一项进 5 个 Tab：

| Tab | 交互要点 |
|---|---|
| 轮播图 | 卡片网格 + 按住封面拖拽排序（`Sortable`，`handle: '.banner-item__cover'`），松手后一次 `PUT /sort` |
| 公告配置 | 多行文本（实时 22/100 字数）+ 开关 + 链接 + 右侧手机预览（真实滚动动画） |
| 金刚区配置 | 左栏分类列表拖拽（带占位动画）+ 右侧 4 列网格预览；每行有显示开关 |
| 热门推荐 | 模式切换；手动模式提供商品搜索选择器、已选卡片（可就地拖拽排序、可移除）；自动模式只填数量 |
| 新品推荐 | 数量输入 + 双列网格预览 |

三个 Tab 共用同一份 `reactive config`，保存时整份提交。
原因：7 个配置项在数据库里是 7 行，但业务上是「首页配置」这一件事。
拆成三次保存意味着运营改完公告又去改热门，中间任何一次失败都会让页面状态
和数据库不一致；整份提交 + 一个保存按钮，心智负担最低。

`?tab=banner|notice|category|hot|new` 支持深链接，方便截图脚本和排查时直接定位。

`banner` 旧路由保留为重定向：`/banner → /home`。老书签不会 404，
但页面上不再有「轮播图管理」这个菜单项。

## 8. 小程序端调整

`pages/index/index.js` 的数据来源从「三次请求」改为「一次 `/api/home`」：

| 改动 | 说明 |
|---|---|
| 公告栏 | 顶部橙色条 + 小喇叭 + 右滑关闭；关闭后写 `globalData.noticeClosed`，本次会话不再出现 |
| 轮播图跳转 | 按 `linkType` 分派：1 商品详情、2 分类页、3 复制外链、4 无操作 |
| 金刚区 | 从接口读 `categories`，超过 7 个才补「更多」入口 |
| 热门推荐 | 标题「热门推荐」，横向滑动卡片 |
| 新品推荐 | 标题「最新发布」，双列网格 |
| 图片地址 | 统一补 `imageFull` / `coverFull`（相对路径 → 服务器绝对地址） |

公告关闭状态放 `globalData` 而不是 `storage`：**「本次会话」是字面意思**——
用户关掉是不想被这条公告反复打扰，不是永久不要公告。下次启动小程序
（比如第二天有了新公告）应该照常展示。

## 9. 测试与验证

| 脚本 | 用例数 | 覆盖 |
|---|---|---|
| `tests/smoke_module12.py` | 84 | 轮播图 CRUD/排序/启停、配置读写与全部校验分支、金刚区保存、聚合接口字段、缓存命中与失效、权限（客服只读） |
| `tests/mp_home_check.cjs` | 22 | 小程序首页 JS 逻辑离线校验（公告关闭、linkType 分派、分类补「更多」） |
| `tests/seed_module12_demo.py` | — | 演示数据：6 件带彩色封面的商品 + `--tidy` 收起测试遗留空壳商品 + `--clean` |
| 全量回归 | m2~m11 共 1012（含 m3.5） | 首页字段断言、轮播图页面迁移相关的旧断言已同步更新 |

缓存失效验证方式：每次写操作后查 Redis `TTL home:index`，确认 key 已删除；
再读一次 `/api/home`，确认重建且 TTL 回到 ≤300。

## 10. 已知边界

1. **热门推荐的「热度」是固定公式**（浏览量 + 收藏量×3），不是时间衰减的推荐算法。
   商品挂半年攒的浏览量会一直压着新品，真实场景需要按时间窗口归零。
   这是有意为之：毕设阶段算法可解释比算法先进重要，答辩时要能说清「为什么这件排第一」。
2. **热门搜索词是固定词表**（`HOT_KEYWORDS` 常量）。冷启动阶段没有足够搜索日志，
   等「AI 智能推荐」模块补上按搜索次数统计即可，接口形状不变。
3. **轮播图 `position=2`（分类页顶部）只留了字段，没有页面在用**。
   加位置维度是为了不再动表结构，不是现在就要用。
4. **金刚区分类数量没有上限**：小程序端超过 7 个折叠到「更多」，管理端不做限制。
   分类总数是运营自己掌握的，加一个「最多 12 个」的限制只会带来新的报错文案。
5. **配置保存是整份覆盖**：两个管理员同时编辑不同 Tab 会互相覆盖（后保存的赢）。
   管理端是单人运营场景，加乐观锁（`update_time` 版本号）的成本高于收益。
6. **`/api/home` 没有做接口级限流**。缓存 5 分钟后单机 QPS 压力可忽略，
   但真要上公网，这一层应该在网关做，而不是在每个接口里散着写。
