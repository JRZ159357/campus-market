# 模块 9 · 举报与客服工单 接口说明

> 依赖脚本：`sql/V10__report_operation_log.sql`（新建 `report` 与 `sys_operation_log` 两张表）
> 基础路径：`http://localhost:8080`，统一返回 `Result<T>`（`code=200` 成功；**异常同样以 HTTP 200 返回，真实结果码在 body.code**）
> 权限：举报提交与「我的举报」需登录；管理端列表与详情管理员、客服均**可读**；接单与处理管理员、客服均**可写**；操作日志仅管理员可读，**接口层不提供任何写方法**
> 相关常量：`common/ReportType`、`common/ReportStatus`、`common/ReportReason`、`common/ReportHandleAction`、`common/ReportRule`、`common/OpLogModule`

---

## 1. 数据库设计

### 1.1 `report`（举报表）

单表承载三类举报，靠 `report_type` 区分，`target_id` 分别指向 `product` / `sys_user` / `review` 的主键。

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | 举报ID |
| reporter_id | BIGINT NOT NULL | 举报人 |
| report_type | TINYINT NOT NULL | 1 商品 / 2 用户 / 3 评价 |
| target_id | BIGINT NOT NULL | 被举报对象ID（语义随 `report_type` 变化） |
| target_user_id | BIGINT NOT NULL | 被举报人（商品/评价举报时由对象反查，便于「按被举报人」聚合） |
| target_snapshot | JSON NOT NULL | 举报那一刻的对象快照 |
| reason | TINYINT NOT NULL | 1 虚假描述 / 2 违禁品 / 3 欺诈 / 4 辱骂 / 5 广告 / 6 其他 |
| content | VARCHAR(500) NOT NULL | 详细描述，5~500 字 |
| images | VARCHAR(600) NULL | 证据图，最多 3 张，逗号分隔 |
| status | TINYINT NOT NULL DEFAULT 0 | 0 待处理 / 1 处理中 / 2 已处理 / 3 已驳回 |
| handler_id | BIGINT NULL | 处理人 |
| accept_time | DATETIME NULL | 接单时间（与提交时间分开记） |
| handle_result | VARCHAR(500) NULL | 处理结论 |
| handle_action | TINYINT NULL | 0 不处理 / 1 下架商品 / 2 封禁用户 / 3 删除评价 / 4 仅扣信誉分 |
| handle_time | DATETIME NULL | 处理完成时间 |
| create_time / update_time / deleted | DATETIME / DATETIME / TINYINT | 通用字段，逻辑删除 |

**索引**

| 索引 | 字段 | 解决什么 |
|---|---|---|
| `idx_status_create` | status, create_time | 管理端「按状态筛 + 按时间倒序」的默认列表 |
| `idx_reporter` | reporter_id, create_time | 「我的举报」倒序分页 |
| `idx_type_target` | report_type, target_id | 24 小时重复举报节流 |
| `idx_target_user` | target_user_id | 按被举报人聚合（同一卖家被反复举报） |
| `idx_reporter_type_target` | reporter_id, report_type, target_id, create_time | 节流查询的覆盖索引，避免回表 |

### 1.2 为什么不拆成三张举报表

举报是**一次性事件**，不是长期关系（不像「收藏」需要频繁按对象反查）。拆表之后客服的默认列表就得写成三表 `UNION ALL` 再套一层排序分页，SQL 复杂度翻倍，而换来的收益只有「字段稍微精准一点」。

单表 + `report_type` 的代价是：`target_id` 语义要靠代码保证。这一风险用**对象快照**兜住了 —— 客服看的是快照内容，而不是去 JOIN 原表。

