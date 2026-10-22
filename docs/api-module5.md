# 模块 5 · 交易与订单 接口说明

> 依赖脚本：`sql/V6__trade_order.sql`（新建 `trade_order` / `order_log` / `address` 三张表）
> 基础路径：`http://localhost:8080`，统一返回 `Result<T>`（`code=200` 成功）
> 权限：地址与买家/卖家接口需登录；管理端读取接口对管理员与客服放开，`admin-cancel` / `admin-refund` 仅管理员；客服调用任何非 GET 接口返回 `403 客服账号无操作权限，请联系管理员`
> 相关配置：`app.order.pay-timeout-minutes`（默认 30 分钟）、`app.order.timeout-scan-ms`（默认 300000 毫秒）

---

## 1. 订单状态机

```
                     ┌──────────── 取消（买家，仅待付款）────────────┐
                     │                                              ▼
  create          pay │          ship            receive         ┌──────┐
 ────────► 0 待付款 ────► 1 待发货 ──────► 2 待收货 ──────► 3 已完成 │ 4 已取消 │
              │              │                │                    └──────┘
              │              │ apply          │ apply                 ▲
              │              ▼                ▼                       │
              │            5 退款中 ◄──────────┘                 timeout_cancel
              │              │  │                                   （定时任务）
              │   agree      │  │  reject / cancel_refund
              │              ▼  └──────────► 回到 1 或 2（refund_from_status）
              │          6 已退款
              └──── 超时 30 分钟 ────► 4 已取消（system 操作）
```

| 状态值 | 名称 | 可执行动作（角色） | 商品状态联动 |
|---|---|---|---|
| 0 | 待付款 | `pay`（买家）、`cancel`（买家）、`timeout_cancel`（系统） | 不变（仍在售，但被占用） |
| 1 | 待发货 | `ship`（卖家）、`apply_refund`（买家） | `pay` 成功时置 3 已售出 |
| 2 | 待收货 | `receive`（买家）、`apply_refund`（买家） | 不变 |
| 3 | 已完成 | — | 不变 |
| 4 | 已取消 | — | 恢复 1 在售 |
| 5 | 退款中 | `agree_refund` / `reject_refund`（卖家）、`cancel_refund`（买家） | 不变 |
| 6 | 已退款 | — | 恢复 1 在售 |

**状态值常量**：`common/OrderStatus.java`（`PENDING_PAY=0` … `REFUNDED=6`）；
**动作常量**：`common/OrderAction.java`（`create/pay/ship/receive/cancel/apply_refund/agree_refund/reject_refund/cancel_refund/timeout_cancel/admin_cancel/admin_refund`）；
**操作者角色**：`common/OperatorRole.java`（`buyer/seller/admin/customer_service/system`）。

**名称处理**：订单状态、动作、角色三类枚举各自带 `label()`，`order_log` 落库时存英文枚举值，查询时由 `OrderLogVO` 翻译成中文，保证日志可追溯且文案改动不影响历史数据。

---

## 2. 接口清单

### 2.1 收货地址（5 个）

| 方法 | 路径 | 说明 | 鉴权 |
|---|---|---|---|
| GET | `/api/address/list` | 我的地址列表（默认地址置顶） | 需登录 |
| POST | `/api/address` | 新增地址，返回地址 ID | 需登录 |
| PUT | `/api/address/{id}` | 修改地址 | 需登录 |
| DELETE | `/api/address/{id}` | 删除地址（逻辑删除） | 需登录 |
| PUT | `/api/address/{id}/default` | 设为默认地址 | 需登录 |

### 2.2 订单（买家 / 卖家）

| 方法 | 路径 | 说明 | 鉴权 |
|---|---|---|---|
| POST | `/api/order/create` | 下单，返回订单号与应付金额 | 需登录 |
| POST | `/api/order/{orderNo}/pay` | 支付（模拟，0→1） | 买家本人 |
| POST | `/api/order/{orderNo}/cancel` | 取消订单（仅待付款） | 买家本人 |
| POST | `/api/order/{orderNo}/receive` | 确认收货（2→3） | 买家本人 |
| POST | `/api/order/{orderNo}/apply-refund` | 申请退款（1/2→5） | 买家本人 |
| POST | `/api/order/{orderNo}/cancel-refund` | 撤销退款申请（5→原状态） | 买家本人 |
| POST | `/api/order/{orderNo}/ship` | 发货（1→2） | 卖家本人 |
| POST | `/api/order/{orderNo}/agree-refund` | 同意退款（5→6） | 卖家本人 |
| POST | `/api/order/{orderNo}/reject-refund` | 拒绝退款（5→原状态） | 卖家本人 |
| GET | `/api/order/{orderNo}` | 订单详情（含快照、日志、按钮权限位） | 买卖双方 |
| GET | `/api/order/buyer/page` | 我买到的（分页） | 需登录 |
| GET | `/api/order/buyer/count` | 我买到的各状态数量（Tab 角标） | 需登录 |
| GET | `/api/order/seller/page` | 我卖出的（分页） | 需登录 |

