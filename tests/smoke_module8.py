#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""模块8「个人中心与卖家主页」接口冒烟测试。

用法：
    mvn spring-boot:run
    python tests/smoke_module8.py

覆盖流程：
    个人中心聚合（未登录 401 / 字段完整性 / 统计数与实际数据一致）->
    卖家主页（免登录可看 / 软登录返回关注状态 / 匿名评价脱敏 / 只展示已上架商品）->
    资料修改（昵称长度、简介长度、性别非法值、头像）->
    手机号（授权码换取、重复手机号拒绝、与当前相同拒绝）->
    管理端用户管理（分页筛选、详情、客服只读、封禁联动下架、角色分配、认证审核）->
    注销账号（口令错误、密码错误、客服拒绝、成功注销且商品下架）-> 数据清理

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


def upload(base, token):
    import uuid as _uuid
    boundary = "----smoke" + _uuid.uuid4().hex
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


def make_student(base, admin_token, suffix, nickname=None):
    """注册微信用户并认证成学生（只有学生能发布商品）。"""
    token, user_id, _ = login_user(base, "m8s" + suffix)
    if nickname:
        call(base, "PUT", "/api/user/profile", token=token, body={"nickname": nickname})
    cert = (upload(base, token).get("data") or "")
    call(base, "POST", "/api/student-auth", token=token,
         body={"studentNo": "M8" + suffix, "realName": "模块八卖家",
               "school": "北华大学", "certImg": cert})
    _, page = call(base, "GET", "/api/student-auth/page?pageNum=1&pageSize=50&status=0",
                   token=admin_token)
    target = next((r for r in ((page.get("data") or {}).get("records") or [])
                   if r.get("userId") == user_id), None)
    if target:
        call(base, "PUT", "/api/student-auth/%s/audit" % target["id"],
             token=admin_token, body={"status": 1})
    return token, user_id, cert


def publish_product(base, token, admin_token, cert, title, price, audit=True):
    _, cat = call(base, "GET", "/api/category/list")
    category_id = (cat.get("data") or [{}])[0].get("id")
    _, res = call(base, "POST", "/api/product", token=token,
                  body={"title": title, "categoryId": category_id, "price": price,
                        "originalPrice": price * 3, "description": "模块八测试商品",
                        "images": [cert]})
    pid = res.get("data")
    if audit:
        call(base, "PUT", "/api/product/%s/audit" % pid, token=admin_token, body={"status": 1})
    return pid


