# 校园二手交易平台（毕业设计）

基于 Spring Boot 3 + MyBatis-Plus + MySQL 8 + Redis 的校园二手交易平台，含微信小程序端与 Vue3 管理端。
当前进度：**模块 12 · 首页推荐位配置已完成并通过端到端测试，全部功能模块（模块 1~12）开发完毕**。小程序端 10 个模块、管理端 8 个模块均已落地，后续只剩演示数据准备、Git 整理与部署文档。

---

## 1. 目录结构

```
campus-market/
├── docs/
│   ├── DEPLOYMENT.md             部署文档（环境要求 / 部署步骤 / 端口 / 演示账号 / 故障排查 / 生产建议）
│   ├── project-profile.md        项目档案（唯一事实来源：角色、模块、约定）
│   ├── api-module1.md            模块1 接口说明
│   ├── api-module2.md            模块2 接口说明（分类 / 商品发布 / 审核）
│   ├── api-module3.md            模块3 接口说明（首页聚合 / 搜索 / 热门 / 轮播图）
│   ├── api-module3_5.md          模块3.5 接口说明（账号认证 / 角色权限模型）
│   ├── api-module4.md            模块4 接口说明（收藏 / 关注 / 详情联动字段）
│   ├── api-module5.md            模块5 接口说明（订单状态机 / 地址 / 定时任务）
│   ├── api-module6.md            模块6 接口说明（WebSocket 协议 / 聊天 / 通知 / 未读数一致性）
│   ├── api-module7.md            模块7 接口说明（评价规则 / 信誉分算法 / 等级区间 / 追评窗口 / 匿名脱敏）
│   ├── api-module8.md            模块8 接口说明（个人中心聚合 / 卖家主页 / 资料设置 / 用户管理 / 注销联动）
│   ├── api-module9.md            模块9 接口说明（举报单表多态 / 对象快照取证 / 状态流转 / 处置联动 / 操作日志）
│   ├── api-module10.md           模块10 接口说明（多源聚合口径 / 分级缓存 / 写后失效 / 趋势补零 / 榜单防扇出）
│   ├── api-module11.md           模块11 接口说明（两级敏感词过滤 / 归一化匹配 / 内存快照 / 账号护栏）
│   ├── api-module12.md           模块12 接口说明（KV 配置不暴露成契约 / 默认值兜底 / 选品回落 / 首页缓存单 key）
│   ├── screenshots/module5/      模块5 管理端真实截图（列表 / 详情 / 客服只读）
│   ├── screenshots/module6/      模块6 管理端真实截图（客服工作台 / 通知管理）
│   ├── screenshots/module7/      模块7 管理端真实截图（评价管理 / 信誉分管理）
│   ├── screenshots/module8/      模块8 管理端真实截图（用户列表 / 详情弹窗 / 标签页 / 编辑弹窗）
│   ├── screenshots/module9/      模块9 管理端真实截图（举报列表 / 详情弹窗 / 客服工作台 / 操作日志）
│   ├── screenshots/module10/     模块10 管理端截图（数据仪表盘整页）
│   ├── screenshots/module11/     模块11 管理端截图（敏感词管理 / 账号管理 / 待人工复核 Tab / 客服视角菜单）
│   └── screenshots/module12/     模块12 管理端截图（首页配置 5 个 Tab：轮播 / 公告 / 金刚区 / 热门 / 新品）
├── sql/
│   ├── V1__user_auth.sql         模块1 建表脚本（sys_user / student_auth）
│   ├── V2__category_product.sql  模块2 建表脚本（category / product / product_image + 预置分类）
│   ├── V3__banner_home.sql       模块3 建表脚本（banner + 列表查询索引）
│   ├── V4__auth_upgrade.sql      模块3.5 变更脚本（register_type + 账号字段收紧）
│   ├── V5__favorite_follow.sql   模块4 建表脚本（favorite / follow + 用户冗余计数）
│   ├── V6__trade_order.sql       模块5 建表脚本（trade_order / order_log / address）
│   ├── V7__chat_notice.sql       模块6 建表脚本（chat_session / chat_message / notice）
│   ├── V8__review_credit.sql     模块7 变更脚本（review / credit_log + trade_order 评价标记）
│   ├── V9__user_profile.sql      模块8 变更脚本（sys_user 增 gender / intro + create_time 索引）
│   ├── V10__report_operation_log.sql  模块9 建表脚本（report / sys_operation_log + 5 处索引）
│   ├── V11__sensitive_word_account.sql 模块11 建表脚本（sensitive_word + product 增 need_manual_review / sensitive_hit）
│   └── V12__home_config.sql      模块12 变更脚本（banner 增 position / category 增 is_home_show / home_config + 预置配置与 3 张轮播图）
├── backend/                      后端工程（Spring Boot 3.2.5）
│   ├── pom.xml
│   ├── sql/init_demo_data.sql    演示数据一键初始化（12 用户 / 40 商品 / 26 订单 / 18 评价…，可重复执行）
│   └── src/main/
│       ├── java/com/campus/market/
│       │   ├── common/           Result / 状态码 / 异常 / 角色与商品常量 / OrderStatus / OrderAction / MsgType / NoticeType / CreditRule / CreditLevel / CreditLogType / ReviewType / ReviewRule / Gender / UserRule / ReportType / ReportStatus / ReportReason / ReportHandleAction / ReportRule / OpLogModule / HomeConfigKey / annotation/OpLog
│       │   ├── config/           MyBatis-Plus、Knife4j、WebMvc、Jackson、WebSocket、密码编码器、数据初始化
│       │   ├── interceptor/      AuthInterceptor（统一鉴权 + 免登录接口软登录 + 客服只读与写前缀白名单）
│       │   ├── aspect/           OperationLogAspect（@OpLog 自动埋点，AfterReturning）
│       │   ├── ws/               ChatWebSocketHandler / ChatHandshakeInterceptor / WsSessionManager / WsFrame
│       │   ├── controller/       Auth / StudentAuth / File / Category / Product / Home / HomeConfig / Banner / Favorite / Follow / Order / Address / Chat / Notice / Review / Credit / User / AdminUser / Report / OperationLog
│       │   ├── service(/impl)    Auth / StudentAuth / User / UserCenter / AdminUser / Category / Product / Home / HomeConfig / HomeCache / Banner / ViewCount / Favorite / Follow / Order / Address / Chat / Notice / Unread / Review / Credit / Report / OperationLog
│       │   ├── service/support/  ReviewAssembler（评价 VO 装配 + 匿名脱敏 + 批量补用户/商品信息）
│       │   ├── task/             OrderTimeoutTask（30 分钟未付款自动取消，默认 5 分钟一扫）
│       │   ├── mapper/           对应十八张表的 Mapper
│       │   ├── entity/           SysUser / StudentAuth / Category / Product / ProductImage / Banner / HomeConfig / Favorite / Follow / TradeOrder / OrderLog / Address / ChatSession / ChatMessage / Notice / Review / CreditLog / Report / SysOperationLog
│       │   ├── dto/ vo/          入参与出参对象
│       │   └── util/             JwtUtil / UserContext / WechatApiClient
│       └── resources/application.yml
├── admin-web/                    管理端（Vue3 + Vite + Element Plus + Pinia）
│   └── src/
│       ├── api/                  auth / studentAuth / category / product / home / banner / upload / order / chat / notice / review / credit / user / report / operationLog
│       ├── directives/           permission.js（v-permission 按钮级权限）
│       ├── components/           ChangePasswordDialog 修改密码弹窗
│       ├── utils/request.js      Axios 封装（统一解包 Result）
│       ├── stores/user.js        Pinia 用户态（isAdmin / isReadOnly）
│       ├── router/index.js       路由即菜单（meta.roles 控制可见性）
│       ├── layout/index.vue      侧边栏 + 顶栏 + 角色标签 + 客服只读提示条
│       └── views/                login / dashboard / student-auth / category / home（首页配置 5 Tab）/ product-audit / order / customer-service / notice / report / system/index / system/log / system/sensitive-word / system/account / review / credit / user
├── miniprogram/                  微信小程序端（原生）
│   ├── api/                      user.js / product.js / home.js / favorite.js / follow.js / order.js / address.js / chat.js / notice.js / review.js / credit.js / report.js
│   ├── components/product-card/  瀑布流商品卡片（五个页面共用）
│   ├── custom-tab-bar/           自定义 tabBar（5 项，中间"发布"为按钮，消息项带实时未读角标）
│   ├── utils/                    request.js / format.js（含 orderStatusMeta / starText / creditDeltaOfStars）/ waterfall.js（双列分配）/ ws.js（心跳 + 重连 + 降级轮询）
│   └── pages/                    index / category / search / message / chat / notice/detail /
│                                 profile（index 个人中心 / edit 编辑资料 / setting 设置 / about 关于我们 / security 账号与安全）/
│                                 login（含 register）/ auth/student / publish / product/{my,detail,list} /
│                                 favorite（我的收藏）/ follow（关注与粉丝）/ user/home（卖家主页）/
│                                 order/{confirm,detail,list} / address/{list,edit} /
│                                 review/{edit,follow,list,pending} / report/{index 举报,my 我的举报}
├── start.bat                    一键启动脚本（Windows：检查 JDK → 起后端 → 起管理端 → 探活）
├── stop.bat                     一键停止脚本（Windows：按端口精确结束 8080 / 5173）
└── tests/
    ├── smoke_test.py             模块1 端到端冒烟测试（22 个用例）
    ├── smoke_module2.py          模块2 端到端冒烟测试（81 个用例）
    ├── smoke_module3.py          模块3 端到端冒烟测试（54 个用例）
    ├── smoke_module3_5.py        模块3.5 端到端冒烟测试（64 个用例）
    ├── smoke_module4.py          模块4 端到端冒烟测试（72 个用例）
    ├── smoke_module5.py          模块5 端到端冒烟测试（87 个用例）
    ├── smoke_module6.py          模块6 端到端冒烟测试（108 个用例）
    ├── smoke_module7.py          模块7 端到端冒烟测试（90 个用例）
    ├── smoke_module8.py          模块8 端到端冒烟测试（89 个用例）
    ├── smoke_module9.py          模块9 端到端冒烟测试（117 个用例）
    ├── smoke_module10.py         模块10 端到端冒烟测试（145 个用例，含缓存命中与写后失效）
    ├── smoke_module11.py         模块11 端到端冒烟测试（104 个用例，含四路径过滤联动与账号护栏）
    ├── smoke_module12.py         模块12 端到端冒烟测试（84 个用例，含配置校验分支、批量排序、首页缓存失效）
    ├── seed_module11_demo.py     模块11 截图演示数据播种（造「待人工复核」商品）
    ├── seed_module12_demo.py     模块12 截图演示数据播种（6 件彩色封面商品；--tidy 收起测试遗留空壳商品）
    ├── ws_check.cjs              模块6 WebSocket 协议校验（26 个用例）
    ├── mp_page_check.cjs         小程序页面逻辑校验（模块 3/4）
    ├── mp_order_check.cjs        小程序订单/地址页逻辑校验（44 个用例）
    ├── mp_chat_check.cjs         小程序消息/聊天/通知页逻辑校验（56 个用例）
    ├── mp_review_check.cjs       小程序评价/追评/评价列表/待评价页逻辑校验（65 个用例）
    ├── mp_profile_check.cjs      小程序个人中心/编辑/卖家主页/设置/安全页逻辑校验（80 个用例）
    ├── mp_report_check.cjs       小程序举报/我的举报页与 4 处入口逻辑校验（84 个用例）
    ├── mp_home_check.cjs         小程序首页逻辑校验（22 个用例：公告关闭、linkType 分派、金刚区补「更多」）
    ├── seed_module7_demo.py      模块7 管理端截图演示数据播种与清理
    ├── seed_module8_demo.py      模块8 管理端截图演示数据播种与清理
    ├── seed_module9_demo.py      模块9 管理端截图演示数据播种与清理（3 类 × 4 态共 6 条）
    ├── demo_image.py             演示用彩色封面图生成（纯标准库；测试用的 1×1 透明图截出来是空白）
    └── shot_admin.py             管理端无头浏览器截图（Edge + CDP）
```

