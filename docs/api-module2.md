# 模块 2 接口文档 — 商品分类与商品发布

> 基础路径：`/api`　　统一返回体：`{ code, msg, data }`　　时间格式：`yyyy-MM-dd HH:mm:ss`

## 1. 权限与状态约定

| 角色 | 本模块权限 |
|---|---|
| 普通用户 `user` | 浏览分类、浏览商品、查看详情；**发布返回 403** |
| 认证学生 `student` | 发布、编辑、下架、删除自己的商品；查看我的发布 |
| 管理员 `admin` | 分类 CRUD、商品审核、强制下架、查看全部商品 |

**商品状态**：`0` 待审核 → `1` 已上架 → `2` 已下架（卖家下架或审核驳回）→ `3` 已售出（订单模块写入）→ `4` 违规下架（管理员强制）

**可见性规则**：非 `1` 状态的商品，只有卖家本人、管理员、客服可以查看详情；公开列表只返回 `status=1`。

**发布额度**：认证学生上限 20 件，计数口径为 `status in (0,1)`，即**待审核也占额度**，防止靠堆积待审核绕过限制。

## 2. 分类接口

### 2.1 分类列表
`GET /api/category/list`　免登录

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| all | boolean | 否 | `true` 时管理员可拿到含禁用项的全部分类；非管理员传了也只返回启用项 |

```json
{
  "code": 200,
  "data": [
    { "id": 1, "name": "数码电子", "icon": "📱", "sort": 1, "status": 1,
      "productCount": 12, "createTime": "2026-10-21 09:55:00" }
  ]
}
```

> `icon` 兼容两种存法：图片地址（`/uploads/...` 或 `http...`）与 emoji 文本，小程序按前缀判断渲染方式。

### 2.2 新增分类
`POST /api/category`　管理员

```json
{ "name": "乐器", "icon": "🎸", "sort": 8, "status": 1 }
```

`sort` 不传时自动追加到末尾；名称重复返回 `400 分类名称「x」已存在`。

### 2.3 编辑分类
`PUT /api/category/{id}`　管理员，入参同新增（`name` 必填）

### 2.4 删除分类
`DELETE /api/category/{id}`　管理员

分类下仍有商品时返回 `500 该分类下还有 N 件商品…`，逻辑删除。

### 2.5 启用/禁用分类
`PUT /api/category/{id}/status?status=1`　管理员，`status`：0 禁用 / 1 启用

禁用后小程序分类导航不再返回该分类，且**不能向该分类发布商品**；已在该分类下的商品不受影响。

## 3. 商品接口

### 3.1 发布商品
`POST /api/product`　认证学生

```json
{
  "title": "九成新山地自行车",
  "categoryId": 6,
  "price": 240.00,
  "originalPrice": 899.00,
  "description": "骑行一年，车况良好，校内自提。",
  "images": ["/uploads/2026/10/a.png", "/uploads/2026/10/b.png"]
}
```

| 字段 | 校验 |
|---|---|
| title | 必填，≤30 字 |
| categoryId | 必填，分类必须存在且启用 |
| price | 必填，>0，≤999999.99，两位小数 |
| originalPrice | 选填，>0，两位小数 |
| description | 选填，≤500 字 |
| images | 必填，1–9 张，第一张为主图 |

成功返回商品 ID，商品状态为 `0 待审核`；超出额度返回 `500 你同时在架与待审核的商品已达 20 件上限…`。

### 3.2 编辑商品
`PUT /api/product/{id}`　卖家本人

只有 `status=0` 或 `status=2` 可编辑；保存后**重新进入待审核**，并清空上一次的驳回原因。已上架商品需先下架。

### 3.3 下架商品
`PUT /api/product/{id}/offline`　卖家本人，仅 `status=1` 可下架

### 3.4 删除商品（补充接口）
`DELETE /api/product/{id}`　卖家本人

