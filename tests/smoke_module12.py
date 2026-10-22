#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""模块12「首页推荐位配置」接口冒烟测试。

用法：
    mvn spring-boot:run
    python tests/smoke_module12.py

覆盖流程：
    公共聚合（字段齐全 / 只含已上架 / 只含启用轮播图 / 金刚区过滤 / 新品倒序）->
    权限（未登录401 / 普通用户403 / 客服可读不可写）->
    轮播图 CRUD（新路径 /api/home/banner；linkType=4 无跳转、非法类型、非法链接、
                编辑、启停用、批量排序、排序含失效ID、删除）->
    缓存（5 分钟 TTL / 轮播图写操作清缓存 / 配置保存清缓存 / 金刚区保存清缓存）->
    首页配置（公告回显与上限 / 非法链接 / 非法模式 / 手动模式未选品 /
            数量越界 / 手动选品顺序回显 / 新品数量生效）->
    金刚区（全量排序 / 关闭后首页不再展示 / 失效ID拒绝）->
    热门推荐回落（手动选品全部失效后回落到按热度自动）->
    操作日志（首页配置的写操作留痕）-> 数据清理

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

CACHE_KEY = "home:index"


def call(base, method, path, token=None, body=None):
    url = base + quote(path, safe="/?&=%:,")
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


# ============================================================
# 通用工具
# ============================================================

def home_data(base):
    _, res = call(base, "GET", "/api/home")
    return res.get("data") or {}


def login_user(base, code):
    _, res = call(base, "POST", "/api/auth/wx-login", body={"code": code})
    data = res.get("data") or {}
    return data.get("token"), data.get("userId"), res


def upload(base, token):
    """上传 1x1 PNG，返回相对地址。认证与商品图都需要。"""
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
        return json.loads(resp.read().decode("utf-8")).get("data") or ""


