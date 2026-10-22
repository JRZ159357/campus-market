# 模块 10 · 数据仪表盘 接口说明

> 依赖脚本：**无**（本模块不新建表，全部指标聚合自既有 9 个模块的数据）
> 基础路径：`http://localhost:8080`，统一返回 `Result<T>`（`code=200` 成功；**异常同样以 HTTP 200 返回，真实结果码在 body.code**）
> 权限：5 个查询接口管理员与客服均**可读**；`refresh` 接口**仅管理员**（客服是只读账号，拦截器会先一步返回 403）
> 相关类：`mapper/DashboardMapper`、`service/impl/DashboardServiceImpl`、`service/impl/DashboardCacheServiceImpl`、`common/RedisKey`

---

## 1. 模块定位

仪表盘不产生任何业务数据，它的全部价值在于**把散落在 9 个模块里的数字收敛成一份可信的口径**。

因此本模块有三条硬约束：

1. **不新建表、不新增字段。** 新增一张 `stat_daily` 之类的预聚合表会让「统计准不准」变成「定时任务跑没跑」，而本系统的数据量（商品百级、订单千级）完全不需要预聚合。
2. **不提供任何写接口。** 唯一的写操作是 `refresh`，它只清 Redis 缓存、不碰数据库。在仪表盘上加「修正数字」的接口等于允许绕过业务规则直接改统计口径，那样所有指标就都不可信了。
3. **口径只有一处定义。** 所有「算不算注销用户」「算不算退款中订单」的规则集中在 `DashboardServiceImpl` 与 `DashboardMapper` 的注释里，VO 字段也逐条标注。任何页面需要同一指标时必须调这里，不允许自己再写一遍 SQL。

### 1.1 为什么不给客服单独做一套

客服排查纠纷时需要「这个商品是不是热点」「这个卖家是不是平台头部」这类上下文。把仪表盘限定为管理员专属，客服就只能靠猜；而两个角色看到的数据本来就该是同一份，没有需要脱敏的字段。因此 5 个查询接口统一按 `@RequireRole({ADMIN, CUSTOMER_SERVICE})` 放开。

---

## 2. 接口清单

| # | 方法 | 路径 | 权限 | 缓存 TTL |
|---|---|---|---|---|
| 1 | GET | `/api/dashboard/summary` | 管理员 / 客服 | 300s |
| 2 | GET | `/api/dashboard/trend?days=7\|30` | 管理员 / 客服 | 600s |
| 3 | GET | `/api/dashboard/distribution` | 管理员 / 客服 | 300s |
| 4 | GET | `/api/dashboard/ranking` | 管理员 / 客服 | 1800s |
| 5 | GET | `/api/dashboard/recent` | 管理员 / 客服 | 30s |
| 6 | POST | `/api/dashboard/refresh` | **仅管理员** | — |

---

## 3. 核心指标 `GET /api/dashboard/summary`