### 1.3 `sys_operation_log`（操作日志表）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | 日志ID |
| operator_id | BIGINT NULL | 操作人（系统任务为 NULL） |
| operator_role | VARCHAR(30) NULL | 操作时的角色（快照，角色后续变更不影响历史） |
| operator_name | VARCHAR(50) NULL | 操作人昵称（快照，用户改名后历史日志仍可读） |
| module | VARCHAR(20) NOT NULL | report / user / product / review / order / student_auth |
| action | VARCHAR(30) NOT NULL | accept / handle / offline / ban / update_profile / update_role / delete / audit |
| target_id | BIGINT NULL | 操作目标ID |
| detail | VARCHAR(500) NULL | 可读描述 + 参数快照 |
| ip | VARCHAR(64) NULL | 来源 IP |
| create_time | DATETIME NOT NULL | 操作时间 |

索引：`idx_operator`、`idx_module`、`idx_time`。

**没有 `update_time`、没有 `deleted`**：日志只追加，不修改、不删除。表结构本身就是这条承诺的一部分。

---

## 2. 业务规则

### 2.1 对象快照：为什么要在提交那一刻把对象「拍下来」

`target_snapshot` 存的是举报提交瞬间对象的完整形态：

| 类型 | 快照内容 |
|---|---|
| 商品 | `kind / title / description / price / status / cover` |
| 用户 | `kind / nickname / avatar / role / creditScore` |
| 评价 | `kind / stars / content / images` |

三个真实场景决定了这一列必须存在：

1. **违规商品被下架后才轮到客服处理**。队列里躺两天，卖家自己下架了，客服点开举报去 JOIN `product` 得到 null —— 于是「举报成立」的判罚没有任何依据可写进结论。
2. **违规评价已被删除**。删除后被举报人才想起申诉，或者举报人要追问「到底处理没处理」，此时原表里查不到任何内容。
3. **卖家先下架商品再被举报**（快递纠纷里极常见）。这时 JOIN 不到内容，客服只能看到举报人一句「东西不对版」。

代价是每条举报多 200~500 字节 JSON。换来的是「任何时点的举报都能被独立复核」—— 这是举报系统的底线。

**快照序列化失败必须让举报提交失败**。一条没有快照的举报等于「证据只有举报人一句话」，它比提交失败更糟：提交失败用户会重试，而残缺的举报会安静地流进客服队列。

### 2.2 状态流转

```
        提交            接单              处理
  ——————————→ 0 待处理 ——————→ 1 处理中 ——————→ 2 已处理
                    │                              ↘ 3 已驳回
                    └──────── 管理员可直接处理 ────────┘
```

- `0 → 1`：客服接单，写 `handler_id` 与 `accept_time`
- `0/1 → 2` 或 `0/1 → 3`：处理，写 `handle_result / handle_action / handle_time`
- `2/3` 是**终态**，不可再处理（`ReportStatus.isFinal`）

**接单用条件更新**：

```sql
UPDATE report SET status = 1, handler_id = ?, accept_time = NOW()
WHERE id = ? AND status = 0
```

影响行数为 0 说明单子已被别人接走。若用「先查再改」，两个客服同时点「接单」会双双通过校验，后写的 `handler_id` 静默覆盖前一个 —— 结果是两个人都以为自己接了这单，都在核实，最后给出两条矛盾结论。

### 2.3 `accept_time` 为什么要和 `create_time` 分开

「平均处理时长」只统计**客服实际花掉的时间**。

若从提交时间起算，一条在队列里躺了 3 天的单会显示「处理时长 72 小时」。这个数字反过来会逼客服**抢单**而不是把单做干净 —— 指标一旦惩罚认真核实，它就开始制造有害行为。

```sql
TIMESTAMPDIFF(MINUTE, IFNULL(accept_time, create_time), handle_time)
```

用 `IFNULL` 兜住「管理员未接单直接处理」的情形（没有 `accept_time` 就只能从提交算起）。

### 2.4 处理率的口径

**处理率 = (已处理 + 已驳回) / 总数**。

驳回也是「客服给了结论」。把驳回排除在外，会让客服越认真驳回越显得没干活 —— 这是个典型的指标反噬。

### 2.5 处置动作与举报类型的强绑定

`ReportType.allowedActions(type)`：