### 2.3 管理端（4 个）

| 方法 | 路径 | 说明 | 鉴权 |
|---|---|---|---|
| GET | `/api/order/admin/page` | 全平台订单分页（支持订单号/买家/卖家/状态/时间范围） | 管理员、客服 |
| GET | `/api/order/admin/{orderNo}` | 订单详情（客服只读） | 管理员、客服 |
| GET | `/api/order/admin/count` | 各状态订单总数（标签页角标） | 管理员、客服 |
| POST | `/api/order/{orderNo}/admin-cancel` | 强制取消（任意进行中状态→4） | 仅管理员 |
| POST | `/api/order/{orderNo}/admin-refund` | 强制退款（任意进行中状态→6） | 仅管理员 |

> 管理端读取接口带 `@RequireRole({ADMIN, CUSTOMER_SERVICE})`，强制干预接口只带 `@RequireRole(ADMIN)`，与拦截器「客服非 GET 一律 403」形成双保险。

---

## 3. 关键接口细节

### 3.1 下单

`POST /api/order/create`

```json
{
  "productId": 137,
  "addressId": 10,
  "remark": "周六下午在图书馆门口交易"
}
```

```json
{
  "code": 200,
  "msg": "下单成功",
  "data": { "orderNo": "202610211119475176292", "price": 25.00, "payDeadline": "2026-10-21 11:49:47" }
}
```

| 场景 | code | msg |
|---|---|---|
| 商品不存在 / 已删除 | 500 | 商品不存在或已被删除 |
| 购买自己的商品 | 500 | 不能购买自己发布的商品 |
| 商品非在售 | 500 | 该商品已售出或已下架 |
| 商品已被别人占用 | 500 | 该商品已有进行中的订单，请稍后再试 |
| 地址不属于本人 | 500 | 收货地址不存在 |

**「进行中」判定**（防重复下单的核心）：

```java
boolean occupied = lambdaQuery()
    .eq(TradeOrder::getProductId, productId)
    .and(w -> w.in(TradeOrder::getStatus,
                    List.of(PENDING_SHIP, PENDING_RECEIVE, REFUNDING))
            .or(x -> x.eq(TradeOrder::getStatus, PENDING_PAY)
                      .gt(TradeOrder::getPayDeadline, LocalDateTime.now())))
    .count() > 0;
```

待付款单必须 `pay_deadline > now()` 才算占用，否则「超时但尚未被定时任务扫到」的僵尸单会让商品永久无法再次下单。

**订单号生成**：`yyyyMMddHHmmssSSS` + 4 位随机数（共 21 位），依赖 `uk_order_no` 唯一索引，捕获 `DuplicateKeyException` 后最多重试 5 次。

**快照冻结**：下单时把商品的 `id/标题/封面/价格/分类/卖家昵称/认证状态/描述` 序列化进 `trade_order.product_snapshot`，把收货人姓名/电话/地址序列化进 `address_snapshot`。此后卖家改价、改标题、删商品，历史订单展示完全不受影响。

### 3.2 支付（状态 CAS + 防超卖）

`POST /api/order/{orderNo}/pay`

```json
{ "code": 200, "msg": "支付成功", "data": null }
```

两步都在同一事务内：

```java
// 1. 订单 CAS：只有当前确实是「待付款」才允许推进
int rows = update(new LambdaUpdateWrapper<TradeOrder>()
        .eq(TradeOrder::getId, id)
        .eq(TradeOrder::getStatus, OrderStatus.PENDING_PAY)
        .set(TradeOrder::getStatus, OrderStatus.PENDING_SHIP)
        .set(TradeOrder::getPaymentTime, now));
if (rows == 0) throw new BusinessException("订单状态已变更，请刷新后重试");

// 2. 商品 CAS：只有仍为「在售」才能抢到
if (productMapper.markSold(productId) == 0) {
    throw new BusinessException("手慢了，该商品已被其他同学买走");  // 回滚整笔订单
}
```

`markSold` 的 SQL：`UPDATE product SET status = 3 WHERE id = ? AND status = 1 AND deleted = 0`。

**为什么不用「先查再改」**：两个买家同时点「立即购买」，`SELECT` 都看到状态 0，随后都执行 `UPDATE`，会生成两笔有效订单（超卖）。改为带 `AND status = ?` 的条件更新后，MySQL 行锁保证只有一个请求的 `affected rows = 1`，另一个拿到 0 直接失败，天然实现乐观锁（CAS）。这也是论文「并发控制」一节的实测依据。