---

## 2. 环境要求

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | **17** | 必须用 17 运行 Maven，见「常见问题 1」 |
| Maven | 3.8+ | |
| MySQL | 8.0 | 字符集 utf8mb4 |
| Redis | 5+ | 存 token 白名单与用户缓存 |
| Node.js | 18+ | 管理端构建 |
| 微信开发者工具 | 最新稳定版 | 小程序调试 |

---

## 3. 启动步骤

> **赶时间就双击 `start.bat`**：脚本会自动找 JDK 17、检查 MySQL/Redis、起后端与前端、轮询探活，最后打印所有访问地址；`stop.bat` 按端口精确停止（不会误杀别的 Java/Node 进程）。
> 下面 3.1～3.4 是手动步骤，两者等价；完整部署说明（含环境变量踩坑、Nginx、Docker、systemd）见 **`docs/DEPLOYMENT.md`**。

### 3.1 建库建表

```bash
# 按顺序执行：V2 依赖 V1 的 sys_user，V3 依赖 V2 的 product，V4 依赖 V1 的 sys_user，
# V5 依赖 V1 的 sys_user 与 V2 的 product，V6 依赖 V1 的 sys_user 与 V2 的 product，
# V7 依赖 V1 的 sys_user 与 V2 的 product，V8 依赖 V1/V2/V6，V9 依赖 V1，
# V10 依赖 V1 的 sys_user 与 V2/V8 的 product、review（举报快照要能反查三类对象），
# V11 依赖 V1 与 V2，V12 依赖 V2 的 category 与 V3 的 banner（补列而非建表）
mysql -uroot -p --default-character-set=utf8mb4 < sql/V1__user_auth.sql
mysql -uroot -p --default-character-set=utf8mb4 < sql/V2__category_product.sql
mysql -uroot -p --default-character-set=utf8mb4 < sql/V3__banner_home.sql
mysql -uroot -p --default-character-set=utf8mb4 < sql/V4__auth_upgrade.sql
mysql -uroot -p --default-character-set=utf8mb4 < sql/V5__favorite_follow.sql
mysql -uroot -p --default-character-set=utf8mb4 < sql/V6__trade_order.sql
mysql -uroot -p --default-character-set=utf8mb4 < sql/V7__chat_notice.sql
mysql -uroot -p --default-character-set=utf8mb4 < sql/V8__review_credit.sql
mysql -uroot -p --default-character-set=utf8mb4 < sql/V9__user_profile.sql
mysql -uroot -p --default-character-set=utf8mb4 < sql/V10__report_operation_log.sql
mysql -uroot -p --default-character-set=utf8mb4 < sql/V11__sensitive_word_account.sql
mysql -uroot -p --default-character-set=utf8mb4 < sql/V12__home_config.sql
```

