# 模块1 · 用户认证 接口说明

> 统一前缀 `/api`，所有接口返回 `Result<T>`。除标注「免登录」外，均需请求头 `Authorization: Bearer {token}`。
> 在线文档：`http://localhost:8080/doc.html`

## 1. 接口清单

| # | 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|---|
| 1 | POST | `/api/auth/wx-login` | 小程序微信登录，openid 不存在时自动注册为普通用户 | 免登录 |
| 2 | POST | `/api/auth/admin-login` | 管理端账号密码登录 | 免登录（仅 admin / customer_service） |
| 3 | POST | `/api/auth/bind-phone` | 绑定手机号 | 登录用户 |
| 4 | POST | `/api/auth/logout` | 退出登录，删除 Redis token 白名单 | 登录用户 |
| 5 | GET | `/api/auth/me` | 当前登录用户信息 | 登录用户 |
| 6 | GET | `/api/student-auth/status` | 我的认证状态（含最近一次申请） | 登录用户 |
| 7 | POST | `/api/student-auth` | 提交学生认证申请 | 登录用户（非后台账号） |
| 8 | GET | `/api/student-auth/page` | 分页查询认证申请 | admin |
| 9 | GET | `/api/student-auth/{id}` | 认证申请详情 | admin |
| 10 | PUT | `/api/student-auth/{id}/audit` | 审核认证申请 | admin |
| 11 | POST | `/api/file/upload` | 图片上传（学生证、商品图共用） | 登录用户 |

## 2. 关键请求/响应示例

### 2.1 微信登录

```http
POST /api/auth/wx-login
Content-Type: application/json

{ "code": "081Kq7Ga1...", "nickname": "小明", "avatar": "https://..." }
```

```json
{
  "code": 200,
  "msg": "登录成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "userId": 3,
    "role": "user",
    "roleLabel": "普通用户",
    "nickname": "小明",
    "avatar": "https://...",
    "studentVerified": 0,
    "publishLimit": 0
  }
}
```

### 2.2 提交学生认证

```http
POST /api/student-auth
Authorization: Bearer {token}

{
  "studentNo": "202315020114",
  "realName": "谢伟庆",
  "school": "北华大学",
  "certImg": "/uploads/2026/10/xxxx.png"
}
```

失败场景与返回：

| 场景 | code | msg |
|---|---|---|
| 已认证 | 500 | 你已完成学生认证，无需重复提交 |
| 已有待审核申请 | 500 | 你已有一条待审核的申请，请耐心等待审核结果 |
| 学号已被他人认证 | 500 | 该学号已被其他账号完成认证，如有疑问请联系客服 |
| 后台账号提交 | 500 | 后台账号无需进行学生认证 |
| 学号格式错误 | 400 | 学号格式不正确（6-30位数字或字母） |

### 2.3 审核

```http
PUT /api/student-auth/12/audit
Authorization: Bearer {admin_token}

{ "status": 1 }
```

驳回时 `{"status": 2, "rejectReason": "学生证照片模糊，请重新上传"}`，`rejectReason` 为空会被拒绝。

审核通过后事务内同步完成：`student_auth.status=1` → `sys_user.role=student`、`student_verified=1`、`publish_limit=10` → 清除用户 Redis 缓存（保证下一次请求即按新角色鉴权）。

### 2.4 分页查询

```http
GET /api/student-auth/page?pageNum=1&pageSize=10&status=0&keyword=2023
```

```json
{
  "code": 200,
  "data": {
    "records": [
      {
        "id": 12, "userId": 3, "nickname": "小明", "phone": "13800001234",
        "studentNo": "202315020114", "realName": "谢伟庆", "school": "北华大学",
        "certImg": "/uploads/2026/10/xxxx.png", "status": 0,
        "rejectReason": null, "createTime": "2026-10-21 09:33:12", "auditTime": null
      }
    ],
    "total": 1, "size": 10, "current": 1, "pages": 1
  }
}
```

`keyword` 匹配学号或姓名；排序规则为「待审核优先，其次按提交时间倒序」。

## 3. 鉴权设计说明

```
请求 → 取 token（Authorization: Bearer / token 头）
     → JWT 解析 userId（失败 401）
     → Redis login:token:{userId} 与 token 比对（不一致 401，实现单端登录 + 强制下线）
     → 读用户缓存（30min，未命中回源 DB）
     → status=1 封禁拦截（403）
     → @RequireRole 角色匹配（不匹配 403）
     → 写入 ThreadLocal，请求结束 clear()
```

设计要点：**JWT 只携带 userId，角色每次都从缓存/DB 读取**。若把 role 写进 JWT，管理员调整角色后旧 token 仍按旧角色放行，需等 7 天过期才生效——这是毕设答辩常被追问的点。

## 4. 测试记录

测试脚本：`tests/smoke_test.py`（22 个用例，可重复执行）

| 分组 | 用例 | 结果 |
|---|---|---|
| 登录 | 微信登录返回 token、角色为 user | 通过 |
| 登录 | 管理端登录成功、错误密码返回 400 | 通过 |
| 鉴权 | 未携带 token 返回 401 | 通过 |
| 鉴权 | 普通用户访问管理端接口返回 403 | 通过 |
| 鉴权 | 退出登录后 token 立即失效 | 通过 |
| 认证 | 上传学生证返回 `/uploads/` 地址 | 通过 |
| 认证 | 提交认证成功、重复提交被拒 | 通过 |
| 审核 | 待审核列表命中新申请 | 通过 |
| 审核 | 驳回未填原因被拒 | 通过 |
| 审核 | 审核通过后角色升级为 student、发布上限 10 | 通过 |
| 唯一性 | 已认证学号不能被第二个账号认证 | 通过 |

执行结果：**22 / 22 通过**。

## 5. 遗留与已知风险

1. `GET /api/student-auth/status` 与 `GET /api/student-auth/{id}` 共用前缀，`status` 为字面量路径，Spring 优先精确匹配；若后续新增 `/{id}` 之外的子路径，建议改为 `/api/student-auth/my-status` 以避免歧义。
2. 分页列表暂不支持按昵称搜索（昵称在 `sys_user` 表），已按学号/姓名实现，昵称检索放到「用户管理」模块统一做。
3. 上传文件存本地磁盘，Docker 部署时需挂载卷；换对象存储只改 `FileController` 一处。
4. 微信登录/手机号在 `wechat.mock=true` 下为模拟实现，接入真实小程序前必须关闭 mock 并配置 AppID/Secret。
5. 操作日志（谁审核了谁）尚未落库，`auditor_id` 已记录，完整审计留到「系统管理」模块。