### 3.3 退款

申请（买家，`RefundApplyDTO`）：

```json
{ "reason": "收到后发现外壳有裂痕，与描述不符" }
```

`reason` 校验 `@Size(min = 5, max = 200)`，前端 textarea 同步做 ≥5 字校验。

流转时把当前状态写入 `refund_from_status`：

| 动作 | 前置状态 | 结果状态 | 商品 |
|---|---|---|---|
| `apply_refund` | 1 或 2 | 5 退款中 | 不变 |
| `cancel_refund`（买家撤销） | 5 | `refund_from_status`（1 或 2） | 不变 |
| `reject_refund`（卖家拒绝） | 5 | `refund_from_status`，并记录 `reject_refund_reason` | 不变 |
| `agree_refund`（卖家同意） | 5 | 6 已退款，记 `refund_time` | 恢复 1 在售 |

卖家拒绝后买家可再次申请，也可申请客服介入（线下流转，管理员用 `admin-refund` 兜底）。`backStatusOf()` 对 `refund_from_status` 为空的历史数据兜底返回 1。

### 3.4 订单详情（按角色下发按钮位）

`GET /api/order/{orderNo}` 返回体中直接带权限位，前端不做状态判断，只渲染 `canXxx === true` 的按钮：

```json
{
  "code": 200,
  "data": {
    "orderNo": "202610211119475176292",
    "status": 2,
    "statusLabel": "待收货",
    "isBuyer": true,
    "isSeller": false,
    "canPay": false,
    "canCancel": false,
    "canShip": false,
    "canReceive": true,
    "canApplyRefund": true,
    "canAgreeRefund": false,
    "canRejectRefund": false,
    "canCancelRefund": false,
    "canAdminCancel": false,
    "canAdminRefund": false,
    "productSnapshot": { "productId": 138, "title": "高等数学教材（第七版）上下册", "price": 25.00, "cover": "…", "categoryName": "教材书籍", "sellerNickname": "微信用户", "sellerVerified": true },
    "addressSnapshot": { "receiverName": "陈同学", "phone": "13800138000", "address": "北华大学 3 号宿舍楼 502" },
    "logs": [
      { "action": "receive", "actionLabel": "确认收货", "operatorRole": "buyer", "operatorRoleLabel": "买家", "operatorNickname": "微信用户", "fromStatus": 2, "toStatus": 3, "createTime": "2026-10-21 11:19:48" }
    ]
  }
}
```

**权限位计算**（`OrderServiceImpl#buildActions`）：

| 字段 | 条件 |
|---|---|
| `canPay` / `canCancel` | 买家 且 状态 0 且未超时 |
| `canReceive` / `canApplyRefund` | 买家 且 状态 2；`canApplyRefund` 另含状态 1 |
| `canCancelRefund` | 买家 且 状态 5 |
| `canShip` / `canAgreeRefund` / `canRejectRefund` | 卖家 且 状态 1 / 5 / 5 |
| `canAdminCancel` / `canAdminRefund` | 管理员 且 状态处于 0/1/2/5 |

服务端计算按钮位的好处：状态机规则只有一处实现，小程序与管理端共用，不会出现「前端放行、后端拒绝」的错位。

### 3.5 管理端筛选

`GET /api/order/admin/page?pageNum=1&pageSize=10&orderNo=&buyerNickname=&sellerNickname=&status=&startTime=&endTime=`

昵称筛选先按 `LIKE` 查 `sys_user` 拿到 userId 列表，再 `IN` 过滤订单，避免 `JOIN` 破坏 `idx_buyer_status` 索引；昵称查不到直接返回空页（`total=0`），不再走订单查询。

---

## 4. 定时任务

`task/OrderTimeoutTask.java`

```java
@Scheduled(fixedDelayString = "${app.order.timeout-scan-ms:300000}")
public void scan() {
    List<Long> ids = orderMapper.selectTimeoutIds(200);   // status=0 且 pay_deadline < now()
    for (Long id : ids) {
        try {
            orderService.cancelTimeoutOrder(id);          // 独立事务，单笔失败不影响整批
        } catch (Exception e) {
            log.warn("订单 {} 超时取消失败：{}", id, e.getMessage());
        }
    }
}
```

- 间隔默认 5 分钟，可用 `app.order.timeout-scan-ms` 覆盖（测试时改成 10 秒验证）；
- 单批上限 200 笔，避免长事务；
- 逐笔调用 `OrderService` 的代理对象，`@Transactional` 才生效——**同类内部调用不走代理**，这是拆出 `OrderTimeoutTask` 的原因；
- 取消时写 `order_log`（`operator_role=system`、`action=timeout_cancel`），并恢复商品为在售。

---

## 5. 表结构