脚本会创建 `campus_market` 库与二十张表，并预置 7 个商品分类、20 条敏感词、3 张首页轮播图与 7 项首页配置。后台账号不写在 SQL 里，由应用启动时自动创建（BCrypt 加密）。

> V2 会把存量认证学生的 `publish_limit` 从 10 调整为 20（模块 2 需求：最多同时上架 20 件）。
> V3 不预置轮播图：图片需由管理员在「首页配置」上传真实素材；小程序对空数据做了渐变降级横幅，不会出现空白块。
> V12 会补 3 张演示用轮播图（指向 `backend/uploads/2026/10/seed-banner-*.png`），正式使用时在「首页配置 → 轮播图」里替换即可。V12 全脚本幂等：加列/加索引前先查 `information_schema`，预置数据用 `NOT EXISTS` / `INSERT IGNORE`，重复执行不会重复插入。
> V7 的 `chat_session` 用生成列 `product_key = IFNULL(product_id, 0) STORED` 参与唯一索引，因为 MySQL 唯一索引把多个 `NULL` 视为互不相等，不归一化会导致无商品的会话可以无限重复创建。
> V8 是幂等脚本：`trade_order` 的两个评价标记用 `information_schema` 判断后再加列，重复执行不会报错。
> V9 同样是幂等脚本：`sys_user` 的 `gender` / `intro` 两列与 `idx_user_create_time` 索引都用 `information_schema` 判断后再加，重复执行会输出「已存在，跳过新增」。
> V10 用 `CREATE TABLE IF NOT EXISTS`，可重复执行。`report.target_snapshot` 是 MySQL 的 `JSON` 列，但实体里按 `String` 读写（自己用 Jackson 序列化/反序列化），这样快照结构演进时不必改库。

### 3.2 启动后端

```bash
cd backend
# Windows CMD
set JAVA_HOME=D:\jdk-17.0.6
mvn spring-boot:run
```

默认读取 `application.yml` 中的连接信息，可通过环境变量覆盖：

```bash
MYSQL_PASSWORD=你的密码 REDIS_PASSWORD=你的密码 mvn spring-boot:run
```

启动成功后：

- 接口地址 `http://localhost:8080`
- 接口文档 `http://localhost:8080/doc.html`
- 实时通道 `ws://localhost:8080/ws/chat?token=<登录返回的 token>`（模块 6 聊天与通知推送，客服只读角色同样可连）
- 首次启动日志会打印 `初始化后台账号: admin / 123456 (管理员)`、`service01 / 123456 (客服)`、`service02 / 123456 (客服)`

### 3.3 启动管理端

```bash
cd admin-web
npm install
npm run dev
```

浏览器打开 `http://localhost:5173`，使用 `admin / 123456` 登录，可进入「学生认证审核」「分类管理」「首页配置」「商品审核」「订单管理」「客服工作台」「通知管理」「系统管理」「评价管理」「信誉分管理」「用户管理」。

使用 `service01 / 123456` 或 `service02 / 123456` 登录即进入**客服只读模式**：顶部出现黄色提示条，所有新增/修改/删除/审核按钮隐藏，「系统管理」菜单不显示；即使手动调用写接口，后端也会返回 `403 客服账号无操作权限，请联系管理员`。

### 3.4 运行小程序

1. 微信开发者工具 → 导入项目 → 选择 `miniprogram` 目录（AppID 可选「测试号」）。
2. 详情 → 本地设置 → 勾选 **不校验合法域名**（否则无法访问 `http://localhost:8080`）。
3. 编译后进入首页 → 「立即登录」，可选用**微信一键登录**或**账号密码登录**（没有账号可「立即注册」，注册成功自动登录）。
4. 微信用户可在「我的 → 账号与安全」设置账号密码，或绑定手机号；账号密码用户可在此修改密码。
5. 登录后进入「学生认证」提交资料；管理员在「学生认证审核」通过后，「发布闲置」即可进入发布页（含图片上传、分类选择、价格校验、额度提示）。
6. 发布后到管理端「商品审核」通过，商品出现在公开列表，可在小程序「我的发布」中编辑 / 下架 / 删除。
7. 交易链路：商品详情点「我想要」→ 确认订单（选收货地址、填留言）→ 提交下单 → 订单详情页点「立即支付」（模拟支付，1 秒 loading）→ 卖家在「我卖出的」发货 → 买家「确认收货」→ 订单完成。待付款 30 分钟未支付会被定时任务自动取消，商品恢复上架。
8. 消息链路：商品详情点「联系卖家」→ 自动创建会话并带上商品卡片 → 聊天页可发文本 / 图片 / 商品卡片 / 订单卡片，对方在线实时收到（气泡位置、未读角标、已读回执、"正在输入"）→ 订单详情页「联系卖家 / 联系买家」进入同一会话 → 底部 tabBar「消息」显示未读总数角标（超 99 显示 `99+`）→ 订单状态变更、商品审核、学生认证、举报处理都会自动生成系统通知，点通知详情可按 `target` 跳到对应订单或商品。
9. 客服链路：管理端「客服工作台」左侧是会话列表（带未读数、关联商品，可按昵称搜索），右侧只读展示聊天记录，底部固定「只读监控：客服不能主动发送消息」；「通知管理」左栏发布公告（全体或指定用户 ID），右栏按发布批次聚合展示接收数 / 已读数，可整组删除。
10. 评价链路：订单完成（确认收货）后订单详情页出现「评价」按钮 → 评价页按方向动态给出维度（买家评卖家三项：描述相符 / 发货速度 / 沟通态度；卖家评买家两项：付款速度 / 沟通态度），可打星、写 ≤500 字文字、传 ≤3 张图、切换匿名 → 提交成功提示「评价成功，信誉分+2」并立即更新对方信誉分 → 交易完成 7 天内可「追评」一次（追评不重新打分，只追加文字与图片）→ 卖家可在评价下方回复一次。商品详情页展示「平均分 + 好评率 + 最新评价」，个人中心「待评价」显示角标，信誉分可在管理端「信誉分管理」查看分数、等级（五色）与全部变动流水。
11. 个人中心链路：底部「我的」进入个人中心，橙色渐变卡展示头像、昵称、角色标签、信誉分大数字与等级（一次聚合接口返回，不会分块加载）→ 统计行「关注 / 粉丝 / 收藏 / 发布 / 已售」可点击跳转对应列表 → 「我的订单」5 个状态图标带角标（待评价角标是"还没评价"的数，点进去是待评价页）→ 功能宫格 10 项（我的发布 / 我的收藏 / 我的关注 / 地址管理 / 账号与安全 / 待评价 / 学生认证 / 联系客服 / 关于我们 / 设置）→ 点右上角「编辑资料」可改头像、昵称（≤20 字）、性别、个人简介（≤100 字）→ 商品详情点头像或关注列表点头像可进入**卖家主页**（免登录可看，含在售商品瀑布流与收到的评价，底部可关注或私信）→ 「设置」里可清除缓存（保留登录态）、查看版本号、退出登录、注销账号（需输入密码并确认口令「注销账号」，注销后账号软删除、在架商品全部下架）。
12. 首页配置链路：管理端「首页配置」一页 5 个 Tab —— 轮播图（按住封面拖拽排序，松手即保存，支持四种跳转类型与生效时间）、公告配置（多行文字 + 开关 + 链接，右侧手机预览带滚动效果）、金刚区配置（左侧拖拽排序、开关控制是否上首页，右侧 4 列网格预览）、热门推荐（手动选品：搜索商品加入已选、拖动调整顺序、最多 20 件；或按热度自动取 N 件）、新品推荐（取最新 N 件）。任意一项保存后立即清 `home:index` 缓存，小程序下拉刷新即可看到效果。

