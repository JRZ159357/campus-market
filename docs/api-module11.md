# 模块 11 · 系统管理收尾（敏感词过滤 + 账号管理）接口说明

> 对应版本：V11__sensitive_word_account.sql
> 代码位置：`backend/src/main/java/com/campus/market/{controller,service,service/impl,mapper,dto,vo,common}`
> 管理端：`admin-web/src/views/system/{sensitive-word,account}.vue`

## 1. 模块定位

模块 11 补的是前 10 个模块一直欠着的两块「平台治理」能力：

| 缺口 | 后果 | 本模块的补法 |
|---|---|---|
| 没有任何内容合规手段 | 违禁品、辱骂、站外引流都能直接发布，平台只能靠事后举报 | 20 条预置词 + 两级策略，接进**发布 / 编辑 / 评价 / 聊天**四条写路径 |
| 后台账号只能靠 SQL 维护 | 新增一个客服要去数据库 insert 一行并手工 BCrypt | 账号管理页：新增、改角色、重置密码、启停用、删除 |

两条能力的设计取向是相反的，值得先说清楚：

- **敏感词是「广度优先」**：一个词生效，全站四条路径同时受约束。所以它必须只有**一个**检测入口，
  否则迟早出现「商品页拦了、聊天页漏了」。
- **账号管理是「深度优先」**：每个写操作都要先回答「这么做会不会把系统锁死」。
  它的代码量里大半不是 CRUD，是护栏。

## 2. 接口清单

### 2.1 敏感词 `/api/sensitive-word`

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/page` | admin / customer_service | 分页，支持类型 Tab、状态、关键词 |
| GET | `/enabled` | admin / customer_service | 当前生效词表（调试 / 词典体检） |
| POST | `/` | admin | 新增 |
| PUT | `/{id}` | admin | 编辑 |
| DELETE | `/{id}` | admin | 删除（物理删除） |
| PUT | `/{id}/status` | admin | 启用 / 禁用（`?status=0\|1`） |
| POST | `/batch` | admin | 批量导入（文本框一行一个） |
| POST | `/reload` | admin | 手动重载内存词典 |

### 2.2 后台账号 `/api/admin/account`

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/page` | admin | 分页，仅返回 `admin` / `customer_service` |
| POST | `/` | admin | 新增 |
| PUT | `/{id}` | admin | 编辑昵称 / 角色 / 状态 |
| PUT | `/{id}/password` | admin | 重置密码 |
| PUT | `/{id}/status` | admin | 启用 / 禁用 |
| DELETE | `/{id}` | admin | 删除（逻辑删除 + 释放账号名） |

> 账号管理的**读接口也只给管理员**。账号列表等于「谁有权进管理端」的地图，属于安全信息，
> 客服没有业务必要（他查用户走的是用户管理）。这与「客服登录后系统管理菜单整体隐藏」是同一套判断。

## 3. 数据模型

### 3.1 `sensitive_word`

```sql
CREATE TABLE `sensitive_word` (
  `id`          bigint      NOT NULL AUTO_INCREMENT,
  `word`        varchar(50) NOT NULL COMMENT '敏感词',
  `type`        tinyint     NOT NULL DEFAULT 4 COMMENT '1违禁品 2辱骂 3广告 4其他',
  `level`       tinyint     NOT NULL DEFAULT 1 COMMENT '1警告 2拦截',
  `status`      tinyint     NOT NULL DEFAULT 1 COMMENT '0禁用 1启用',
  `create_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_word` (`word`),
  KEY `idx_type_status` (`type`, `status`)
);
```

三个设计决定：

1. **`uk_word` 承担批量导入的去重**。`INSERT IGNORE` / 存在性批量查询都依赖它，
   重复导入同一份词表不会产生脏数据，也不需要「先查再插」的两倍开销。
2. **`level` 只有两级，不用分值**。分值必须配「累计多少分才拦」的阈值，阈值一旦可配，
   运营就会去调它，调完之后「为什么这条能发那条不能发」就说不清了。两级语义与用户侧结果一一对应。
3. **`status` 与删除是两件事**。禁用是「暂时不拦但保留配置」（活动期很常见），
   删除是「这个词不再需要」。因此禁用行留在表里但不参与内存匹配。

> **与需求的一处偏差**：需求把 type 4 写成「政治」，但预置词里的「骗子 / 跑路」既不是违禁品也不是辱骂，
> 管理端 Tab 又只给了「其他」一个位置。本实现统一收敛为 `4 = 其他`（政治类归入其中），
> 否则会出现一个没有 Tab 可筛的类型。

