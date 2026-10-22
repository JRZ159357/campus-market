#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""模块9「举报与客服工单」接口冒烟测试。

用法：
    mvn spring-boot:run
    python tests/smoke_module9.py

覆盖流程：
    举报提交（未登录401 / 举报自己被拒 / 对象不存在被拒 / 描述过短被拒 /
                证据图超3张被截断 / 快照写入成功 / 24小时节流）->
    我的举报（只能看自己的，看别人的报"不存在"）->
    管理端列表与筛选（普通用户403 / 客服可读 / 状态排序）->
    接单（待处理→处理中 / 重复接单被拒 / 已终态不能接）->
    处理联动（商品举报→下架+扣10分 / 用户举报→封禁+扣10分+商品下架 /
                评价举报→删评价+扣5分 / 驳回不执行动作 /
                动作与类型不匹配被拒 / 驳回带动作被拒）->
    通知双向送达 ->
    操作日志（写入成功 / 客服只读可查 / 无写接口）->
    统计（待处理数 / 处理率 / 平均时长）-> 数据清理

退出码：0 全部通过，1 存在失败。
"""

import argparse
import glob
import json
import os
import subprocess
import sys
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
        with urllib.request.urlopen(req, timeout=25) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", "replace")
        try:
            return e.code, json.loads(raw)
        except Exception:
            return e.code, {"code": e.code, "msg": raw}


def upload(base, token, png=None):
    """上传一张图片，返回接口响应。

    默认仍是 1×1 透明 PNG —— 冒烟测试只关心「有没有落库、字段对不对」，
    上传小图能让 117 个用例跑得更快。演示数据需要肉眼可见的图，
    会从 demo_image 生成后作为 png 传进来。
    """
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


def make_student(base, admin_token, suffix, nickname=None, cert_png=None):
    """注册微信用户并认证成学生（只有学生能发布商品）。"""
    token, user_id, _ = login_user(base, "m9s" + suffix)
    if nickname:
        call(base, "PUT", "/api/user/profile", token=token, body={"nickname": nickname})
    cert = (upload(base, token, cert_png).get("data") or "")
    call(base, "POST", "/api/student-auth", token=token,
         body={"studentNo": "M9" + suffix, "realName": "模块九卖家",
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
    重新登录会把调用方手里的 admin_token 直接作废，
    后面所有管理端接口都会返回「登录已失效」并引发级联失败。
    """
    token, user_id, _ = login_user(base, "m9c" + suffix)
    call(base, "PUT", "/api/user/profile", token=token, body={"nickname": nickname})
    call(base, "PUT", "/api/admin/user/%s/role" % user_id, token=admin_token,
         body={"role": "customer_service"})
    return token, user_id


def publish_product(base, token, admin_token, cert, title, price, audit=True):
    _, cat = call(base, "GET", "/api/category/list")
    category_id = (cat.get("data") or [{}])[0].get("id")
    _, res = call(base, "POST", "/api/product", token=token,
                  body={"title": title, "categoryId": category_id, "price": price,
                        "originalPrice": price * 3, "description": "模块九测试商品",
                        "images": [cert]})
    pid = res.get("data")
    if audit:
        call(base, "PUT", "/api/product/%s/audit" % pid, token=admin_token, body={"status": 1})
    return pid


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


def credit_of(user_id):
    rows = mysql_query("SELECT credit_score FROM sys_user WHERE id = %s;" % user_id)
    if not rows:
        return None
    return int(rows.splitlines()[0].split("\t")[0])