> `wechat.mock=true`（默认）时后端不会调用真实微信接口：`wx.login` 的 code 直接映射为模拟 openid，手机号根据 code 派生。切换到真实环境需配置 `WECHAT_APP_ID` / `WECHAT_APP_SECRET` 并设 `WECHAT_MOCK=false`。

> 订单超时参数可用环境变量覆盖：`ORDER_PAY_TIMEOUT_MINUTES`（默认 30 分钟）、`ORDER_TIMEOUT_SCAN_MS`（默认 300000 毫秒）。联调退款与超时时建议设成 `ORDER_PAY_TIMEOUT_MINUTES=2 ORDER_TIMEOUT_SCAN_MS=10000`，不必等半小时。

> **聊天联调**：微信开发者工具需勾选「不校验合法域名」，且 WebSocket 走 `ws://`（非 `wss://`），否则真机预览会被拦截；验证实时推送建议用两个不同账号（手机 + 模拟器，或两个开发者工具窗口）。若长连接建立失败，小程序会自动降级为 10 秒轮询未读数，聊天页仍可发送（走 REST）与下拉刷新拉历史。token 只在握手时校验一次，已建立的连接不会因 token 到期被踢；但到期后重连会握手失败，最终降级为轮询并提示重新登录。

---

## 4. 默认账号

| 账号 | 密码 | 角色 | 登录入口 |
|---|---|---|---|
| admin | 123456 | 管理员 | 管理端（全权） |
| service01 | 123456 | 客服 | 管理端（**只读**） |
| service02 | 123456 | 客服 | 管理端（**只读**） |
| 微信登录用户 | — | 普通用户 | 小程序 |
| 自行注册账号 | 自定义 | 普通用户 | 小程序（账号密码登录） |

首次登录后请在右上角头像菜单「修改密码」中更换初始密码（小程序在「我的 → 账号与安全」）。

---

## 5. 端到端测试

后端启动后可一键回归：

```bash
python tests/smoke_test.py        # 模块1：22 个用例
python tests/smoke_module2.py     # 模块2：81 个用例
python tests/smoke_module3.py     # 模块3：54 个用例（需 --app.view-count.flush-interval-ms=2000 才能验证浏览量回写）
python tests/smoke_module3_5.py   # 模块3.5：64 个用例
python tests/smoke_module4.py     # 模块4：72 个用例
python tests/smoke_module5.py     # 模块5：87 个用例（需 --app.order.pay-timeout-minutes=2 --app.order.timeout-scan-ms=10000）
python tests/smoke_module6.py     # 模块6：108 个用例（同上两个订单参数，通知触发依赖订单流转）
python tests/smoke_module7.py     # 模块7：90 个用例（同上两个订单参数，追评超期校验依赖改库改完成时间）
python tests/smoke_module8.py     # 模块8：89 个用例（同上两个订单参数，商品下架联动与角色变更联动依赖完整链路）
python tests/smoke_module9.py     # 模块9：117 个用例（同样需要上述参数，举报处理会联动订单/评价链路）
python tests/smoke_module10.py    # 模块10：145 个用例（缓存命中/失效断言需 Redis 可连；DB 对账需本机 mysql 客户端）
python tests/smoke_module11.py    # 模块11：104 个用例（过滤联动需 Redis 与 mysql 客户端；会临时造商品/订单/会话并在结束时清理）
python tests/smoke_module12.py    # 模块12：84 个用例（配置校验分支/批量排序/首页缓存断言需 Redis；DB 对账需 mysql 客户端）

# WebSocket 协议（需要后端在同一台机器上跑，用的是 Node 原生 ws 客户端）
node tests/ws_check.cjs           # 模块6：26 个用例（握手鉴权 / 实时推送 / 已读回执 / 心跳 / 离线拉取）

# 小程序页面逻辑（不需要后端，纯 JS 驱动 Page 实例）
node tests/mp_page_check.cjs      # 模块3/4 页面
node tests/mp_order_check.cjs     # 模块5 订单/地址页：44 个用例
node tests/mp_chat_check.cjs      # 模块6 消息/聊天/通知页：56 个用例
node tests/mp_review_check.cjs    # 模块7 评价/追评/评价列表/待评价页：65 个用例
node tests/mp_profile_check.cjs   # 模块8 个人中心/编辑/卖家主页/设置/安全页：80 个用例
node tests/mp_report_check.cjs    # 模块9 举报/我的举报页与 4 处入口：84 个用例
node tests/mp_home_check.cjs      # 模块12 首页：22 个用例（公告关闭、linkType 分派、金刚区补「更多」）

# 管理端截图（需要后端与管理端都在运行）
python tests/seed_module8_demo.py            # 播种演示数据（普通/认证学生/客服/管理员/已封禁）
python tests/seed_module8_demo.py --clean    # 清理演示数据
python tests/shot_admin.py --out docs/screenshots/module8 --token <JWT> \
    --user-info '{"role":"admin","roleLabel":"管理员","username":"admin"}' \
    --url http://127.0.0.1:5173 --shots "user-list=user:3500"

python tests/seed_module9_demo.py            # 播种举报演示数据（3 类 × 4 态共 6 条）
python tests/seed_module9_demo.py --clean    # 清理（含 sys_operation_log）
python tests/shot_admin.py --out docs/screenshots/module9 --token <JWT> \
    --user-info '{"role":"admin","roleLabel":"管理员","username":"admin"}' \
    --url http://127.0.0.1:5173 --shots "report-list=report:5000,operation-log=operation-log:4500"

python tests/shot_admin.py --out docs/screenshots/module10 --token <JWT> \
    --user-info '{"role":"admin","roleLabel":"管理员","username":"admin"}' \
    --url http://localhost:5173 --shots "dashboard-full=dashboard:5000"

python tests/seed_module11_demo.py                 # 先造「待人工复核」商品，否则该 Tab 是空的
python tests/shot_admin.py --out docs/screenshots/module11 --token <JWT> \
    --user-info '{"role":"admin","roleLabel":"管理员","username":"admin"}' \
    --url http://localhost:5173 \
    --shots "sensitive-word=system/sensitive-word:4500,account=system/account:3500,product-audit-review=product/audit?tab=review:4000"
python tests/seed_module11_demo.py --clean         # 截图后清理演示数据

python tests/seed_module12_demo.py                 # 造 6 件带彩色封面的演示商品（热门/新品区块才不是空的）
python tests/seed_module12_demo.py --tidy          # 收起冒烟测试遗留的「额度测试商品NN」在售空壳商品
python tests/shot_admin.py --out docs/screenshots/module12 --token <JWT> \
    --user-info '{"role":"admin","roleLabel":"管理员","username":"admin"}' \
    --url http://localhost:5173 \
    --shots "home-banner=home?tab=banner:4000,home-notice=home?tab=notice:3500,home-category=home?tab=category:3500,home-hot=home?tab=hot:4000,home-new=home?tab=new:3500"
# 注意：本机 Vite 只监听 IPv6 回环 [::1]:5173，用 127.0.0.1 会连不上，务必写 localhost
```