| 举报类型 | 允许动作 |
|---|---|
| 商品（1） | 不处理 / 下架商品 / 仅扣信誉分 |
| 用户（2） | 不处理 / 封禁用户 / 仅扣信誉分 |
| 评价（3） | 删除评价 / 仅扣信誉分 |

不这么绑，就会出现「商品举报」被处理成「封禁卖家」—— 买家投诉的是一支笔的图文不符，判罚却把整个店铺封了。语义错乱之外，这种判罚在申诉时几乎无法自证。

### 2.6 驳回不能带动作，成立必须带动作

两条互补的校验，堵的是同一类矛盾数据：

- `status = 3（驳回）` 时 `handleAction` 必须为 `NONE`。否则会出现「状态说驳回，却把人封了」。
- `status = 2（已处理）` 时 `handleAction` 不能为 `NONE`。否则会出现「已处理，但什么都没做」—— 这种记录对举报人无法交代，对被举报人也没有任何约束力。

第二条最初漏了，前端默认值还停在「不处理」，两边一起把矛盾数据放了出去。现在后端拦、前端默认值也跟着结论走（选「举报成立」时默认落到该类型的典型处罚）。

### 2.7 24 小时重复举报节流

同一用户对同一对象，24 小时内只能提交一次（`ReportRule.DUP_COOLDOWN_HOURS`）。

查询走 `idx_reporter_type_target` 覆盖索引：

```sql
SELECT MAX(create_time) FROM report
WHERE reporter_id = ? AND report_type = ? AND target_id = ?
```

注意**逻辑删除的记录也要参与判断**。否则举报人自己删掉记录就能立刻重新提交，节流形同虚设。

### 2.8 三种处置动作的联动

处理动作不是只改 `report.status`，而是真的落到业务表：

| 动作 | 联动 | 复用哪个 Service |
|---|---|---|
| 下架商品 | 商品置为违规下架 + 卖家信誉分 -10 + 通知卖家 | `productService.forceOfflineByReport` |
| 封禁用户 | 账号封禁（可选时长）+ 信誉分 -10 + 名下商品全部下架 + 清登录缓存 + 通知 | `adminUserService.updateStatus` |
| 删除评价 | 评价逻辑删除 + 评价人信誉分 -5 + 通知评价人 | `reviewService.adminDelete` |
| 仅扣信誉分 | 信誉分 -10（商品/用户）或 -5（评价） | `creditService` |

**为什么不自己写一遍联动逻辑**：封禁用户至少有四个副作用（改状态、下架商品、发通知、清 Redis 登录缓存）。任何一处漏掉都会留下漏洞，尤其清缓存 —— 漏了会导致被封用户在缓存过期前（30 分钟）仍能正常访问，客服以为已经封了、实际没封。复用既有 Service，等于把「封禁该做什么」这件事收敛到一个地方。

**评价人要扣分**：被评价人已经拿不到这条好评了，若评价人不付出代价，就形成「靠发违规评价刷信誉分」的无成本套利。

**通知失败不回滚处置**。处置已生效后，通知写失败只记 `warn`。反过来做等于给攻击者留一条绕过路径：把通知表写满，封禁就不生效了。

### 2.9 客服的写权限为什么要单独开一个口

`AuthInterceptor` 里原本的规则是「客服只读：除白名单外一切非 GET 返回 403」。这条规则对商品审核、用户管理是对的，但**接单与处理是客服的本职工作**，一刀切会让整个工单系统无法运转，并且会诱发线下用管理员账号代操作。

开口方式：

```java
CUSTOMER_SERVICE_WRITE_PREFIXES = List.of("/api/report/admin/")
```

用**前缀**而不是精确路径：接单和处理路径里含 `{id}` 变量，精确匹配永远匹配不上。

前缀只解决路径变量匹配的问题，**授权边界仍然在 `@RequireRole({ADMIN, CUSTOMER_SERVICE})`**。两层职责不同：拦截器管「这个角色能不能写」，注解管「这个方法允许哪些角色」。