### 3.1 返回示例

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "totalUsers": 65,
    "newUsersToday": 12,
    "newUsersWeek": 65,
    "userGrowth": null,
    "totalProducts": 80,
    "onSaleProducts": 4,
    "pendingAuditProducts": 72,
    "productGrowth": null,
    "totalOrders": 0,
    "completedOrders": 0,
    "gmvTotal": 0.00,
    "gmvToday": 0.00,
    "orderGrowth": null,
    "gmvGrowth": null,
    "pendingReports": 0,
    "reportGrowth": null,
    "pendingRefunds": 0,
    "activeSessions": 0,
    "avgCreditScore": 99.8,
    "avgCreditLevel": "优秀",
    "avgCreditColor": "#67C23A",
    "statTime": "2026-10-22 09:48:31"
  }
}
```

### 3.2 字段与口径

| 字段 | 类型 | 口径 |
|---|---|---|
| `totalUsers` | Long | 全部 `deleted = 0` 的用户，含管理员与客服 |
| `newUsersToday` | Long | `create_time >= CURDATE()` |
| `newUsersWeek` | Long | 近 7 个自然日（`>= CURDATE() - 6 天`） |
| `userGrowth` | Double? | 近 7 日 vs 前 7 日百分比，**前期为 0 时为 `null`** |
| `totalProducts` | Long | 全部未删除商品，**含待审核** |
| `onSaleProducts` | Long | `status = 1`（已上架） |
| `pendingAuditProducts` | Long | `status = 0`（待审核），前端红角标取这个值 |
| `productGrowth` | Double? | 近 7 日新增商品 vs 前 7 日 |
| `totalOrders` | Long | 全部未删除订单，**不含逻辑删除** |
| `completedOrders` | Long | `status = 3`（已完成） |
| `gmvTotal` | BigDecimal | `SUM(price) WHERE status = 3`，**只算已完成** |
| `gmvToday` | BigDecimal | 同上，且 `finish_time >= CURDATE()`，**按完成时间归集** |
| `orderGrowth` / `gmvGrowth` | Double? | 近 7 日 vs 前 7 日 |
| `pendingReports` | Long | `status IN (0,1)`，即**待处理 + 处理中** |
| `reportGrowth` | Double? | 近 7 日新增举报 vs 前 7 日 |
| `pendingRefunds` | Long | `status = 5`（退款中） |
| `activeSessions` | Long | 近 24 小时有消息往来的聊天会话数 |
| `avgCreditScore` | Double | `ROUND(AVG(credit_score), 1)` |
| `avgCreditLevel` | String | 由分数映射的等级名，与小程序端一致 |
| `avgCreditColor` | String | 等级配色，前端直接用于标签色 |
| `statTime` | String | 本次统计时刻；命中缓存时该值不变（见 §6.3） |

### 3.3 三个容易答错的口径

**GMV 为什么只算 `status = 3`。** 退款中（5）的订单钱还在流转，计入会让成交额虚高；已退款（6）的钱已经退回去了。只有「已完成」是真正落袋的交易，这与订单模块 `statByUser` 的口径保持一致——两处口径一旦分叉，就会出现「卖家主页说卖了 1000 元，仪表盘说全平台成交 800 元」的尴尬。

**`gmvToday` 为什么按 `finish_time` 而不是 `create_time`。** 今天下单、明天收货完成的订单，创造的收入属于明天。按 `create_time` 归集会把「今天成交额」算成「今天下单额」，两者在业务上是不同的事。

**`activeSessions` 为什么不用 WebSocket 在线连接数。** 在线连接数是纯内存指标，一旦放进 5 分钟缓存就会变成「5 分钟前的在线数」，读的人以为看到的是实时值，反而误导。改成「近 24 小时有消息的会话数」是个落库的、可复现的、与缓存 TTL 无关的指标。

### 3.4 实现要点：一条 SQL 算出 22 个数字

`selectSummary()` 用 22 个标量子查询并列算出全部指标：

```sql
SELECT
  (SELECT COUNT(*) FROM sys_user WHERE deleted = 0) AS totalUsers,
  ...
  (SELECT COALESCE(SUM(price), 0) FROM trade_order
     WHERE deleted = 0 AND status = 3) AS gmvTotal,
  ...