> 截图的 JWT 要**现取现用**：同一账号重新登录会让上一个 token 下线，
> 先用旧 token 登录取新 token、再拿旧 token 去截图，截出来的是登录页而不是业务页。

最近一次执行结果：**模块 1 通过 22/22，模块 2 通过 81/81，模块 3 通过 54/54，模块 3.5 通过 64/64，模块 4 通过 72/72，模块 5 通过 87/87，模块 6 通过 108/108，模块 7 通过 90/90，模块 8 通过 89/89，模块 9 通过 117/117，模块 10 通过 145/145，模块 11 通过 105/105，模块 12 通过 84/84；WebSocket 协议 26/26 通过；小程序页面 32/32、订单/地址页 44/44、消息/聊天/通知页 56/56、评价相关页 65/65、个人中心相关页 80/80、举报相关页 84/84、首页 22/22 通过**；后端规范自检 234 项通过 / 0 失败 / 1 警告。

模块 10 覆盖：未登录 5 个接口全 401、普通用户全 403、客服 5 个接口全 200 而刷新接口 403、普通用户刷新 403；summary 的 22 个字段齐全且非 null、在售 ≤ 总数、已完成 ≤ 总订单、今日 GMV ≤ 累计 GMV、平均信誉分在 0~150 且带等级与配色、`statTime` 格式正确、**待审核商品数与 `SELECT COUNT(*) WHERE status=0 AND deleted=0` 对账一致**；trend 不传默认 7 天、`days=30` 返回 30 个点、`days=0/1/99999/-5` 全部收敛为 7、`days=abc` 返回 400 而非静默兜底、趋势日期连续且最后一天是今天、无数据日补零为数值 0、**趋势今日新增用户数 == summary 的 `newUsersToday` == 数据库当日新增**；distribution 六组全为数组、角色固定 4 档 / 信誉分固定 5 档且区间顺序正确 / 星级固定 5 档、角色合计 == 总用户数、信誉分合计 == 总用户数、商品状态合计 == 总商品数、每档都有中文标签；ranking 三张榜数量不超上限、卖家榜按成交额倒序且信誉分合法、热门商品按浏览量倒序且价格为正、热门分类按商品数倒序、字段齐全；recent 不超过 20 条、按时间倒序、模块与动作中文名非空；缓存 6 个 key 全部生成且 TTL 符合 300/300/600/600/1800/30 分级、**`sleep(1.2s)` 后二次请求 `statTime` 不变（证明命中缓存而非重算）**、刷新接口清空缓存、**商品审核后 summary 与 ranking 缓存被清空**、改角色后 summary 缓存被清空、缓存缺失时接口降级查库仍 200 且指标完整；最后做 summary 与 distribution 的四项一致性对账与测试数据清理。

模块 10 的业务要点（详见 `docs/api-module10.md`）：不新建表，全部指标聚合自既有 9 个模块，因为新增 `stat_daily` 预聚合表会让「统计准不准」退化成「定时任务跑没跑」；22 个指标用一条 SQL 的 22 个标量子查询并列算出，首页 5 类数据从 5 次往返降为 1 次；趋势 5 个维度 `UNION ALL` 成 `(metric, dt, val)` 窄表，一次传输后在应用层横向拼装；三张榜全部 `ORDER BY + LIMIT` 在库内截断，Java 端不做全量排序，卖家榜把商品数与成交额各自放在子查询里先聚合再 JOIN，避免 JOIN 扇出把成交额重复累加；按天 `GROUP BY` 只返回有数据的日期、应用层补零，前端拿到的数组长度恒等于 days，补零逻辑不散到每个页面；`GROUP BY` 必须写与 `SELECT` 完全相同的表达式，MySQL 8 的 `only_full_group_by` 既不认 `DATE()` 也不认 SELECT 别名；口径集中定义（GMV 只算 `status=3` 且按 `finish_time` 归集、pendingReports 取 `status IN (0,1)`、activeSessions 取近 24h 有消息的会话而非 WebSocket 内存态、热门商品只取 `status IN (1,3)`、卖家榜排除管理员与客服），避免「首页说 100 个用户、列表页数出 98 个」；分级缓存按「变化频率 × 查询代价」定 30s~1800s 五档，缓存最终 VO 使二次请求完全不碰数据库；失效用「删除」而不是「更新」（删除幂等，缓存上做加减在并发下必错），并用 `TransactionSynchronizationManager` 推迟到 `afterCommit`，否则事务内先删缓存、提交前有读请求进来会把未提交数据回填，回滚的变更就被缓存 5~30 分钟；Redis 异常一律降级查库，缓存是优化不是功能依赖；把缓存能力抽成只依赖 Redis + Jackson 的独立服务，打破「商品审核要清仪表盘缓存、仪表盘要读商品表」的循环依赖；`days` 只接受 7/30，防止 `days=99999` 一个 GET 请求扫一年数据。

模块 12 覆盖：未登录 `/api/home` 与 `/api/home/index` 均 200 且内容一致、聚合返回 6 个字段齐全、公告为对象而非 null、轮播图只返回启用中且在展示期内的、金刚区只返回 `is_home_show=1` 且按 sort 升序；轮播图 6 个管理接口的权限（客服只读 / 写操作 403 / 未登录 401）、四种跳转类型校验（商品ID与分类ID必须正整数、外链必须 http(s)、类型 4 清空 linkValue、类型越界 400）、`endTime` 早于 `startTime` 被拒、启停用与删除后 `home:index` 缓存被删；批量排序一次提交后 sort 落库且全量生效、含已删除 ID 返回 400；配置读写 7 项字段、公告超 100 字 400、非法链接 400、`hotMode` 非法 400、手动模式未选品 400、手动选品超 20 件 400、`hotCount`/`newCount` 越界收敛到 1-20；手动选品按配置顺序返回、`IN` 查询顺序被纠正、选品全部下架后回落热度榜、部分下架时少推；金刚区保存后首页顺序改变、关闭的分类不出现、未参与本次提交的分类不受影响、含已删除分类返回 400；缓存 TTL ≤ 300 且二次请求命中、四类轮播图写操作与配置保存、金刚区保存后缓存被删、Redis 键缺失时接口降级查库仍 200；操作日志覆盖首页模块的 create/update/status/sort/delete；最后清理测试数据并 `POST /api/dashboard/refresh` 复原统计缓存。