- 已上架 → 拒绝，提示先下架
- 已售出 → 拒绝，交易记录需保留
- 其他状态 → 逻辑删除商品与图片

> 需求文档中"我的发布"页面有删除操作，故补充该接口。

### 3.5 我的发布
`GET /api/product/my?pageNum=1&pageSize=10&status=0`　认证学生

返回分页对象，字段与 3.6 列表项一致（含 `status`、`statusLabel`、`cover`、`viewCount`）。

### 3.6 商品列表
`GET /api/product/page`　免登录

| 参数 | 说明 |
|---|---|
| pageNum / pageSize | 分页，pageSize 上限 100 |
| categoryId | 分类筛选 |
| keyword | 匹配标题或描述 |
| minPrice / maxPrice | 价格区间，最低价高于最高价返回 400 |
| sort | `default` 综合（认证学生优先）/ `new` 最新 / `priceAsc` / `priceDesc` / `hot` 最热 |

只返回 `status=1`。列表项示例：

```json
{
  "id": 12, "title": "九成新山地自行车",
  "sellerId": 3, "sellerNickname": "小明", "sellerAvatar": "/uploads/...",
  "categoryId": 6, "categoryName": "交通工具",
  "price": 240.00, "originalPrice": 899.00,
  "cover": "/uploads/2026/10/a.png",
  "status": 1, "statusLabel": "已上架",
  "viewCount": 8, "favoriteCount": 0, "isVerifiedSeller": 1,
  "auditReason": null, "createTime": "2026-10-21 09:57:20"
}
```

### 3.7 商品详情
`GET /api/product/{id}`　免登录（携带 token 时识别访问者）

```json
{
  "id": 12, "title": "…", "description": "…",
  "price": 240.00, "originalPrice": null,
  "images": ["/uploads/a.png", "/uploads/b.png"],
  "status": 1, "statusLabel": "已上架",
  "viewCount": 9, "favoriteCount": 0, "isVerifiedSeller": true,
  "auditReason": null, "createTime": "2026-10-21 09:57:20",
  "isOwner": false, "categoryId": 6, "categoryName": "交通工具",
  "seller": { "id": 3, "nickname": "小明", "avatar": "/uploads/...",
              "creditScore": 100, "studentVerified": true, "onSaleCount": 5 }
}
```

浏览数每次非卖家访问 +1（SQL 原子自增），卖家自己查看不计数。

### 3.8 审核列表
`GET /api/product/audit/page`　管理员

参数在 3.6 基础上增加：

| 参数 | 说明 |
|---|---|
| status | 不传为全部；0/1/2/3/4 对应五种状态 |
| startTime / endTime | 发布时间区间，支持 `yyyy-MM-dd` 或 `yyyy-MM-dd HH:mm:ss` |

不筛选状态时**待审核排最前**，便于审核人员处理。

### 3.9 审核商品
`PUT /api/product/{id}/audit`　管理员

```json
{ "status": 1, "auditReason": "图片不清晰，请重新拍摄" }
```

`status=1` 通过并上架；`status=2` 驳回，`auditReason` 必填，商品状态置为 `2 已下架`，卖家可修改后重新提交。

### 3.10 强制下架
`PUT /api/product/{id}/force-offline`　管理员

```json
{ "reason": "涉嫌违规商品" }
```

仅 `status=1` 可强制下架，结果状态为 `4 违规下架`，原因展示给卖家。

## 4. 复用接口

`POST /api/file/upload`　登录用户，`multipart/form-data`，字段 `file`，≤5MB，返回 `/uploads/yyyy/MM/xxx.png`。分类图标与商品图片共用。

## 5. 错误码速查

| code | 场景 |
|---|---|
| 200 | 成功 |
| 400 | 参数校验失败（标题超长、价格非法、图片数量、驳回原因为空等） |
| 401 | 未登录 |
| 403 | 普通用户发布、非卖家编辑、非管理员操作 |
| 500 | 业务失败（额度超限、分类停用、状态不允许、分类下有商品等） |