### 2.10 操作日志：不可篡改靠的是接口层不存在写方法

`OperationLogController` 只有**一个 GET 方法**。没有新增、没有修改、没有删除。

不是靠前端隐藏按钮 —— 而是接口层根本不存在这些入口。日志一旦可编辑，它就不再是证据，只是一份普通业务数据。

记录方式用 AOP：`@OpLog(module, action, targetIndex, desc)` + `OperationLogAspect`。

**为什么用 `@AfterReturning` 而不是 `@Around`**：`Around` 需要自己 `try/finally` 转发 `proceed()`，一旦某处漏掉就把业务方法吞了（方法被调用但没执行，日志却记了一条「成功」）。`AfterReturning` 由容器负责转发，写法更短也更不容易出错。

**为什么不用 SpEL 做参数模板**：`#{args[2]}` 这种字符串可读性极差，且求值失败会连带影响切面执行。这里直接把参数用 Jackson 序列化成 JSON —— 排障时能一眼看到「封禁时传了 status=1、reason=虚假宣传」。

**detail 优先用注解的 `desc()`**。审计页面上没人愿意读 `调用 ReportServiceImpl#handle, 参数: 129, {"status":2,...}`。所以 detail 现在是：

```
处理举报 #135；参数：135, {"status":2,"handleAction":3,"handleResult":"核实为广告导流评价…"}
```

前半句给人看，后半句给排障用，两者都保留。

### 2.11 举报入口为什么藏在「更多」里

商品详情、卖家主页、评价卡片、聊天页，四个入口全部收在「⋯ 更多」或长按手势后面，而不是做成显眼的常驻按钮。

举报是**低频且需要理由**的动作。一键直达会让「我不想要了」「买贵了」也顺手变成举报提交，把客服队列淹成垃圾场。多一次点击，就是一道最低限度的意图过滤。

评价的举报入口用**长按**而不是每条挂一个「⋯」按钮：列表里每条都挂控件，页面会变成一排图标；长按是列表通用的「更多操作」手势，用户不会陌生。

**匿名评价不给举报入口**：服务端已把 `reviewerId` 脱敏，前端拿不到真实 ID，拿假 ID 提交只会得到一条必然失败的请求。

### 2.12 证据图超量：显式拒绝，不静默截断

`ReportSubmitDTO.images` 用 `@Size(max = 3)` 直接拒绝。

静默截断（只存前 3 张）会让用户以为 5 张全提交了，而客服只看到 3 张 —— 用户依据「我传了 5 张证据」判断举报会成立，客服依据 3 张判断证据不足，双方在信息不对称下互相埋怨。

---

## 3. 接口清单

### 3.1 用户端（3 个）

| 方法 | 路径 | 说明 | 鉴权 |
|---|---|---|---|
| POST | `/api/report` | 提交举报 | 需登录 |
| GET | `/api/report/my` | 我的举报（分页） | 需登录 |
| GET | `/api/report/my/{id}` | 我的举报详情 | 需登录（仅本人） |

**`POST /api/report` 请求体**（`ReportSubmitDTO`）

```json
{
  "reportType": 1,
  "targetId": 487,
  "reason": 2,
  "content": "这是化工溶剂，学校明令禁止带进宿舍，卖家还包邮",
  "images": ["/uploads/2026/10/a.png", "/uploads/2026/10/b.png"]
}
```

校验链与失败码：

| 场景 | code | 提示 |
|---|---|---|
| 未登录 | 401 | 登录已失效，请重新登录 |
| reportType 不是 1~3 | 400 | 举报类型不合法 |
| reason 不是 1~6 | 400 | 举报原因不合法 |
| content 少于 5 字 | 400 | 请填写至少 5 个字的详细描述 |
| images 超过 3 张 | 400 | 证据图片最多上传 3 张 |
| 被举报对象不存在 | **400** | 被举报商品不存在 / 用户不存在 / 评价不存在 |
| 举报自己 | 400 | 不能举报自己 |
| 24 小时内重复举报同一对象 | 400 | 该对象你已在 24 小时内举报过，请勿重复提交 |

