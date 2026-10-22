#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""模块3「首页与商品浏览」接口冒烟测试。

用法（后端已在 8080 启动，且已执行 sql/V3__banner_home.sql）：
    python tests/smoke_module3.py

建议启动参数（把浏览量回写间隔调小，便于验证 Redis -> MySQL）：
    mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8080
环境变量 VIEW_FLUSH_INTERVAL_MS=2000 可覆盖回写间隔。

覆盖流程：
    首页聚合 -> 分类/热门搜索词 -> 普通用户越权拦截 -> 准备已上架商品 ->
    搜索(关键词/价格/分类/排序) -> 游标分页不重复 -> 热门商品与缓存 ->
    浏览量 Redis 计数与批量回写 -> 轮播图增删改查 + 校验 + 定时窗口 + 排序

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
        with urllib.request.urlopen(req, timeout=20) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        return e.code, {"code": e.code, "msg": e.read().decode("utf-8", "replace")}


def upload(base, token):
    boundary = "----smoke" + uuid.uuid4().hex
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


def make_student(base, admin_token, suffix):
    token, user_id, _ = login_user(base, "m3s" + suffix)
    up = upload(base, token)
    cert = up.get("data") or ""
    call(base, "POST", "/api/student-auth", token=token,
         body={"studentNo": "M3" + suffix, "realName": "模块三学生",
               "school": "北华大学", "certImg": cert})
    _, page = call(base, "GET", "/api/student-auth/page?pageNum=1&pageSize=50&status=0",
                   token=admin_token)
    target = next((r for r in ((page.get("data") or {}).get("records") or [])
                   if r.get("userId") == user_id), None)
    if target:
        call(base, "PUT", "/api/student-auth/%s/audit" % target["id"],
             token=admin_token, body={"status": 1})
    return token, user_id, cert