FROM DUAL
```

管理端首页一打开就要同时展示十几个数字，写成 5 条 SQL 就是 5 次数据库往返；而这 22 个标量子查询彼此没有依赖（都是纯聚合），完全可以并列算完。环比需要的「前 7 日」对比值也在这条 SQL 里一并算出，避免为了拿上上周的数字再发一轮请求。

> **注意**：MyBatis-Plus 的逻辑删除只对内置 CRUD 生效，注解里的手写 SQL 框架**不会**自动追加 `deleted = 0`。本 Mapper 的每一条 SQL 都手写了这个条件，漏写会让已注销用户、已删商品被算进统计。

---

## 4. 趋势 `GET /api/dashboard/trend`

### 4.1 参数

| 参数 | 必填 | 说明 |
|---|---|---|
| `days` | 否 | 只接受 `7` 或 `30`，缺省 7。**其他任何整数一律按 7 处理** |

### 4.2 返回示例

```json
{
  "code": 200,
  "data": {
    "days": 7,
    "points": [
      { "date": "2026-10-16", "newUsers": 0, "newProducts": 0, "orders": 0, "gmv": 0, "reports": 0 },
      { "date": "2026-10-21", "newUsers": 53, "newProducts": 60, "orders": 0, "gmv": 0, "reports": 0 },
      { "date": "2026-10-22", "newUsers": 12, "newProducts": 20, "orders": 0, "gmv": 0, "reports": 0 }
    ]
  }
}
```

`points` 长度**恒等于** `days`，日期从「今天往前 `days-1` 天」连续排到今天。

### 4.3 边界行为

| 输入 | 行为 | 原因 |
|---|---|---|
| 不传 | `days = 7` | 缺省值 |
| `days=30` | `days = 30` | 唯一另一个合法窗口 |
| `days=0` / `1` / `99999` / `-5` | 一律收敛为 7 | **防拖库**：`days=99999` 会让数据库扫一年数据，一个 GET 请求就能把库拖垮 |
| `days=abc` | HTTP 400「参数类型错误: days」 | Spring 的 `Integer` 绑定失败。**故意不静默兜底**——静默当成 7 会让调用方以为自己传的参数生效了 |

### 4.4 实现要点一：5 个维度 UNION 成窄表

`selectTrend(since)` 把 5 个维度的按天统计 `UNION ALL` 成 `(metric, dt, val)` 三列返回，一次请求只发一条 SQL、只做一次结果集传输。应用层再按 `metric` 拆开、横向拼成「每天一行」。

比写 5 条 `GROUP BY` 再在 Java 里合并更省一次网络往返，也比拉全表回来自己按天分组更省内存。

### 4.5 实现要点二：`only_full_group_by` 的坑

MySQL 8 默认开启 `only_full_group_by`。下面两种写法都会报错：

```sql
-- 错误 1：SELECT 与 GROUP BY 表达式不一致
SELECT DATE_FORMAT(create_time,'%Y-%m-%d') AS dt ... GROUP BY DATE(create_time)
-- ERROR 1055: Expression #2 of SELECT list is not in GROUP BY clause