模块 12 的业务要点（详见 `docs/api-module12.md`）：一句话贯穿整个模块——**配置决定「推什么」，不决定「什么能上首页」**。运营可以选品、选数量、排顺序，但「只有已上架商品能出现」写死在代码里，不开放给配置，否则迟早有人把一件待审核商品推到首页头条。表用 KV（`home_config`），因为首页可配置项还会继续加，每加一项就 `ALTER TABLE` 一次在答辩阶段能接受，真上线就是「每次运营提需求都要发一次版」；但**接口不是 KV**，给前端的仍是 7 个结构化字段，让前端拼 `config_key` 等于把数据库设计暴露成接口契约，KV 的唯一好处也就没了。默认值双保险（SQL 预置 + `HomeConfigKey.defaultValue()`），表被清空、脚本没跑、误删一行，首页都要照常能打开——首页打不开是这个系统里最贵的事故，严重程度远高于「公告文案不对」；读配置做防御性收敛（数量截断 1-20、非法模式回落 auto、坏 JSON 当空数组），代价十几行 `if`，收益是配置被写坏也不会 500。手动选品允许「选了但商品已下架」：按已上架过滤、部分失效少推几件、全部失效回落热度榜并 `log.warn`，因为首页开一个空白区块是明确的 bug，推荐策略退化只是体验下降；判定依据是「**结果为空**」而不是「配置为空」；`WHERE id IN (...)` 不保证顺序，用 `LinkedHashMap` 按传入 ID 重排，否则运营排第一的商品会变成随机一件，而这种 bug 只在小数据量偶发、看起来像「排序没保存成功」。公告要**开关打开且文案非空**才展示，运营清空文案忘了关开关很常见，只看开关首页就会出现一条空白公告条。缓存整份 `HomeIndexVO` 存成单个 key `home:index`（TTL 300s），拆多个 key 只会让失效逻辑变复杂；运营的写操作主动失效不受 TTL 影响；失效推迟到 `afterCommit`，事务回滚就不该清缓存；抽独立 `HomeCacheService`（只依赖 Redis + Jackson）打破「轮播图/配置/金刚区要清首页缓存、首页又要读这三者」的循环依赖。商品写操作的 8 处失效收拢成 `evictProductCaches()`，新增一种缓存时改一行就够，漏掉一处就是一个只在特定操作后复现的脏数据。批量排序一次事务全量覆盖、先 `count` 校验 ID 存在，避免「前端循环发 N 个请求、第 3 个失败后一半新一半旧」，也避免静默跳过让「保存成功」变成假话。路径上保留 `/api/banner` 与 `/api/home/index` 两个别名：老路径出现在已交付文档和已发版的小程序里，为了目录整齐全改掉，收益只是好看，风险是某个没被搜到的调用方悄悄 404。

模块 11 覆盖：未登录访问敏感词列表 401、普通用户 403、客服可读 200 而新增 403、客服访问账号管理 403、未登录访问账号管理 401；预置词 ≥20 条且违禁品/广告两类存在、类型筛选只返回该类型、关键词筛选命中、词条带类型与等级中文名；新增/重复新增被拒/空词 400/超长 400/非法类型 400/非法等级 400、编辑落库且改成已存在词被拒、编辑不存在返回 404、禁用后掉出生效词表且重复禁用被拒、重新启用回到词表、**删除为物理删除（行消失、重复删除 404、同名可重新添加）**；批量导入「识别 4 / 新增 3 / 重复 1」三者口径一致且回显重复明细、空文本 400、**超长词计入非法并回显而不是整批报错**；商品发布命中拦截级词被拒且不落库、提示含命中词、拦截时给用户发通知；命中警告级词可发布但 `need_manual_review=1` 且 `sensitive_hit` 记录命中词、**「走私  名表」「走*私名表」等变体仍被拦截**、干净内容不被标记；**编辑商品改成违禁词同样被拒**（防「先发干净商品再改标题」绕过）；审核页 `status=0&needManualReview=1` 能筛出该商品并返回命中词、审核通过后标记清零而命中词保留；评价命中「傻逼」被拒且不落库、命中「加微信」可提交；聊天命中拦截级词整条照发但词被替换为 `**`、警告级词原样发送；账号列表只含 `admin`/`customer_service` 且当前登录账号 `self=true`、重复账号被拒、账号过短/密码过短/非法角色均 400、昵称编辑落库、**不能改自己角色 / 不能禁自己 / 不能删自己 / 不能重置自己密码 / 不能通过账号接口改普通用户**；重置他人密码后新密码可登录且旧密码失效、禁用后无法登录、重新启用可登录、角色可在管理员与客服间互转；新建第二个管理员后可删除、**删除为逻辑删除且账号名改为 `name#del{id}`、原账号名可重新创建**；操作日志覆盖敏感词的 create/update/status/batch_import/delete 与账号的 create/update/status/reset_password/delete。

