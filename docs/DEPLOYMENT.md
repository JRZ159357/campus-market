# 校园二手交易平台 · 部署文档

> 适用版本：v1.0.0（12 个功能模块全部完成）
> 适用场景：毕业设计答辩演示（Windows 单机）、生产环境部署（Linux + Nginx + Docker）
> 文档中「环境要求」「部署步骤」「常见故障排查」三章可直接引用到论文「系统部署」章节。

---

## 目录

- [一、环境要求](#一环境要求)
- [二、环境变量配置（重点，踩坑记录）](#二环境变量配置重点踩坑记录)
- [三、部署步骤（从零开始）](#三部署步骤从零开始)
- [四、端口占用清单](#四端口占用清单)
- [五、演示账号](#五演示账号)
- [六、常见故障排查（FAQ）](#六常见故障排查faq)
- [七、生产环境部署建议](#七生产环境部署建议)
- [附录 A：目录结构](#附录-a目录结构)
- [附录 B：部署验收清单](#附录-b部署验收清单)

---

## 一、环境要求

### 1.1 硬件要求

| 项目 | 最低配置 | 推荐配置 | 说明 |
|---|---|---|---|
| CPU | 4 核 | 8 核 | Maven 编译 + 前端构建 + 数据库同时运行 |
| 内存 | 8 GB | 16 GB | JVM 默认堆 + MySQL + Redis + Node 开发服务器 |
| 硬盘 | 20 GB 空闲 | 40 GB | 依赖仓库（Maven 约 300 MB、npm 约 200 MB） |
| 显示 | 1280×720 | 1920×1080 | 管理端为宽表格布局，建议 1440 以上 |

### 1.2 操作系统

| 环境 | 系统 | 用途 |
|---|---|---|
| 演示环境 | Windows 10 / 11（64 位） | 答辩现场单机演示，一键脚本已适配 |
| 生产环境 | CentOS 7+ / Ubuntu 20.04+ | Nginx + jar 部署，或 Docker Compose 编排 |

### 1.3 软件清单

| 软件 | 版本要求 | 本项目实际验证版本 | 用途 |
|---|---|---|---|
| JDK | **必须是 17.x** | 17.0.6（Oracle JDK） | 后端运行与编译 |
| Maven | 3.6+ | 3.10.0 | 后端依赖管理与启动 |
| MySQL | 8.0.x | 8.0.46 | 业务数据存储 |
| Redis | 3.2+（推荐 5.0+） | 3.2.100（Windows 版） | 登录态、浏览量缓冲、首页/看板缓存 |
| Node.js | **18+**（Vite 5 的硬性要求） | 22.22.2 | 管理端构建 |
| npm | 9+ | 10.9.7 | 管理端依赖 |
| 微信开发者工具 | 最新稳定版 | Stable | 小程序编译调试 |
| Git | 任意较新版本 | 2.55.0 | 拉取代码（可选，也可直接下载 ZIP） |

> **为什么 JDK 必须锁 17**
> 项目基于 Spring Boot 3.2.5，该版本要求 JDK 17+；同时 Lombok 通过注解处理器在编译期改写 AST，
> JDK 21 之后注解处理机制收紧，JDK 26 等更高版本会导致 Lombok 报
> `java.lang.NoSuchFieldError: Class com.sun.tools.javac.tree.JCTree$JCImport` 或
> `IllegalAccessError` 之类的编译期错误。因此**不要用系统默认的高版本 JDK**。

### 1.4 本机验证命令

```bash
java -version     # 期望：java version "17.0.6"
mvn -v            # 期望：Apache Maven 3.x，且 Java version: 17.x
mysql --version   # 期望：mysql  Ver 8.0.x
redis-cli --version
node -v           # 期望：v18 及以上
npm -v
```

> Redis 版本说明：Windows 上常见的 `redis-server.exe` 是微软归档的 **3.2.100**，本项目用到的
> 指令（`SET` / `GET` / `DEL` / `EXPIRE` / `INCR`）在该版本全部可用，实测无兼容问题。
> Linux 生产环境建议直接用 5.0+ 或 7.x。

---

## 二、环境变量配置（重点，踩坑记录）

项目里所有可变配置都支持**环境变量覆盖**，`backend/src/main/resources/application.yml` 中写的是
`${变量名:默认值}` 形式。这意味着：**换机器部署通常不需要改 yml**，只改环境变量即可。

### 2.1 JAVA_HOME —— 必须指向 JDK 17

这是本项目部署失败的第一大原因。若系统里装了多个 JDK，`mvn` 会使用 `JAVA_HOME` 指向的那个。

**Windows（临时，仅当前窗口）**

```bat
set JAVA_HOME=D:\jdk-17.0.6
set PATH=%JAVA_HOME%\bin;%PATH%
```

**Windows（永久，需重开命令行窗口生效）**

```bat
setx JAVA_HOME "D:\jdk-17.0.6" /M
```

**Linux / macOS**

```bash
export JAVA_HOME=/usr/local/jdk-17.0.6
export PATH=$JAVA_HOME/bin:$PATH
# 永久生效写入 ~/.bashrc 或 /etc/profile
```

验证：

```bash
echo %JAVA_HOME%     :: Windows
mvn -v               :: 输出中 Java version 必须是 17.x
```

### 2.2 SERVER_PORT —— 系统级环境变量会抢走端口（重点坑）

若机器上存在系统环境变量 `SERVER_PORT=60520`（某些 IDE、其他项目或系统工具会设置），
Spring Boot 的松散绑定会把它当作 `server.port`，于是你以为启动在 8080，实际在 60520，
前端 5173 的代理全部 502。

排查：

```bat
echo %SERVER_PORT%
```

两种解决方式，任选其一：

1. **启动时显式覆盖**（推荐，脚本已内置）：

   ```bat
   mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8080
   ```

   命令行参数的优先级高于环境变量，能稳定压住 `SERVER_PORT`。

2. 删除系统环境变量 `SERVER_PORT` 后重新打开命令行窗口。

> 优先级顺序（高 → 低）：命令行参数 > Java 系统属性 > 操作系统环境变量 > application.yml

### 2.3 MySQL 连接与字符集

默认连接信息（`application.yml`）：

```yaml
url: jdbc:mysql://${MYSQL_HOST:localhost}:${MYSQL_PORT:3306}/${MYSQL_DATABASE:campus_market}
     ?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
username: ${MYSQL_USERNAME:root}
password: ${MYSQL_PASSWORD:123456}
```

也就是说**默认账号 root / 123456**。如果你的 MySQL 密码不是 123456，不必改 yml，设环境变量即可：

```bat
set MYSQL_PASSWORD=你的密码
set MYSQL_HOST=localhost
set MYSQL_PORT=3306
set MYSQL_DATABASE=campus_market
set MYSQL_USERNAME=root
```

**字符集必须 utf8mb4**。建库时显式指定，否则商品标题、评价内容里的中文与 emoji 会变成 `???`：

```sql
CREATE DATABASE campus_market DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

导入 SQL 时也要带字符集参数，否则客户端用 GBK 读文件同样乱码：

```bat
mysql -uroot -p --default-character-set=utf8mb4 campus_market < sql\V1__user_auth.sql
```

验证库与表字符集：

```sql
SELECT DEFAULT_CHARACTER_SET_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='campus_market';
-- 期望：utf8mb4
```

### 2.4 Redis 连接

默认本地无密码连接 `localhost:6379`，`database: 0`。

若 Redis 设置了密码，**不要改 yml**，设环境变量：

```bat
set REDIS_HOST=localhost
set REDIS_PORT=6379
set REDIS_PASSWORD=你的密码
```

验证：

```bat
redis-cli ping
:: 期望：PONG
```

### 2.5 其他可选变量

| 变量名 | 默认值 | 说明 |
|---|---|---|
| `FILE_UPLOAD_PATH` | `./uploads` | 上传根目录，相对**启动时的工作目录**。`cd backend` 启动即落在 `backend/uploads` |
| `WECHAT_APP_ID` / `WECHAT_APP_SECRET` | 占位值 | 小程序 AppID 与密钥 |
| `WECHAT_MOCK` | `true` | `true` 时不调用真实微信接口，本地无小程序账号也能联调 |
| `ORDER_PAY_TIMEOUT_MINUTES` | `30` | 待付款保留分钟数，超时由定时任务取消 |
| `ORDER_TIMEOUT_SCAN_MS` | `300000` | 超时订单扫描间隔（5 分钟） |
| `VIEW_FLUSH_INTERVAL_MS` | `60000` | 浏览量 Redis 增量回写 MySQL 的间隔 |

### 2.6 环境变量速查表（演示机推荐配置）

```bat
set JAVA_HOME=D:\jdk-17.0.6
set MYSQL_PASSWORD=123456
set WECHAT_MOCK=true
:: 不要设置 SERVER_PORT，或在启动命令里加 --server.port=8080 覆盖
```

---

## 三、部署步骤（从零开始）

> **演示机最快路径**：建库建表（步骤 2）做完之后，直接双击项目根目录的 **`start.bat`**。
> 脚本会自动找 JDK 17、检查 MySQL/Redis 端口、开两个窗口分别启动后端与管理端、
> 轮询探活并把所有访问地址打印出来；演示结束双击 **`stop.bat`** 即可（按端口精确停止，
> 不会误杀机器上的其他 Java / Node 程序）。
> 下面 1～6 步是等价的手动流程，用于论文描述与排错。

### 步骤 1：获取代码

```bash
git clone https://github.com/JRZ159357/campus-market.git
cd campus-market
```

没有 Git 也可以：在仓库页面 `Code → Download ZIP` 解压，效果一致。

目录说明见[附录 A](#附录-a目录结构)。

### 步骤 2：数据库初始化

**2.1 创建数据库**

```sql
CREATE DATABASE campus_market DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

**2.2 按顺序执行建表脚本**

> ⚠️ **注意脚本位置**：`V1__` ~ `V12__` 在项目根目录的 **`sql/`** 下；
> 只有演示数据脚本在 `backend/sql/init_demo_data.sql`。两者不同目录，别找错。

顺序不能乱——后一个脚本依赖前一个建的表（V2 依赖 V1 的 `sys_user`，V6 依赖 V1/V2 等）：

```bat
cd /d D:\campus-market
mysql -uroot -p --default-character-set=utf8mb4 campus_market < sql\V1__user_auth.sql
mysql -uroot -p --default-character-set=utf8mb4 campus_market < sql\V2__category_product.sql
mysql -uroot -p --default-character-set=utf8mb4 campus_market < sql\V3__banner_home.sql
mysql -uroot -p --default-character-set=utf8mb4 campus_market < sql\V4__auth_upgrade.sql
mysql -uroot -p --default-character-set=utf8mb4 campus_market < sql\V5__favorite_follow.sql
mysql -uroot -p --default-character-set=utf8mb4 campus_market < sql\V6__trade_order.sql
mysql -uroot -p --default-character-set=utf8mb4 campus_market < sql\V7__chat_notice.sql
mysql -uroot -p --default-character-set=utf8mb4 campus_market < sql\V8__review_credit.sql
mysql -uroot -p --default-character-set=utf8mb4 campus_market < sql\V9__user_profile.sql
mysql -uroot -p --default-character-set=utf8mb4 campus_market < sql\V10__report_operation_log.sql
mysql -uroot -p --default-character-set=utf8mb4 campus_market < sql\V11__sensitive_word_account.sql
mysql -uroot -p --default-character-set=utf8mb4 campus_market < sql\V12__home_config.sql
```

或者一条命令合并（Windows CMD）：

```bat
for %f in (sql\V*.sql) do mysql -uroot -p --default-character-set=utf8mb4 campus_market < "%f"
```

> 用 Navicat / DBeaver 图形化工具也可以：依次「运行 SQL 文件」，编码选 UTF-8。

**2.3 灌入演示数据（答辩必做）**

```bat
mysql -uroot -p --default-character-set=utf8mb4 campus_market < backend\sql\init_demo_data.sql
```

脚本可重复执行：每次运行会先按固定 ID 段（9001+）清理旧演示数据，再重新插入，
最后自动打印各表统计与 9 项一致性校验结果。

**2.4 验证**

```sql
USE campus_market;
SELECT COUNT(*) FROM information_schema.TABLES WHERE table_schema='campus_market';  -- 期望：20
SELECT COUNT(*) FROM sys_user WHERE id BETWEEN 9001 AND 9012;                        -- 期望：12（演示用户）
SELECT COUNT(*) FROM product WHERE id BETWEEN 9001 AND 9040;                         -- 期望：40
SELECT COUNT(*) FROM trade_order WHERE id BETWEEN 9001 AND 9026;                     -- 期望：26
```

> 后台账号 `admin / service01 / service02` 不写在 SQL 里，由应用启动时
> `DataInitializer` 自动创建（BCrypt 加密），所以必须**先启动一次后端**才能登录管理端。

### 步骤 3：启动后端

```bat
cd /d D:\campus-market\backend
set JAVA_HOME=D:\jdk-17.0.6
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8080
```

首次启动会下载依赖，视网络情况 3～10 分钟；控制台出现下面这行即成功：

```
Started CampusMarketApplication in 6.482 seconds (process running for 7.031)
```

**验证接口**

```bash
curl http://localhost:8080/api/home
```

浏览器直接访问也行，应返回 JSON，且首层包含 `notice / banners / categories / hotProducts / newProducts / hotKeywords` 六个字段：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "notice": { "text": "欢迎来到校园二手交易平台，毕业季特惠！", "link": "", "enabled": 1 },
    "banners": [ ... ],
    "categories": [ ... ],
    "hotProducts": [ ... ],
    "newProducts": [ ... ],
    "hotKeywords": [ ... ]
  }
}
```

**接口文档（答辩演示加分项）**：<http://localhost:8080/doc.html>（Knife4j），
也可访问 `/v3/api-docs` 查看 OpenAPI 原始定义。

### 步骤 4：启动管理端

```bat
cd /d D:\campus-market\admin-web
npm install          :: 首次执行，约 1～3 分钟
npm run dev
```

控制台输出：

```
  VITE v5.4.x  ready in 1200 ms
  ➜  Local:   http://localhost:5173/
```

**验证**：浏览器打开 <http://localhost:5173>，用 `admin / 123456` 登录，进入仪表盘。

> ⚠️ **必须用 `localhost`，不能用 `127.0.0.1`**
> Vite 5 默认只监听 IPv6 回环 `[::1]:5173`。`localhost` 会解析到 `::1` 因此可访问；
> 而 `127.0.0.1` 是 IPv4，连接会被拒绝（浏览器报 `ERR_CONNECTION_REFUSED`）。
> 实测：`netstat -ano | findstr :5173` 只出现 `[::1]:5173 LISTENING`，没有 `0.0.0.0:5173`。

若确实需要用 `127.0.0.1`，可改 `admin-web/vite.config.js`：

```js
server: {
  host: '127.0.0.1',   // 或 '0.0.0.0' 供局域网其他机器访问
  port: 5173
}
```

开发期跨域已由 Vite 代理解决，`/api` 与 `/uploads` 都转发到 `http://localhost:8080`，
前端代码里不需要写后端地址。

### 步骤 5：启动小程序

1. 打开**微信开发者工具** → 导入项目；
2. 目录选择 `campus-market/miniprogram`；
3. AppID 填测试号（`touristappid`）或自己的小程序 AppID；
4. 右上角「详情 → 本地设置」勾选 **不校验合法域名、web-view（业务域名）、TLS 版本以及 HTTPS 证书**；
5. 点击「编译」，模拟器中即可看到首页。

小程序请求地址在 `miniprogram/config.js` 中配置：

```js
dev:  { BASE_URL: 'http://localhost:8080' },
prod: { BASE_URL: 'https://your-domain.com' }
```

> 用真机预览时 `localhost` 指向的是手机自己，必须改成电脑的局域网 IP（如 `http://192.168.1.10:8080`），
> 并确保手机与电脑在同一 WiFi 下、电脑防火墙放行 8080。

### 步骤 6：Nginx 反向代理（生产环境可选）

**6.1 构建管理端静态文件**

```bash
cd admin-web
npm run build            # 产物在 admin-web/dist
```

**6.2 完整 nginx.conf**

```nginx
# /etc/nginx/conf.d/campus-market.conf
upstream campus_backend {
    server 127.0.0.1:8080;
    keepalive 32;
}

server {
    listen       80;
    server_name  campus.example.com;      # 换成你的域名或服务器 IP

    # 上传文件体积上限：与后端 multipart 的 max-request-size=20MB 保持一致
    client_max_body_size 20m;

    # ---------- 管理端静态文件 ----------
    root  /var/www/campus-market/dist;
    index index.html;

    # 前端为 SPA，history 路由需要回退到 index.html，
    # 否则刷新 /order 这类子路由会 404
    location / {
        try_files $uri $uri/ /index.html;
    }

    # 带 hash 的静态资源可长期缓存
    location /assets/ {
        expires 30d;
        add_header Cache-Control "public, immutable";
    }

    # ---------- 后端接口 ----------
    location /api/ {
        proxy_pass         http://campus_backend;
        proxy_http_version 1.1;
        proxy_set_header   Host              $host;
        proxy_set_header   X-Real-IP         $remote_addr;
        proxy_set_header   X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header   X-Forwarded-Proto $scheme;
        proxy_connect_timeout 10s;
        proxy_read_timeout    60s;
    }

    # ---------- WebSocket 聊天 ----------
    # 必须单独配，且带 Upgrade / Connection 头，否则握手失败
    location /ws/ {
        proxy_pass         http://campus_backend;
        proxy_http_version 1.1;
        proxy_set_header   Upgrade    $http_upgrade;
        proxy_set_header   Connection "upgrade";
        proxy_set_header   Host       $host;
        proxy_read_timeout 3600s;     # 长连接，别用默认 60s
    }

    # ---------- 上传文件 ----------
    location /uploads/ {
        proxy_pass http://campus_backend;
        expires 7d;
    }

    # 接口文档在生产环境建议关闭或加访问限制
    location /doc.html {
        # allow 10.0.0.0/8;
        # deny all;
        proxy_pass http://campus_backend;
    }
}
```

**6.3 生效与验证**

```bash
nginx -t                 # 语法检查，必须 successful
nginx -s reload

curl http://campus.example.com/api/home          # 期望返回 JSON
curl -I http://campus.example.com/               # 期望 200，Content-Type: text/html
```

---

## 四、端口占用清单

| 服务 | 端口 | 协议 | 是否可改 | 说明 |
|---|---|---|---|---|
| MySQL | 3306 | TCP | 可（`MYSQL_PORT`） | 数据存储 |
| Redis | 6379 | TCP | 可（`REDIS_PORT`） | 缓存与会话 |
| 后端 Spring Boot | 8080 | HTTP | 可（`--server.port`） | 全部 `/api/**` 接口 |
| WebSocket | 8080 | WS | 随后端 | 路径 `/ws/chat?token=xxx`，与 HTTP 同端口 |
| 管理端（开发） | 5173 | HTTP | 可（`vite.config.js`） | Vite Dev Server，仅监听 `[::1]` |
| 管理端（生产） | 80 / 443 | HTTP(S) | 可（Nginx） | Nginx 托管静态文件并反代 |
| 小程序 | — | — | — | 无本地端口，请求后端 8080 |

查看端口占用：

```bat
netstat -ano | findstr :8080
tasklist | findstr <上一步查到的PID>
```

---

## 五、演示账号

所有演示账号密码统一为 **`123456`**。

### 5.1 后台账号（应用启动时自动创建）

| 角色 | 用户名 | 密码 | 昵称 | 权限说明 |
|---|---|---|---|---|
| 管理员 | `admin` | `123456` | 系统管理员 | 全部功能，含系统管理、账号管理 |
| 客服 | `service01` | `123456` | 客服小助手 | 只读查看，无写权限（写操作返回 403） |
| 客服 | `service02` | `123456` | 客服小助手二号 | 同上 |

> 客服账号登录后菜单里看不到「系统管理」，点按钮也无法提交——这是模块 3.5「客服只读拦截器」的设计，
> 答辩时可作为「权限体系」的演示点。

### 5.2 小程序演示账号

| 类型 | 用户名 | 昵称 | 信誉分 | 特点 |
|---|---|---|---|---|
| 普通用户 | `linxiaoman` | 林小满 | 100 | 有收藏、关注、订单，数据最完整，**推荐主用** |
| 普通用户 | `zhouzihao` | 周子豪 | 100 | 有发布商品 |
| 普通用户 | `xuzhiyuan` | 徐志远 | 92 | 有举报记录 |
| 普通用户 | `chenjiahe` | 陈家禾 | 81 | — |
| 普通用户 | `suxiaotong` | 苏晓彤 | 72 | 低信誉演示 |
| 普通用户 | `hesiyuan` | 何思远 | 65 | **已被封禁**，用于演示封号拦截 |
| 认证学生 | `wangzihan` | 王梓涵 | 135 | 带认证标识，信誉最高 |
| 认证学生 | `lisiyuan` | 李思远 | 128 | 有被举报商品（已下架） |
| 认证学生 | `zhangyutong` | 张雨桐 | 100 | — |
| 认证学生 | `zhaoyiming` | 赵一鸣 | 86 | 有订单待发货 |
| 认证学生 | `liuxingyu` | 刘星宇 | 100 | — |
| 认证学生 | `sunjianing` | 孙嘉宁 | 55 | **低信誉**，演示信誉分限制 |

小程序端用「账号密码登录」入口，输入上表用户名与 `123456` 即可。

> 演示顺序建议：`wangzihan` 发布商品 → `linxiaoman` 下单 → `wangzihan` 发货 → `linxiaoman` 收货评价
> → 管理端查看订单与评价 → 仪表盘数据变化。

---

## 六、常见故障排查（FAQ）

### 1. 后端启动报 Lombok 错误

**现象**

```
java.lang.NoSuchFieldError: Class com.sun.tools.javac.tree.JCTree$JCImport does not have member field 'com.sun.tools.javac.tree.JCTree qualid'
```

或 `java: 程序包 lombok 不存在` / `cannot find symbol: method getXxx()`。

**原因**：`JAVA_HOME` 指向了 JDK 21/26 等高版本，Lombok 注解处理器与新版 javac 内部 API 不兼容。

**解决**：

```bat
set JAVA_HOME=D:\jdk-17.0.6
mvn -v      :: 确认输出的 Java version 是 17.x
```

仍失败则清掉旧的编译产物再试：

```bat
mvn clean compile
```

### 2. 后端启动端口不是 8080

**现象**：控制台显示 `Tomcat started on port 60520`，前端页面全部接口 404 / 502。

**原因**：系统环境变量存在 `SERVER_PORT=60520`，Spring Boot 松散绑定把它当成了 `server.port`。

**解决**：

```bat
echo %SERVER_PORT%                                      :: 先确认
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8080
```

或删除该系统变量后重开窗口。

### 3. 管理端用 127.0.0.1 打不开

**现象**：`http://localhost:5173` 正常，`http://127.0.0.1:5173` 报 `ERR_CONNECTION_REFUSED`。

**原因**：Vite 5 默认只监听 IPv6 回环地址 `[::1]`。

**解决**：改用 `localhost`；或在 `vite.config.js` 中设置 `server.host`。

### 4. 小程序请求失败

**现象**：模拟器里所有接口报 `request:fail`，或提示「不在以下 request 合法域名列表中」。

**解决**：

1. 微信开发者工具 → 详情 → 本地设置 → 勾选「不校验合法域名…」；
2. 确认后端已启动（`curl http://localhost:8080/api/home` 有返回）；
3. 真机调试时把 `config.js` 的 `BASE_URL` 改成电脑局域网 IP，并放行防火墙 8080。

### 5. 中文乱码

**现象**：商品标题、昵称显示为 `????` 或 `æ ¡å•†å“`。

**原因**：建库时未指定 utf8mb4，或导入时客户端字符集不对。

**解决**：删库重建，重新导入：

```sql
DROP DATABASE IF EXISTS campus_market;
CREATE DATABASE campus_market DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

导入命令务必带 `--default-character-set=utf8mb4`。

### 6. Redis 连接失败

**现象**：`Unable to connect to Redis` / `RedisConnectionFailureException` / 登录后立刻掉线。

**排查**：

```bat
redis-cli ping                       :: 期望 PONG
netstat -ano | findstr :6379         :: 期望有 LISTENING
```

- 服务未启动 → 启动 Redis（Windows 可在 `D:\Redis` 下执行 `redis-server.exe`）；
- 有密码但没配 → `set REDIS_PASSWORD=你的密码`；
- 端口被改过 → `set REDIS_PORT=xxxx`。

### 7. 上传图片 404

**现象**：商品图、头像显示为裂图，请求 `/uploads/xxx.png` 返回 404。

**原因**：

1. 上传目录不存在（首次运行且从未上传过文件时不会自动创建）；
2. 启动时的工作目录不对，导致 `./uploads` 落到了别处；
3. 静态资源被反向代理拦截。

**解决**：

```bat
cd backend
mkdir uploads          :: 手动创建
dir uploads
```

确认启动目录是 `backend`，则实际目录为 `backend\uploads`，对应 URL `/uploads/**`。
若改了 `FILE_UPLOAD_PATH`，需要保证该目录存在且有写权限（Linux 下 `chown -R nginx:nginx`）。

演示数据依赖的图片已随仓库提供：`backend/uploads/demo/`（18 张）与
`backend/uploads/2026/10/seed-banner-*.png`（3 张），其余上传目录不入库。

### 8. 待付款订单被自动取消

**现象**：刚下的单，几分钟后状态变成「已取消」。

**原因**：`OrderTimeoutTask` 每 5 分钟扫描一次超时未支付订单（`pay-timeout-minutes: 30`）。

**说明**：这是**正常业务逻辑**，不是 Bug。演示数据的 `pay_deadline` 已设为 `NOW() + 3 天`，
跑完 `init_demo_data.sql` 后不会立刻被取消；只有现场新下的单才受 30 分钟限制。

若演示时想延长，启动加参数：

```bat
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8080 --app.order.pay-timeout-minutes=180"
```

### 9. 同账号重新登录被踢下线

**现象**：在另一台设备/另一个浏览器登录同一账号，前一个会话立刻失效（返回 401）。

**说明**：这是**单端登录**设计，不是故障。JWT 的 `jti` 存在 Redis，新登录会覆盖旧 `jti`，旧 token 校验失败。

> 副作用提醒：用同一个 `admin` 账号同时做「截图」和「手动操作」会互相踢下线；
> 截图脚本必须**现登录现用** token，否则截到的是登录页。

### 10. GitHub 推送需要 token

**现象**：`git push` 提示 `Support for password authentication was removed` / 401。

**解决**：GitHub 已不支持账号密码推送，改用 Personal Access Token：

1. GitHub → Settings → Developer settings → Personal access tokens → Tokens (classic) → Generate new token；
2. 勾选 `repo` 权限，生成后复制（只显示一次）；
3. 推送时用户名填 GitHub 用户名，**密码位置粘贴 token**：

```bash
git push https://<用户名>:<token>@github.com/JRZ159357/campus-market.git main
```

或用凭据管理器记住：

```bash
git config --global credential.helper manager
```

### 11. Maven 依赖下载慢 / 卡住

在 `~/.m2/settings.xml` 配置国内镜像：

```xml
<mirror>
  <id>aliyun</id>
  <mirrorOf>central</mirrorOf>
  <url>https://maven.aliyun.com/repository/public</url>
</mirror>
```

### 12. npm install 报错 / 卡住

```bat
npm config set registry https://registry.npmmirror.com
npm cache clean --force
rmdir /s /q node_modules
npm install
```

---

## 七、生产环境部署建议

> 本章为论文「系统部署」的扩展内容，可体现对工程化的理解。

### 7.1 后端打包为可执行 jar

```bash
cd backend
mvn clean package -DskipTests
# 产物：backend/target/campus-market-1.0.0.jar
```

启动：

```bash
java -jar campus-market-1.0.0.jar --server.port=8080
```

> `application.yml` 已开启 `spring-boot-maven-plugin` 的 repackage，jar 内嵌 Tomcat，无需额外容器。

### 7.2 systemd 托管（Linux）

`/etc/systemd/system/campus-market.service`：

```ini
[Unit]
Description=Campus Market Backend
After=network.target mysql.service redis.service

[Service]
Type=simple
User=www-data
WorkingDirectory=/opt/campus-market/backend
Environment="JAVA_HOME=/usr/local/jdk-17.0.6"
Environment="MYSQL_PASSWORD=你的密码"
Environment="FILE_UPLOAD_PATH=/opt/campus-market/uploads"
ExecStart=/usr/local/jdk-17.0.6/bin/java -Xms256m -Xmx512m \
    -jar /opt/campus-market/backend/campus-market-1.0.0.jar --server.port=8080
Restart=always
RestartSec=10
StandardOutput=append:/var/log/campus-market/app.log
StandardError=append:/var/log/campus-market/err.log

[Install]
WantedBy=multi-user.target
```

```bash
systemctl daemon-reload
systemctl enable --now campus-market
systemctl status campus-market
journalctl -u campus-market -f
```

**为什么用 systemd 而不是 nohup**：进程崩溃自动拉起、开机自启、日志统一走 journald、
权限收敛到 `www-data`，避免以 root 运行 Web 服务。

### 7.3 Docker Compose 编排

`docker-compose.yml`（放在项目根目录）：

```yaml
version: "3.8"

services:
  mysql:
    image: mysql:8.0
    container_name: cm-mysql
    restart: always
    environment:
      MYSQL_ROOT_PASSWORD: "123456"
      MYSQL_DATABASE: campus_market
      TZ: Asia/Shanghai
    command:
      - --character-set-server=utf8mb4
      - --collation-server=utf8mb4_general_ci
    ports:
      - "3306:3306"
    volumes:
      - mysql-data:/var/lib/mysql
      # 首次启动自动执行建表脚本。注意命名顺序问题，见下方说明
      - ./sql:/docker-entrypoint-initdb.d:ro
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "127.0.0.1", "-p123456"]
      interval: 10s
      timeout: 5s
      retries: 10

  redis:
    image: redis:7-alpine
    container_name: cm-redis
    restart: always
    command: ["redis-server", "--appendonly", "yes"]
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 3s
      retries: 5

  backend:
    build:
      context: ./backend
    container_name: cm-backend
    restart: always
    depends_on:
      mysql:
        condition: service_healthy
      redis:
        condition: service_healthy
    environment:
      MYSQL_HOST: mysql
      MYSQL_PORT: "3306"
      MYSQL_DATABASE: campus_market
      MYSQL_USERNAME: root
      MYSQL_PASSWORD: "123456"
      REDIS_HOST: redis
      REDIS_PORT: "6379"
      FILE_UPLOAD_PATH: /app/uploads
      TZ: Asia/Shanghai
    ports:
      - "8080:8080"
    volumes:
      - ./uploads:/app/uploads

  nginx:
    image: nginx:1.25-alpine
    container_name: cm-nginx
    restart: always
    depends_on:
      - backend
    ports:
      - "80:80"
    volumes:
      - ./admin-web/dist:/usr/share/nginx/html:ro
      - ./deploy/nginx.conf:/etc/nginx/conf.d/default.conf:ro

volumes:
  mysql-data:
  redis-data:
```

`backend/Dockerfile`：

```dockerfile
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
ENV TZ=Asia/Shanghai
COPY --from=build /build/target/campus-market-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Xms256m", "-Xmx512m", "-jar", "app.jar"]
```

一键启动：

```bash
docker compose up -d --build
docker compose ps
docker compose logs -f backend
```

**注意三点**：

1. `./sql` 挂载到 `docker-entrypoint-initdb.d` 只在**数据库首次初始化**（数据卷为空）时执行；
   想重新初始化需 `docker compose down -v` 清卷。
2. **初始化脚本的执行顺序是文件名字典序，不是自然序**。`V10__xxx.sql` 会排在 `V1__xxx.sql`
   **前面**（逐字符比较时 `'0'`(0x30) < `'_'`(0x5F)），于是 `V10`～`V12` 反而最先执行，
   因外键依赖不存在而建表失败。做法二选一：
   - 把脚本重命名为 `V01__…` 到 `V12__…`（补零），再挂载；
   - 或者不挂载 `sql` 目录，容器首次启动后手动按 V1→V12 顺序执行一遍。
3. 演示数据 `init_demo_data.sql` 在 `backend/sql/` 下，不在被挂载的 `sql/` 里，
   需要单独执行；它依赖 `backend/uploads/demo` 的图片路径，
   容器内需把上传目录挂载出来，且 Nginx 反代 `/uploads/` 到后端。

### 7.4 MySQL 主从与 Redis 持久化

**MySQL 主从（读写分离）**

- 主库 `my.cnf`：`server-id=1`、`log-bin=mysql-bin`、`binlog-format=ROW`；
- 从库：`server-id=2`、`relay-log=relay-bin`、`read_only=ON`；
- 主库建复制账号 `CREATE USER 'repl'@'%' IDENTIFIED BY '...'; GRANT REPLICATION SLAVE ON *.* TO 'repl'@'%';`
- 从库 `CHANGE MASTER TO MASTER_HOST='...', MASTER_USER='repl', MASTER_LOG_FILE='...', MASTER_LOG_POS=...; START SLAVE;`
- 应用侧接入 ShardingSphere-JDBC 或 MyBatis-Plus 动态数据源实现读写分离。

> 本项目当前为单库架构，主从属于扩展建议。答辩时说明「读多写少的商品列表查询可下沉到从库」即可。

**Redis 持久化**

- RDB：`save 900 1` / `save 300 10`，快照适合备份，重启可能丢最后一次快照后的数据；
- AOF：`appendonly yes` + `appendfsync everysec`，最多丢 1 秒数据，推荐生产开启；
- 本项目 Redis 存的是登录态（JWT jti）、浏览量增量、首页/看板缓存，**全部可重建**，
  即使 Redis 数据全丢也只影响「用户需重新登录 + 缓存回源查库」，不会造成业务数据丢失。

### 7.5 HTTPS 配置

用 Let's Encrypt 免费证书：

```bash
apt install certbot python3-certbot-nginx
certbot --nginx -d campus.example.com
```

Nginx 会自动改写配置并加上 80 → 443 跳转。手动配置参考：

```nginx
server {
    listen 443 ssl http2;
    server_name campus.example.com;

    ssl_certificate     /etc/letsencrypt/live/campus.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/campus.example.com/privkey.pem;
    ssl_protocols       TLSv1.2 TLSv1.3;
    ssl_ciphers         HIGH:!aNULL:!MD5;

    # 其余 location 同 6.2
}

server {
    listen 80;
    server_name campus.example.com;
    return 301 https://$host$request_uri;
}
```

小程序正式发布**强制要求 HTTPS**，且域名需在小程序后台「开发管理 → 开发设置 → 服务器域名」中配置。

### 7.6 生产环境加固清单

| 项目 | 做法 |
|---|---|
| JWT secret | 不要把仓库里的默认值用于生产，用环境变量注入随机 32+ 字节字符串 |
| 数据库密码 | 不用 root 连接，建专用账号并只授权 `campus_market` 库 |
| 接口文档 | 生产环境关闭 `/doc.html`，或加 IP 白名单 |
| 日志级别 | `logging.level.com.campus.market` 从 `debug` 调为 `info`，避免 SQL 全量打印 |
| 上传目录 | 独立分区挂载，限制可执行权限，禁止上传 `.jsp/.php` 等 |
| 备份 | MySQL 每日全量 + binlog 增量；`mysqldump -uroot -p campus_market > backup.sql` |
| 监控 | 后端 `/api/home` 做健康探测；`spring-boot-starter-actuator` 可选接入 |

---

## 附录 A：目录结构

```
campus-market/
├── backend/                        后端工程（Spring Boot 3.2.5 · JDK 17）
│   ├── pom.xml
│   ├── sql/init_demo_data.sql      演示数据脚本（幂等，可重复执行）
│   ├── uploads/                    上传目录（演示素材随仓库提供）
│   └── src/main/
│       ├── java/com/campus/market/
│       │   ├── common/             统一响应、异常、常量、注解
│       │   ├── config/             MyBatis-Plus / WebMvc / WebSocket / 初始化
│       │   ├── controller/         23 个 REST 控制器
│       │   ├── service(/impl)      业务服务与实现
│       │   ├── mapper/             MyBatis-Plus Mapper
│       │   ├── entity/ dto/ vo/    数据模型三层
│       │   ├── interceptor/        登录校验与客服只读拦截
│       │   ├── aspect/             操作日志切面
│       │   ├── task/               订单超时扫描
│       │   └── ws/                 WebSocket 会话管理
│       └── resources/application.yml
├── admin-web/                      管理端（Vue 3 + Vite + Element Plus）
│   ├── src/{api,views,router,stores,layout,utils,directives}/
│   └── vite.config.js              开发代理：/api、/uploads → 8080
├── miniprogram/                    微信小程序原生开发
│   ├── pages/                      16 个页面目录
│   ├── api/ config.js utils/       请求封装与地址配置
│   └── custom-tab-bar/ components/
├── sql/                            建表脚本 V1__ ~ V12__
├── docs/                           部署文档、接口说明、项目概览、截图
├── tests/                          冒烟测试、离线校验、造数与截图工具
├── start.bat                       一键启动（Windows）
├── stop.bat                        一键停止（Windows）
└── README.md
```

## 附录 B：部署验收清单

按顺序勾选，全部通过即部署成功：

| # | 检查项 | 命令 / 操作 | 期望结果 |
|---|---|---|---|
| 1 | JDK 版本 | `mvn -v` | `Java version: 17.x` |
| 2 | 数据库字符集 | `SELECT DEFAULT_CHARACTER_SET_NAME ...` | `utf8mb4` |
| 3 | 建表数量 | `information_schema.TABLES` | 20 张表 |
| 4 | 演示数据 | `init_demo_data.sql` 末尾输出 | 9 项一致性校验均为 0 |
| 5 | Redis 可用 | `redis-cli ping` | `PONG` |
| 6 | 后端启动 | 控制台 | `Started CampusMarketApplication` |
| 7 | 首页接口 | `curl http://localhost:8080/api/home` | 返回 6 个字段的 JSON |
| 8 | 后台账号 | 启动日志 | `初始化后台账号: admin / 123456 (admin)` |
| 9 | 接口文档 | 浏览器 `/doc.html` | Knife4j 页面正常渲染 |
| 10 | 管理端登录 | <http://localhost:5173> | `admin / 123456` 进入仪表盘 |
| 11 | 数据看板 | 仪表盘页 | GMV、在售商品、订单数非 0 |
| 12 | 图片可访问 | `/uploads/demo/cover-1.png` | 返回 200 且为图片 |
| 13 | 小程序首页 | 开发者工具编译 | 显示公告、轮播图、金刚区、热门/新品 |
| 14 | 客服只读 | `service01` 登录管理端 | 无「系统管理」菜单，写操作被拒 |