### 5.1 `trade_order`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | bigint PK | |
| `order_no` | varchar(32) | 唯一索引 `uk_order_no` |
| `buyer_id` / `seller_id` | bigint | 买卖双方 |
| `product_id` | bigint | 商品 ID |
| `product_snapshot` | json | 下单时的商品快照 |
| `price` | decimal(10,2) | 成交价（= 快照价） |
| `status` | tinyint | 0-6 |
| `address_snapshot` | json | 收货信息快照 |
| `pay_deadline` | datetime | 支付截止时间（单独存列，便于走组合索引） |
| `payment_time` / `ship_time` / `finish_time` / `cancel_time` / `refund_time` | datetime | 各节点时间 |
| `cancel_reason` / `refund_reason` / `reject_refund_reason` | varchar | 原因 |
| `refund_from_status` | tinyint | 退款来源状态（拒绝/撤销时回到此状态） |
| `remark` | varchar(200) | 买家留言 |
| `created_at` / `updated_at` | datetime | 自动填充 |

索引：

| 索引 | 字段 | 用途 |
|---|---|---|
| `uk_order_no` | `order_no` | 唯一约束 + 详情查询 |
| `idx_buyer_status` | `buyer_id, status, id` | 「我买到的」按状态筛选 + 排序 |
| `idx_seller_status` | `seller_id, status, id` | 「我卖出的」 |
| `idx_status_deadline` | `status, pay_deadline` | 定时任务扫超时单 |
| `idx_product_status` | `product_id, status` | 下单占用校验 |
| `idx_create_time` | `created_at` | 管理端按时间范围筛选 |

`idx_buyer_status` 把 `id` 放在最后，是为了让「按状态筛选 + 按 id 倒序分页」走同一个索引，避免 filesort。

### 5.2 `order_log`

`id`、`order_id`、`operator_id`、`operator_role`、`action`、`from_status`、`to_status`、`remark`、`created_at`；索引 `idx_order (order_id, id)`——按订单查日志并按主键升序，即天然的时间线顺序。

日志是**审计凭据**：任何状态变更都必须先写日志再改状态（或同事务），保证「订单状态是怎么变成现在这样的」永远可回答，也是纠纷处理时客服唯一可信的依据。

### 5.3 `address`

`id`、`user_id`、`receiver_name`、`phone`、`address`、`is_default`、`deleted`、`created_at`、`updated_at`；索引 `idx_user_default (user_id, is_default)`。

业务约束在 `AddressServiceImpl` 内保证（无数据库唯一索引，因为「每用户仅一个默认」用唯一索引表达会与逻辑删除冲突）：

- 单用户最多 20 条；
- 第一个地址自动设为默认；
- 设默认时先 `UPDATE address SET is_default = 0 WHERE user_id = ?` 再置目标，同事务；
- 删除默认地址后自动把最新一条补为默认。

---

## 6. 小程序接口调用对应关系

| 页面 | 调用的接口 |
|---|---|
| `pages/order/confirm` | `GET /api/product/{id}`（商品）、`GET /api/address/list`、`POST /api/order/create` |
| `pages/order/detail` | `GET /api/order/{orderNo}`、`pay` / `cancel` / `receive` / `ship` / `apply-refund` / `agree-refund` / `reject-refund` / `cancel-refund` |
| `pages/order/list` | `GET /api/order/buyer/page` 或 `/seller/page`、`GET /api/order/buyer/count` |
| `pages/address/list` | `GET /api/address/list`、`DELETE /api/address/{id}`、`PUT /api/address/{id}/default` |
| `pages/address/edit` | `POST /api/address`、`PUT /api/address/{id}` |
| `pages/profile/index` | `GET /api/order/buyer/count`（订单卡片角标） |

---

## 7. 性能与可靠性要点

| 要点 | 做法 |
|---|---|
| 防超卖 | 商品状态 CAS（`WHERE status = 1`），行数 0 即失败回滚 |
| 防重复点击 | 订单状态 CAS（`WHERE status = 预期值`），幂等失败返回业务异常 |
| 防重复下单 | 同商品「进行中订单」唯一性校验，待付款单额外要求未超时 |
| 快照隔离 | 商品与地址 JSON 冻结，历史订单不随源数据变化 |
| 列表查询 | 覆盖索引 `(buyer_id, status, id)`，状态筛选 + 分页不 filesort |
| 昵称搜索 | 先查用户表拿 ID 再 `IN`，避免 JOIN 破坏索引 |
| 超时取消 | 定时任务 + 单批 200 笔 + 逐笔独立事务，失败不影响整批 |
| 缓存失效 | 支付成功后主动清除商品热门缓存，避免「已售出」商品仍出现在热门位 |
| 审计 | 每次状态变更写 `order_log`，含操作者、角色、前后状态 |