### 3.2 `product` 新增两列

```sql
ALTER TABLE `product`
  ADD COLUMN `need_manual_review` tinyint NOT NULL DEFAULT 0 COMMENT '待人工复核:0否 1是',
  ADD COLUMN `sensitive_hit` varchar(255) DEFAULT NULL COMMENT '命中的敏感词，逗号分隔',
  ADD KEY `idx_manual_review` (`need_manual_review`, `status`);
```

不把「待人工复核」塞进 `status`：`status` 是商品的**对外生命周期**（待审核/上架/下架/售出/违规），
「需要重点看」是**审核侧的工作标记**。合成一个字段后，审核通过时必须二选一地丢掉一个语义。

## 4. 核心：两级过滤机制

### 4.1 策略表

| 等级 | 商品发布 / 编辑 | 评价提交 | 聊天消息 |
|---|---|---|---|
| **1 警告** | 放行，`needManualReview=1`，通知管理员 | 放行，记日志 | 放行，记日志 |
| **2 拦截** | 拒绝，提示命中词，通知发布者 | 拒绝，提示命中词 | 正文中的词替换为 `**`，消息照发 |

三种场景的处置不同，不是实现偷懒，而是产品判断：

- **商品**可以「先放后审」，因为审核队列本来就是人工闸门，多带一个标记成本很低。
- **评价**对「警告级」放行：「我们加了微信沟通」是正常叙述，拦掉会显得平台不让人说话。
- **聊天**不能整条拒绝：即时通讯里消息被吞，发送者只会以为网络坏了反复重发，
  客服收到的投诉量远大于收益。替换成 `**` 既阻断传播，又给了即时反馈。

### 4.2 统一入口

```java
SensitiveCheckResult check(String... texts);
```

四个调用点：

| 位置 | 调用 | 说明 |
|---|---|---|
| `ProductServiceImpl.publish` | `check(title, description)` | 拦截抛异常；警告打标记 |
| `ProductServiceImpl.updateProduct` | 同上 | **编辑也要检**，见 4.4 |
| `ReviewServiceImpl.submit` | `check(content)` | 只拦不标 |
| `ChatServiceImpl.sendMessage` | `check(content)` | 仅 `msgType = TEXT` |

`SensitiveCheckResult` 同时给出 `isBlocked()` / `isWarned()` / `getBlockWords()` / `getWarnWords()` /
`blockMessage()` / `maskBlockWords()`，把「提示文案长什么样」也收在一处——
否则会出现「商品页说包含违规词、聊天页说消息不合规」的不一致。

### 4.3 归一化匹配：拦住「加 微 信」

```java
// 全角转半角 → 去掉零宽字符 → 去掉空白与英文符号 → 转小写
"加*微信"  →  "加微信"   ✅ 命中
"加 微 信" →  "加微信"   ✅ 命中
"加微\u200B信" → "加微信" ✅ 命中
```

只剔除**英文符号**（`*-_.·|/\~`等）与空白，**保留中文标点**：中文标点承担断句语义，
去掉后会产生跨句拼接的误判（「这笔生意做不成。骗子太多了」不该被算成额外组合）。

代价是可能把两个正常词拼成敏感词，这是「宁可多拦一条、不可漏放一条」的取舍，
且被误判的内容最多只是进入人工复核队列，不会直接拒绝发布。

### 4.4 编辑路径不能漏

只在 `publish` 做检测是不够的：先发一个正常商品通过审核，再「编辑」把标题改成违禁内容——
编辑会把状态打回待审核，但文案已经进库。这条路径比发布更隐蔽（不涉及图片上传），
一个脚本几秒就能把全店标题改一遍。因此 `updateProduct` 同样调用 `check`。

## 5. 性能设计：内存快照 + 写时替换

### 5.1 为什么不用 Redis

检测发生在全站最高频的三条写路径上，其中聊天消息的量级最大。如果每条消息都去 Redis
取一次全量词表（1000 词约 20KB），单机 QPS 上千时网络开销会直接压到 RT 上，
且 Redis 抖动会让聊天不可用。

词表是典型的「读极多、写极少」配置数据（运营一天改不了几次），放进堆内存最合适。

### 5.2 无锁并发模型

```java
private volatile WordSnapshot snapshot;   // 不可变快照，刷新即整体替换
```