-- 错误 2：GROUP BY 引用 SELECT 别名
SELECT DATE_FORMAT(create_time,'%Y-%m-%d') AS dt ... GROUP BY dt
-- MySQL 不允许；Oracle / PostgreSQL 允许，跨库迁移时最容易踩
```

正确写法是让 `SELECT` 与 `GROUP BY` 使用**完全相同的表达式**：

```sql
GROUP BY DATE_FORMAT(create_time, '%Y-%m-%d')
```

> 这个坑在本模块开发时真实触发过：接口返回 500「系统繁忙」，日志里是 1055。修复方式是 5 个 UNION 分支全部统一成上面的写法。这也是本项目唯一一处跨分支必须同步修改的地方——只改一个分支会得到「用户趋势有数据、订单趋势是空的」这种更难排查的现象。

### 4.6 实现要点三：应用层补零

数据库只返回「有数据的那几天」。应用层按 `window` 循环补齐全部日期，缺的指标补 0：

```java
LocalDate firstDay = today.minusDays(window - 1L);
for (int i = 0; i < window; i++) {
    LocalDate day = firstDay.plusDays(i);
    Map<String, Object> metrics = grouped.get(day.format(DATE_FMT));
    point.setNewUsers(longOf(metrics, "newUsers"));  // metrics 为 null 时返回 0
    ...
}
```

**为什么不在 SQL 里补。** 在 SQL 里生成连续日期需要递归 CTE 加 `LEFT JOIN`，语句会长到无法维护，而且「哪几天没有数据」这个信息会被 SQL 抹平——应用层补零则保留了这个区分（补出来的 0 和真实统计出的 0 在 `grouped` 里是能分辨的）。

**好处**：前端拿到的数组长度恒等于 `days`，不需要自己补零就能画出连续折线。补零逻辑放在前端的话，每个用到趋势图的页面都要写一遍，迟早有一处忘了。

---

## 5. 分布 `GET /api/dashboard/distribution`

### 5.1 返回结构

```json
{
  "code": 200,
  "data": {
    "userRoleDist": [
      { "name": "admin", "label": "管理员", "value": 1 },
      { "name": "customer_service", "label": "客服", "value": 2 },
      { "name": "student", "label": "认证学生", "value": 20 },
      { "name": "user", "label": "普通用户", "value": 42 }
    ],
    "productCategoryDist": [ { "name": "数码电子", "label": "数码电子", "value": 80 } ],
    "productStatusDist": [
      { "name": "0", "label": "待审核", "value": 72 },
      { "name": "1", "label": "已上架", "value": 4 },
      { "name": "4", "label": "违规下架", "value": 4 }
    ],
    "orderStatusDist": [],
    "creditScoreDist": [
      { "name": "0-59", "label": "0-59 分", "value": 0 },
      { "name": "60-79", "label": "60-79 分", "value": 0 },
      { "name": "80-99", "label": "80-99 分", "value": 1 },
      { "name": "100-119", "label": "100-119 分", "value": 64 },
      { "name": "120-150", "label": "120-150 分", "value": 0 }
    ],
    "reviewStarDist": [
      { "name": "1", "label": "1 星", "value": 0 },
      { "name": "3", "label": "3 星", "value": 0 },
      { "name": "5", "label": "5 星", "value": 0 }
    ]
  }
}
```

`NameValueVO` 统一三字段：`name`（原始码值，前端做 key）/ `label`（中文名，前端做图例）/ `value`（数值）。

### 5.2 固定档位：为什么「为 0 也要返回」

| 分布 | 档位数 | 是否固定补齐 |
|---|---|---|
| `userRoleDist` | 4 | **是**，user / student / customer_service / admin 全列 |
| `creditScoreDist` | 5 | **是**，0-59 / 60-79 / 80-99 / 100-119 / 120-150 全列 |
| `reviewStarDist` | 5 | **是**，1~5 星全列 |
| `productCategoryDist` | 不定 | 否，只返回有商品的分类 |
| `productStatusDist` / `orderStatusDist` | 不定 | 否，只返回出现过的状态 |

前三者必须在服务端补零，因为它们会被画成**饼图 / 柱状图**：饼图的图例如果随数据变化，同一个账号在不同时间会看到不同的图例项；柱状图的某一档如果是 0 而消失了，看图的人会以为「这个区间不存在」，而不是「这个区间没人」。

后两者是「有多少算多少」，因为分类是用户可增删的，状态是业务流转产生的，强行补齐反而会造出不存在的分类。

### 5.3 各分布的口径

| 分布 | SQL 口径 | 说明 |
|---|---|---|
| 角色 | `GROUP BY role`，中文名在 Java 用 `RoleConst.label()` 映射 | 不在 SQL 里写 `CASE WHEN`，否则角色码到中文名的映射会分裂成两处 |
| 分类 | `JOIN category ON c.id = p.category_id AND c.deleted = 0` | 分类已删但商品还在时，这些商品不计入任何分类，**各分类之和可能小于商品总数**——这比塞进「已删除分类」更诚实 |
| 商品状态 | `GROUP BY status` | 状态码用 `ProductStatus.label()` 翻译 |
| 订单状态 | `GROUP BY status` | 状态码用 `OrderStatus.label()` 翻译 |
| 信誉分 | 一条 SQL 里 5 个 `SUM(CASE WHEN ...)` | 5 档写成 5 条 SQL 只会带来 5 次往返和「改档位要改 5 处」的负担 |
| 评价星级 | `WHERE review_type = 1` | **只统计买家评卖家**。卖家评买家是另一个方向，混进来会让商品口碑被「卖家对买家付款速度的评分」污染 |

---

## 6. 排行榜 `GET /api/dashboard/ranking`

### 6.1 三张榜单

| 榜单 | 字段 | 排序 | 上限 |
|---|---|---|---|
| `topSellers` | sellerId / nickname / avatar / productCount / soldCount / amount / creditScore / creditLevel / creditColor | `amount DESC, soldCount DESC, id ASC` | 10 |
| `topProducts` | productId / title / price / viewCount / favoriteCount / cover | `view_count DESC, favorite_count DESC, id ASC` | 10 |
| `topCategories` | categoryId / name / productCount / viewCount | `viewCount DESC, productCount DESC, id ASC` | 7 |

排序与截断**全部在 SQL 里**完成（`ORDER BY ... LIMIT`），Java 端只做字段映射，不做全量排序。

### 6.2 实现要点一：防 JOIN 扇出

卖家榜最容易写错的地方是「JOIN 后再 GROUP BY」：

```sql
-- 错误：商品表与订单表相乘，商品多的卖家成交额被重复累加
SELECT u.id, SUM(o.price)
FROM sys_user u
JOIN product p ON p.seller_id = u.id
JOIN trade_order o ON o.seller_id = u.id
GROUP BY u.id
```

一个有 20 件商品的卖家，每笔订单会被重复算 20 次。正确做法是**各自先在子查询里聚合，再 JOIN 到用户表**：

```sql
LEFT JOIN (
  SELECT seller_id, COUNT(*) AS soldCount, SUM(price) AS amount
  FROM trade_order WHERE deleted = 0 AND status = 3 GROUP BY seller_id
) o ON o.seller_id = u.id
```

`productCount` 则用相关子查询（只算个数，不参与 JOIN），彻底避开两表相乘。

### 6.3 实现要点二：榜单的入选门槛

| 榜单 | 过滤条件 | 原因 |
|---|---|---|
| 卖家榜 | `role IN ('user','student')` | 管理员与客服不参与交易，混进销量榜会让榜单失真 |
| 热门商品 | `status IN (1, 3)` | 待审核的商品还没对外展示；下架/违规下架的商品不该出现在「热门」里 |
| 热门分类 | `LEFT JOIN` 而非 `INNER JOIN` | 浏览量为 0 的新分类也该出现，否则运营看不到自己刚建的分类 |

`order by` 都追加了 `id ASC` 作为最终 tie-breaker：没有它，成交额相同的卖家在不同次查询里顺序可能不同，缓存前后看到的榜单会「跳动」。

---

## 7. 最近动态 `GET /api/dashboard/recent`

```json
{
  "code": 200,
  "data": [
    {
      "id": 118, "operatorId": 1, "operatorName": "管理员", "operatorAvatar": "/upload/a.png",
      "operatorRole": "admin", "module": "user", "moduleLabel": "用户管理",
      "action": "update_role", "actionLabel": "变更角色",
      "targetId": 198, "detail": "将用户 198 的角色变更为 customer_service",
      "createTime": "2026-10-22 09:50:50"
    }
  ]
}
```

- 数据源是模块 9 的 `sys_operation_log`，`ORDER BY id DESC LIMIT 20`。
- `operatorName` 取日志里的**冗余快照**而非 JOIN 用户表：用户改过昵称后，日志要保留当时的名称，这正是冗余昵称存在的意义。头像则 JOIN 用户表实时取（头像变更不需要追溯历史）。
- `moduleLabel` / `actionLabel` 由 `OpLogModule` 统一翻译，前端不写映射表。
- TTL 只有 **30 秒**：这是「最近动态」，读的人预期看到的就是刚刚发生的事，缓存 5 分钟会让人以为系统没记录。

---

## 8. 缓存策略

### 8.1 分级 TTL

| 数据 | Key | TTL | 为什么是这个值 |
|---|---|---|---|
| summary | `dashboard:summary:all` | 300s | 指标卡是首页第一眼看到的，5 分钟内的偏差可以接受 |
| distribution | `dashboard:distribution:all` | 300s | 与 summary 同源，同 TTL 便于一起失效 |
| trend | `dashboard:trend:trend:7` / `:30` | 600s | 趋势图是「看走势」，10 分钟不影响判断，且 SQL 最重（5 段 UNION） |
| ranking | `dashboard:ranking:all` | 1800s | 榜单变化最慢，30 分钟足够；这是最贵的查询（3 张榜 + 子查询） |
| recent | `dashboard:recent:all` | 30s | 最近动态必须新鲜 |

TTL 的依据是**数据变化频率 × 查询代价**：榜单变化最慢、查询最贵 → TTL 最长；动态变化最快、查询便宜 → TTL 最短。

### 8.2 缓存的是最终 VO

`putSummary(vo)` 存的是组装完成的 `DashboardSummaryVO` 的 JSON，不是原始统计行。好处是**第二次请求完全不碰数据库**——不需要「取缓存 → 再组装一遍」。代价是缓存内容与 VO 结构强绑定：VO 字段改名后，旧 JSON 反序列化会失败。这一点用「反序列化异常降级为未命中」兜住（见 §8.4）。

### 8.3 写操作主动失效

| 触发点 | 清除的缓存 | 原因 |
|---|---|---|
| `evictOnProductChange` | summary / distribution / ranking / trend(7,30) | 商品口径散布在指标卡、分布图、热门商品榜、热门分类榜、趋势图五处 |
| `evictOnOrderChange` | summary / distribution / ranking / trend(7,30) | 订单影响成交额、订单状态分布、卖家榜、趋势的订单与成交额两条线 |
| `evictOnReportChange` | summary / trend(7,30) | 举报只影响待处理数与趋势里的举报数 |
| `evictOnUserChange` | summary / distribution / trend(7,30) | 用户影响总数、角色分布、信誉分分布、趋势新增用户 |
| `evictOnReviewChange` | summary / distribution | 评价影响星级分布与平均信誉分，不影响榜单与趋势 |
| `evictAll`（手动刷新） | 全部 6 个 key | — |

接入点共 15 处：商品 7 处（发布 / 编辑 / 下架 / 删除 / 审核 / 强制下架 / 批量下架）、订单 2 处（`casStatus` 统一覆盖 11 种流转 + `create`）、举报 3 处（提交 / 接单 / 处理）、信誉分 1 处（`addScore`，覆盖评价/举报/管理员调整三条链路）、评价 2 处、用户 4 处（微信注册 / 账号注册 / 改角色 / 改状态 / 学生认证通过）。

### 8.4 三条工程约束

**① 用「删除」而不是「更新」。** 删除是幂等的；在缓存上做加减在并发下极易算错（两个写操作同时读到旧值再各自减 1）。

**② 事务感知失效。** 如果当前处于事务中，删除动作推迟到 `afterCommit` 执行：

```java
if (TransactionSynchronizationManager.isSynchronizationActive()) {
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override public void afterCommit() { doDelete(keys); }
    });
    return;
}
doDelete(keys);
```

原因：事务内先删缓存、事务后提交，中间存在一个窗口——此刻若有读请求进来，会读到**未提交**的数据并把它回填进缓存，于是这笔被回滚的变更就会被缓存 5~30 分钟。推迟到提交后就没有这个窗口。

**③ Redis 异常一律降级查库。** 所有 redis 调用都包在 try-catch 里，异常时读返回 `null`（视为未命中）、写与删静默跳过。缓存是优化不是功能依赖——Redis 挂掉时仪表盘应该变慢，但不应该打不开。

### 8.5 为什么单独抽 `DashboardCacheService`

商品审核要清仪表盘缓存，而仪表盘要读商品表。如果让 `ProductServiceImpl` 直接依赖 `DashboardService`，Spring 启动时会出现循环依赖。

解法是把「缓存读写 + 失效」抽成一个只依赖 `StringRedisTemplate` 与 `ObjectMapper` 的独立服务：

```
ProductServiceImpl ──依赖──> DashboardCacheService <──依赖── DashboardServiceImpl
                                    │
                                    └──只依赖 Redis + Jackson