def nickname_of(base, token):
    _, res = call(base, "GET", "/api/auth/me", token=token)
    return ((res.get("data") or {}).get("nickname") or "").strip()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="http://127.0.0.1:8080")
    args = parser.parse_args()
    base = args.base.rstrip("/")
    suffix = uuid.uuid4().hex[:6]

    print("=" * 60)
    print("模块8 个人中心与卖家主页 接口冒烟测试  base =", base)
    print("=" * 60)

    # ---------------- 准备数据 ----------------
    _, admin_res = call(base, "POST", "/api/auth/admin-login",
                        body={"username": "admin", "password": "123456"})
    admin_token = (admin_res.get("data") or {}).get("token")
    _, cs_res = call(base, "POST", "/api/auth/admin-login",
                     body={"username": "service01", "password": "123456"})
    cs_token = (cs_res.get("data") or {}).get("token")
    if not admin_token:
        print("管理员登录失败，终止测试")
        return 1

    seller_token, seller_id, cert = make_student(base, admin_token, suffix, "模块八卖家" + suffix)
    buyer_token, buyer_id, _ = login_user(base, "m8b" + suffix)
    guest_token, guest_id, _ = login_user(base, "m8g" + suffix)
    check("准备三个测试用户", bool(seller_token and buyer_token and guest_token),
          [seller_id, buyer_id, guest_id])

    p1 = publish_product(base, seller_token, admin_token, cert, "模块八-在售商品-自行车", 150)
    p2 = publish_product(base, seller_token, admin_token, cert, "模块八-在售商品-台灯", 45)
    p3 = publish_product(base, seller_token, admin_token, cert, "模块八-待审核商品", 20, audit=False)
    check("卖家发布 3 件商品（2 件已上架 1 件待审核）", bool(p1 and p2 and p3), [p1, p2, p3])

    # 买家收藏 p1、关注卖家
    call(base, "POST", "/api/favorite/%s" % p1, token=buyer_token)
    call(base, "POST", "/api/follow/%s" % seller_id, token=buyer_token)

    print("\n== 1. 个人中心聚合接口 ==")
    _, res = call(base, "GET", "/api/user/profile")
    check("未登录访问个人中心 401", res.get("code") == 401, res.get("msg"))

    _, res = call(base, "GET", "/api/user/profile", token=seller_token)
    profile = res.get("data") or {}
    check("登录后个人中心返回 200", res.get("code") == 200, res.get("msg"))
    required = ["userId", "nickname", "creditScore", "creditLevelLabel", "creditLevelColor",
                "publishCount", "soldCount", "onSaleCount", "favoriteCount", "followCount",
                "fansCount", "pendingReviewCount", "orderCount", "genderLabel", "roleLabel"]
    missing = [k for k in required if k not in profile]
    check("个人中心返回全部必需字段", not missing, "缺失: %s" % missing)
    check("个人中心发布数与实际一致（3 件）", profile.get("publishCount") == 3,
          profile.get("publishCount"))
    check("个人中心在售数与实际一致（2 件）", profile.get("onSaleCount") == 2,
          profile.get("onSaleCount"))
    check("个人中心粉丝数与实际一致（1 人）", profile.get("fansCount") == 1,
          profile.get("fansCount"))
    check("个人中心返回信誉分等级与颜色", bool(profile.get("creditLevelLabel")) and
          bool(profile.get("creditLevelColor")), profile.get("creditLevelLabel"))
    check("个人中心返回订单数分组", isinstance(profile.get("orderCount"), dict),
          profile.get("orderCount"))

    _, res = call(base, "GET", "/api/user/profile", token=buyer_token)
    buyer_profile = res.get("data") or {}
    check("买家收藏数为 1", buyer_profile.get("favoriteCount") == 1,
          buyer_profile.get("favoriteCount"))
    check("买家关注数为 1", buyer_profile.get("followCount") == 1,
          buyer_profile.get("followCount"))
    check("买家待评价数为 0", buyer_profile.get("pendingReviewCount") == 0,
          buyer_profile.get("pendingReviewCount"))

    print("\n== 2. 卖家主页（免登录） ==")
    _, res = call(base, "GET", "/api/user/home/%s" % seller_id)
    home = res.get("data") or {}
    check("未登录可访问卖家主页", res.get("code") == 200, res.get("msg"))
    check("卖家主页返回昵称与信誉分", bool(home.get("nickname")) and home.get("creditScore") is not None,
          [home.get("nickname"), home.get("creditScore")])
    check("卖家主页在售数正确（2）", home.get("onSaleCount") == 2, home.get("onSaleCount"))
    check("卖家主页粉丝数正确（1）", home.get("fansCount") == 1, home.get("fansCount"))
    check("未登录时 followed 为 false", home.get("followed") is False, home.get("followed"))
    check("未登录时 isSelf 为 false", home.get("isSelf") is False, home.get("isSelf"))
    check("卖家主页返回评价统计字段", home.get("reviewCount") is not None and
          home.get("goodRate") is not None, [home.get("reviewCount"), home.get("goodRate")])

    _, res = call(base, "GET", "/api/user/home/%s" % seller_id, token=buyer_token)
    home2 = res.get("data") or {}
    check("已关注者访问时 followed 为 true", home2.get("followed") is True, home2.get("followed"))
    _, res = call(base, "GET", "/api/user/home/%s" % seller_id, token=seller_token)
    home3 = res.get("data") or {}
    check("本人访问自己主页时 isSelf 为 true", home3.get("isSelf") is True, home3.get("isSelf"))

    _, res = call(base, "GET", "/api/user/home/999999999")
    check("访问不存在的用户主页返回业务错误", res.get("code") != 200, res.get("msg"))

    print("\n== 3. 卖家在售商品列表 ==")
    _, res = call(base, "GET", "/api/user/products/%s?pageNum=1&pageSize=10" % seller_id)
    page = res.get("data") or {}
    check("免登录可查卖家在售商品", res.get("code") == 200, res.get("msg"))
    check("只返回已上架商品（2 件）", page.get("total") == 2, page.get("total"))
    statuses = set(r.get("status") for r in (page.get("records") or []))
    check("返回的商品状态全部为已上架", statuses == {1}, statuses)

    print("\n== 4. 修改个人资料 ==")
    _, res = call(base, "PUT", "/api/user/profile", body={"nickname": "未登录改昵称"})
    check("未登录修改资料 401", res.get("code") == 401, res.get("msg"))

    _, res = call(base, "PUT", "/api/user/profile", token=buyer_token,
                  body={"nickname": "x" * 21})
    check("昵称超 20 字被拒绝", res.get("code") != 200, res.get("msg"))

    _, res = call(base, "PUT", "/api/user/profile", token=buyer_token,
                  body={"intro": "y" * 101})
    check("简介超 100 字被拒绝", res.get("code") != 200, res.get("msg"))

    _, res = call(base, "PUT", "/api/user/profile", token=buyer_token,
                  body={"gender": 9})
    check("非法性别值被拒绝", res.get("code") != 200, res.get("msg"))

    _, res = call(base, "PUT", "/api/user/profile", token=buyer_token,
                  body={"nickname": "模块八新昵称" + suffix, "gender": 2, "intro": "喜欢卖闲置"})
    check("修改资料成功", res.get("code") == 200, res.get("msg"))

    _, res = call(base, "GET", "/api/user/profile", token=buyer_token)
    p = res.get("data") or {}
    check("修改后昵称生效", p.get("nickname") == "模块八新昵称" + suffix, p.get("nickname"))
    check("修改后性别生效且文案正确", p.get("gender") == 2 and p.get("genderLabel") == "女",
          [p.get("gender"), p.get("genderLabel")])
    check("修改后简介生效", p.get("intro") == "喜欢卖闲置", p.get("intro"))

    _, res = call(base, "PUT", "/api/user/profile", token=buyer_token, body={})
    check("空更新被拒绝", res.get("code") != 200, res.get("msg"))

    print("\n== 5. 修改手机号 ==")
    _, res = call(base, "PUT", "/api/user/phone", token=buyer_token, body={"code": ""})
    check("空授权码被拒绝", res.get("code") != 200, res.get("msg"))

    _, res = call(base, "PUT", "/api/user/phone", token=buyer_token, body={"code": "m8phone" + suffix})
    check("授权码换取手机号成功", res.get("code") == 200, res.get("msg"))
    _, res = call(base, "GET", "/api/user/profile", token=buyer_token)
    p = res.get("data") or {}
    check("手机号已写入个人中心", bool(p.get("phone")), p.get("phone"))
    bound_phone = p.get("phone")

    _, res = call(base, "PUT", "/api/user/phone", token=buyer_token, body={"code": "m8phone" + suffix})
    check("换成相同手机号被拒绝", res.get("code") != 200, res.get("msg"))

    # 让 guest 也绑定同一个授权码，验证手机号唯一性
    _, res = call(base, "PUT", "/api/user/phone", token=guest_token, body={"code": "m8phone" + suffix})
    check("第二个账号绑定同一手机号被拒绝（唯一性校验）", res.get("code") != 200, res.get("msg"))
    _, res = call(base, "GET", "/api/user/profile", token=guest_token)
    guest_phone = (res.get("data") or {}).get("phone")
    check("被拒绝后第二个账号手机号未被占用", not guest_phone, guest_phone)

    print("\n== 6. 管理端用户列表 ==")
    _, res = call(base, "GET", "/api/admin/user/page?pageNum=1&pageSize=10")
    check("未登录访问管理端用户列表 401", res.get("code") == 401, res.get("msg"))

    _, res = call(base, "GET", "/api/admin/user/page?pageNum=1&pageSize=10", token=cs_token)
    check("客服可访问用户列表（只读）", res.get("code") == 200, res.get("msg"))

    seller_nickname = nickname_of(base, seller_token)
    _, res = call(base, "GET", "/api/admin/user/page?pageNum=1&pageSize=10&keyword=%s"
                  % quote(seller_nickname), token=admin_token)
    records = (res.get("data") or {}).get("records") or []
    check("按昵称搜索命中卖家", any(r.get("userId") == seller_id for r in records),
          [(r.get("userId"), r.get("nickname")) for r in records])
    hit = next((r for r in records if r.get("userId") == seller_id), {})
    check("列表返回角色文案与等级色", bool(hit.get("roleLabel")) and bool(hit.get("creditLevelColor")),
          [hit.get("roleLabel"), hit.get("creditLevelColor")])
    check("列表返回学号", hit.get("studentNo") == "M8" + suffix, hit.get("studentNo"))
    check("列表返回发布数", hit.get("publishCount") == 3, hit.get("publishCount"))

    _, res = call(base, "GET", "/api/admin/user/page?pageNum=1&pageSize=10&studentVerified=1"
                  "&minCredit=0&maxCredit=150", token=admin_token)
    check("按认证状态 + 信誉分区间筛选可用", res.get("code") == 200, res.get("msg"))

    _, res = call(base, "GET", "/api/admin/user/page?pageNum=1&pageSize=10&role=student",
                  token=admin_token)
    roles = set(r.get("role") for r in ((res.get("data") or {}).get("records") or []))
    check("按角色筛选只返回该角色", roles <= {"student"}, roles)

    print("\n== 7. 管理端用户详情 ==")
    _, res = call(base, "GET", "/api/admin/user/%s" % seller_id, token=admin_token)
    detail = res.get("data") or {}
    check("管理端用户详情返回成功", res.get("code") == 200, res.get("msg"))
    check("详情含基本信息（昵称/学号/简介）", bool(detail.get("studentNo")) and
          detail.get("user") is not None, detail.get("studentNo"))
    check("详情含信誉分变动记录数组", isinstance(detail.get("creditLogs"), list),
          type(detail.get("creditLogs")).__name__)
    check("详情含发布商品列表", isinstance(detail.get("products"), dict) or
          isinstance(detail.get("products"), list), type(detail.get("products")).__name__)
    check("详情含订单记录", detail.get("orders") is not None, type(detail.get("orders")).__name__)
    check("详情含评价记录", detail.get("reviews") is not None, type(detail.get("reviews")).__name__)

    print("\n== 8. 管理端写操作权限 ==")
    _, res = call(base, "PUT", "/api/admin/user/%s" % seller_id, token=cs_token,
                  body={"nickname": "客服改的昵称"})
    check("客服修改用户资料 403", res.get("code") == 403, res.get("msg"))

    _, res = call(base, "PUT", "/api/admin/user/%s/status" % seller_id, token=cs_token,
                  body={"status": 1})
    check("客服封禁用户 403", res.get("code") == 403, res.get("msg"))

    _, res = call(base, "PUT", "/api/admin/user/%s/role" % seller_id, token=cs_token,
                  body={"role": "customer_service"})
    check("客服分配角色 403", res.get("code") == 403, res.get("msg"))

    _, res = call(base, "PUT", "/api/admin/user/%s" % seller_id, token=admin_token,
                  body={"nickname": "管理员改的昵称" + suffix, "intro": "管理员改的简介"})
    check("管理员修改用户资料成功", res.get("code") == 200, res.get("msg"))
    _, res = call(base, "GET", "/api/user/profile", token=seller_token)
    check("管理员改的资料对用户可见",
          (res.get("data") or {}).get("nickname") == "管理员改的昵称" + suffix,
          (res.get("data") or {}).get("nickname"))

    print("\n== 9. 封禁与解封联动 ==")
    _, res = call(base, "PUT", "/api/admin/user/%s/status" % seller_id, token=admin_token,
                  body={"status": 1, "reason": "模块八测试封禁"})
    check("管理员封禁用户成功", res.get("code") == 200, res.get("msg"))
    _, res = call(base, "GET", "/api/user/home/%s" % seller_id)
    check("封禁后卖家主页不可见", res.get("code") != 200, res.get("msg"))

    rows = mysql_query("SELECT COUNT(*) FROM product WHERE seller_id = %s AND status = 1;" % seller_id)
    if rows is None:
        print("  [SKIP] 未找到 mysql 客户端，跳过下架联动校验")
    else:
        check("封禁后其在架商品全部下架", rows.strip() == "0", rows)

    _, res = call(base, "PUT", "/api/admin/user/%s/status" % seller_id, token=admin_token,
                  body={"status": 0})
    check("管理员解封用户成功", res.get("code") == 200, res.get("msg"))
    _, res = call(base, "GET", "/api/user/home/%s" % seller_id)
    check("解封后卖家主页恢复可见", res.get("code") == 200, res.get("msg"))

    _, res = call(base, "PUT", "/api/admin/user/%s/status" % seller_id, token=admin_token,
                  body={"status": 0})
    check("重复设置相同状态被拒绝", res.get("code") != 200, res.get("msg"))

    print("\n== 10. 角色分配 ==")
    _, res = call(base, "PUT", "/api/admin/user/%s/role" % seller_id, token=admin_token,
                  body={"role": "not_exist_role"})
    check("非法角色被拒绝", res.get("code") != 200, res.get("msg"))

    _, res = call(base, "PUT", "/api/admin/user/%s/role" % seller_id, token=admin_token,
                  body={"role": "customer_service"})
    check("降级为客服成功", res.get("code") == 200, res.get("msg"))
    rows = mysql_query("SELECT role, student_verified, publish_limit FROM sys_user WHERE id = %s;"
                       % seller_id)
    if rows is None:
        print("  [SKIP] 未找到 mysql 客户端，跳过角色联动校验")
    else:
        parts = rows.strip().split("\t")
        check("角色已更新为 customer_service", parts[0] == "customer_service", rows)
        check("降级后认证状态被清除", parts[1] == "0", rows)
        check("降级后发布额度被回收", parts[2] == "0", rows)

    _, res = call(base, "PUT", "/api/admin/user/%s/role" % seller_id, token=admin_token,
                  body={"role": "student"})
    check("恢复为认证学生成功", res.get("code") == 200, res.get("msg"))
    rows = mysql_query("SELECT student_verified, publish_limit FROM sys_user WHERE id = %s;"
                       % seller_id)
    if rows is not None:
        parts = rows.strip().split("\t")
        check("恢复后认证状态置回 1", parts[0] == "1", rows)
        check("恢复后发布额度还原为 20", parts[1] == "20", rows)

    print("\n== 11. 学生认证审核 ==")
    pending_token, pending_id, _ = login_user(base, "m8p" + suffix)
    cert2 = (upload(base, pending_token).get("data") or "")
    call(base, "POST", "/api/student-auth", token=pending_token,
         body={"studentNo": "M8P" + suffix, "realName": "模块八待审",
               "school": "北华大学", "certImg": cert2})
    _, res = call(base, "PUT", "/api/admin/user/%s/student-auth" % pending_id, token=admin_token,
                  body={"status": 1})
    check("管理员通过学生认证成功", res.get("code") == 200, res.get("msg"))
    _, res = call(base, "GET", "/api/user/profile", token=pending_token)
    check("认证通过后个人中心显示已认证",
          (res.get("data") or {}).get("studentVerified") == 1,
          (res.get("data") or {}).get("studentVerified"))

    _, res = call(base, "PUT", "/api/admin/user/%s/student-auth" % pending_id, token=cs_token,
                  body={"status": 1})
    check("客服审核学生认证 403", res.get("code") == 403, res.get("msg"))

    print("\n== 12. 注销账号 ==")
    _, res = call(base, "DELETE", "/api/user/account", token=guest_token,
                  body={"password": "123456", "confirmText": "注销账号"})
    check("未设密码的用户注销被拒绝（提示先设密码）", res.get("code") != 200, res.get("msg"))

    # 给 guest 设置账号密码
    _, res = call(base, "POST", "/api/auth/bind-account", token=guest_token,
                  body={"username": "m8del" + suffix, "password": "123456"})
    check("绑定账号密码成功", res.get("code") == 200, res.get("msg"))

    _, res = call(base, "DELETE", "/api/user/account", token=guest_token,
                  body={"password": "123456", "confirmText": "随便写"})
    check("确认口令错误时注销被拒绝", res.get("code") != 200, res.get("msg"))

    _, res = call(base, "DELETE", "/api/user/account", token=guest_token,
                  body={"password": "wrongpwd", "confirmText": "注销账号"})
    check("密码错误时注销被拒绝", res.get("code") != 200, res.get("msg"))

    # 客服 / 管理员不能自助注销
    _, res = call(base, "DELETE", "/api/user/account", token=admin_token,
                  body={"password": "123456", "confirmText": "注销账号"})
    check("管理员自助注销被拒绝", res.get("code") == 403, res.get("msg"))

    # 造一个带在架商品的账号，注销后验证商品被下架
    del_seller_token, del_seller_id, del_cert = make_student(base, admin_token, suffix + "d")
    del_pid = publish_product(base, del_seller_token, admin_token, del_cert, "模块八-注销测试商品", 30)
    call(base, "POST", "/api/auth/bind-account", token=del_seller_token,
         body={"username": "m8dels" + suffix, "password": "123456"})
    _, res = call(base, "DELETE", "/api/user/account", token=del_seller_token,
                  body={"password": "123456", "confirmText": "注销账号"})
    check("普通用户注销账号成功", res.get("code") == 200, res.get("msg"))

    rows = mysql_query("SELECT deleted FROM sys_user WHERE id = %s;" % del_seller_id)
    if rows is None:
        print("  [SKIP] 未找到 mysql 客户端，跳过注销落库校验")
    else:
        check("账号为软删除（deleted=1）", rows.strip() == "1", rows)
        rows = mysql_query("SELECT status FROM product WHERE id = %s;" % del_pid)
        check("注销后商品被下架（status=2）", rows is not None and rows.strip() == "2", rows)

    _, res = call(base, "GET", "/api/user/profile", token=del_seller_token)
    check("注销后 token 失效（401）", res.get("code") == 401, res.get("msg"))

    print("\n== 13. 退出登录 ==")
    _, res = call(base, "POST", "/api/user/logout", token=buyer_token)
    check("退出登录返回 200", res.get("code") == 200, res.get("msg"))
    _, res = call(base, "GET", "/api/user/profile", token=buyer_token)
    check("退出后 token 失效（401）", res.get("code") == 401, res.get("msg"))

    # ---------------- 清理 ----------------
    print("\n== 14. 数据清理 ==")
    user_ids = [seller_id, buyer_id, guest_id, pending_id, del_seller_id]
    id_list = ",".join(str(i) for i in user_ids)
    extra = mysql_query("SELECT id FROM product WHERE seller_id IN (%s);" % id_list) or ""
    pids = [p1, p2, p3, del_pid]
    for line in extra.splitlines():
        if line.strip():
            pids.append(int(line.strip()))
    pid_list = ",".join(str(i) for i in set(pids))
    mysql_exec("DELETE FROM product_image WHERE product_id IN (%s);" % pid_list)
    mysql_exec("DELETE FROM product WHERE id IN (%s);" % pid_list)
    mysql_exec("DELETE FROM follow WHERE user_id IN (%s) OR followed_id IN (%s);" % (id_list, id_list))
    mysql_exec("DELETE FROM favorite WHERE user_id IN (%s);" % id_list)
    mysql_exec("DELETE FROM student_auth WHERE user_id IN (%s);" % id_list)
    mysql_exec("DELETE FROM credit_log WHERE user_id IN (%s);" % id_list)
    mysql_exec("DELETE FROM notice WHERE user_id IN (%s);" % id_list)
    mysql_exec("DELETE FROM sys_user WHERE id IN (%s);" % id_list)
    left = mysql_query("SELECT COUNT(*) FROM sys_user WHERE id IN (%s);" % id_list)
    if left is not None:
        check("测试用户已清理", left.strip() == "0", left)

    total = len(RESULTS)
    failed = total - PASSED
    print("\n" + "=" * 60)
    print("用例 %d 个，通过 %d 个，失败 %d 个" % (total, PASSED, failed))
    print("结论：全部通过" if failed == 0 else "结论：存在失败用例")
    print("=" * 60)
    if failed:
        for name, ok, detail in RESULTS:
            if not ok:
                print("  FAIL:", name, "->", detail)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