- 快照对象的字段全部是 `final` 不可变集合；
- 刷新时 `new WordSnapshot(entries)` 后赋给 volatile 引用，读线程要么看到旧快照、
  要么看到新快照，永远不会看到「改了一半」的中间态；
- 代价是刷新瞬间可能仍按旧词典判定几百毫秒——这个偏差对业务无影响，比给每次检测加读锁划算。

### 5.3 刷新时机：事务提交之后

```java
TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
    @Override public void afterCommit() { safeReload(); }
});
```

事务内刷新会读到本事务尚未提交的数据，一旦事务回滚，内存里就留下一个数据库里不存在的词，
且要等下一次写操作才会被纠正。刷新本身失败也只记 warn——数据库已经是对的，内存滞后会被下一轮纠正。

### 5.4 启动失败不影响可用性

`@PostConstruct` 里的加载用 try-catch 包住：表还没建、数据库正在迁移、
多实例中某个实例启动早于 SQL 执行，这些情况下应用仍应能起来（词表为空 = 不过滤），
而不是启动即崩。

### 5.5 复杂度与上限

当前实现是「遍历词表 + `String.contains`」，复杂度 `O(词数 × 文本长度)`。
20 条词、几百字文本的实际耗时在微秒级；**词表上万时应换 Aho-Corasick 自动机**
（一次扫描找出全部模式）。这一点在文档里写明，避免评审误以为不知道优化方向。

## 6. 批量导入

`POST /api/sensitive-word/batch`

```json
{ "text": "走私\n加微信\n刷单", "type": 4, "level": 1 }
```

响应：

```json
{
  "total": 4, "inserted": 2, "duplicated": 1, "invalid": 1,
  "duplicatedWords": ["走私"],
  "invalidWords": ["这是一个超过五十个字的非常长的词……"]
}
```

四个要点：

1. **按行拆分、去空行、保序去重**（`LinkedHashSet`），同一份文本里的重复行只算一次。
2. **一次 `IN` 查询查出已存在的词**，而不是逐条 `selectCount`——1000 行词表逐条查就是 1000 次往返。
3. **超长词跳过并回显**，不截断入库：截断后的词永远匹配不到，属于「看起来加了其实没用」。
   这里**不能**复用单条新增的校验方法——它会抛异常，等于一个坏词废掉整批。
4. **并发撞唯一索引时降级为逐条插入**：批量语句失败时整批都不生效，直接回报「全部重复」是错的。
   降级后能精确统计成功条数。

## 7. 账号管理的六条护栏

| 护栏 | 触发接口 | 为什么 |
|---|---|---|
| 不能修改自己的角色 | PUT `/{id}` | 把自己降成客服会立刻失去写权限，若是最后一个管理员则系统锁死 |
| 不能禁用自己 | PUT `/{id}`、PUT `/{id}/status` | 同上 |
| 不能删除自己 | DELETE `/{id}` | 同上 |
| 不能重置自己的密码 | PUT `/{id}/password` | 自助改密需要校验旧密码，绕过它等于把改密的前提拿掉 |
| 至少保留一个**可用**管理员 | PUT / DELETE | 判断条件必须同时含 `role=admin` 与 `status=0`：只数 `role=admin` 会把已禁用的管理员算进去，结果是「有两个管理员但都进不去」 |
| 目标必须是后台角色 | 全部写接口 | 接口是 `/api/admin/account/{id}`，传入普通用户 id 就能改其角色，等于绕过用户管理模块的额度回收逻辑 |

### 删除为什么要把账号名改掉

`sys_user.uk_username` 是唯一索引，而逻辑删除只把 `deleted` 置 1，行还在表里。
若不同时改名，这个账号名就被永久占用——运营常见的「删了重建同名账号」会直接撞唯一键，
而界面上这个账号已经不存在了，运维根本想不到是索引在挡。

实现上分两步：

```java
// 1. 先改名（@TableLogic 字段不会随 updateById 更新，所以不能合并成一条）
sysUserMapper.update(null, Wrappers.<SysUser>lambdaUpdate()
        .eq(SysUser::getId, id)
        .set(SysUser::getUsername, original + "#del" + id));
// 2. 再逻辑删除
sysUserMapper.deleteById(id);
```

顺序不能反：先删的话，这行在 MP 的视角里已经不存在，`updateById` 会带上 `deleted=0` 的
where 条件，更新 0 行。

## 8. 管理端

### 8.1 敏感词管理 `views/system/sensitive-word.vue`

