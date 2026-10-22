# 模块 3.5 · 权限体系升级 接口说明

> 依赖脚本：`sql/V4__auth_upgrade.sql`（`sys_user.register_type`，账号字段长度收紧）
> 基础路径：`http://localhost:8080`，统一返回 `Result<T>`（`code=200` 成功）
> 登录方式：微信登录与账号密码登录签发**同一套 JWT**（只携带 userId），角色每次从 Redis/数据库读取

---

## 1. 账号认证

| 方法 | 路径 | 说明 | 鉴权 |
|---|---|---|---|
| POST | `/api/auth/wx-login` | 微信登录，openid 不存在时自动注册为普通用户 | 免登录 |
| POST | `/api/auth/register` | 账号密码注册，默认普通用户，成功后直接返回 token | 免登录 |
| POST | `/api/auth/login` | 账号密码登录，全角色通用 | 免登录 |
| POST | `/api/auth/admin-login` | 管理端登录，仅 admin / customer_service | 免登录 |
| GET | `/api/auth/check-username` | 校验账号是否可注册（唯一性实时校验） | 免登录 |
| POST | `/api/auth/bind-account` | 微信用户绑定账号密码 | 需登录 |
| POST | `/api/auth/change-password` | 修改当前账号密码 | 需登录 |
| POST | `/api/auth/logout` | 退出登录（删除 Redis 白名单） | 需登录 |
| GET | `/api/auth/me` | 当前登录用户信息（含账号、注册方式） | 需登录 |

### 1.1 账号注册

`POST /api/auth/register`

| 参数 | 类型 | 必填 | 规则 |
|---|---|---|---|
| `username` | string | 是 | 4-20 位字母或数字，全局唯一 |
| `password` | string | 是 | 6-20 位，BCrypt 加密入库 |
| `nickname` | string | 否 | ≤20 字，留空自动生成「用户+账号」 |

响应：

```json
{
  "code": 200,
  "msg": "注册成功",
  "data": { "token": "eyJhbGciOi...", "userId": 12, "role": "user", "roleLabel": "普通用户",
            "nickname": "用户smoke01", "studentVerified": 0, "publishLimit": 0 }
}
```

失败示例：

| 场景 | code | msg |
|---|---|---|
| 账号格式不合法 | 400 | 账号需为4-20位字母或数字 |
| 密码长度不合法 | 400 | 密码长度需为6-20位 |
| 账号已存在 | 500 | 该账号已被注册，请更换 |

### 1.2 账号登录

`POST /api/auth/login`

```json
{ "username": "smoke01", "password": "abc12345" }
```

账号不存在与密码错误统一返回 `400 账号或密码错误`，避免账号枚举；封禁账号返回 `403`。

### 1.3 校验账号是否可注册

`GET /api/auth/check-username?username=smoke01`

```json
{ "code": 200, "msg": "操作成功", "data": true }
```

`data=false` 表示格式不合法或已被占用，注册页据此展示「可用 / 已占用」。

### 1.4 微信用户绑定账号

`POST /api/auth/bind-account`

```json
{ "username": "wxsmoke01", "password": "abc12345" }
```

绑定后 `register_type` 仍为 1（微信注册），但 `hasAccount=true`，两种方式均可登录同一账号。
已绑定（`username` 非空）的账号再次调用返回失败，需联系管理员更换。

### 1.5 修改密码

`POST /api/auth/change-password`

```json
{ "oldPassword": "abc12345", "newPassword": "newpass123" }
```

校验：原密码正确、新旧不同、新密码 6-20 位。修改后旧密码立即失效。

### 1.6 当前用户信息

`GET /api/auth/me`

```json
{
  "code": 200,
  "data": {
    "id": 2, "username": "service01", "registerType": 2, "registerTypeLabel": "账号密码注册",
    "hasAccount": true, "nickname": "客服小助手", "role": "customer_service", "roleLabel": "客服",
    "studentVerified": 0, "creditScore": 100, "publishLimit": 0, "createTime": "2026-10-21 09:55:50"
  }
}
```

---

## 2. 权限模型

| 角色 | 管理端 | 小程序 | 写操作 |
|---|---|---|---|
| `user` | 不可登录 | 浏览/收藏/下单/评价 | 按业务接口 |
| `student` | 不可登录 | 上述 + 发布商品（上限 20） | 按业务接口 |
| `customer_service` | 可登录（**只读**） | 可登录 | **一切非 GET 请求 403** |
| `admin` | 可登录 | 可登录 | 全部放行 |