> 对象不存在用 400 而不是 500：「客户端传错 ID」是参数问题。用 500 会让监控告警把它误判成服务端故障，半夜把人叫起来看一条前端拼错的 ID。

**`GET /api/report/my` 响应**（`ReportVO` 分页，节选）

```json
{
  "code": 200,
  "data": {
    "records": [
      {
        "id": 135,
        "reportType": 1,
        "reportTypeLabel": "商品举报",
        "targetId": 493,
        "targetTitle": "自制四氯化碳 500ml 工业溶剂",
        "targetCover": "/uploads/2026/10/xxx.png",
        "targetUserName": "张无良",
        "targetUserStatus": "已封禁",
        "targetMissing": false,
        "reason": 2,
        "reasonLabel": "违禁品",
        "content": "这是化工溶剂，学校明令禁止带进宿舍，卖家还包邮",
        "images": [],
        "status": 2,
        "statusLabel": "已处理",
        "statusColor": "success",
        "handleAction": 1,
        "handleActionLabel": "下架商品",
        "handleResult": "核实为违禁品，已下架商品并扣除卖家信誉分",
        "handleTime": "2026-10-22 08:56:52",
        "acceptTime": "2026-10-22 08:56:51",
        "handleHours": 0.02,
        "createTime": "2026-10-22 08:56:51",
        "hoursAgo": 0
      }
    ],
    "total": 1, "pageNum": 1, "pageSize": 10
  }
}
```

`targetMissing = true` 表示快照里三类对象的标识字段（`title` / `nickname` / `content`）一个都没有 —— 历史脏数据或对象在提交时就是空的。前端据此显示「对象已不存在」。

### 3.2 管理端（5 个，管理员 + 客服）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/report/admin/page` | 分页列表 |
| GET | `/api/report/admin/{id}` | 详情（含快照与举报人信息） |
| PUT | `/api/report/admin/{id}/accept` | 接单 |
| PUT | `/api/report/admin/{id}/handle` | 处理 |
| GET | `/api/report/admin/stats` | 统计 |

**查询参数**（`ReportQueryDTO`）

| 参数 | 说明 |
|---|---|
| pageNum / pageSize | 分页，pageSize 上限 100 |
| reportType | 1/2/3，不传为全部 |
| status | 0/1/2/3，不传为全部 |
| keyword | 模糊匹配举报人昵称 / 被举报人昵称 / 举报内容 |
| pendingOnly | true 时只返回 0 与 1（客服工作台默认视图） |
| handlerId | 只看某人的单 |
| startTime / endTime | `yyyy-MM-dd HH:mm:ss`，按提交时间过滤 |

**列表排序**

```sql
ORDER BY CASE status WHEN 0 THEN 0 WHEN 1 THEN 1 WHEN 2 THEN 2 ELSE 3 END,
         create_time DESC
```

待处理永远在最前，同为待处理则按提交时间倒序（旧的先处理，避免老单沉底）。

**`PUT /api/report/admin/{id}/handle` 请求体**（`ReportHandleDTO`）

```json
{
  "status": 2,
  "handleAction": 1,
  "handleResult": "核实为违禁品，已下架商品并扣除卖家信誉分"
}
```

失败码：

| 场景 | code | 提示 |
|---|---|---|
| 举报记录不存在 | **404** | 举报记录不存在 |
| 已是终态 | 400 | 该举报当前状态为「已处理」，不能重复处理 |
| status 不是 2/3 | 400 | 处理结论不合法 |
| handleResult 为空 | 400 | 请填写处理结果 |
| 驳回却带动作 | 400 | 举报不成立时不能执行处置动作 |
| 成立却没带动作 | 400 | 举报成立时请选择一项处置动作 |
| 动作与举报类型不匹配 | 400 | 「封禁用户」不适用于商品举报 |

**`GET /api/report/admin/stats` 响应**（`ReportStatsVO`）