- 类型 Tab（全部 / 违禁品 / 辱骂 / 广告 / 其他）+ 关键词 + 状态筛选
- 表格：敏感词、类型标签、等级标签、状态、创建时间、操作
- 配色：类型「违禁品红 / 辱骂橙 / 广告蓝 / 其他灰」，等级「警告橙 / 拦截红」
- 新增 / 编辑弹窗：敏感词、类型下拉、等级单选（带策略说明）、状态开关
- 批量弹窗：大文本框 + 「已识别 N 个」实时计数 + 提交按钮直接显示条数
- 导入结果弹窗：识别 / 新增 / 已存在 / 非法四栏 + 跳过明细

### 8.2 账号管理 `views/system/account.vue`

- 表格：账号（含「本人」标记）、昵称、角色标签、状态、最后登录、创建时间、操作
- 角色标签：管理员红、客服蓝
- 对「本人」行，重置密码 / 禁用 / 删除三个按钮直接置灰，并用 `title` 说明原因——
  让按钮点下去再报错，是把后端护栏当成交互反馈，体验很差
- 新增 / 编辑：账号（编辑时只读）、密码 + 确认密码（仅新增）、昵称、角色、状态
- 重置密码：新密码 + 确认密码，成功后 toast 回显新密码 6 秒（管理员自己输入的，回显只为核对）

### 8.3 菜单整合

「系统管理」从一个菜单项改成一个分组：

```
系统管理 ▾
  ├─ 账号管理     /system/account
  ├─ 敏感词管理   /system/sensitive-word
  └─ 操作日志     /system/log
```

- `roles: ['admin']` 只写在父级：Vue Router 的 `route.meta` 会合并父子 meta，
  子路由自动继承，因此客服手敲 `/system/account` 也会被守卫弹回仪表盘；
- 原 `views/system/index.vue`（账号设置 / 角色权限矩阵）改为 `/system/profile`，
  不占菜单项，改由右上角下拉的「账号设置」进入——它的核心内容「修改密码」下拉里已有，
  单独占一个菜单项属于重复入口；
- `/operation-log` 保留一条 `redirect → /system/log`，避免旧书签 404；
- 侧边栏渲染改成支持 `el-sub-menu`（原来只渲染平级 `el-menu-item`）。

### 8.4 商品审核页

新增「待人工复核」Tab（带红色角标计数），它是 `status=0` **且** `needManualReview=1` 两个条件的交集。
列表行内直接显示「待复核」标签 + 「命中：加微信」，审核员不用打开详情就知道要重点看什么。
支持 `/product/audit?tab=review` 直达。

审核通过或驳回后 `needManualReview` 清零（它表示「还没人看过」，一直留着这个 Tab 会越积越多），
`sensitiveHit` 保留作为事后追溯依据。

## 9. 论文可写点

1. **多级过滤机制**：两级语义与「用户侧结果」一一对应（发得出去 / 发不出去），
   而不是用连续分值和阈值——阈值的可配置性会摧毁规则的可解释性。
2. **内存缓存 + 主动刷新**：`volatile` 不可变快照实现无锁读、写时整体替换；
   刷新推迟到 `afterCommit`，避免回滚数据污染内存；加载失败降级为「不过滤」而非阻断启动。
3. **多场景统一入口**：四条写路径共用一个 `check()`，规则变更只改一处；
   并由 `SensitiveCheckResult` 统一生成提示文案，避免三处文案不一致。
4. **归一化匹配的收益与代价**：能拦住「加 微 信 / 加*微信」这类规避写法，
   代价是可能跨词拼接误判——用「警告级只标记不拒绝」把误判成本压到最低。
5. **护栏式权限设计**：账号管理把「会不会把系统锁死」作为每个写操作的前置判断，
   这类约束无法用通用 CRUD 生成器产出，是「业务理解」在代码里的具体形态。

## 10. 已知边界

| 边界 | 现状 | 若要做成生产级 |
|---|---|---|
| 词表规模 | 千条以内，`contains` 遍历 | 换 Aho-Corasick，或引入 DFA |
| 多实例部署 | 每个实例各自持有内存快照，改动只在本实例生效 | 提供 `/reload` 手动同步，或接 Redis Pub/Sub 广播 |
| 命中审计 | 只记商品标记与日志，不落命中明细表 | 高频拦截落表会迅速膨胀，需要抽样或按天聚合 |
| 繁体 / 拼音 / 谐音 | 未覆盖 | 需要同义词库或多语言归一化 |
| 图片内容 | 不检测 | 需要接入 OCR 或图像审核服务 |