def make_student(base, admin_token, suffix, nickname):
    token, user_id, _ = login_user(base, "m12s" + suffix)
    call(base, "PUT", "/api/user/profile", token=token, body={"nickname": nickname})
    cert = upload(base, token)
    call(base, "POST", "/api/student-auth", token=token,
         body={"studentNo": "M12" + suffix, "realName": "模块十二卖家",
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


def query_one(sql):
    rows = mysql_query(sql)
    if not rows:
        return None
    return rows.splitlines()[0].split("\t")


def redis_cmd(*args):
    from shutil import which
    binary = next((c for c in ["redis-cli", "/d/Redis/redis-cli", r"D:\Redis\redis-cli.exe"]
                   if c and (os.path.exists(c) or which(c))), None)
    if not binary:
        return None
    try:
        out = subprocess.run([binary] + list(args), capture_output=True, text=True, timeout=15)
        if out.returncode != 0:
            return None
        return out.stdout.strip()
    except Exception:
        return None


def cache_exists():
    out = redis_cmd("EXISTS", CACHE_KEY)
    if out is None:
        return None
    return out == "1"


def cache_ttl():
    out = redis_cmd("TTL", CACHE_KEY)
    if out is None:
        return None
    try:
        return int(out)
    except ValueError:
        return None


def publish(base, token, category_id, title, price, image):
    _, res = call(base, "POST", "/api/product", token=token, body={
        "title": title,
        "categoryId": category_id,
        "price": price,
        "originalPrice": price * 3,
        "description": "模块十二首页推荐位测试商品",
        "images": [image],
    })
    return res.get("data")


def audit_pass(base, admin_token, product_id):
    return call(base, "PUT", "/api/product/%s/audit" % product_id,
                token=admin_token, body={"status": 1})


def banner_payload(title, image, link_type=4, link_value=None, sort=100, status=1):
    return {
        "title": title,
        "imageUrl": image,
        "linkType": link_type,
        "linkValue": link_value,
        "sort": sort,
        "status": status,
    }


# ============================================================
# 主流程
# ============================================================

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="http://127.0.0.1:8080")
    args = parser.parse_args()
    base = args.base.rstrip("/")
    suffix = uuid.uuid4().hex[:6]
    marker = "模块十二" + suffix

    print("=" * 66)
    print("模块12 首页推荐位配置 接口冒烟测试  base =", base)
    print("=" * 66)

    # 开局清一次首页缓存，避免上一轮遗留的缓存干扰断言
    redis_cmd("DEL", CACHE_KEY)

    _, admin = call(base, "POST", "/api/auth/admin-login",
                    body={"username": "admin", "password": "123456"})
    admin_token = (admin.get("data") or {}).get("token")
    if not admin_token:
        print("管理员登录失败，无法继续")
        return 1

    user_token, user_id, _ = login_user(base, "m12u" + suffix)
    banner_ids = []
    created_accounts = []
    product_ids = []
    seller_id = None
    student_token = None
    category_backup = []

    try:
        # ---------------------------------------------------------
        print("\n== 1. 公共聚合接口 ==")
        data = home_data(base)
        check("GET /api/home 免登录可访问", bool(data))
        need = ("notice", "banners", "categories", "hotProducts", "newProducts", "hotKeywords")
        check("聚合返回公告/轮播图/金刚区/热门/新品/热词",
              all(k in data for k in need), list(data.keys()))
        notice = data.get("notice") or {}
        check("公告结构含 text/link/enabled",
              all(k in notice for k in ("text", "link", "enabled")), notice)
        check("轮播图只返回启用且展示期内的",
              all(b.get("status") == 1 and b.get("effective") is True for b in (data.get("banners") or [])),
              [(b.get("id"), b.get("status"), b.get("effective")) for b in (data.get("banners") or [])])
        check("轮播图按 sort 升序",
              [b.get("sort") for b in (data.get("banners") or [])]
              == sorted([b.get("sort") for b in (data.get("banners") or [])]))
        cats = data.get("categories") or []
        check("金刚区只含启用且开启首页展示的分类",
              all(c.get("status") == 1 and c.get("isHomeShow") == 1 for c in cats),
              [(c.get("id"), c.get("status"), c.get("isHomeShow")) for c in cats])
        check("金刚区按 sort 升序",
              [c.get("sort") for c in cats] == sorted([c.get("sort") for c in cats]))
        check("热门推荐只含已上架商品",
              all(p.get("status") == 1 for p in (data.get("hotProducts") or [])))
        new_ids = [p.get("id") for p in (data.get("newProducts") or [])]
        check("新品推荐只含已上架商品",
              all(p.get("status") == 1 for p in (data.get("newProducts") or [])))
        check("新品推荐按发布时间倒序（id 倒序）", new_ids == sorted(new_ids, reverse=True), new_ids)

        _, alias = call(base, "GET", "/api/home/index")
        check("/api/home/index 老地址仍可用且内容一致",
              alias.get("code") == 200 and (alias.get("data") or {}).get("notice") == notice)

        _, blist = call(base, "GET", "/api/home/banner/list")
        check("轮播图列表接口与聚合一致",
              [b.get("id") for b in (blist.get("data") or [])]
              == [b.get("id") for b in (data.get("banners") or [])])

        # ---------------------------------------------------------
        print("\n== 2. 权限 ==")
        status, res = call(base, "GET", "/api/home/config")
        check("未登录读取首页配置返回 401",
              res.get("code") == 401, "http=%s code=%s" % (status, res.get("code")))
        _, res = call(base, "GET", "/api/home/config", token=user_token)
        check("普通用户读取首页配置返回 403", res.get("code") == 403, res.get("msg"))
        _, res = call(base, "GET", "/api/home/category/sort")
        check("未登录读取金刚区配置返回 401", res.get("code") == 401, res.get("code"))

        # 造一个客服账号
        cs_name = "m12cs" + suffix
        _, res = call(base, "POST", "/api/admin/account", token=admin_token, body={
            "username": cs_name, "password": "123456",
            "nickname": "模块十二客服", "role": "customer_service"
        })
        cs_id = res.get("data")
        if cs_id:
            created_accounts.append(cs_id)
        _, cs_login = call(base, "POST", "/api/auth/login",
                           body={"username": cs_name, "password": "123456"})
        cs_token = (cs_login.get("data") or {}).get("token")
        check("客服账号创建并可登录", bool(cs_token), res.get("msg"))

        _, res = call(base, "GET", "/api/home/config", token=cs_token)
        check("客服可读首页配置", res.get("code") == 200 and bool(res.get("data")))
        _, res = call(base, "PUT", "/api/home/config", token=cs_token,
                      body={"noticeEnabled": 1})
        check("客服保存首页配置被拒绝", res.get("code") == 403, res.get("msg"))
        _, res = call(base, "GET", "/api/home/banner/page?pageNum=1&pageSize=10", token=cs_token)
        check("客服可读轮播图列表", res.get("code") == 200)
        _, res = call(base, "PUT", "/api/home/banner/sort", token=cs_token,
                      body={"items": [{"id": 1, "sort": 1}]})
        check("客服批量排序被拒绝", res.get("code") == 403, res.get("msg"))

        # ---------------------------------------------------------
        print("\n== 3. 轮播图 CRUD（/api/home/banner） ==")
        img = upload(base, admin_token)

        _, res = call(base, "POST", "/api/home/banner", token=admin_token,
                      body=banner_payload(marker + "无跳转", img, link_type=4, sort=90))
        b1 = res.get("data") or {}
        if b1.get("id"):
            banner_ids.append(b1["id"])
        check("新增「无跳转」轮播图成功", res.get("code") == 200 and bool(b1.get("id")), res.get("msg"))
        check("无跳转类型不落跳转值", b1.get("linkValue") is None, b1.get("linkValue"))
        check("跳转类型文案为「无跳转」", b1.get("linkTypeLabel") == "无跳转", b1.get("linkTypeLabel"))

        _, res = call(base, "POST", "/api/home/banner", token=admin_token,
                      body=banner_payload(marker + "非法类型", img, link_type=5))
        check("跳转类型 5 被拒绝", res.get("code") == 400, res.get("msg"))

        _, res = call(base, "POST", "/api/home/banner", token=admin_token,
                      body=banner_payload(marker + "坏链接", img, link_type=3, link_value="ftp://x"))
        check("外部链接非 http(s) 被拒绝", res.get("code") == 400, res.get("msg"))

        _, res = call(base, "POST", "/api/home/banner", token=admin_token,
                      body=banner_payload(marker + "商品跳转", img, link_type=1, link_value="1", sort=91))
        b2 = res.get("data") or {}
        if b2.get("id"):
            banner_ids.append(b2["id"])
        check("新增「商品详情」轮播图成功", res.get("code") == 200, res.get("msg"))

        check("新轮播图立即出现在小程序列表",
              any(b.get("id") == b1.get("id") for b in (call(base, "GET", "/api/home/banner/list")
                                                        [1].get("data") or [])))

        _, page = call(base, "GET", "/api/home/banner/page?pageNum=1&pageSize=50&keyword="
                       + quote(marker), token=admin_token)
        records = (page.get("data") or {}).get("records") or []
        check("管理端分页可按标题筛选到新建的轮播图", len(records) == 2, len(records))

        _, res = call(base, "PUT", "/api/home/banner/%s" % b1["id"], token=admin_token,
                      body=banner_payload(marker + "无跳转-改", img, link_type=4, sort=90))
        check("编辑轮播图成功", res.get("code") == 200, res.get("msg"))
        row = query_one("SELECT title FROM banner WHERE id = %s;" % b1["id"])
        check("编辑已落库", row is not None and row[0] == marker + "无跳转-改", row)

        call(base, "PUT", "/api/home/banner/%s/status?status=0" % b1["id"], token=admin_token)
        _, blist = call(base, "GET", "/api/home/banner/list")
        check("禁用后不再下发到小程序",
              not any(b.get("id") == b1["id"] for b in (blist.get("data") or [])))

        call(base, "PUT", "/api/home/banner/%s/status?status=1" % b1["id"], token=admin_token)
        _, blist = call(base, "GET", "/api/home/banner/list")
        check("重新启用后恢复下发",
              any(b.get("id") == b1["id"] for b in (blist.get("data") or [])))

        # 批量排序：把 b1 排到最前面
        _, res = call(base, "PUT", "/api/home/banner/sort", token=admin_token,
                      body={"items": [{"id": b1["id"], "sort": 1}, {"id": b2["id"], "sort": 2}]})
        check("批量排序成功", res.get("code") == 200, res.get("msg"))
        _, blist = call(base, "GET", "/api/home/banner/list")
        first = (blist.get("data") or [{}])[0]
        check("批量排序生效：b1 排第一", first.get("id") == b1["id"], first.get("id"))

        _, res = call(base, "PUT", "/api/home/banner/sort", token=admin_token,
                      body={"items": [{"id": 999999, "sort": 1}]})
        check("排序含不存在的ID被拒绝", res.get("code") == 400, res.get("msg"))

        _, res = call(base, "PUT", "/api/home/banner/sort", token=admin_token, body={"items": []})
        check("空排序列表被拒绝", res.get("code") == 400, res.get("msg"))

        # ---------------------------------------------------------
        print("\n== 4. 缓存 ==")
        call(base, "GET", "/api/home")
        exists = cache_exists()
        ttl = cache_ttl()
        if exists is None:
            print("  [SKIP] 未找到 redis-cli，跳过缓存断言")
        else:
            check("首页聚合命中缓存", exists is True, exists)
            check("首页缓存 TTL 不超过 5 分钟", ttl is not None and 0 < ttl <= 300, ttl)

            # 轮播图写操作应清缓存
            _, res = call(base, "POST", "/api/home/banner", token=admin_token,
                          body=banner_payload(marker + "缓存验证", img, link_type=4, sort=95))
            b3 = res.get("data") or {}
            if b3.get("id"):
                banner_ids.append(b3["id"])
            time.sleep(0.4)
            check("新增轮播图清除首页缓存", cache_exists() is False, cache_exists())

            call(base, "GET", "/api/home")
            call(base, "PUT", "/api/home/banner/%s/status?status=0" % b3["id"], token=admin_token)
            time.sleep(0.4)
            check("启用/禁用轮播图清除首页缓存", cache_exists() is False, cache_exists())

            call(base, "GET", "/api/home")
            call(base, "DELETE", "/api/home/banner/%s" % b3["id"], token=admin_token)
            banner_ids = [i for i in banner_ids if i != b3["id"]]
            time.sleep(0.4)
            check("删除轮播图清除首页缓存", cache_exists() is False, cache_exists())

            call(base, "GET", "/api/home")
            call(base, "PUT", "/api/home/banner/sort", token=admin_token,
                 body={"items": [{"id": b1["id"], "sort": 1}]})
            time.sleep(0.4)
            check("批量排序清除首页缓存", cache_exists() is False, cache_exists())

            call(base, "GET", "/api/home")
            call(base, "PUT", "/api/home/config", token=admin_token,
                 body={"noticeText": "缓存验证公告", "noticeEnabled": 1, "hotMode": "auto"})
            time.sleep(0.4)
            check("保存首页配置清除首页缓存", cache_exists() is False, cache_exists())

            call(base, "GET", "/api/home")
            _, csort = call(base, "GET", "/api/home/category/sort", token=admin_token)
            cat_items = [{"id": c["id"], "isHomeShow": c["isHomeShow"], "sort": c["sort"]}
                         for c in (csort.get("data") or [])]
            call(base, "PUT", "/api/home/category/sort", token=admin_token,
                 body={"items": cat_items})
            time.sleep(0.4)
            check("保存金刚区配置清除首页缓存", cache_exists() is False, cache_exists())

        # ---------------------------------------------------------
        print("\n== 5. 首页配置 ==")
        _, res = call(base, "GET", "/api/home/config", token=admin_token)
        cfg = res.get("data") or {}
        check("读取配置返回 7 个业务字段",
              all(k in cfg for k in ("noticeText", "noticeLink", "noticeEnabled", "hotMode",
                                     "hotProductIds", "hotCount", "newCount")), list(cfg.keys()))

        _, res = call(base, "PUT", "/api/home/config", token=admin_token, body={
            "noticeText": marker + "本周六校园集市",
            "noticeLink": "https://example.com/market",
            "noticeEnabled": 1,
            "hotMode": "auto",
            "hotProductIds": [],
            "hotCount": 6,
            "newCount": 3
        })
        check("保存首页配置成功", res.get("code") == 200, res.get("msg"))
        _, res = call(base, "GET", "/api/home/config", token=admin_token)
        cfg = res.get("data") or {}
        check("公告文字已落库", cfg.get("noticeText") == marker + "本周六校园集市", cfg.get("noticeText"))
        check("公告链接已落库", cfg.get("noticeLink") == "https://example.com/market", cfg.get("noticeLink"))
        check("自动模式数量已落库", cfg.get("hotCount") == 6 and cfg.get("newCount") == 3,
              (cfg.get("hotCount"), cfg.get("newCount")))

        data = home_data(base)
        check("首页公告使用最新配置",
              (data.get("notice") or {}).get("text") == marker + "本周六校园集市",
              (data.get("notice") or {}).get("text"))
        check("首页公告带跳转链接",
              (data.get("notice") or {}).get("link") == "https://example.com/market")
        check("新品推荐数量按配置生效",
              len(data.get("newProducts") or []) <= 3, len(data.get("newProducts") or []))

        # 公告关闭
        call(base, "PUT", "/api/home/config", token=admin_token, body={
            "noticeText": "这些字还在但开关关了", "noticeEnabled": 0,
            "hotMode": "auto", "hotProductIds": [], "hotCount": 6, "newCount": 3
        })
        check("公告关闭后首页 enabled=0",
              (home_data(base).get("notice") or {}).get("enabled") == 0)

        # 公告为空但开关打开 -> 也不展示
        call(base, "PUT", "/api/home/config", token=admin_token, body={
            "noticeText": "   ", "noticeEnabled": 1,
            "hotMode": "auto", "hotProductIds": [], "hotCount": 6, "newCount": 3
        })
        check("公告文字为空时首页仍不展示",
              (home_data(base).get("notice") or {}).get("enabled") == 0)

        # 各种非法输入
        _, res = call(base, "PUT", "/api/home/config", token=admin_token,
                      body={"noticeText": "长" * 101, "noticeEnabled": 1})
        check("公告超过 100 字被拒绝", res.get("code") == 400, res.get("msg"))
        _, res = call(base, "PUT", "/api/home/config", token=admin_token,
                      body={"noticeLink": "ftp://x"})
        check("公告链接非 http(s) 被拒绝", res.get("code") == 400, res.get("msg"))
        _, res = call(base, "PUT", "/api/home/config", token=admin_token, body={"hotMode": "xxx"})
        check("热门模式非法取值被拒绝", res.get("code") == 400, res.get("msg"))
        _, res = call(base, "PUT", "/api/home/config", token=admin_token,
                      body={"hotMode": "manual", "hotProductIds": []})
        check("手动模式未选品被拒绝", res.get("code") == 400, res.get("msg"))
        _, res = call(base, "PUT", "/api/home/config", token=admin_token, body={"hotCount": 0})
        check("自动数量 0 被拒绝", res.get("code") == 400, res.get("msg"))
        _, res = call(base, "PUT", "/api/home/config", token=admin_token, body={"newCount": 25})
        check("新品数量 25 被拒绝", res.get("code") == 400, res.get("msg"))

        # ---------------------------------------------------------
        print("\n== 6. 准备已上架商品 ==")
        student_token, seller_id, cert = make_student(base, admin_token, suffix, marker + "卖家")
        _, cats_res = call(base, "GET", "/api/category/list")
        category_id = ((cats_res.get("data") or [{}])[0]).get("id")
        for idx in range(3):
            pid = publish(base, student_token, category_id,
                          "%s测试商品%s" % (marker, idx + 1), 10 + idx * 5, cert)
            if pid:
                product_ids.append(pid)
        check("发布 3 件测试商品", len(product_ids) == 3, product_ids)
        for pid in product_ids[:2]:
            audit_pass(base, admin_token, pid)
        _, hot = call(base, "GET", "/api/product/hot")
        check("商品审核后进入已上架池",
              all(any(p.get("id") == pid for p in (hot.get("data") or [])) for pid in product_ids[:2]),
              [p.get("id") for p in (hot.get("data") or [])][:5])

        # ---------------------------------------------------------
        print("\n== 7. 热门推荐手动选品 ==")
        p1, p2, p3 = product_ids
        _, res = call(base, "PUT", "/api/home/config", token=admin_token, body={
            "noticeText": "", "noticeEnabled": 0,
            "hotMode": "manual", "hotProductIds": [p2, p1],
            "hotCount": 6, "newCount": 3
        })
        check("保存手动选品成功", res.get("code") == 200, res.get("msg"))
        data = home_data(base)
        check("手动选品按运营顺序下发",
              [p.get("id") for p in (data.get("hotProducts") or [])] == [p2, p1],
              [p.get("id") for p in (data.get("hotProducts") or [])])

        _, res = call(base, "PUT", "/api/home/config", token=admin_token,
                      body={"hotMode": "manual",
                            "hotProductIds": list(range(1, 22)), "hotCount": 6, "newCount": 3})
        check("手动选品超过 20 件被拒绝", res.get("code") == 400, res.get("msg"))

        # 手动选品里混入不存在/未上架的商品：应被静默过滤，而不是报错
        audit_pass(base, admin_token, p3)
        _, res = call(base, "PUT", "/api/home/config", token=admin_token, body={
            "hotMode": "manual", "hotProductIds": [p1, 999999, p3],
            "hotCount": 6, "newCount": 3
        })
        check("手动选品含无效ID仍可保存", res.get("code") == 200, res.get("msg"))
        ids = [p.get("id") for p in (home_data(base).get("hotProducts") or [])]
        check("无效ID被静默过滤，有效商品保持顺序", ids == [p1, p3], ids)

        # 全部失效 -> 回落自动模式
        call(base, "PUT", "/api/product/%s/force-offline" % p3, token=admin_token,
             body={"reason": "模块十二测试：模拟商品被下架"})
        call(base, "PUT", "/api/product/%s/force-offline" % p1, token=admin_token,
             body={"reason": "模块十二测试：模拟商品被下架"})
        _, res = call(base, "PUT", "/api/home/config", token=admin_token, body={
            "hotMode": "manual", "hotProductIds": [p1, p3], "hotCount": 5, "newCount": 3
        })
        data = home_data(base)
        hot_ids = [p.get("id") for p in (data.get("hotProducts") or [])]
        check("手动选品全部失效后回落为按热度自动",
              len(hot_ids) > 0 and p1 not in hot_ids and p3 not in hot_ids, hot_ids)
        check("回落后的热门推荐不超过配置数量", len(hot_ids) <= 5, len(hot_ids))

        # ---------------------------------------------------------
        print("\n== 8. 金刚区配置 ==")
        _, res = call(base, "GET", "/api/home/category/sort", token=admin_token)
        cat_list = res.get("data") or []
        check("金刚区配置返回全部分类", len(cat_list) >= 3, len(cat_list))
        check("金刚区配置含显示状态/排序/在售数",
              all(all(k in c for k in ("id", "name", "isHomeShow", "sort", "productCount"))
                  for c in cat_list))
        category_backup = [{"id": c["id"], "isHomeShow": c["isHomeShow"], "sort": c["sort"]}
                           for c in cat_list]

        ids = [c["id"] for c in cat_list[:3]]
        reversed_items = [{"id": ids[2], "isHomeShow": 1, "sort": 1},
                          {"id": ids[1], "isHomeShow": 1, "sort": 2},
                          {"id": ids[0], "isHomeShow": 0, "sort": 3}]
        _, res = call(base, "PUT", "/api/home/category/sort", token=admin_token,
                      body={"items": reversed_items})
        check("保存金刚区顺序成功", res.get("code") == 200, res.get("msg"))
        shown = [c.get("id") for c in (home_data(base).get("categories") or [])]
        check("金刚区按新顺序下发", shown[:2] == [ids[2], ids[1]], shown[:4])
        check("关闭的分类不出现在金刚区", ids[0] not in shown, shown)

        # 未在本次提交里的分类保持原状：全量提交只影响提交项，
        # 不能把没提交的分类当成「关闭」处理
        untouched = [c for c in cat_list if c["id"] not in ids and c["isHomeShow"] == 1]
        if untouched:
            missing = [c["id"] for c in untouched if c["id"] not in shown]
            check("未提交的分类仍显示在金刚区", not missing, missing)

        _, res = call(base, "PUT", "/api/home/category/sort", token=admin_token,
                      body={"items": [{"id": 999999, "isHomeShow": 1, "sort": 1}]})
        check("金刚区含不存在分类被拒绝", res.get("code") == 400, res.get("msg"))

        # 恢复
        call(base, "PUT", "/api/home/category/sort", token=admin_token,
             body={"items": category_backup})

        # ---------------------------------------------------------
        print("\n== 9. 新品推荐 ==")
        call(base, "PUT", "/api/home/config", token=admin_token, body={
            "noticeText": "", "noticeEnabled": 0, "hotMode": "auto",
            "hotProductIds": [], "hotCount": 6, "newCount": 2
        })
        data = home_data(base)
        check("新品数量按配置生效（2 件）",
              len(data.get("newProducts") or []) <= 2, len(data.get("newProducts") or []))
        call(base, "PUT", "/api/home/config", token=admin_token, body={
            "noticeText": "", "noticeEnabled": 0, "hotMode": "auto",
            "hotProductIds": [], "hotCount": 6, "newCount": 20
        })
        data = home_data(base)
        check("新品数量上限 20 件",
              len(data.get("newProducts") or []) <= 20, len(data.get("newProducts") or []))

        # ---------------------------------------------------------
        print("\n== 10. 操作日志 ==")
        logs = query_one("SELECT COUNT(*) FROM sys_operation_log WHERE module = 'home';")
        check("首页配置的写操作已留痕", logs is not None and int(logs[0]) >= 5,
              logs[0] if logs else None)
        for action in ("create", "update", "status", "sort", "delete"):
            row = query_one("SELECT COUNT(*) FROM sys_operation_log "
                            "WHERE module = 'home' AND action = '%s';" % action)
            check("操作日志覆盖动作 %s" % action, row is not None and int(row[0]) > 0,
                  row[0] if row else None)

    finally:
        # ---------------------------------------------------------
        print("\n== 清理测试数据 ==")
        for bid in banner_ids:
            call(base, "DELETE", "/api/home/banner/%s" % bid, token=admin_token)
        if category_backup:
            call(base, "PUT", "/api/home/category/sort", token=admin_token,
                 body={"items": category_backup})
        # 恢复默认首页配置
        call(base, "PUT", "/api/home/config", token=admin_token, body={
            "noticeText": "校园二手交易平台正式上线，认证学生发布更放心",
            "noticeLink": "",
            "noticeEnabled": 1,
            "hotMode": "auto",
            "hotProductIds": [],
            "hotCount": 10,
            "newCount": 10
        })
        for aid in created_accounts:
            call(base, "DELETE", "/api/admin/account/%s" % aid, token=admin_token)
        if product_ids:
            plist = ",".join(str(i) for i in product_ids)
            mysql_query("DELETE FROM product_image WHERE product_id IN (%s);" % plist)
            mysql_query("DELETE FROM product WHERE id IN (%s);" % plist)
        if seller_id:
            mysql_query("DELETE FROM student_auth WHERE user_id = %s;" % seller_id)
            mysql_query("DELETE FROM sys_operation_log WHERE operator_id = %s;" % seller_id)
            mysql_query("DELETE FROM sys_user WHERE id = %s;" % seller_id)
        if user_id:
            mysql_query("DELETE FROM sys_user WHERE id = %s;" % user_id)
        mysql_query("DELETE FROM banner WHERE title LIKE '%s%%';" % marker)
        redis_cmd("DEL", CACHE_KEY)
        # 上面用 SQL 直接删用户/商品绕过了「写后失效」，仪表盘的趋势缓存里
        # 还留着这些已删除的行，会让后续模块10的「趋势 vs 数据库」对账变成假失败。
        # 这里顺手清一次，保证整套回归可以在同一个库上连续跑
        call(base, "POST", "/api/dashboard/refresh", token=admin_token)
        print("  已清理轮播图 %d 张、账号 %d 个、商品 %d 件"
              % (len(banner_ids), len(created_accounts), len(product_ids)))

    # ---------------------------------------------------------
    total = len(RESULTS)
    failed = total - PASSED
    print("\n" + "=" * 66)
    print("通过 %d / %d" % (PASSED, total))
    if failed:
        print("失败用例：")
        for name, ok, detail in RESULTS:
            if not ok:
                print("  - %s  -> %s" % (name, detail))
    print("=" * 66)
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
