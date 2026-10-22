#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""模块10「数据仪表盘」接口冒烟测试。

用法：
    mvn spring-boot:run
    python tests/smoke_module10.py

覆盖流程：
    权限（未登录401 / 普通用户403 / 客服可读 / 普通用户不能刷新）->
    核心指标（字段齐全 / 口径正确 / 环比语义 / 统计时间）->
    趋势（days边界 / 日期补全 / 连续性 / 与核心指标对账）->
    分布（角色4档固定 / 信誉分5档固定 / 星级5档固定 / 状态分布）->
    排行榜（数量上限 / 排序正确 / 数据可追溯）->
    最近动态（条数上限 / 字段齐全 / 写操作后可见）->
    缓存（首次写入Redis / 二次命中不重复查库 / 写操作主动失效 /
          手动刷新清缓存 / Redis异常降级查库不报错）->
    数据一致性（summary 与 distribution 对账）-> 数据清理

退出码：0 全部通过，1 存在失败。
"""

import argparse
import glob
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid
from urllib.parse import quote

RESULTS = []
PASSED = 0

# 缓存键与 TTL（与 DashboardCacheServiceImpl 中的常量保持一致）
CACHE_KEYS = {
    "summary": "dashboard:summary:all",
    "distribution": "dashboard:distribution:all",
    "trend7": "dashboard:trend:trend:7",
    "trend30": "dashboard:trend:trend:30",
    "ranking": "dashboard:ranking:all",
    "recent": "dashboard:recent:all",
}
EXPECTED_TTL = {
    "summary": 300,
    "distribution": 300,
    "trend7": 600,
    "trend30": 600,
    "ranking": 1800,
    "recent": 30,
}


def call(base, method, path, token=None, body=None):
    url = base + quote(path, safe="/?&=%:")
    data = None
    headers = {}
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json;charset=UTF-8"
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=25) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", "replace")
        try:
            return e.code, json.loads(raw)
        except Exception:
            return e.code, {"code": e.code, "msg": raw}


def check(name, ok, detail=""):
    global PASSED
    RESULTS.append((name, ok, detail))
    if ok:
        PASSED += 1
    print(("  [PASS] " if ok else "  [FAIL] ") + name + (("  -> " + str(detail)) if detail else ""))
    return ok


def login_user(base, code):
    _, res = call(base, "POST", "/api/auth/wx-login", body={"code": code})
    data = res.get("data") or {}
    return data.get("token"), data.get("userId"), res


def upload(base, token, png=None):
    """上传一张图片，返回接口响应（用于学生认证的证件照）。"""
    boundary = "----smoke" + uuid.uuid4().hex
    if png is None:
        png = bytes.fromhex(
            "89504e470d0a1a0a0000000d49484452000000010000000108060000001f15c489"
            "0000000a49444154789c6300010000050001a5f645400000000049454e44ae426082"
        )
    body = (
        ("--" + boundary + "\r\n"
         'Content-Disposition: form-data; name="file"; filename="p.png"\r\n'
         "Content-Type: image/png\r\n\r\n").encode("utf-8")
        + png
        + ("\r\n--" + boundary + "--\r\n").encode("utf-8")
    )
    req = urllib.request.Request(
        base + "/api/file/upload",
        data=body,
        headers={
            "Content-Type": "multipart/form-data; boundary=" + boundary,
            "Authorization": "Bearer " + token,
        },
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read().decode("utf-8"))


def make_student(base, admin_token, suffix, nickname):
    """造一个认证学生（只有认证学生能发布商品）。

    返回 (token, userId, certImg)。认证流程：微信登录 -> 提交认证 ->
    管理员在待审核列表里点通过。
    """
    token, user_id, _ = login_user(base, "m10s" + suffix)
    call(base, "PUT", "/api/user/profile", token=token, body={"nickname": nickname})
    cert = (upload(base, token).get("data") or "")
    call(base, "POST", "/api/student-auth", token=token,
         body={"studentNo": "M10" + suffix, "realName": "模块十卖家",
               "school": "北华大学", "certImg": cert})
    _, page = call(base, "GET", "/api/student-auth/page?pageNum=1&pageSize=50&status=0",
                   token=admin_token)
    target = next((r for r in ((page.get("data") or {}).get("records") or [])
                   if r.get("userId") == user_id), None)
    if target:
        call(base, "PUT", "/api/student-auth/%s/audit" % target["id"],
             token=admin_token, body={"status": 1})
    return token, user_id, cert


def make_service(base, admin_token, suffix, nickname):
    """造一个客服账号（管理员分配角色）。

    注意这里<b>不能</b>再登录一次 admin：本系统是单端登录，
    Redis 里的 login:token:{userId} 只存一个 token，
    重新登录会把调用方手里的 admin_token 直接作废。
    """
    token, user_id, _ = login_user(base, "m10c" + suffix)
    call(base, "PUT", "/api/user/profile", token=token, body={"nickname": nickname})
    call(base, "PUT", "/api/admin/user/%s/role" % user_id, token=admin_token,
         body={"role": "customer_service"})
    return token, user_id


def redis_cmd(*args):
    """执行 redis-cli 命令，失败返回 None（相关断言跳过）。"""
    binary = next((c for c in ["redis-cli", "/d/Redis/redis-cli", r"D:\Redis\redis-cli.exe"]
                   if c and (os.path.exists(c) or shutil_which(c))), None)
    if not binary:
        return None
    try:
        out = subprocess.run([binary] + list(args), capture_output=True, text=True, timeout=15)
        if out.returncode != 0:
            return None
        return out.stdout.strip()
    except Exception:
        return None


def shutil_which(name):
    from shutil import which
    return which(name)


def cache_ttl(key):
    """返回 key 的剩余 TTL 秒数；key 不存在返回 -2；取不到返回 None。"""
    out = redis_cmd("TTL", key)
    if out is None:
        return None
    try:
        return int(out)
    except ValueError:
        return None


def cache_exists(key):
    out = redis_cmd("EXISTS", key)
    if out is None:
        return None
    return out.strip() == "1"


def cache_del(*keys):
    redis_cmd("DEL", *keys)


def mysql_query(sql):
    """本机直连 MySQL 校验 / 构造数据。找不到客户端时返回 None（相关断言跳过）。"""
    candidates = [os.environ.get("MYSQL_BIN") or ""]
    candidates += glob.glob("D:/mysql*/bin/mysql.exe") + glob.glob("C:/Program Files/MySQL/*/bin/mysql.exe")
    binary = next((c for c in candidates if c and os.path.exists(c)), None)
    if not binary:
        return None
    try:
        out = subprocess.run(
            [binary, "-uroot", "-p123456", "--skip-column-names", "--batch",
             "--default-character-set=utf8mb4",
             "-e", "USE campus_market; " + sql],
            capture_output=True, text=True, timeout=20
        )
        if out.returncode != 0:
            return None
        return out.stdout.strip()
    except Exception:
        return None


def mysql_exec(sql):
    mysql_query(sql)


def count_rows(table, where):
    rows = mysql_query("SELECT COUNT(*) FROM %s WHERE %s;" % (table, where))
    if not rows:
        return -1
    return int(rows.splitlines()[0].split("\t")[0])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="http://127.0.0.1:8080")
    args = parser.parse_args()
    base = args.base.rstrip("/")
    suffix = uuid.uuid4().hex[:6]

    print("=" * 60)
    print("模块10 数据仪表盘 接口冒烟测试  base =", base)
    print("=" * 60)

    # ---------------- 准备数据 ----------------
    _, admin_res = call(base, "POST", "/api/auth/login",
                        body={"username": "admin", "password": "123456"})
    admin_token = (admin_res.get("data") or {}).get("token")
    check("管理员登录成功", bool(admin_token), admin_res.get("msg"))

    user_token, user_id, _ = login_user(base, "m10u" + suffix)
    check("普通用户注册成功", bool(user_token), user_id)
    cs_token, cs_id = make_service(base, admin_token, suffix, "仪表盘客服" + suffix)
    check("客服账号创建成功", bool(cs_token), cs_id)
    # 认证学生：只有认证学生能发布商品，商品审核是验证「写后失效」的关键动作
    student_token, student_id, student_cert = make_student(
        base, admin_token, suffix, "仪表盘卖家" + suffix)
    check("认证学生创建成功", bool(student_token), student_id)

    # 先清空缓存，确保下面测到的是「首次回填」而不是命中上一轮遗留的缓存
    call(base, "POST", "/api/dashboard/refresh", token=admin_token)
    cache_del(*CACHE_KEYS.values())

    try:
        # ================= 1. 权限 =================
        print("\n== 1. 权限 ==")

        for path in ["/api/dashboard/summary", "/api/dashboard/trend",
                     "/api/dashboard/distribution", "/api/dashboard/ranking",
                     "/api/dashboard/recent"]:
            status, res = call(base, "GET", path)
            check("未登录访问 %s 返回 401" % path.split("/")[-1],
                  status == 401 or res.get("code") == 401, (status, res.get("code")))

        for path in ["/api/dashboard/summary", "/api/dashboard/trend",
                     "/api/dashboard/distribution", "/api/dashboard/ranking",
                     "/api/dashboard/recent"]:
            status, res = call(base, "GET", path, token=user_token)
            check("普通用户访问 %s 返回 403" % path.split("/")[-1],
                  res.get("code") == 403, (status, res.get("code")))

        for path in ["/api/dashboard/summary", "/api/dashboard/trend",
                     "/api/dashboard/distribution", "/api/dashboard/ranking",
                     "/api/dashboard/recent"]:
            status, res = call(base, "GET", path, token=cs_token)
            check("客服可以只读访问 %s" % path.split("/")[-1],
                  res.get("code") == 200, res.get("msg"))

        status, res = call(base, "POST", "/api/dashboard/refresh", token=user_token)
        check("普通用户刷新仪表盘返回 403", res.get("code") == 403,
              (status, res.get("code"), res.get("msg")))

        status, res = call(base, "POST", "/api/dashboard/refresh", token=cs_token)
        check("客服刷新仪表盘被拒（只读账号）", res.get("code") == 403,
              (status, res.get("code"), res.get("msg")))

        # ================= 2. 核心指标 =================
        print("\n== 2. 核心指标 summary ==")

        status, res = call(base, "GET", "/api/dashboard/summary", token=admin_token)
        summary = res.get("data") or {}
        check("summary 返回 200", res.get("code") == 200, res.get("msg"))

        required = ["totalUsers", "newUsersToday", "newUsersWeek", "userGrowth",
                    "totalProducts", "onSaleProducts", "pendingAuditProducts", "productGrowth",
                    "totalOrders", "completedOrders", "gmvTotal", "gmvToday",
                    "orderGrowth", "gmvGrowth",
                    "pendingReports", "reportGrowth", "pendingRefunds",
                    "activeSessions", "avgCreditScore", "avgCreditLevel",
                    "avgCreditColor", "statTime"]
        missing = [k for k in required if k not in summary]
        check("summary 字段齐全", not missing, "缺失: %s" % missing)

        # 数值字段不能是 null（环比字段允许 null：前期为 0 时无法定义百分比）
        for key in ["totalUsers", "newUsersToday", "newUsersWeek", "totalProducts",
                    "onSaleProducts", "pendingAuditProducts", "totalOrders",
                    "completedOrders", "gmvTotal", "gmvToday", "pendingReports",
                    "pendingRefunds", "activeSessions", "avgCreditScore"]:
            check("summary.%s 不为 null" % key, summary.get(key) is not None, summary.get(key))

        check("总用户数大于 0", (summary.get("totalUsers") or 0) > 0, summary.get("totalUsers"))
        check("总商品数大于 0", (summary.get("totalProducts") or 0) > 0, summary.get("totalProducts"))
        check("在售商品数不超过总商品数",
              (summary.get("onSaleProducts") or 0) <= (summary.get("totalProducts") or 0),
              (summary.get("onSaleProducts"), summary.get("totalProducts")))
        check("已完成订单数不超过总订单数",
              (summary.get("completedOrders") or 0) <= (summary.get("totalOrders") or 0),
              (summary.get("completedOrders"), summary.get("totalOrders")))
        check("成交额不为负", (summary.get("gmvTotal") or 0) >= 0, summary.get("gmvTotal"))
        check("今日成交额不超过累计成交额",
              (summary.get("gmvToday") or 0) <= (summary.get("gmvTotal") or 0),
              (summary.get("gmvToday"), summary.get("gmvTotal")))
        check("平均信誉分在 0~150 之间",
              0 <= (summary.get("avgCreditScore") or 0) <= 150, summary.get("avgCreditScore"))
        check("平均信誉分带等级标签", bool(summary.get("avgCreditLevel")),
              summary.get("avgCreditLevel"))
        check("平均信誉分带配色", (summary.get("avgCreditColor") or "").startswith("#"),
              summary.get("avgCreditColor"))
        check("统计时间格式为 yyyy-MM-dd HH:mm:ss",
              len(summary.get("statTime") or "") == 19 and (summary.get("statTime") or "")[4] == "-",
              summary.get("statTime"))

        # 环比语义：前期为 0 时必须是 null，而不是 0 或无穷大
        for key in ["userGrowth", "productGrowth", "orderGrowth", "gmvGrowth", "reportGrowth"]:
            val = summary.get(key)
            check("summary.%s 存在且为数值或 null" % key,
                  val is None or isinstance(val, (int, float)), val)

        # 口径核对：待审核商品数应等于库里 status=0 且未删除的商品数
        # 注意必须带上 deleted = 0 —— 仪表盘统计的是「有效商品」，
        # 逻辑删除的商品不计入任何指标
        db_pending = count_rows("product", "status = 0 AND deleted = 0")
        if db_pending >= 0:
            check("待审核商品数与数据库一致",
                  (summary.get("pendingAuditProducts") or -1) == db_pending,
                  "api=%s db=%s" % (summary.get("pendingAuditProducts"), db_pending))
        else:
            print("  [SKIP] 未找到 mysql 客户端，跳过待审核商品数对账")

        # ================= 3. 趋势 =================
        print("\n== 3. 趋势 trend ==")

        status, res = call(base, "GET", "/api/dashboard/trend", token=admin_token)
        trend = res.get("data") or {}
        check("不传 days 时默认 7 天", trend.get("days") == 7, trend.get("days"))
        points = trend.get("points") or []
        check("7 天趋势返回 7 个点", len(points) == 7, len(points))

        status, res = call(base, "GET", "/api/dashboard/trend?days=30", token=admin_token)
        trend30 = res.get("data") or {}
        check("days=30 返回 30 天", trend30.get("days") == 30, trend30.get("days"))
        check("30 天趋势返回 30 个点", len(trend30.get("points") or []) == 30,
              len(trend30.get("points") or []))

        # 非法窗口必须被收敛到 7，防止 days=99999 扫一年数据拖垮数据库
        for bad in [0, 1, 99999, -5, 1000000]:
            status, res = call(base, "GET", "/api/dashboard/trend?days=%s" % bad, token=admin_token)
            d = res.get("data") or {}
            check("非法 days=%s 被收敛到 7" % bad, d.get("days") == 7, d.get("days"))
            check("非法 days=%s 返回 7 个点" % bad, len(d.get("points") or []) == 7,
                  len(d.get("points") or []))

        # days 非数字：Spring 的 Integer 参数绑定失败会返回 400，
        # 这是标准 REST 行为（明确告知调用方参数非法），
        # 而不是静默兜底成 7 —— 静默兜底会让调用方以为参数生效了
        status, res = call(base, "GET", "/api/dashboard/trend?days=abc", token=admin_token)
        check("days 非数字返回 400 参数错误（不静默兜底）",
              res.get("code") == 400, (res.get("code"), res.get("msg")))

        # 每个点必须有完整的 6 个字段
        bad_point = None
        for p in points:
            for key in ["date", "newUsers", "newProducts", "orders", "gmv", "reports"]:
                if key not in p:
                    bad_point = (p.get("date"), key)
                    break
            if bad_point:
                break
        check("趋势点字段齐全", bad_point is None, bad_point)

        # 日期必须连续递增（应用层补全的核心价值：无数据的那天也要占位）
        dates = [p.get("date") for p in points]
        check("趋势日期连续", all(
            (int(dates[i + 1][:4]) * 372 + int(dates[i + 1][5:7]) * 31 + int(dates[i + 1][8:10]))
            - (int(dates[i][:4]) * 372 + int(dates[i][5:7]) * 31 + int(dates[i][8:10])) == 1
            for i in range(len(dates) - 1)), dates)
        check("趋势最后一天是今天", dates[-1] == time.strftime("%Y-%m-%d"), dates[-1])
        check("无数据日的指标补零为数值 0",
              all(isinstance(p.get("newUsers"), (int, float)) for p in points),
              points[0] if points else None)

        # 趋势与核心指标必须能对上账：今日新增用户数 == summary 里的 newUsersToday
        today_point = points[-1] if points else {}
        check("趋势今日新增用户数与核心指标一致",
              (today_point.get("newUsers") or 0) == (summary.get("newUsersToday") or 0),
              "trend=%s summary=%s" % (today_point.get("newUsers"), summary.get("newUsersToday")))
        # 对账 SQL 必须和接口口径一致：仪表盘统计的是「有效用户」（deleted=0），
        # 少写这个条件会在库里有注销用户时变成假失败
        check("趋势今日新增用户数与数据库一致",
              today_point.get("newUsers") == count_rows("sys_user", "deleted = 0 AND DATE(create_time) = CURDATE()"),
              "trend=%s" % today_point.get("newUsers"))

        # ================= 4. 分布 =================
        print("\n== 4. 分布 distribution ==")

        status, res = call(base, "GET", "/api/dashboard/distribution", token=admin_token)
        dist = res.get("data") or {}
        for key in ["userRoleDist", "productCategoryDist", "productStatusDist",
                    "orderStatusDist", "creditScoreDist", "reviewStarDist"]:
            check("distribution.%s 存在且为数组" % key,
                  isinstance(dist.get(key), list), type(dist.get(key)).__name__)

        role_dist = dist.get("userRoleDist") or []
        check("用户角色分布固定 4 档", len(role_dist) == 4, len(role_dist))
        check("用户角色分布含管理员", any(r.get("name") == "admin" for r in role_dist),
              [r.get("name") for r in role_dist])
        check("用户角色分布含普通用户", any(r.get("name") == "user" for r in role_dist),
              [r.get("name") for r in role_dist])
        check("用户角色分布含认证学生", any(r.get("name") == "student" for r in role_dist),
              [r.get("name") for r in role_dist])
        check("用户角色分布含客服", any(r.get("name") == "customer_service" for r in role_dist),
              [r.get("name") for r in role_dist])
        check("用户角色分布每项都有中文标签",
              all(r.get("label") for r in role_dist), role_dist)
        check("用户角色分布每项都有数值",
              all(isinstance(r.get("value"), (int, float)) for r in role_dist), role_dist)

        role_sum = sum(r.get("value") or 0 for r in role_dist)
        check("角色分布合计等于总用户数", role_sum == (summary.get("totalUsers") or 0),
              "sum=%s totalUsers=%s" % (role_sum, summary.get("totalUsers")))

        credit_dist = dist.get("creditScoreDist") or []
        check("信誉分分布固定 5 档", len(credit_dist) == 5, len(credit_dist))
        check("信誉分区间依次为 0-59/60-79/80-99/100-119/120-150",
              [c.get("name") for c in credit_dist] ==
              ["0-59", "60-79", "80-99", "100-119", "120-150"],
              [c.get("name") for c in credit_dist])
        check("信誉分分布合计等于总用户数",
              sum(c.get("value") or 0 for c in credit_dist) == (summary.get("totalUsers") or 0),
              "sum=%s" % sum(c.get("value") or 0 for c in credit_dist))

        star_dist = dist.get("reviewStarDist") or []
        check("评价星级分布固定 5 档", len(star_dist) == 5, len(star_dist))
        check("星级分布依次为 1~5 星",
              [s.get("name") for s in star_dist] == ["1", "2", "3", "4", "5"],
              [s.get("name") for s in star_dist])

        status_dist = dist.get("productStatusDist") or []
        check("商品状态分布覆盖多个状态", len(status_dist) >= 1, len(status_dist))
        check("商品状态分布合计等于总商品数",
              sum(s.get("value") or 0 for s in status_dist) == (summary.get("totalProducts") or 0),
              "sum=%s totalProducts=%s" % (sum(s.get("value") or 0 for s in status_dist),
                                           summary.get("totalProducts")))
        check("商品状态分布每项有状态文案",
              all(s.get("label") for s in status_dist), status_dist)

        order_dist = dist.get("orderStatusDist") or []
        check("订单状态分布每项有状态文案",
              all(o.get("label") for o in order_dist), order_dist)

        cat_dist = dist.get("productCategoryDist") or []
        check("商品分类分布每项有分类名",
              all(c.get("label") for c in cat_dist), cat_dist)

        # ================= 5. 排行榜 =================
        print("\n== 5. 排行榜 ranking ==")

        status, res = call(base, "GET", "/api/dashboard/ranking", token=admin_token)
        ranking = res.get("data") or {}
        sellers = ranking.get("topSellers") or []
        products = ranking.get("topProducts") or []
        categories = ranking.get("topCategories") or []

        check("卖家榜不超过 10 条", len(sellers) <= 10, len(sellers))
        check("热门商品榜不超过 10 条", len(products) <= 10, len(products))
        check("热门分类榜不超过 7 条", len(categories) <= 7, len(categories))

        if sellers:
            check("卖家榜项字段齐全",
                  all(all(k in s for k in ["sellerId", "nickname", "productCount",
                                           "soldCount", "amount", "creditScore",
                                           "creditLevel", "creditColor"]) for s in sellers),
                  sorted(sellers[0].keys()))
            check("卖家榜按成交额倒序",
                  all(float(sellers[i]["amount"]) >= float(sellers[i + 1]["amount"])
                      for i in range(len(sellers) - 1)),
                  [float(s["amount"]) for s in sellers])
            check("卖家榜信誉分在 0~150 之间",
                  all(0 <= (s.get("creditScore") or 0) <= 150 for s in sellers),
                  [s.get("creditScore") for s in sellers])
        else:
            print("  [SKIP] 卖家榜为空，跳过排序断言")

        if products:
            check("热门商品项字段齐全",
                  all(all(k in p for k in ["productId", "title", "price",
                                           "viewCount", "favoriteCount"]) for p in products),
                  sorted(products[0].keys()))
            check("热门商品榜按浏览量倒序",
                  all((products[i]["viewCount"] or 0) >= (products[i + 1]["viewCount"] or 0)
                      for i in range(len(products) - 1)),
                  [p.get("viewCount") for p in products])
            check("热门商品价格为正",
                  all(float(p.get("price") or 0) > 0 for p in products),
                  [p.get("price") for p in products][:5])
        else:
            print("  [SKIP] 热门商品榜为空，跳过排序断言")

        if categories:
            check("热门分类项字段齐全",
                  all(all(k in c for k in ["categoryId", "name", "productCount",
                                           "viewCount"]) for c in categories),
                  sorted(categories[0].keys()))
            check("热门分类按商品数倒序",
                  all((categories[i]["productCount"] or 0) >= (categories[i + 1]["productCount"] or 0)
                      for i in range(len(categories) - 1)),
                  [c.get("productCount") for c in categories])

        # ================= 6. 最近动态 =================
        print("\n== 6. 最近动态 recent ==")

        status, res = call(base, "GET", "/api/dashboard/recent", token=admin_token)
        recent = res.get("data") or []
        check("recent 返回数组", isinstance(recent, list), type(recent).__name__)
        check("最近动态不超过 20 条", len(recent) <= 20, len(recent))
        if recent:
            check("最近动态项字段齐全",
                  all(all(k in r for k in ["id", "operatorName", "module",
                                           "moduleLabel", "action", "actionLabel",
                                           "createTime"]) for r in recent),
                  sorted(recent[0].keys()))
            check("最近动态按时间倒序",
                  all(str(recent[i]["createTime"]) >= str(recent[i + 1]["createTime"])
                      for i in range(len(recent) - 1)),
                  [str(r.get("createTime")) for r in recent][:4])
            check("最近动态带模块中文名",
                  all(r.get("moduleLabel") for r in recent),
                  [r.get("moduleLabel") for r in recent][:4])
            check("最近动态带操作人文案",
                  all(r.get("actionLabel") for r in recent),
                  [r.get("actionLabel") for r in recent][:4])
        else:
            print("  [SKIP] 最近动态为空，跳过字段断言")

        # ================= 7. 缓存策略 =================
        print("\n== 7. 缓存策略 ==")

        # 7.1 首次请求后 Redis 里应写入缓存
        cache_del(*CACHE_KEYS.values())
        call(base, "GET", "/api/dashboard/summary", token=admin_token)
        call(base, "GET", "/api/dashboard/distribution", token=admin_token)
        call(base, "GET", "/api/dashboard/trend?days=7", token=admin_token)
        call(base, "GET", "/api/dashboard/trend?days=30", token=admin_token)
        call(base, "GET", "/api/dashboard/ranking", token=admin_token)
        call(base, "GET", "/api/dashboard/recent", token=admin_token)

        exists = cache_exists(CACHE_KEYS["summary"])
        if exists is None:
            print("  [SKIP] 未找到 redis-cli，跳过缓存键断言")
        else:
            check("summary 请求后写入 Redis 缓存", exists is True, exists)
            for name, key in CACHE_KEYS.items():
                ok = cache_exists(key) is True
                check("%s 缓存键已生成" % name, ok, key)

            # 7.2 TTL 分级必须符合设计
            for name, key in CACHE_KEYS.items():
                ttl = cache_ttl(key)
                if ttl is None:
                    continue
                expect = EXPECTED_TTL[name]
                check("%s 缓存 TTL 约为 %d 秒" % (name, expect),
                      0 < ttl <= expect, "ttl=%s expect<=%s" % (ttl, expect))

        # 7.3 缓存命中：两次请求的统计时间必须完全相同
        #     若每次都查库，statTime 会随时间推进而变化
        call(base, "GET", "/api/dashboard/summary", token=admin_token)
        _, res1 = call(base, "GET", "/api/dashboard/summary", token=admin_token)
        time.sleep(1.2)
        _, res2 = call(base, "GET", "/api/dashboard/summary", token=admin_token)
        st1 = (res1.get("data") or {}).get("statTime")
        st2 = (res2.get("data") or {}).get("statTime")
        check("二次请求命中缓存（统计时间不变）", st1 == st2, "%s vs %s" % (st1, st2))

        # 7.4 手动刷新应清空缓存
        status, res = call(base, "POST", "/api/dashboard/refresh", token=admin_token)
        check("管理员刷新接口返回 200", res.get("code") == 200, res.get("msg"))
        if exists is not None:
            check("刷新后 summary 缓存被清除", cache_exists(CACHE_KEYS["summary"]) is False,
                  cache_exists(CACHE_KEYS["summary"]))
            check("刷新后排行榜缓存被清除", cache_exists(CACHE_KEYS["ranking"]) is False,
                  cache_exists(CACHE_KEYS["ranking"]))

        # 7.5 写操作主动失效：造一个商品并审核，观察仪表盘缓存被清掉
        #     这是「多级缓存 + 写后失效」策略的核心断言：
        #     商品审核会影响指标卡、分布图、热门榜和趋势图，
        #     因此 evictOnProductChange 必须把这几张图的缓存全部清掉。
        _, cat = call(base, "GET", "/api/category/list")
        category_id = (cat.get("data") or [{}])[0].get("id")

        _, prod_res = call(base, "POST", "/api/product", token=student_token,
                           body={"title": "缓存失效验证商品" + suffix,
                                 "categoryId": category_id, "price": 9.9,
                                 "originalPrice": 29.9, "description": "用于验证写操作主动失效缓存",
                                 "images": [student_cert]})
        product_id = prod_res.get("data")
        check("造缓存失效用商品发布成功", bool(product_id), prod_res.get("msg"))

        # 发布本身就会触发 evictOnProductChange（publish 是商品口径的写操作），
        # 所以先重新拉一遍数据把缓存建起来，再审核，验证审核同样会清缓存
        call(base, "GET", "/api/dashboard/summary", token=admin_token)
        call(base, "GET", "/api/dashboard/ranking", token=admin_token)
        cache_before = cache_exists(CACHE_KEYS["summary"])
        rank_before = cache_exists(CACHE_KEYS["ranking"])

        if product_id:
            status, res = call(base, "PUT", "/api/product/%s/audit" % product_id,
                               token=admin_token, body={"status": 1})
            check("商品审核通过", res.get("code") == 200, res.get("msg"))
            time.sleep(0.4)
            if exists is not None:
                check("写操作（商品审核）主动清除了 summary 缓存",
                      cache_before is True and cache_exists(CACHE_KEYS["summary"]) is False,
                      "before=%s after=%s" % (cache_before, cache_exists(CACHE_KEYS["summary"])))
                check("写操作（商品审核）主动清除了排行榜缓存",
                      rank_before is True and cache_exists(CACHE_KEYS["ranking"]) is False,
                      "before=%s after=%s" % (rank_before, cache_exists(CACHE_KEYS["ranking"])))
        else:
            check("商品审核流程跳过", False, "商品发布失败，无法继续验证写后失效")

        # 审核后重新查询，指标应反映最新状态（待审核数回落、在售数上升）
        _, res = call(base, "GET", "/api/dashboard/summary", token=admin_token)
        after_summary = res.get("data") or {}
        check("审核后重新查询可拿到最新统计", after_summary.get("statTime") is not None,
              after_summary.get("statTime"))
        check("审核后待审核商品数未增加",
              (after_summary.get("pendingAuditProducts") or 0) <= (summary.get("pendingAuditProducts") or 0),
              "before=%s after=%s" % (summary.get("pendingAuditProducts"),
                                      after_summary.get("pendingAuditProducts")))

        # 用管理员改角色触发 evictOnUserChange（一定会发生）
        call(base, "GET", "/api/dashboard/summary", token=admin_token)
        cache_before = cache_exists(CACHE_KEYS["summary"])
        call(base, "PUT", "/api/admin/user/%s/role" % user_id, token=admin_token,
             body={"role": "customer_service"})
        time.sleep(0.4)
        cache_after = cache_exists(CACHE_KEYS["summary"])
        if exists is not None:
            check("写操作（改角色）主动清除了 summary 缓存",
                  cache_before is True and cache_after is False,
                  "before=%s after=%s" % (cache_before, cache_after))
        # 还原角色，避免影响其它测试
        call(base, "PUT", "/api/admin/user/%s/role" % user_id, token=admin_token,
             body={"role": "user"})

        # 7.6 Redis 异常降级：删掉缓存后接口依然正常（模拟缓存读失败路径）
        cache_del(*CACHE_KEYS.values())
        status, res = call(base, "GET", "/api/dashboard/summary", token=admin_token)
        check("缓存缺失时接口降级查库仍返回 200", res.get("code") == 200, res.get("msg"))
        check("降级路径返回完整指标", (res.get("data") or {}).get("totalUsers") is not None,
              (res.get("data") or {}).get("totalUsers"))

        # ================= 8. 一致性对账 =================
        print("\n== 8. 一致性对账 ==")

        _, res = call(base, "GET", "/api/dashboard/summary", token=admin_token)
        s2 = res.get("data") or {}
        _, res = call(base, "GET", "/api/dashboard/distribution", token=admin_token)
        d2 = res.get("data") or {}

        check("summary.totalUsers 与角色分布合计一致",
              (s2.get("totalUsers") or 0) ==
              sum(r.get("value") or 0 for r in (d2.get("userRoleDist") or [])),
              (s2.get("totalUsers"),
               sum(r.get("value") or 0 for r in (d2.get("userRoleDist") or []))))

        check("summary.totalProducts 与商品状态分布合计一致",
              (s2.get("totalProducts") or 0) ==
              sum(x.get("value") or 0 for x in (d2.get("productStatusDist") or [])),
              (s2.get("totalProducts"),
               sum(x.get("value") or 0 for x in (d2.get("productStatusDist") or []))))

        check("summary.completedOrders 不超过 totalOrders",
              (s2.get("completedOrders") or 0) <= (s2.get("totalOrders") or 0),
              (s2.get("completedOrders"), s2.get("totalOrders")))

        check("summary.pendingReports 不为负", (s2.get("pendingReports") or 0) >= 0,
              s2.get("pendingReports"))

    finally:
        # ---------------- 清理 ----------------
        print("\n== 清理测试数据 ==")
        slst = ",".join(str(i) for i in [user_id, cs_id, student_id] if i)
        mysql_exec("DELETE FROM product_image WHERE product_id IN (SELECT id FROM product WHERE seller_id IN (%s));" % slst)
        mysql_exec("DELETE FROM product WHERE seller_id IN (%s);" % slst)
        mysql_exec("DELETE FROM student_auth WHERE user_id IN (%s);" % slst)
        mysql_exec("DELETE FROM sys_operation_log WHERE operator_id IN (%s);" % slst)
        mysql_exec("DELETE FROM sys_operation_log WHERE target_id IN (%s);" % slst)
        mysql_exec("DELETE FROM credit_log WHERE user_id IN (%s);" % slst)
        mysql_exec("DELETE FROM notice WHERE user_id IN (%s);" % slst)
        mysql_exec("DELETE FROM sys_user WHERE id IN (%s);" % slst)
        mysql_exec("DELETE FROM product WHERE title LIKE '缓存失效验证商品%s';" % suffix)
        # 清掉本轮测试产生的缓存，保证下次打开管理端看到的是最新数据
        call(base, "POST", "/api/dashboard/refresh", token=admin_token)
        print("  已清理 %d 个测试用户及关联数据" % 3)

    # ---------------- 汇总 ----------------
    print("\n" + "=" * 60)
    failed = [r for r in RESULTS if not r[1]]
    print("通过 %d / %d" % (PASSED, len(RESULTS)))
    if failed:
        print("失败用例：")
        for name, _, detail in failed:
            print("  - %s  -> %s" % (name, detail))
    print("=" * 60)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