模块 11 的业务要点（详见 `docs/api-module11.md`）：敏感词只有两级而不用分值——分值必须配「累计多少分才拦」的阈值，阈值一旦可配，运营就会去调它，调完之后「为什么这条能发那条不能发」就说不清了；两级语义与用户侧结果一一对应（发得出去 / 发不出去），标记只影响审核侧，用户侧无感。**三个场景的处置刻意不同**：商品放行并打标记（审核队列本来就是人工闸门，多带一个标记成本很低）、评价对警告级放行（「我们加了微信沟通」是正常叙述，拦掉显得平台不让人说话）、聊天只替换不整条拒绝（消息被吞，发送者只会以为网络坏了反复重发，投诉量远大于收益）。检测入口只留一个 `check(String...)`，否则迟早出现「商品页拦了、聊天页漏了」；**编辑路径必须一起检**，否则「先发一个正常商品通过审核、再编辑把标题改成违禁内容」比发布更隐蔽且更快（不涉及图片上传，一个脚本几秒能改一遍全店标题）。匹配前做归一化（全角转半角、去零宽字符、去空白与英文符号、转小写）才能拦住「加*微信 / 加 微 信」，但**保留中文标点**——中文标点承担断句语义，去掉后会产生跨句拼接的误判；误判的代价被「警告级只标记不拒绝」压到最低。性能上不放 Redis：检测发生在全站最高频的三条写路径上，每条消息都取一次 20KB 词表会把网络开销压到 RT 上，且 Redis 抖动会让聊天不可用；词表是「读极多、写极少」的配置数据，放进堆内存最合适。并发用 `volatile` 不可变快照 + 写时整体替换，读线程要么看到旧快照要么看到新快照，永远不会看到「改了一半」；刷新推迟到 `afterCommit`，否则事务内刷新会读到未提交数据，回滚后内存里就留下一个数据库里不存在的词；启动加载失败降级为「不过滤」而不是启动即崩（表还没建、多实例启动早于 SQL 执行都不该让应用起不来）。批量导入按行去重后用一次 `IN` 查已存在（1000 行逐条 `selectCount` 就是 1000 次往返）、超长词跳过并回显而不是截断入库（截断后的词永远匹配不到，属于「看起来加了其实没用」）、并发撞唯一索引时降级逐条插入（批量语句失败时整批不生效，直接回报「全部重复」是错的）。账号管理大半代码不是 CRUD 而是护栏：**判断「至少保留一个可用管理员」必须同时含 `role=admin` 与 `status=0`**，只数 `role=admin` 会把已禁用的管理员算进去，结果是「有两个管理员但都进不去」；不能重置自己的密码，是因为自助改密要校验旧密码，绕过它等于把改密的前提拿掉；**删除时必须先把账号名改成 `name#del{id}` 再逻辑删除**，否则 `uk_username` 被已删行永久占用、「删了重建同名账号」会直接撞唯一键，而界面上这个账号已经不存在了，运维根本想不到是索引在挡——两步的顺序也不能反，先删的话这行在 MP 视角里已经不存在，`updateById` 会带上 `deleted=0` 的 where 条件更新 0 行。
模块 9 覆盖：未登录提交举报 401、类型/原因非法、举报自己被拒、对象不存在返回 400（而不是 500）、描述少于 5 字被拒、24 小时内重复举报同一对象被节流、证据图超过 3 张显式拒绝、3 张证据图全部落库、我的举报分页与倒序、查看他人举报返回 404、普通用户访问管理端列表 403 而客服可读、按状态/关键词/类型筛选、**管理端列表的类型标签/状态标签/对象标题/封面/快照全部非空**、接单状态流转 0→1 并记录处理人与 `accept_time`、重复接单被拒、管理员可跳过接单直接处理、终态不能接单也不能重复处理、商品举报成立后商品下架 + 卖家信誉分 -10 + 通知卖家 + 信誉流水关联 + 不牵连同卖家其他商品、用户举报成立后封禁 + -10 分 + 名下商品同步下架 + 主页 403 + 不牵连无关用户、评价举报成立后删除评价 + 评价人 -5 分 + 已删评价不能再被举报 + 快照 `images` 解析为数组、动作与举报类型不匹配被拒、驳回带动作被拒、**成立却不带动作被拒**、驳回不扣分不封禁、处理结果为空被 DTO 拦下、举报不存在返回 404、操作日志普通用户 403 / 客服只读 / 接单与处理各写一条 / 昵称与角色与 IP 完整 / detail 可读 / 按模块与动作筛选 / 无删除接口、统计的总数/待处理数/处理率/今日新增/平均时长，以及测试数据清理。

模块 9 的业务要点（详见 `docs/api-module9.md`）：单表 `report_type` 承载三类举报而不是拆三张表，因为举报是一次性事件、拆表会让客服列表变成三表 `UNION` 再套分页；`target_snapshot` 在提交瞬间固化对象形态，解决「商品已下架 / 评价已删 / 卖家先下架再被举报」三种查不到原对象的场景；接单用 `WHERE id=? AND status=0` 条件更新防并发抢单；`accept_time` 与 `create_time` 分离，平均处理时长只统计客服实际耗时才不至于逼客服抢单；处理率含驳回，否则认真驳回的客服指标反而更低；处置动作与举报类型强绑定，避免「商品图文不符」被判成封禁整个店铺；封禁与删评价复用既有 Service 而非重写联动，避免漏掉清缓存这类副作用；通知失败不回滚处置，否则等于给攻击者留一条「写满通知表就能让封禁失效」的绕过路径；操作日志的不可篡改靠接口层只有一个 GET 方法，而不是靠前端隐藏按钮。

模块 8 覆盖：未登录访问个人中心 401、聚合接口返回全部必需字段且统计数与实际数据一致、卖家主页免登录可看且软登录返回关注状态与 isSelf、卖家商品列表只返回已上架商品、访问不存在的用户返回业务错误、修改资料的昵称/简介长度与性别合法性校验、空更新被拒、改完立即生效、手机号授权码换取与「相同手机号」「手机号已被占用」两种拒绝、管理端列表按昵称/角色/认证状态/信誉分区间筛选、列表返回学号与发布数、管理端详情含信誉分流水与商品/订单/评价三类记录、客服的三个写操作全部 403 而只读列表放行、管理员改资料对用户可见、封禁后其商品全部下架且主页不可见、重复设置相同状态被拒、非法角色被拒、角色降级后认证状态与发布额度被回收、恢复学生角色后额度还原为 20、认证审核通过后个人中心显示已认证、注销的三重确认（口令错 / 密码错 / 管理员拒绝 / 未设密码提示先设密码）与成功注销后的软删除与商品下架与 token 失效、退出登录后 token 失效、测试数据清理。

模块 7 覆盖：未登录提交评价与查看待评价 401、客服提交/删除/调分 403 与只读列表放行、未完成订单不能评价、非订单参与方不能评价、评价方向与角色不符被拒、维度缺失被拒、星级越界被拒、内容超 500 字被拒、买家评卖家与卖家评买家的维度校验与综合星级算法、5 星 +2 / 4 星 +1 / 3 星 0 的信誉分增减与流水快照（before/after）、重复评价被拒、追评（本人 / 空内容 / 重复追评 / 超 7 天窗口被拒 / 追评不重复加分）、卖家回复（越权 / 方向不符 / 重复回复）、匿名评价在用户端脱敏而在管理端保留真实昵称、商品评价按星级区间筛选与统计、用户评价列表、管理端按昵称/标题/有追评筛选与无匹配返回空页、管理员删除违规评价（不回退分数但写 type=4 审计流水）、管理员调分（0 分被拒 / 超 ±100 被拒 / 原因必填 / 边界截断 / 边界再改被拒 / 流水记实际生效变动）、等级与进度展示、DB 落库校验与测试数据清理。

模块 6 覆盖：未登录 401 与客服写操作 403、会话创建幂等（重复点「联系卖家」只生成一个会话）与角色反转（卖家从订单侧发起不产生反向会话）、首次联系自动带商品卡片、发消息与未读累加、进入会话即已读、图片消息、商品/订单卡片归属校验（防伪造卡片内容）、系统消息禁止客户端发送、非参与者访问统一返回「会话不存在」、游标分页翻页无重复、会话详情首屏 50 条 + `hasMore`、单向删除后对方仍可见且新消息让会话回到列表、订单六个状态与退款全流程的通知触发（含 `relatedNo` 与 `target` 跳转目标）、商品审核与学生认证通知、通知列表未读优先 + `onlyUnread` + 单条已读 + 越权查看、全部已读后角标归零、公告全体发布与定向发布、标题/内容长度校验、按批次聚合展示与整组删除、客服工作台按昵称搜索与聊天记录（`self` 恒 false、`fromBuyer` 区分左右）、以及全部写操作落库后的 DB 校验。

WebSocket 用例覆盖：错误 token 拒绝握手、连接建立即收一帧 `unread`、实时 `message` 帧（内容与会话 ID 正确、接收方视角 `self=false`）、未读数变化帧、`read` 上行帧触发对方收到已读回执、`typing` 转发、`ping/pong`、新通知的 `notice` 帧、对方离线时消息落库且重连后能拉取到。