def count_notice(user_id, keyword):
    if not keyword:
        return -1
    rows = mysql_query(
        "SELECT COUNT(*) FROM notice WHERE user_id = %s AND title LIKE CONCAT('%%', '%s', '%%');"
        % (user_id, keyword))
    return int(rows.splitlines()[0].split("\t")[0]) if rows else -1


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="http://127.0.0.1:8080")
    args = parser.parse_args()
    base = args.base.rstrip("/")
    suffix = uuid.uuid4().hex[:6]

    print("=" * 60)
    print("模块9 举报与客服工单 接口冒烟测试  base =", base)
    print("=" * 60)

    # ---------------- 准备数据 ----------------
    _, admin_res = call(base, "POST", "/api/auth/login",
                        body={"username": "admin", "password": "123456"})
    admin_token = (admin_res.get("data") or {}).get("token")
    check("管理员登录成功", bool(admin_token), admin_res.get("msg"))

    # 违规卖家（会被举报的对象）、买家（举报人）、正常卖家、客服
    seller_token, seller_id, seller_cert = make_student(base, admin_token, suffix + "a", "违规卖家" + suffix)
    buyer_token, buyer_id, _ = login_user(base, "m9b" + suffix)
    call(base, "PUT", "/api/user/profile", token=buyer_token, body={"nickname": "正义买家" + suffix})
    # 正常卖家：认证学生（要发布商品，普通用户会被 @RequireRole(STUDENT) 拦住）
    ok_token, ok_id, ok_cert = make_student(base, admin_token, suffix + "o", "正常卖家" + suffix)
    cs_token, cs_id = make_service(base, admin_token, suffix + "c", "客服小周" + suffix)

    bad_product = publish_product(base, seller_token, admin_token, seller_cert,
                                  "违规商品" + suffix, 1.0)
    ok_product = publish_product(base, seller_token, admin_token, seller_cert,
                                 "正常商品" + suffix, 20.0)
    check("准备：违规商品已上架", bool(bad_product and ok_product), (bad_product, ok_product))

    ids = []

    try:
        # ================= 1. 提交举报 =================
        print("\n== 1. 提交举报 ==")

        status, res = call(base, "POST", "/api/report",
                           body={"reportType": 1, "targetId": bad_product,
                                 "reason": 1, "content": "收到的东西和描述完全不符"})
        check("未登录提交举报返回 401", status == 401 or res.get("code") == 401,
              (status, res.get("code")))

        status, res = call(base, "POST", "/api/report", token=buyer_token,
                      body={"reportType": 9, "targetId": bad_product,
                            "reason": 1, "content": "用一个不存在的类型试试看"})
        check("举报类型非法被拒", res.get("code") == 400 and "举报类型" in (res.get("msg") or ""),
              res.get("msg"))

        status, res = call(base, "POST", "/api/report", token=seller_token,
                           body={"reportType": 1, "targetId": bad_product,
                                 "reason": 1, "content": "举报自己的商品试试"})
        check("不能举报自己的商品", res.get("code") == 400 and "自己" in (res.get("msg") or ""),
              res.get("msg"))

        status, res = call(base, "POST", "/api/report", token=buyer_token,
                           body={"reportType": 1, "targetId": 99999999,
                                 "reason": 1, "content": "举报一个不存在的商品"})
        check("举报不存在的商品被拒", res.get("code") == 400 and "不存在" in (res.get("msg") or ""),
              res.get("msg"))

        status, res = call(base, "POST", "/api/report", token=buyer_token,
                           body={"reportType": 1, "targetId": bad_product,
                                 "reason": 1, "content": "假货"})
        check("详细描述少于5字被拒", res.get("code") == 400, res.get("msg"))

        status, res = call(base, "POST", "/api/report", token=buyer_token,
                           body={"reportType": 1, "targetId": bad_product,
                                 "reason": 1, "content": "商品描述严重造假，实物与图片完全不同"})
        product_report_id = res.get("data")
        check("商品举报提交成功", isinstance(product_report_id, int), res.get("msg"))
        ids.append(product_report_id)

        # 24 小时节流
        status, res = call(base, "POST", "/api/report", token=buyer_token,
                           body={"reportType": 1, "targetId": bad_product,
                                 "reason": 1, "content": "再举报一次同样的内容试试看"})
        check("同一对象24小时内重复举报被拒",
              res.get("code") == 400 and "小时" in (res.get("msg") or ""), res.get("msg"))

        # 证据图超过3张：显式拒绝而非静默截断
        status, res = call(base, "POST", "/api/report", token=buyer_token,
                           body={"reportType": 1, "targetId": ok_product, "reason": 2,
                                 "content": "证据图片超过三张应该被拒绝而不是悄悄截断",
                                 "images": ["/a.png", "/b.png", "/c.png", "/d.png", "/e.png"]})
        check("证据图超过3张被拒绝",
              res.get("code") == 400 and "最多3张" in (res.get("msg") or ""), res.get("msg"))

        # 恰好3张可以提交
        status, res = call(base, "POST", "/api/report", token=buyer_token,
                           body={"reportType": 1, "targetId": ok_product, "reason": 2,
                                 "content": "恰好三张证据图应该可以正常提交",
                                 "images": ["/a.png", "/b.png", "/c.png"]})
        multi_img_report = res.get("data")
        check("3张证据图的举报提交成功", isinstance(multi_img_report, int), res.get("msg"))
        ids.append(multi_img_report)
        if multi_img_report:
            _, res = call(base, "GET", "/api/report/my/%s" % multi_img_report, token=buyer_token)
            img_count = len((res.get("data") or {}).get("images") or [])
            check("3张证据图全部保存", img_count == 3, img_count)

        # 快照
        _, res = call(base, "GET", "/api/report/my/%s" % product_report_id, token=buyer_token)
        detail = res.get("data") or {}
        snapshot = detail.get("snapshot") or {}
        check("商品举报快照记录了标题", snapshot.get("title") == "违规商品" + suffix, snapshot.get("title"))
        check("商品举报快照记录了价格", snapshot.get("price") is not None, snapshot.get("price"))
        check("商品举报快照记录了状态文案", snapshot.get("statusLabel") == "已上架",
              snapshot.get("statusLabel"))
        check("举报状态初始为待处理", detail.get("status") == 0, detail.get("statusLabel"))
        check("举报人被正确记录", detail.get("reporterName") == "正义买家" + suffix,
              detail.get("reporterName"))
        check("被举报人昵称被正确带出", detail.get("targetUserName") == "违规卖家" + suffix,
              detail.get("targetUserName"))
        check("举报原因文案正确", detail.get("reasonLabel") == "虚假描述", detail.get("reasonLabel"))

        # ================= 2. 我的举报 =================
        print("\n== 2. 我的举报 ==")
        _, res = call(base, "GET", "/api/report/my?pageNum=1&pageSize=10", token=buyer_token)
        mine = (res.get("data") or {}).get("records") or []
        check("我的举报列表返回2条", len(mine) == 2, len(mine))
        check("我的举报列表按时间倒序",
              mine and mine[0]["id"] > mine[-1]["id"] if len(mine) >= 2 else False,
              [r["id"] for r in mine])

        status, res = call(base, "GET", "/api/report/my/%s" % product_report_id, token=ok_token)
        check("看别人的举报返回「不存在」", res.get("msg") == "举报记录不存在", res.get("msg"))

        # ================= 3. 管理端列表与权限 =================
        print("\n== 3. 管理端列表与权限 ==")
        status, res = call(base, "GET", "/api/report/admin/page", token=buyer_token)
        check("普通用户访问举报管理返回 403", res.get("code") == 403, res.get("code"))

        status, res = call(base, "PUT", "/api/report/admin/%s/accept" % product_report_id,
                           token=buyer_token)
        check("普通用户接单返回 403", res.get("code") == 403, res.get("code"))

        _, res = call(base, "GET", "/api/report/admin/page?pageNum=1&pageSize=10",
                      token=cs_token)
        records = (res.get("data") or {}).get("records") or []
        check("客服可以查看举报列表", res.get("code") == 200 and len(records) >= 2, len(records))

        _, res = call(base, "GET", "/api/report/admin/page?pageNum=1&pageSize=10&status=0",
                      token=admin_token)
        pending_records = (res.get("data") or {}).get("records") or []
        check("按状态筛选待处理生效",
              all(r["status"] == 0 for r in pending_records) and pending_records,
              [r["status"] for r in pending_records])

        _, res = call(base, "GET",
                      "/api/report/admin/page?pageNum=1&pageSize=10&keyword=%s" % suffix,
                      token=admin_token)
        kw_records = (res.get("data") or {}).get("records") or []
        check("按关键词搜索命中本轮举报", len(kw_records) >= 2, len(kw_records))

        _, res = call(base, "GET",
                      "/api/report/admin/page?pageNum=1&pageSize=10&reportType=1",
                      token=admin_token)
        type_records = (res.get("data") or {}).get("records") or []
        check("按类型筛选生效",
              type_records and all(r["reportType"] == 1 for r in type_records),
              [r["reportType"] for r in type_records][:5])

        _, res = call(base, "GET",
                      "/api/report/admin/page?pageNum=1&pageSize=10&reportType=3",
                      token=admin_token)
        none_records = (res.get("data") or {}).get("records") or []
        check("筛选不存在的类型返回空列表", none_records == [], len(none_records))

        # 列表的派生字段（标签 / 对象摘要 / 快照）必须与详情接口一致。
        # 这组断言是有来历的：管理端列表走的是「JOIN + 投影」的 SQL，
        # 当初这条路径绕过了详情用的那套装配逻辑，
        # 表现为「列表页类型列、状态列、对象标题全是空白，点进详情却又是好的」——
        # 那种问题极难定位，所以在这里钉死。
        _, res = call(base, "GET", "/api/report/admin/page?pageNum=1&pageSize=50",
                      token=admin_token)
        all_records = (res.get("data") or {}).get("records") or []
        check("列表返回本轮举报", bool(all_records), len(all_records))
        if all_records:
            one = all_records[0]
            check("列表项带类型文案",
                  one.get("reportTypeLabel") in ("商品举报", "用户举报", "评价举报"),
                  one.get("reportTypeLabel"))
            check("列表项带状态文案",
                  one.get("statusLabel") in ("待处理", "处理中", "已处理", "已驳回"),
                  one.get("statusLabel"))
            check("列表项带状态配色", bool(one.get("statusColor")), one.get("statusColor"))
            check("列表项带原因文案", bool(one.get("reasonLabel")), one.get("reasonLabel"))
            check("列表项带处理动作文案", bool(one.get("handleActionLabel")),
                  one.get("handleActionLabel"))
            check("列表项带被举报对象标题", bool(one.get("targetTitle")),
                  one.get("targetTitle"))
            check("列表项带快照", isinstance(one.get("snapshot"), dict) and bool(one.get("snapshot")),
                  one.get("snapshot"))
            check("列表项带距今小时数", one.get("hoursAgo") is not None, one.get("hoursAgo"))
            check("列表项标记了是否本人接单", one.get("acceptedByMe") in (True, False),
                  one.get("acceptedByMe"))
            check("列表项证据图为数组（不是逗号串）",
                  isinstance(one.get("images"), list), one.get("images"))

            # 与详情接口逐字段对齐
            _, res2 = call(base, "GET", "/api/report/admin/%s" % one["id"], token=admin_token)
            detail = res2.get("data") or {}
            check("列表与详情的类型文案一致",
                  one.get("reportTypeLabel") == detail.get("reportTypeLabel"),
                  (one.get("reportTypeLabel"), detail.get("reportTypeLabel")))
            check("列表与详情的状态文案一致",
                  one.get("statusLabel") == detail.get("statusLabel"),
                  (one.get("statusLabel"), detail.get("statusLabel")))
            check("列表与详情的对象标题一致",
                  one.get("targetTitle") == detail.get("targetTitle"),
                  (one.get("targetTitle"), detail.get("targetTitle")))
            check("列表与详情的快照键一致",
                  sorted((one.get("snapshot") or {}).keys()) == sorted((detail.get("snapshot") or {}).keys()),
                  (sorted((one.get("snapshot") or {}).keys()),
                   sorted((detail.get("snapshot") or {}).keys())))

        # 被举报人当前状态必须是文案（前端要按「已封禁/已注销」渲染标签）
        states = [(r.get("id"), r.get("targetUserStatus")) for r in all_records]
        bad_states = [s for s in states if s[1] not in ("正常", "已封禁", "账号已注销")]
        check("列表的被举报人状态为文案而非数字", not bad_states, bad_states)

        # ================= 4. 接单 =================
        print("\n== 4. 接单 ==")
        status, res = call(base, "PUT", "/api/report/admin/%s/accept" % product_report_id,
                           token=cs_token)
        check("客服接单成功", res.get("code") == 200, res.get("msg"))
        _, res = call(base, "GET", "/api/report/my/%s" % product_report_id, token=buyer_token)
        accepted = res.get("data") or {}
        check("接单后状态变为处理中", accepted.get("status") == 1, accepted.get("statusLabel"))
        check("接单后记录了处理人", accepted.get("handlerName") == "客服小周" + suffix,
              accepted.get("handlerName"))
        check("接单后记录了接单时间", accepted.get("acceptTime") is not None,
              accepted.get("acceptTime"))

        status, res = call(base, "PUT", "/api/report/admin/%s/accept" % product_report_id,
                           token=admin_token)
        check("重复接单被拒", res.get("code") == 400 and "接单" in (res.get("msg") or ""),
              res.get("msg"))

        # 未接单直接处理：管理员有权直接判定
        status, res = call(base, "PUT", "/api/report/admin/%s/handle" % multi_img_report,
                           token=admin_token,
                           body={"status": 3, "handleAction": 0,
                                 "handleResult": "经核实商品为正品，不构成违规"})
        check("管理员可未接单直接处理", res.get("code") == 200, res.get("msg"))

        status, res = call(base, "PUT", "/api/report/admin/%s/accept" % multi_img_report,
                           token=cs_token)
        check("已驳回的举报不能接单", res.get("code") == 400, res.get("msg"))

        # ================= 5. 处理联动：商品举报成立 =================
        print("\n== 5. 处理联动：商品举报成立 ==")
        seller_credit_before = credit_of(seller_id)
        notice_before = count_notice(seller_id, "违规")

        status, res = call(base, "PUT", "/api/report/admin/%s/handle" % product_report_id,
                           token=cs_token,
                           body={"status": 2, "handleAction": 1,
                                 "handleResult": "经核实商品为违禁品，已强制下架"})
        check("商品举报处理成功", res.get("code") == 200, res.get("msg"))

        _, res = call(base, "GET", "/api/report/my/%s" % product_report_id, token=buyer_token)
        handled = res.get("data") or {}
        check("处理后状态为已处理", handled.get("status") == 2, handled.get("statusLabel"))
        check("处理动作记录为下架商品", handled.get("handleAction") == 1,
              handled.get("handleActionLabel"))
        check("处理结果包含处置摘要", "强制下架" in (handled.get("handleResult") or ""),
              handled.get("handleResult"))

        rows = mysql_query("SELECT status FROM product WHERE id = %s;" % bad_product)
        check("被举报商品已变为违规下架", rows == "4", rows)
        rows = mysql_query("SELECT status FROM product WHERE id = %s;" % ok_product)
        check("未举报的同卖家商品未被牵连", rows == "1", rows)

        seller_credit_after = credit_of(seller_id)
        if seller_credit_before is not None and seller_credit_after is not None:
            check("卖家信誉分扣10分",
                  seller_credit_before - seller_credit_after == 10,
                  "%s -> %s" % (seller_credit_before, seller_credit_after))
        else:
            print("  [SKIP] 未找到 mysql 客户端，跳过信誉分校验")

        notice_after = count_notice(seller_id, "违规")
        if notice_before >= 0:
            check("被举报人收到违规通知", notice_after > notice_before,
                  "%s -> %s" % (notice_before, notice_after))
        else:
            print("  [SKIP] 未找到 mysql 客户端，跳过通知校验")

        rows = mysql_query("SELECT COUNT(*) FROM credit_log WHERE user_id = %s AND related_id = %s;"
                           % (seller_id, product_report_id))
        check("扣分写入信誉分流水且关联举报ID", rows == "1", rows)

        # ================= 6. 处理联动：用户举报 =================
        print("\n== 6. 处理联动：用户举报 ==")
        _, res = call(base, "POST", "/api/report", token=buyer_token,
                      body={"reportType": 2, "targetId": seller_id, "reason": 3,
                            "content": "该用户涉嫌欺诈，收款后拉黑不发货"})
        user_report_id = res.get("data")
        check("用户举报提交成功", isinstance(user_report_id, int), res.get("msg"))
        ids.append(user_report_id)

        _, res = call(base, "GET", "/api/report/my/%s" % user_report_id, token=buyer_token)
        user_snapshot = ((res.get("data") or {}).get("snapshot") or {})
        check("用户举报快照记录了昵称", user_snapshot.get("nickname") == "违规卖家" + suffix,
              user_snapshot.get("nickname"))
        check("用户举报快照记录了信誉分", user_snapshot.get("creditScore") is not None,
              user_snapshot.get("creditScore"))

        # 动作类型不匹配：用户举报不能选「删除评价」
        call(base, "PUT", "/api/report/admin/%s/accept" % user_report_id, token=cs_token)
        status, res = call(base, "PUT", "/api/report/admin/%s/handle" % user_report_id,
                           token=cs_token,
                           body={"status": 2, "handleAction": 3,
                                 "handleResult": "试图用删除评价处理用户举报"})
        check("用户举报不能选择删除评价动作",
              res.get("code") == 400 and "不适用" in (res.get("msg") or ""), res.get("msg"))

        # 记下无关用户的信誉分：处理完用户举报后它必须原封不动
        bystander_credit = credit_of(ok_id)
        status, res = call(base, "PUT", "/api/report/admin/%s/handle" % user_report_id,
                           token=cs_token,
                           body={"status": 2, "handleAction": 2,
                                 "handleResult": "核实存在欺诈行为，封禁账号"})
        check("用户举报处理成功", res.get("code") == 200, res.get("msg"))
        rows = mysql_query("SELECT status FROM sys_user WHERE id = %s;" % seller_id)
        check("被举报用户已封禁", rows == "1", rows)
        seller_credit2 = credit_of(seller_id)
        if seller_credit_before is not None and seller_credit2 is not None:
            check("用户封禁再扣10分",
                  seller_credit_after - seller_credit2 == 10,
                  "%s -> %s" % (seller_credit_after, seller_credit2))
        check("用户举报不牵连无关用户", credit_of(ok_id) == bystander_credit,
              "%s -> %s" % (bystander_credit, credit_of(ok_id)))
        check("被封禁用户的商品同步下架",
              mysql_query("SELECT COUNT(*) FROM product WHERE seller_id = %s AND status IN (0,1);"
                          % seller_id) == "0",
              mysql_query("SELECT status FROM product WHERE seller_id = %s;" % seller_id))

        status, res = call(base, "GET", "/api/user/home/%s" % seller_id)
        check("封禁后卖家主页返回 403", res.get("code") == 403, res.get("code"))


        # ================= 7. 处理联动：评价举报 =================
        print("\n== 7. 处理联动：评价举报 ==")
        # 造一笔完成订单并评价
        # ok_token 是认证学生：只有认证学生能发布商品，普通用户会被拦下，
        # 评价举报这一段就无从测起。
        _, res = call(base, "GET", "/api/category/list")
        category_id = (res.get("data") or [{}])[0].get("id")
        _, res = call(base, "POST", "/api/product", token=ok_token,
                      body={"title": "评价测试商品" + suffix, "categoryId": category_id,
                            "price": 15.0, "originalPrice": 45.0,
                            "description": "用于评价举报测试", "images": [seller_cert]})
        review_pid = res.get("data")
        check("评价举报专用商品发布成功", bool(review_pid), res.get("msg"))
        if review_pid:
            call(base, "PUT", "/api/product/%s/audit" % review_pid, token=admin_token,
                 body={"status": 1})

        # 买家需要先有收货地址才能下单
        _, res = call(base, "POST", "/api/address", token=buyer_token,
                      body={"receiverName": "测试收货人", "phone": "13800000000",
                            "address": "东区1号楼101"})
        address_id = res.get("data")
        check("为买家创建收货地址", bool(address_id), res.get("msg"))

        _, res = call(base, "POST", "/api/order/create", token=buyer_token,
                      body={"productId": review_pid, "addressId": address_id})
        order_no = (res.get("data") or {}).get("orderNo")
        check("为评价举报准备订单", bool(order_no), (review_pid, order_no, res.get("msg")))

        if order_no:
            call(base, "POST", "/api/order/%s/pay" % order_no, token=buyer_token)
            call(base, "POST", "/api/order/%s/ship" % order_no, token=ok_token)
            call(base, "POST", "/api/order/%s/receive" % order_no, token=buyer_token)
            _, res = call(base, "GET", "/api/order/%s" % order_no, token=buyer_token)
            order_id = (res.get("data") or {}).get("id")

            _, res = call(base, "POST", "/api/review", token=buyer_token,
                          body={"orderId": order_id, "reviewType": 1,
                                "descStars": 1, "shipStars": 1, "attitudeStars": 1,
                                "content": "卖家骗人，全是广告没有一句实话"})
            review_id = (res.get("data") or {}).get("id")
            check("违规评价提交成功", bool(review_id), res.get("msg"))

            if review_id:
                reviewer_credit_before = credit_of(buyer_id)
                _, res = call(base, "POST", "/api/report", token=ok_token,
                              body={"reportType": 3, "targetId": review_id, "reason": 5,
                                    "content": "这条评价是恶意广告，已被多人举报过"})
                review_report_id = res.get("data")
                check("评价举报提交成功", isinstance(review_report_id, int), res.get("msg"))
                ids.append(review_report_id)

                if review_report_id:
                    _, res = call(base, "GET", "/api/report/my/%s" % review_report_id, token=ok_token)
                    review_snapshot = ((res.get("data") or {}).get("snapshot") or {})
                    check("评价举报快照记录了内容", "全是广告" in str(review_snapshot.get("content")),
                          review_snapshot.get("content"))
                    # 评价的配图在库里是 JSON 数组字符串，快照里必须已经解析成数组，
                    # 否则管理端会按逗号 split 出带引号的假路径
                    check("评价快照的配图已解析为数组",
                          isinstance(review_snapshot.get("images"), list),
                          review_snapshot.get("images"))

                    call(base, "PUT", "/api/report/admin/%s/accept" % review_report_id,
                         token=cs_token)
                    status, res = call(base, "PUT",
                                       "/api/report/admin/%s/handle" % review_report_id,
                                       token=cs_token,
                                       body={"status": 2, "handleAction": 3,
                                             "handleResult": "核实为广告评价，已删除"})
                    check("评价举报处理成功", res.get("code") == 200, res.get("msg"))
                    check("被举报评价已逻辑删除",
                          mysql_query("SELECT deleted FROM review WHERE id = %s;" % review_id) == "1",
                          mysql_query("SELECT deleted FROM review WHERE id = %s;" % review_id))
                    reviewer_credit_after = credit_of(buyer_id)
                    if reviewer_credit_before is not None and reviewer_credit_after is not None:
                        check("评价人信誉分扣5分",
                              reviewer_credit_before - reviewer_credit_after == 5,
                              "%s -> %s" % (reviewer_credit_before, reviewer_credit_after))

                    status, res = call(base, "POST", "/api/report", token=ok_token,
                                       body={"reportType": 3, "targetId": review_id,
                                             "reason": 5, "content": "评价已经被删了不该再能举报"})
                    check("举报已删除的评价被拒",
                          res.get("code") == 400 and "不存在" in (res.get("msg") or ""),
                          res.get("msg"))
        else:
            check("评价举报流程跳过", False, "订单创建失败，无法继续评价举报测试")

        # ================= 8. 驳回校验 =================
        print("\n== 8. 驳回校验 =================")
        _, res = call(base, "POST", "/api/report", token=buyer_token,
                      body={"reportType": 2, "targetId": ok_id, "reason": 4,
                            "content": "对方说话有点冲，但这也值得举报一下"})
        reject_report_id = res.get("data")
        ids.append(reject_report_id)
        call(base, "PUT", "/api/report/admin/%s/accept" % reject_report_id, token=cs_token)

        status, res = call(base, "PUT", "/api/report/admin/%s/handle" % reject_report_id,
                           token=cs_token,
                           body={"status": 3, "handleAction": 2,
                                 "handleResult": "举报不成立"})
        check("驳回时不能携带处置动作",
              res.get("code") == 400 and "不能执行处置动作" in (res.get("msg") or ""),
              res.get("msg"))

        ok_credit_before = credit_of(ok_id)
        status, res = call(base, "PUT", "/api/report/admin/%s/handle" % reject_report_id,
                           token=cs_token,
                           body={"status": 3, "handleAction": 0,
                                 "handleResult": "经核实对方的言行属于正常沟通，不构成辱骂骚扰"})
        check("驳回成功", res.get("code") == 200, res.get("msg"))
        check("驳回不改变被举报人信誉分", credit_of(ok_id) == ok_credit_before,
              "%s -> %s" % (ok_credit_before, credit_of(ok_id)))
        rows = mysql_query("SELECT status FROM sys_user WHERE id = %s;" % ok_id)
        check("驳回不封禁用户", rows == "0", rows)

        status, res = call(base, "PUT", "/api/report/admin/%s/handle" % reject_report_id,
                           token=cs_token,
                           body={"status": 2, "handleAction": 0,
                                 "handleResult": "重复处理测试"})
        check("终态举报不能重复处理",
              res.get("code") == 400 and "不能重复处理" in (res.get("msg") or ""), res.get("msg"))

        status, res = call(base, "PUT", "/api/report/admin/%s/handle" % product_report_id,
                           token=cs_token,
                           body={"status": 2, "handleAction": 1, "handleResult": "再处理一次"})
        check("已处理的举报不能重复处理", res.get("code") == 400, res.get("msg"))

        status, res = call(base, "PUT", "/api/report/admin/%s/handle" % multi_img_report,
                           token=cs_token,
                           body={"status": 2, "handleAction": 1, "handleResult": "再处理一次"})
        check("已驳回的举报不能改判为已处理", res.get("code") == 400, res.get("msg"))

        # 注意：处理结果为空会被 DTO 的 @NotBlank 先拦下（在 Controller 层），
        # 压根到不了 Service 的终态校验，所以这条用例必须带非空 result
        status, res = call(base, "PUT", "/api/report/admin/%s/handle" % user_report_id,
                           token=cs_token,
                           body={"status": 2, "handleAction": 2, "handleResult": "再处理一次试试看"})
        check("已终结举报的重复处理被拒",
              res.get("code") == 400 and "不能重复处理" in (res.get("msg") or ""), res.get("msg"))

        status, res = call(base, "PUT", "/api/report/admin/99999999/handle", token=cs_token,
                           body={"status": 2, "handleAction": 0, "handleResult": "处理不存在的举报"})
        check("处理不存在的举报被拒", res.get("code") == 404, (res.get("code"), res.get("msg")))

        # 「举报成立」必须配一个处置动作。
        # 成立却不执行任何动作是矛盾数据：举报人被告知「已处理」，
        # 但违规内容还挂在那儿，等于处理了个寂寞。
        # 这里另建一条待处理举报，避免被上面的「终态不能重复处理」抢先拦下。
        # 用 ok_token 举报：它没举报过 ok_product，且自己不是该商品的卖家，
        # 因此不会撞上 24 小时节流与「不能举报自己」两条校验
        _, res = call(base, "POST", "/api/report", token=ok_token,
                      body={"reportType": 1, "targetId": ok_product, "reason": 3,
                            "content": "用来验证成立必须带处置动作的临时举报记录"})
        guard_report_id = res.get("data")
        if guard_report_id:
            ids.append(guard_report_id)
            status, res = call(base, "PUT", "/api/report/admin/%s/handle" % guard_report_id,
                               token=cs_token,
                               body={"status": 2, "handleAction": 0,
                                     "handleResult": "成立但是没有任何处置动作"})
            check("举报成立但不带处置动作被拒",
                  res.get("code") == 400 and "处置动作" in (res.get("msg") or ""), res.get("msg"))

            # 同一条件下，结果为空也要被拒
            status, res = call(base, "PUT", "/api/report/admin/%s/handle" % guard_report_id,
                               token=cs_token,
                               body={"status": 2, "handleAction": 1, "handleResult": "   "})
            check("处理结果为空被拒",
                  res.get("code") == 400 and "处理结果" in (res.get("msg") or ""), res.get("msg"))

            # 把这条临时举报处理掉：它停在「待处理」会让末尾的统计断言
            # 「待处理数为0」失败，也会在库里留一条没人管的记录
            status, res = call(base, "PUT", "/api/report/admin/%s/handle" % guard_report_id,
                               token=cs_token,
                               body={"status": 3, "handleAction": 0,
                                     "handleResult": "校验用临时举报，直接驳回以清理测试数据"})
            check("校验用举报已清理", res.get("code") == 200, res.get("msg"))
        else:
            check("准备校验用举报失败", False, res.get("msg"))

        # ================= 9. 操作日志 =================
        print("\n== 9. 操作日志 ==")
        status, res = call(base, "GET", "/api/operation-log/page", token=buyer_token)
        check("普通用户查操作日志返回 403", res.get("code") == 403, res.get("code"))

        _, res = call(base, "GET", "/api/operation-log/page?pageNum=1&pageSize=20&module=report",
                      token=admin_token)
        logs = (res.get("data") or {}).get("records") or []
        check("管理员可查询举报模块操作日志", len(logs) >= 4, len(logs))
        actions = [log["action"] for log in logs]
        check("接单动作被记录", "accept" in actions, actions)
        check("处理动作被记录", "handle" in actions, actions)
        check("操作日志记录了操作人昵称",
              any(log.get("operatorName") == "客服小周" + suffix for log in logs),
              [log.get("operatorName") for log in logs][:4])
        check("操作日志记录了操作人角色",
              any(log.get("operatorRoleLabel") == "客服" for log in logs),
              [log.get("operatorRoleLabel") for log in logs][:4])
        check("操作日志记录了IP", any(log.get("ip") for log in logs),
              [log.get("ip") for log in logs][:3])
        check("操作日志含可读详情",
              any(log.get("detail") and "参数" in log["detail"] for log in logs),
              [log.get("detail") for log in logs][:2])

        _, res = call(base, "GET", "/api/operation-log/page?pageNum=1&pageSize=20&module=user",
                      token=admin_token)
        user_logs = (res.get("data") or {}).get("records") or []
        check("用户管理模块操作日志被记录", len(user_logs) >= 1, len(user_logs))
        check("封禁动作被记录", any(l["action"] == "ban" for l in user_logs),
              [l["action"] for l in user_logs])

        _, res = call(base, "GET", "/api/operation-log/page?pageNum=1&pageSize=20&module=product",
                      token=admin_token)
        product_logs = (res.get("data") or {}).get("records") or []
        check("商品强制下架被记录", len(product_logs) >= 1, len(product_logs))

        _, res = call(base, "GET", "/api/operation-log/page?pageNum=1&pageSize=10&action=accept",
                      token=admin_token)
        accept_logs = (res.get("data") or {}).get("records") or []
        check("按动作筛选日志生效",
              accept_logs and all(l["action"] == "accept" for l in accept_logs),
              [l["action"] for l in accept_logs])

        _, res = call(base, "GET", "/api/operation-log/page?pageNum=1&pageSize=20",
                      token=cs_token)
        check("客服可查看操作日志（只读）", res.get("code") == 200, res.get("msg"))

        status, res = call(base, "DELETE", "/api/operation-log/1", token=admin_token)
        check("操作日志没有删除接口", status == 404 or res.get("code") in (400, 404, 500),
              (status, res.get("code")))

        # ================= 10. 统计 =================
        print("\n== 10. 举报统计 ==")
        _, res = call(base, "GET", "/api/report/admin/stats", token=admin_token)
        stats = res.get("data") or {}
        check("统计返回总数字段", "totalCount" in stats, sorted(stats.keys()))
        check("待处理数为0（已全部处理完）", stats.get("pendingCount") == 0,
              stats.get("pendingCount"))
        check("处理中数为0", stats.get("processingCount") == 0, stats.get("processingCount"))
        check("已处理数大于0", (stats.get("handledCount") or 0) >= 2, stats.get("handledCount"))
        check("已驳回数大于0", (stats.get("rejectedCount") or 0) >= 2, stats.get("rejectedCount"))
        check("处理率在0~1之间", 0 <= (stats.get("handleRate") or 0) <= 1, stats.get("handleRate"))
        check("今日新增数大于0", (stats.get("todayCount") or 0) >= 4, stats.get("todayCount"))
        check("平均处理时长为数值", isinstance(stats.get("avgHandleHours"), (int, float)),
              stats.get("avgHandleHours"))

        status, res = call(base, "GET", "/api/report/admin/stats", token=buyer_token)
        check("普通用户查统计返回 403", res.get("code") == 403, res.get("code"))

    finally:
        # ---------------- 清理 ----------------
        print("\n== 清理测试数据 ==")
        # 先清掉历次失败运行留下的孤儿举报（它们的举报人已被删除，
        # 留着会让「待处理数」「处理率」这类全局统计被污染）
        mysql_exec("DELETE r FROM report r LEFT JOIN sys_user u ON r.reporter_id = u.id"
                   " WHERE u.id IS NULL;")
        id_list = [i for i in ids if i]
        if id_list:
            lst = ",".join(str(i) for i in id_list)
            mysql_exec("DELETE FROM report WHERE id IN (%s);" % lst)
        rows = mysql_query("SELECT id FROM product WHERE title LIKE '%%%s%%' OR title LIKE '违规商品%s';"
                           % (suffix, suffix)) or ""
        pids = [int(x.split("\t")[0]) for x in rows.splitlines() if x.strip()]
        seller_ids = [seller_id, ok_id, buyer_id, cs_id]
        slst = ",".join(str(i) for i in seller_ids)
        mysql_exec("DELETE FROM product_image WHERE product_id IN (SELECT id FROM product WHERE seller_id IN (%s));"
                   % slst)
        mysql_exec("DELETE FROM product WHERE seller_id IN (%s);" % slst)
        mysql_exec("DELETE FROM trade_order WHERE buyer_id IN (%s) OR seller_id IN (%s);" % (slst, slst))
        mysql_exec("DELETE FROM review WHERE reviewer_id IN (%s) OR reviewee_id IN (%s);" % (slst, slst))
        # 只按 operator_id 删会漏掉管理员那几条：改角色、审核认证的日志
        # 操作人是 admin（不在本轮造的用户里），但目标是本轮造的用户。
        # 所以再按目标ID删一遍，否则库里会积累没人认领的日志行。
        mysql_exec("DELETE FROM sys_operation_log WHERE operator_id IN (%s);" % slst)
        log_targets = id_list + pids + seller_ids
        if log_targets:
            mysql_exec("DELETE FROM sys_operation_log WHERE target_id IN (%s);"
                       % ",".join(str(i) for i in log_targets))
        mysql_exec("DELETE FROM credit_log WHERE user_id IN (%s);" % slst)
        mysql_exec("DELETE FROM notice WHERE user_id IN (%s);" % slst)
        mysql_exec("DELETE FROM address WHERE user_id IN (%s);" % slst)
        mysql_exec("DELETE FROM favorite WHERE user_id IN (%s);" % slst)
        mysql_exec("DELETE FROM follow WHERE user_id IN (%s) OR followed_id IN (%s);" % (slst, slst))
        mysql_exec("DELETE FROM student_auth WHERE user_id IN (%s);" % slst)
        mysql_exec("DELETE FROM sys_user WHERE id IN (%s);" % slst)
        print("  已清理 %d 条举报、%d 个商品、%d 个用户"
              % (len(id_list), len(pids), len(seller_ids)))

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