```json
{
  "code": 200,
  "data": {
    "totalCount": 6, "pendingCount": 2, "processingCount": 1,
    "handledCount": 2, "rejectedCount": 1,
    "todayCount": 6, "todayHandledCount": 3,
    "handleRate": 50.0, "avgHandleHours": 0.0
  }
}
```

`handleRate` 与 `avgHandleHours` 的口径见 2.3 / 2.4。`avgHandleHours` 保留两位小数，无终态记录时为 0。

### 3.3 操作日志（1 个，仅管理员）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/operation-log/page` | 分页查询 |

**查询参数**（`OperationLogQueryDTO`）：`pageNum / pageSize / operatorId / operatorName / module / action / startTime / endTime`

**响应**（`OperationLogVO`）

```json
{
  "id": 311,
  "operatorId": 423, "operatorName": "客服小周",
  "operatorRole": "customer_service", "operatorRoleLabel": "客服",
  "module": "report", "moduleLabel": "举报处理",
  "action": "handle", "actionLabel": "处理",
  "targetId": 135,
  "detail": "处理举报 #135；参数：135, {\"status\":2,\"handleAction\":3,\"handleResult\":\"…\"}",
  "ip": "127.0.0.1",
  "createTime": "2026-10-22 08:56:53"
}
```

自动埋点的位置：

| Service 方法 | 模块 | 动作 |
|---|---|---|
| `ReportServiceImpl#accept` | report | accept（接单举报） |
| `ReportServiceImpl#handle` | report | handle（处理举报） |
| `ProductServiceImpl#forceOffline` | product | offline（强制下架违规商品） |
| `ProductServiceImpl#forceOfflineByReport` | product | offline（举报成立，强制下架商品） |
| `AdminUserServiceImpl#updateProfile` | user | update_profile（修改用户资料） |
| `AdminUserServiceImpl#updateStatus` | user | ban（封禁/解封用户） |
| `AdminUserServiceImpl#updateRole` | user | update_role（变更用户角色） |
| `AdminUserServiceImpl#auditStudentAuth` | student_auth | audit（审核学生认证） |
| `ReviewServiceImpl#adminDelete` | review | delete（删除违规评价） |

---

## 4. 数据一致性设计小结

| 可能出现的问题 | 处理方式 |
|---|---|
| 商品被下架后客服看不到举报内容 | 提交时写 `target_snapshot`，处理依据看快照而非 JOIN |
| 两个客服同时接单、结论互相矛盾 | `WHERE id=? AND status=0` 条件更新，行数为 0 直接报错 |
| 「已处理」却没有实际处置 | 成立必须带非 `NONE` 动作（后端拦 + 前端默认值跟着结论） |
| 「已驳回」却封了人 | 驳回时动作必须为 `NONE` |
| 商品举报被判成封禁卖家 | `ReportType.allowedActions` 按类型收窄，越界报错 |
| 封禁后用户在缓存过期前仍能访问 | 复用 `adminUserService.updateStatus`，内部 `evictCache` |
| 评价删了但评价人毫无代价 | 删除评价同时扣评价人 5 分 |
| 通知写失败导致封禁回滚 | 通知失败只 warn，处置不回滚 |
| 举报人在 24h 内刷同一条举报 | `idx_reporter_type_target` 覆盖索引查询，含逻辑删除记录 |
| 平均处理时长被队列堆积污染 | `accept_time` 与 `create_time` 分离，从接单起算 |
| 认真驳回的客服处理率反而更低 | 处理率含驳回 |
| 客服无法接单（只读规则误伤） | `CUSTOMER_SERVICE_WRITE_PREFIXES` 前缀白名单 + `@RequireRole` 双层 |
| 操作日志被篡改 | Controller 只有 GET，接口层不存在写方法 |
| 审计日志读不懂 | `@OpLog(desc=…)` 生成「处理举报 #135；参数：…」 |
| 前端 API 路径重复前缀 | `admin-web` 的 `baseURL` 已含 `/api`，`report.js` 不得再写 `/api` |
| 未知路由被报成「系统繁忙」 | `NoResourceFoundException` 显式映射 404 |