模块 5 覆盖：未登录下单 401、客服写操作 403 与只读放行、地址 CRUD 与「默认地址唯一」、地址上限与越权删除、下单参数校验（不存在/自己买自己/非在售/已占用/他人地址）、商品快照隔离（直接改库改价后历史订单金额不变）、待付款取消与商品恢复上架、支付成功商品置已售出、支付重复提交被状态 CAS 拒绝、并发抢占商品只成功一单、卖家发货与买家收货、退款全流程（申请→卖家拒绝→再申请→买家撤销→再申请→卖家同意→商品恢复上架）、退款原因长度校验、管理端按订单号/买家/卖家/状态/时间范围筛选、各状态计数、强制取消与强制退款仅管理员可用、订单日志的先后顺序与操作者角色、超时自动取消（改 `pay_deadline` 后轮询验证）。

模块 4 覆盖：未登录收藏/关注 401、客服写操作 403 与只读放行、收藏切换与商品/用户两处计数联动、唯一索引与物理删除、Redis 收藏态判断、我的收藏游标分页不重复、已下架商品保留并带状态蒙层、关注切换与粉丝数、禁止自关注、关注/粉丝列表的在售数与回关状态、卖家本人不显示关注按钮、个人中心三项统计与清理后计数归零。

模块 3.5 覆盖：预置账号登录（admin / service01 / service02）、错误密码、普通用户登录管理端被拒、用户名唯一性校验、注册与重复注册、账号登录、个人账号信息、客服 GET 全部放行与 POST/PUT/DELETE 一律 403、客服白名单（绑定账号 / 改密）、管理员增删改查放行、微信用户绑定账号并登录、修改密码后旧密码失效、退出登录后 token 失效，以及管理端 `v-permission` / `meta.roles` / 只读提示条与小程序登录注册页的静态校验。

模块 3 覆盖：首页聚合字段完整性、分类/热词、未登录与普通用户越权、关键词/价格区间/分类筛选与四种排序、游标分页不重复、热门商品加权与缓存、浏览量 Redis 计数（含卖家不计数、定时回写 MySQL）、轮播图增删改查、跳转类型校验、定时上下架、拖拽排序后的 sort 顺序、下架商品使热门缓存失效。

模块 2 覆盖：分类列表免登录、普通用户发布被拒 403、参数校验（图片数量/标题长度/价格/分类）、发布与主图、待审核可见性、驳回原因必填、审核通过、编辑后重新提审、浏览计数、分类/关键词/价格筛选与排序、越权编辑、卖家下架、强制下架、分类增删改查与禁用、20 件发布额度、删除商品。

> 测试脚本都会写入测试数据，回归后可执行以下 SQL 清空（保留三个后台账号与预置分类）：
> `DELETE FROM sys_operation_log; DELETE FROM report; DELETE FROM credit_log; DELETE FROM review; DELETE FROM chat_message; DELETE FROM chat_session; DELETE FROM notice; DELETE FROM order_log; DELETE FROM trade_order; DELETE FROM address; DELETE FROM favorite; DELETE FROM follow; DELETE FROM product_image; DELETE FROM product; DELETE FROM banner; DELETE FROM student_auth; DELETE FROM sys_user WHERE open_id IS NOT NULL OR username LIKE 'smoke%';`
> 数据库与 Redis 计数可能不同步，清理后建议 `redis-cli FLUSHDB`（同时会清掉登录态与未读缓存，需重新登录）。
> 首页配置想恢复默认：`DELETE FROM home_config;` 后重跑 `sql/V12__home_config.sql`，或直接留着——`HomeConfigKey.defaultValue()` 会为缺行兜底，首页照常能打开。

**演示数据（答辩前执行一次）**

```bash
# 一键重置演示数据：12 个用户 / 40 件商品 / 26 笔订单 / 18 条评价 / 63 条收藏 /
# 17 条关注 / 5 组聊天（32 条消息）/ 5 条举报 / 3 张轮播图 / 1 条公告
mysql -uroot -p --default-character-set=utf8mb4 campus_market < backend/sql/init_demo_data.sql
```

脚本可重复执行（每次先按固定 ID 段 9001+ 清理再插入），末尾自动输出各表统计与 9 项一致性校验（结果应全为 0）。
演示账号统一密码 `123456`，可直接登录：`linxiaoman`（林小满，普通用户）、`wangzihan`（王梓涵，认证学生）。
商品图片与头像在 `backend/uploads/demo/`（6 张彩色封面 + 12 张头像，代码生成，无版权问题）。
⚠️ 该脚本会**清空所有业务数据**（商品/订单/评价/聊天/举报/收藏/关注/地址/学生认证/轮播图），只保留 3 个后台账号、7 个分类、20 条敏感词与首页配置，仅用于演示与开发环境。
跑完冒烟测试后想恢复干净的演示数据，再执行一次即可。

---

## 6. 常见问题

**1）`mvn compile` 报 `找不到符号 getXxx()` / `找不到符号 变量 log`**

Maven 实际使用的是 JDK 26（`mvn -v` 可确认），而 Spring Boot 3.2.5 托管的 Lombok 1.18.30 最高只支持到 JDK 21。
JDK 23 起 javac 又不再隐式启用 classpath 上的注解处理器，于是 Lombok 被整体跳过，
所有 `@Data` 生成的 getter/setter 与 `@Slf4j` 生成的 log 全部不存在。
解决：构建前把 `JAVA_HOME` 指向 JDK 17。

```bash
set JAVA_HOME=D:\jdk-17.0.6     # Windows CMD
export JAVA_HOME=/d/jdk-17.0.6  # Git Bash
```

> 顺带一提：如果一定要用高版本 JDK 构建，需要在 `pom.xml` 里显式声明
> `maven-compiler-plugin` 的 `annotationProcessorPaths`（指向 lombok），
> 否则 `@Data` / `@Slf4j` 依旧不生效。本项目按 JDK 17 构建，未加该配置。

**2）启动报 `Port xxxxx was already in use`**

系统环境变量中存在 `SERVER__PORT`/`SERVER_PORT`，Spring 的松散绑定会覆盖 `application.yml` 的 8080。启动时显式指定端口即可：

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8080
```

**3）登录提示「登录已失效，请重新登录」**

token 白名单存在 Redis。Redis 未启动、被清空或换库都会导致全部登录态失效，重启 Redis 后重新登录即可。

**4）管理端上传的学生证图片显示 404**

开发期图片通过 `vite.config.js` 的 `/uploads` 代理到后端，确认后端已启动且 `backend/uploads/` 目录下存在文件。

---

## 7. 开发约定（详见 docs/project-profile.md）

- 接口路径 `/api/{模块}/{操作}`，动词由 HTTP 方法表达
- 统一返回 `Result<T>`，成功 `code=200`，失败 `code=500`，未登录 401，无权限 403
- 数据库小写下划线，Java 驼峰，MyBatis-Plus 自动映射
- 每个接口必须有 `@Tag` / `@Operation` 注解，缺注解视为未完成
- 前端所有请求集中在 `src/api/`，页面不直接使用 axios
- 一次只开发一个模块，走完「建表 → 实体/Mapper → Service → Controller → 前端 → 测试」六步并确认后再继续