```

业务 Service 单向依赖它，缓存服务不反向依赖任何业务 Service，依赖图无环。这也让「失效粒度」有了唯一的定义处——未来加缓存维度不必翻遍 15 个接入点。

---

## 9. 手动刷新 `POST /api/dashboard/refresh`

- **仅管理员**（`@RequireRole(RoleConst.ADMIN)`）。
- 用 `POST` 而不是 `GET`：它是状态变更（清缓存），`GET` 不应有副作用。
- 只清 Redis，不碰数据库。
- 客服调用会被拦截器的只读规则挡在前面（非 GET → 403），即使绕过拦截器，`@RequireRole` 也会再挡一次。
- 前端按钮用 `v-permission="'admin'"` 隐藏，客服登录时看不到。

---

## 10. 验证要点

`tests/smoke_module10.py` 覆盖 145 个用例，重点断言：

| 类别 | 关键断言 |
|---|---|
| 权限 | 未登录 5 个接口全 401；普通用户全 403；客服 5 个接口全 200；普通用户与客服刷新均 403 |
| 指标 | 22 个字段齐全；在售 ≤ 总数、已完成 ≤ 总订单、今日 GMV ≤ 累计 GMV；待审核商品数与 `SELECT COUNT(*) WHERE status=0 AND deleted=0` 对账 |
| 趋势 | 长度恒等于 days；日期连续且最后一天是今天；`days=0/1/99999/-5` 全部收敛为 7；`days=abc` 返回 400；趋势今日新增用户数 == summary 的 `newUsersToday` |
| 分布 | 角色固定 4 档、信誉分固定 5 档、星级固定 5 档；角色合计 == 总用户数；商品状态合计 == 总商品数 |
| 榜单 | 数量不超上限；成交额/浏览量/商品数倒序；字段齐全 |
| 缓存 | 6 个 key 全部生成且 TTL 符合分级设计；`sleep(1.2s)` 后二次请求 `statTime` 不变（证明命中而非重算）；**商品审核后 summary 与 ranking 缓存被清空** |
| 降级 | 手动删掉缓存后接口仍 200 且返回完整指标 |

> 关于 `statTime` 断言：这是验证「真命中缓存」的巧办法。如果每次都查库，`statTime` 会随时间推进；命中缓存则返回的是缓存里那个时刻的值。仅靠「两次响应体相同」不足以证明命中（同秒内两次查库也会相同），所以中间刻意 `sleep(1.2s)` 跨过整秒边界。

---

## 11. 性能优化小结（论文可写）

| 优化点 | 做法 | 效果 |
|---|---|---|
| 多源聚合 | 一条 SQL 用 22 个标量子查询合并 5 张表的统计 | 首页 5 类数据从 5 次往返降为 1 次 |
| 窄表 UNION | 趋势 5 个维度 `UNION ALL` 成 `(metric, dt, val)` | 1 条 SQL、1 次结果集传输 |
| 分级缓存 | 按「变化频率 × 查询代价」定 30s~1800s 五档 TTL | 榜单这类最贵查询的命中率最高 |
| 写后失效 | 15 个写接入点 + 事务感知推迟删除 | 数据变更即时可见，且不会把回滚数据缓存下来 |
| SQL 排序截断 | `ORDER BY + LIMIT` 三张榜 | 不在 Java 里全量排序，内存占用与数据量无关 |
| 按天 GROUP BY + 应用层补零 | 数据库只返回有数据的日期 | 不生成无用行，前端直接拿到连续数组 |
| 参数白名单 | `days` 只接受 7/30 | 防止 `days=99999` 扫库 |