---

## 5. 测试

### 5.1 接口冒烟

```bash
python tests/smoke_module9.py
```

覆盖 **117 个用例、10 个段落**：

1. **提交举报** — 未登录 401 / 类型非法 / 举报自己 / 对象不存在（400）/ 描述过短 / 24h 节流 / 证据图超量拒绝 / 3 张全部落库
2. **我的举报** — 列表分页 / 按时间倒序 / 查看他人举报返回 404
3. **管理端列表** — 普通用户 403 / 客服可读 / 状态筛选 / 关键词 / 类型筛选 / **派生字段（类型标签、状态标签、对象标题、封面、快照）非空**
4. **接单** — 状态流转 0→1 / 记录处理人与 `accept_time` / 重复接单被拒 / 管理员可跳过接单直接处理 / 终态不能接单
5. **商品举报处理** — 下架 + 卖家 -10 分 + 通知 + 信誉流水关联 + 不牵连同卖家其他商品
6. **用户举报处理** — 封禁 + -10 分 + 名下商品同步下架 + 主页 403 + 不牵连无关用户 + 动作类型不匹配被拒
7. **评价举报处理** — 删除评价 + 评价人 -5 分 + 已删评价不能再被举报 + 快照 `images` 解析为数组
8. **驳回与校验** — 驳回带动作被拒 / 成立不带动作被拒 / 驳回不扣分不封禁 / 终态不能重复处理 / 处理结果为空被拒（DTO 层）/ 举报不存在 404
9. **操作日志** — 普通用户 403 / 接单与处理各写一条 / 昵称与角色 / IP / detail 可读 / 按模块与动作筛选 / 客服只读 / 无删除接口
10. **统计** — 总数 / 待处理数 / 处理率 / 今日新增 / 平均时长 / 普通用户 403

### 5.2 小程序逻辑离线校验

```bash
node tests/mp_report_check.cjs
```

覆盖 **84 个用例**：举报页（三类对象加载、原因单选、字数统计与上限、证据图最多 3 张、少于 5 字不提交、提交成功跳「我的举报」）、
我的举报（列表倒序、状态胶囊配色、处理结果块、空状态、详情弹层）、
四处入口（商品详情更多菜单、卖家主页更多、评价长按、聊天页更多）、
匿名评价不给举报入口、本人不显示「举报自己」。

### 5.3 演示数据与截图

```bash
python tests/seed_module9_demo.py          # 播种（3 类举报 × 4 种状态，共 6 条）
python tests/seed_module9_demo.py --clean  # 清理（含 sys_operation_log）
python tests/shot_admin.py --out docs/screenshots/module9 --token <JWT> \
    --user-info '{"role":"admin","roleLabel":"管理员","username":"admin"}' \
    --url http://127.0.0.1:5173 \
    --shots "report-list=report:5000,report-detail=report:5000|…,customer-service=customer-service:5500,operation-log=operation-log:4500"
```

> 演示数据用 `tests/demo_image.py` 生成 240×240 的真实封面图。冒烟测试用的 1×1 透明 PNG 对接口校验足够，但截图里 34×34 的缩略图拉满一个透明像素后仍然全透明 —— 看起来像「缩略图压根没渲染」，排查成本远高于这几十行生成代码。

截图见 `docs/screenshots/module9/`：

| 文件 | 内容 |
|---|---|
| `report-list.png` | 举报管理：4 张统计卡 + 5 个状态 Tab + 6 条举报覆盖 3 类 4 态 |
| `report-detail.png` | 详情弹窗：处理时间线 + 灰底对象快照 + 举报信息 + 处理区 |
| `customer-service.png` | 客服工作台：统计卡 + 待处理列表（可接单）+ 右侧详情与处理表单 |
| `operation-log.png` | 操作日志：只读提示 + 筛选 + 可读的操作详情 |