### 2.1 客服只读拦截

在 `AuthInterceptor` 中统一判定，与具体接口无关：

```java
if (RoleConst.CUSTOMER_SERVICE.equals(user.getRole())
        && !HttpMethod.GET.matches(request.getMethod())
        && !CUSTOMER_SERVICE_WRITE_WHITELIST.contains(request.getServletPath())) {
    return reject(response, ResultCode.FORBIDDEN, "客服账号无操作权限，请联系管理员");
}
```

响应体（HTTP 状态码仍为 200，前端只判断 `body.code`）：

```json
{ "code": 403, "msg": "客服账号无操作权限，请联系管理员", "data": null }
```

### 2.2 白名单（客服也可调用的写接口）

| 路径 | 原因 |
|---|---|
| `/api/auth/login` | 登录入口 |
| `/api/auth/admin-login` | 管理端登录入口 |
| `/api/auth/wx-login` | 小程序登录入口 |
| `/api/auth/register` | 账号注册（公共入口） |
| `/api/auth/logout` | 退出登录 |
| `/api/auth/me` | 个人信息 |
| `/api/auth/bind-account` | 绑定账号（客服已在初始化时绑定，调用会提示） |
| `/api/auth/change-password` | 客服自助修改密码，否则无法更换初始密码 |

> 需求给定的白名单为 login / logout / me / bind-account，实现时补充了登录入口、注册入口与改密：
> 前三者是「无 token 即可访问」的入口，改密是账号自身安全操作，均不涉及平台内容变更。

### 2.3 只读接口的角色放开

客服需要查看各类数据，因此以下 GET 接口由「仅管理员」调整为「管理员 + 客服」：

| 接口 | 说明 |
|---|---|
| `GET /api/student-auth/page` | 学生认证列表 |
| `GET /api/student-auth/{id}` | 学生认证详情 |
| `GET /api/product/audit/page` | 商品审核列表 |
| `GET /api/banner/page` | 轮播图列表 |
| `GET /api/category/list?all=true` | 分类列表（本就免登录） |

写接口（`POST/PUT/DELETE`）保持 `@RequireRole(admin)`，客服在拦截器层先被 403 拦截。

---

## 3. 预置账号

`DataInitializer` 在应用启动时创建，密码统一 `123456`（BCrypt 加密），可重复启动：

| 账号 | 密码 | 角色 | 说明 |
|---|---|---|---|
| `admin` | 123456 | admin | 系统管理员 |
| `service01` | 123456 | customer_service | 客服（只读） |
| `service02` | 123456 | customer_service | 客服（只读） |

---

## 4. 数据库变更

```sql
ALTER TABLE `sys_user`
  ADD COLUMN `register_type` tinyint NOT NULL DEFAULT 1
  COMMENT '注册方式:1微信 2账号密码' AFTER `password`;

ALTER TABLE `sys_user`
  MODIFY COLUMN `username` varchar(20) DEFAULT NULL COMMENT '登录账号（4-20位字母数字），微信用户可为空',
  MODIFY COLUMN `password` varchar(100) DEFAULT NULL COMMENT '登录密码(BCrypt)，微信用户未绑定账号时为空';

UPDATE `sys_user` SET `register_type` = 2 WHERE `open_id` IS NULL AND `username` IS NOT NULL;
```

---

## 5. 前端权限约定

| 位置 | 产物 | 说明 |
|---|---|---|
| `admin-web/src/directives/permission.js` | `v-permission="'admin'"` | 无权限时元素从 DOM 移除 |
| `admin-web/src/stores/user.js` | `isAdmin` / `isReadOnly` | 页面统一取这两个状态控制表格列与开关 |
| `admin-web/src/router/index.js` | `meta.roles` | 菜单过滤 + 路由守卫双重拦截 |
| `admin-web/src/layout/index.vue` | 角色标签 + 只读提示条 | 管理员红色标签、客服蓝色标签 |
| 小程序 `pages/profile/security` | 账号与安全 | 展示登录方式、绑定账号、修改密码 |

> 前端隐藏仅是体验层，真正的权限判定在服务端拦截器：即使手动调用接口，客服写操作仍返回 403。