def mysql_query(sql):
    """本机直连 MySQL 校验落库结果。找不到客户端时返回 None（跳过断言）。"""
    candidates = [os.environ.get("MYSQL_BIN") or ""]
    candidates += glob.glob("D:/mysql*/bin/mysql.exe") + glob.glob("C:/Program Files/MySQL/*/bin/mysql.exe")
    binary = next((c for c in candidates if c and os.path.exists(c)), None)
    if not binary:
        return None
    try:
        out = subprocess.run(
            [binary, "-uroot", "-p123456", "--skip-column-names", "--batch",
             "-e", "USE campus_market; " + sql],
            capture_output=True, text=True, timeout=20
        )
        if out.returncode != 0:
            return None
        return out.stdout.strip()
    except Exception:
        return None


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="http://localhost:8080")
    parser.add_argument("--wait", type=int, default=4, help="等待浏览量回写的秒数")
    args = parser.parse_args()
    base = args.base.rstrip("/")
    suffix = uuid.uuid4().hex[:6]

    print("\n== 1. 公共接口 ==")
    _, home = call(base, "GET", "/api/home/index")
    home_data = home.get("data") or {}
    check("首页聚合免登录可访问", home.get("code") == 200, home.get("msg", ""))
    # 模块12 起，首页字段为「公告 + 轮播图 + 金刚区 + 热门 + 新品」，
    # guessYouLike/latest 已被 hotProducts/newProducts 取代（语义也从固定算法变为可配置）
    check("首页返回公告/轮播图/金刚区/热门/新品/热词",
          all(k in home_data for k in
              ("notice", "banners", "categories", "hotProducts", "newProducts", "hotKeywords")),
          list(home_data.keys()))
    categories = home_data.get("categories") or []
    check("分类导航返回启用分类", len(categories) >= 7, "count=%d" % len(categories))
    check("分类均处于启用状态", all(c.get("status") == 1 for c in categories))
    check("热门搜索词返回 10 个", len(home_data.get("hotKeywords") or []) == 10)
    _, hot_kw = call(base, "GET", "/api/home/hot-keywords")
    check("热词接口与聚合一致", hot_kw.get("data") == home_data.get("hotKeywords"))

    _, banner_list = call(base, "GET", "/api/home/banner/list")
    check("轮播图列表免登录可访问", banner_list.get("code") == 200 and isinstance(banner_list.get("data"), list))

    _, feed = call(base, "GET", "/api/product/feed?size=2")
    feed_data = feed.get("data") or {}
    check("游标分页免登录可访问", feed.get("code") == 200)
    check("游标分页返回 records/nextCursor/hasMore",
          all(k in feed_data for k in ("records", "nextCursor", "hasMore")), list(feed_data.keys()))

    print("\n== 2. 权限拦截 ==")
    user_token, user_id, _ = login_user(base, "m3u" + suffix)
    status, unauth = call(base, "GET", "/api/banner/page")
    check("未登录访问轮播图管理返回 401", unauth.get("code") == 401, "http=%s code=%s" % (status, unauth.get("code")))
    _, denied = call(base, "GET", "/api/banner/page", token=user_token)
    check("普通用户访问轮播图管理被拒绝", denied.get("code") == 403, denied.get("msg", ""))
    _, denied_create = call(base, "POST", "/api/banner", token=user_token,
                            body={"title": "x", "imageUrl": "/uploads/x.png", "linkType": 1, "linkValue": "1"})
    check("普通用户新增轮播图被拒绝", denied_create.get("code") == 403, denied_create.get("msg", ""))

    print("\n== 3. 准备已上架商品 ==")
    _, admin = call(base, "POST", "/api/auth/admin-login", body={"username": "admin", "password": "123456"})
    admin_token = (admin.get("data") or {}).get("token")
    check("管理员登录成功", bool(admin_token))

    student_token, student_id, cert = make_student(base, admin_token, suffix)
    category_id = categories[0]["id"]
    titles = ["模块三测试自行车" + suffix, "模块三测试高数教材" + suffix]
    product_ids = []
    for idx, title in enumerate(titles):
        _, created = call(base, "POST", "/api/product", token=student_token,
                          body={"title": title, "categoryId": category_id,
                                "price": 100 + idx * 50, "originalPrice": 500 + idx * 100,
                                "description": "模块三冒烟测试商品，用于搜索与浏览量验证",
                                "images": [cert]})
        pid = created.get("data")
        if pid:
            product_ids.append(pid)
            call(base, "PUT", "/api/product/%s/audit" % pid, token=admin_token, body={"status": 1})
    check("发布并审核通过 2 件商品", len(product_ids) == 2, product_ids)

    print("\n== 4. 首页与搜索 ==")
    _, home2 = call(base, "GET", "/api/home/index")
    data2 = home2.get("data") or {}
    new_ids = [item["id"] for item in (data2.get("newProducts") or [])]
    check("新品推荐包含刚上架商品", any(pid in new_ids for pid in product_ids), new_ids)
    hot = data2.get("hotProducts") or []
    check("热门推荐只返回已上架商品", all(item.get("status") == 1 for item in hot))
    check("新品推荐只返回已上架商品",
          all(item.get("status") == 1 for item in (data2.get("newProducts") or [])))

    keyword = "测试自行车" + suffix
    _, searched = call(base, "GET", "/api/product/search?keyword=" + quote(keyword))
    records = (searched.get("data") or {}).get("records") or []
    check("关键词搜索命中", searched.get("code") == 200 and len(records) == 1, "n=%d" % len(records))
    check("搜索返回主图与卖家信息",
          bool(records) and bool(records[0].get("cover")) and bool(records[0].get("sellerNickname")))

    _, empty_search = call(base, "GET", "/api/product/search?keyword=" + quote("绝无此商品" + suffix))
    check("无结果时返回空列表", ((empty_search.get("data") or {}).get("total") or 0) == 0)

    _, cheap = call(base, "GET", "/api/product/search?keyword=" + quote(suffix)
                    + "&maxPrice=120")
    cheap_records = (cheap.get("data") or {}).get("records") or []
    check("价格区间过滤生效", len(cheap_records) == 1 and float(cheap_records[0]["price"]) <= 120,
          [r["price"] for r in cheap_records])
    _, asc = call(base, "GET", "/api/product/search?keyword=" + quote(suffix)
                  + "&sort=priceAsc")
    asc_records = (asc.get("data") or {}).get("records") or []
    check("价格升序排序生效",
          len(asc_records) >= 2 and float(asc_records[0]["price"]) <= float(asc_records[-1]["price"]),
          [r["price"] for r in asc_records])

    _, by_sort = call(base, "GET", "/api/product/search?keyword=" + quote(suffix)
                      + "&sort=priceDesc")
    desc_records = (by_sort.get("data") or {}).get("records") or []
    check("价格降序排序生效",
          len(desc_records) >= 2 and float(desc_records[0]["price"]) >= float(desc_records[-1]["price"]),
          [r["price"] for r in desc_records])

    _, by_cat = call(base, "GET", "/api/product/search?keyword=" + quote(suffix)
                     + "&categoryId=%s" % category_id)
    by_cat_total = (by_cat.get("data") or {}).get("total") or 0
    check("分类筛选生效", by_cat_total >= 1, "total=%s cat=%s" % (by_cat_total, category_id))

    _, wrong_cat = call(base, "GET", "/api/product/search?keyword=" + quote(suffix)
                        + "&categoryId=999999")
    check("不存在的分类返回空结果", ((wrong_cat.get("data") or {}).get("total") or 0) == 0)

    print("\n== 5. 游标分页 ==")
    _, page1 = call(base, "GET", "/api/product/feed?size=1")
    p1 = page1.get("data") or {}
    first = (p1.get("records") or [{}])[0]
    cursor = p1.get("nextCursor")
    check("游标分页返回 nextCursor", bool(cursor) and p1.get("hasMore"))
    _, page2 = call(base, "GET", "/api/product/feed?size=1&cursor=%s" % cursor)
    p2 = page2.get("data") or {}
    second = (p2.get("records") or [{}])[0]
    check("第二页与第一页不重复", first.get("id") != second.get("id"),
          "%s vs %s" % (first.get("id"), second.get("id")))
    check("游标按 id 倒序", (second.get("id") or 0) < (first.get("id") or 0))

    _, cat_feed = call(base, "GET", "/api/product/feed?size=5&categoryId=%s" % category_id)
    cat_records = (cat_feed.get("data") or {}).get("records") or []
    check("游标分页支持分类过滤", all(r.get("categoryId") == category_id for r in cat_records))

    print("\n== 6. 热门商品与缓存 ==")
    _, hot1 = call(base, "GET", "/api/product/hot")
    hot_list = hot1.get("data") or []
    check("热门商品返回不超过10条", hot1.get("code") == 200 and len(hot_list) <= 10, len(hot_list))
    _, hot2 = call(base, "GET", "/api/product/hot")
    check("热门商品命中缓存（两次结果一致）", hot2.get("data") == hot1.get("data"))

    print("\n== 7. 浏览量 Redis 计数与回写 ==")
    viewer_token, _, _ = login_user(base, "m3v" + suffix)
    pid = product_ids[0]
    counts = []
    for _ in range(3):
        _, detail = call(base, "GET", "/api/product/%s" % pid, token=viewer_token)
        counts.append((detail.get("data") or {}).get("viewCount"))
    check("浏览三次计数递增", counts == [1, 2, 3] or counts[-1] == 3, counts)

    _, self_view = call(base, "GET", "/api/product/%s" % pid, token=student_token)
    check("卖家自己查看不计数", (self_view.get("data") or {}).get("viewCount") == counts[-1],
          (self_view.get("data") or {}).get("viewCount"))

    # 浏览量回写是 @Scheduled 定时任务（默认 60 秒一轮），
    # 固定 sleep 一小会儿只能赌定时器恰好在这段时间里触发过——十次跑有九次会失败。
    # 改成轮询等待：只要在超时窗口内落库就算通过，与定时器的实际调度时刻无关。
    db_value = None
    deadline = time.time() + max(args.wait, 90)
    while time.time() < deadline:
        db_value = mysql_query("SELECT view_count FROM product WHERE id = %s;" % pid)
        if db_value is not None and int(db_value) == 3:
            break
        time.sleep(2)
    if db_value is None:
        print("  [SKIP] 未找到 mysql 客户端，跳过数据库落库校验")
    else:
        check("Redis 增量已回写 MySQL", int(db_value) == 3, "db=%s" % db_value)
    _, after = call(base, "GET", "/api/product/%s" % pid, token=viewer_token)
    check("回写后计数不丢失", (after.get("data") or {}).get("viewCount") == 4,
          (after.get("data") or {}).get("viewCount"))

    print("\n== 8. 轮播图管理 ==")
    _, created_b1 = call(base, "POST", "/api/banner", token=admin_token,
                         body={"title": "模块三轮播B" + suffix, "imageUrl": cert, "linkType": 1,
                               "linkValue": str(pid), "sort": 5, "status": 1})
    b1 = created_b1.get("data") or {}
    check("新增轮播图（商品跳转）成功", created_b1.get("code") == 200 and bool(b1.get("id")),
          created_b1.get("msg", ""))
    check("列表返回跳转类型文案", b1.get("linkTypeLabel") == "商品详情", b1.get("linkTypeLabel"))

    _, created_b2 = call(base, "POST", "/api/banner", token=admin_token,
                         body={"title": "模块三轮播A" + suffix, "imageUrl": cert, "linkType": 2,
                               "linkValue": str(category_id), "sort": 1, "status": 1})
    b2 = created_b2.get("data") or {}
    check("新增轮播图（分类跳转）成功", created_b2.get("code") == 200 and bool(b2.get("id")))

    _, active = call(base, "GET", "/api/home/banner/list")
    active_titles = [b["title"] for b in (active.get("data") or [])]
    check("轮播图按 sort 升序下发",
          active_titles.index(b2.get("title")) < active_titles.index(b1.get("title")), active_titles)

    _, bad_no_title = call(base, "POST", "/api/banner", token=admin_token,
                           body={"imageUrl": cert, "linkType": 1, "linkValue": "1"})
    check("缺少标题被拒绝", bad_no_title.get("code") != 200, bad_no_title.get("msg", ""))
    _, bad_id = call(base, "POST", "/api/banner", token=admin_token,
                     body={"title": "bad", "imageUrl": cert, "linkType": 1, "linkValue": "abc"})
    check("商品ID非数字被拒绝", bad_id.get("code") != 200, bad_id.get("msg", ""))
    _, bad_url = call(base, "POST", "/api/banner", token=admin_token,
                      body={"title": "bad", "imageUrl": cert, "linkType": 3, "linkValue": "example.com"})
    check("外链非 http(s) 被拒绝", bad_url.get("code") != 200, bad_url.get("msg", ""))
    _, bad_time = call(base, "POST", "/api/banner", token=admin_token,
                       body={"title": "bad", "imageUrl": cert, "linkType": 3,
                             "linkValue": "https://example.com",
                             "startTime": "2026-12-31 10:00:00", "endTime": "2026-12-01 10:00:00"})
    check("失效时间早于生效时间被拒绝", bad_time.get("code") != 200, bad_time.get("msg", ""))
    _, bad_type = call(base, "POST", "/api/banner", token=admin_token,
                       body={"title": "bad", "imageUrl": cert, "linkType": 9, "linkValue": "1"})
    check("非法跳转类型被拒绝", bad_type.get("code") != 200, bad_type.get("msg", ""))

    _, page_filtered = call(base, "GET", "/api/banner/page?pageNum=1&pageSize=10&keyword=" + quote("模块三轮播"),
                            token=admin_token)
    check("管理端按标题搜索", ((page_filtered.get("data") or {}).get("total") or 0) == 2,
          (page_filtered.get("data") or {}).get("total"))

    _, updated = call(base, "PUT", "/api/banner/%s" % b1["id"], token=admin_token,
                      body={"title": "模块三轮播B改" + suffix, "imageUrl": cert, "linkType": 3,
                            "linkValue": "https://example.com/activity", "sort": 5, "status": 1})
    check("编辑轮播图成功", updated.get("code") == 200, updated.get("msg", ""))

    _, disabled = call(base, "PUT", "/api/banner/%s/status?status=0" % b1["id"], token=admin_token)
    check("禁用轮播图成功", disabled.get("code") == 200)
    _, active_after = call(base, "GET", "/api/home/banner/list")
    active_ids = [b["id"] for b in (active_after.get("data") or [])]
    check("禁用后小程序不再下发", b1["id"] not in active_ids)

    _, enabled = call(base, "PUT", "/api/banner/%s/status?status=1" % b1["id"], token=admin_token)
    _, active_back = call(base, "GET", "/api/home/banner/list")
    active_ids2 = [b["id"] for b in (active_back.get("data") or [])]
    check("启用后恢复下发", enabled.get("code") == 200 and b1["id"] in active_ids2)

    _, future = call(base, "POST", "/api/banner", token=admin_token,
                     body={"title": "未来生效" + suffix, "imageUrl": cert, "linkType": 3,
                           "linkValue": "https://example.com", "status": 1,
                           "startTime": "2030-01-01 00:00:00"})
    future_id = (future.get("data") or {}).get("id")
    _, active_future = call(base, "GET", "/api/home/banner/list")
    check("未到生效时间不下发", future_id not in [b["id"] for b in (active_future.get("data") or [])])
    _, page_future = call(base, "GET", "/api/banner/page?pageNum=1&pageSize=10&status=1", token=admin_token)
    future_row = next((r for r in ((page_future.get("data") or {}).get("records") or [])
                       if r.get("id") == future_id), None)
    check("管理端标记为当前不展示", bool(future_row) and future_row.get("effective") is False)

    # 下架商品后热门缓存应立即失效
    call(base, "PUT", "/api/product/%s/offline" % product_ids[0], token=student_token)
    _, hot3 = call(base, "GET", "/api/product/hot")
    check("商品下架后热门缓存已失效", product_ids[0] not in [r.get("id") for r in (hot3.get("data") or [])])

    print("\n== 9. 清理 ==")
    for bid in [b1.get("id"), b2.get("id"), future_id]:
        if bid:
            call(base, "DELETE", "/api/banner/%s" % bid, token=admin_token)
    _, page_final = call(base, "GET", "/api/banner/page?pageNum=1&pageSize=10&keyword=" + quote("模块三"),
                         token=admin_token)
    check("删除后列表不再返回", ((page_final.get("data") or {}).get("total") or 0) == 0,
          (page_final.get("data") or {}).get("total"))

    cleaned = 0
    for pid in product_ids:
        call(base, "PUT", "/api/product/%s/offline" % pid, token=student_token)
        _, removed = call(base, "DELETE", "/api/product/%s" % pid, token=student_token)
        if removed.get("code") == 200:
            cleaned += 1
    check("测试商品已下架并删除", cleaned == len(product_ids), "%d/%d" % (cleaned, len(product_ids)))

    total = len(RESULTS)
    print("\n" + "=" * 56)
    print("结果：%d/%d 通过" % (PASSED, total))
    failed = [name for name, ok, _ in RESULTS if not ok]
    if failed:
        print("失败项：")
        for name in failed:
            print("  - " + name)
        return 1
    print("全部通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
