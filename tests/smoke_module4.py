#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""模块4「收藏与关注」接口冒烟测试。

用法（后端已在 8080 启动，且已执行 sql/V5__favorite_follow.sql）：
    python tests/smoke_module4.py

覆盖流程：
    权限（未登录 401 / 客服只读 403）-> 收藏切换与三处计数联动 ->
    Redis 收藏态与唯一索引 -> 我的收藏游标分页与状态蒙层 ->
    关注切换 / 禁止自关注 / 关注与粉丝列表 / 回关 ->
    个人中心统计 -> 清理

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
    token, user_id, _ = login_user(base, "m4s" + suffix)
    cert = (upload(base, token).get("data") or "")
    call(base, "POST", "/api/student-auth", token=token,
         body={"studentNo": "M4" + suffix, "realName": "模块四学生",
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
    """本机直连 MySQL 校验落库结果。找不到客户端时返回 None（相关断言跳过）。"""
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
    args = parser.parse_args()
    base = args.base.rstrip("/")
    suffix = uuid.uuid4().hex[:6]

    print("\n== 1. 准备账号与商品 ==")
    _, admin = call(base, "POST", "/api/auth/admin-login",
                    body={"username": "admin", "password": "123456"})
    admin_token = (admin.get("data") or {}).get("token")
    check("管理员登录成功", bool(admin_token))

    buyer_token, buyer_id, _ = login_user(base, "m4a" + suffix)
    check("普通用户（买家）微信登录成功", bool(buyer_token) and bool(buyer_id))

    seller_token, seller_id, cert = make_student(base, admin_token, suffix)
    check("学生卖家注册并认证通过", bool(seller_token) and bool(seller_id))

    _, cats = call(base, "GET", "/api/category/list")
    category_id = (cats.get("data") or [{}])[0].get("id")
    product_ids = []
    for idx in range(3):
        _, created = call(base, "POST", "/api/product", token=seller_token,
                          body={"title": "模块四收藏测试%s-%d" % (suffix, idx),
                                "categoryId": category_id, "price": 88 + idx,
                                "originalPrice": 199, "description": "收藏关注冒烟测试商品",
                                "images": [cert]})
        pid = created.get("data")
        if pid:
            call(base, "PUT", "/api/product/%s/audit" % pid, token=admin_token, body={"status": 1})
            product_ids.append(pid)
    check("卖家发布并上架 3 件商品", len(product_ids) == 3, product_ids)
    if len(product_ids) < 3:
        print("商品准备失败，终止测试")
        return 1
    p1, p2, p3 = product_ids

    print("\n== 2. 权限校验 ==")
    status, unauth = call(base, "POST", "/api/favorite/%s" % p1)
    check("未登录收藏返回 401", unauth.get("code") == 401, "http=%s code=%s" % (status, unauth.get("code")))
    _, unauth_my = call(base, "GET", "/api/favorite/my")
    check("未登录查看收藏列表返回 401", unauth_my.get("code") == 401, unauth_my.get("msg"))
    _, unauth_follow = call(base, "POST", "/api/follow/%s" % seller_id)
    check("未登录关注返回 401", unauth_follow.get("code") == 401, unauth_follow.get("msg"))

    _, cs_login = call(base, "POST", "/api/auth/login",
                       body={"username": "service01", "password": "123456"})
    cs_token = (cs_login.get("data") or {}).get("token")
    check("客服账号登录成功", bool(cs_token))
    _, cs_fav = call(base, "POST", "/api/favorite/%s" % p1, token=cs_token)
    check("客服写操作被拦截（收藏返回 403）", cs_fav.get("code") == 403, cs_fav.get("msg"))
    check("拦截文案符合约定", "客服账号无操作权限" in (cs_fav.get("msg") or ""), cs_fav.get("msg"))
    _, cs_my = call(base, "GET", "/api/favorite/my", token=cs_token)
    check("客服只读可查看收藏列表", cs_my.get("code") == 200, cs_my.get("msg"))
    _, cs_following = call(base, "GET", "/api/follow/following", token=cs_token)
    check("客服只读可查看关注列表", cs_following.get("code") == 200, cs_following.get("msg"))

    _, missing = call(base, "POST", "/api/favorite/99999999", token=buyer_token)
    check("收藏不存在的商品被拒绝", missing.get("code") != 200, missing.get("msg"))

    print("\n== 3. 收藏切换与计数联动 ==")
    _, detail0 = call(base, "GET", "/api/product/%s" % p1, token=buyer_token)
    d0 = detail0.get("data") or {}
    check("初始未收藏且收藏数为 0", d0.get("favorited") is False and d0.get("favoriteCount") == 0,
          "favorited=%s count=%s" % (d0.get("favorited"), d0.get("favoriteCount")))

    _, fav1 = call(base, "POST", "/api/favorite/%s" % p1, token=buyer_token)
    f1 = fav1.get("data") or {}
    check("收藏成功返回已收藏", fav1.get("code") == 200 and f1.get("favorited") is True,
          fav1.get("msg"))
    check("收藏后商品收藏数为 1", f1.get("favoriteCount") == 1, f1.get("favoriteCount"))

    _, chk1 = call(base, "GET", "/api/favorite/check/%s" % p1, token=buyer_token)
    check("检查收藏状态返回 true", chk1.get("data") is True, chk1.get("data"))

    _, detail1 = call(base, "GET", "/api/product/%s" % p1, token=buyer_token)
    d1 = detail1.get("data") or {}
    check("商品详情返回已收藏状态", d1.get("favorited") is True and d1.get("favoriteCount") == 1,
          "favorited=%s count=%s" % (d1.get("favorited"), d1.get("favoriteCount")))

    db_fav = mysql_query("SELECT COUNT(*) FROM favorite WHERE user_id=%s AND product_id=%s;"
                         % (buyer_id, p1))
    if db_fav is not None:
        check("favorite 表只有一条收藏记录（唯一索引生效）", db_fav == "1", db_fav)
    db_product_count = mysql_query("SELECT favorite_count FROM product WHERE id=%s;" % p1)
    if db_product_count is not None:
        check("product.favorite_count 同步为 1", db_product_count == "1", db_product_count)
    db_user_count = mysql_query("SELECT favorite_count FROM sys_user WHERE id=%s;" % buyer_id)
    if db_user_count is not None:
        check("sys_user.favorite_count 同步为 1", db_user_count == "1", db_user_count)

    _, fav_off = call(base, "POST", "/api/favorite/%s" % p1, token=buyer_token)
    off = fav_off.get("data") or {}
    check("再次点击取消收藏", off.get("favorited") is False, fav_off.get("msg"))
    check("取消后收藏数回到 0", off.get("favoriteCount") == 0, off.get("favoriteCount"))
    _, chk2 = call(base, "GET", "/api/favorite/check/%s" % p1, token=buyer_token)
    check("取消后检查状态为 false", chk2.get("data") is False, chk2.get("data"))
    db_fav2 = mysql_query("SELECT COUNT(*) FROM favorite WHERE user_id=%s AND product_id=%s;"
                          % (buyer_id, p1))
    if db_fav2 is not None:
        check("取消收藏后记录已物理删除", db_fav2 == "0", db_fav2)

    _, fav_again = call(base, "POST", "/api/favorite/%s" % p1, token=buyer_token)
    check("取消后可再次收藏（幂等）", (fav_again.get("data") or {}).get("favorited") is True)

    print("\n== 4. 我的收藏（游标分页） ==")
    call(base, "POST", "/api/favorite/%s" % p2, token=buyer_token)
    call(base, "POST", "/api/favorite/%s" % p3, token=buyer_token)
    _, my = call(base, "GET", "/api/favorite/my", token=buyer_token)
    my_data = my.get("data") or {}
    records = my_data.get("records") or []
    check("我的收藏返回 3 条", len(records) == 3, len(records))
    check("按收藏时间倒序（最后收藏的排最前）",
          records and records[0].get("id") == p3, [r.get("id") for r in records])
    first = records[0] if records else {}
    check("收藏项包含商品卡片所需字段",
          all(first.get(k) for k in ("cover", "sellerNickname", "statusLabel", "favoriteTime")),
          list(first.keys()))

    _, page1 = call(base, "GET", "/api/favorite/my?size=2", token=buyer_token)
    pd1 = page1.get("data") or {}
    check("游标分页第一页返回 2 条", len(pd1.get("records") or []) == 2)
    check("第一页 hasMore 为 true", pd1.get("hasMore") is True)
    check("第一页返回 nextCursor", bool(pd1.get("nextCursor")), pd1.get("nextCursor"))
    cursor = pd1.get("nextCursor")
    _, page2 = call(base, "GET", "/api/favorite/my?size=2&cursor=%s" % cursor, token=buyer_token)
    pd2 = page2.get("data") or {}
    check("第二页返回剩余 1 条", len(pd2.get("records") or []) == 1, len(pd2.get("records") or []))
    check("第二页 hasMore 为 false", pd2.get("hasMore") is False)
    ids_page1 = [r.get("id") for r in (pd1.get("records") or [])]
    ids_page2 = [r.get("id") for r in (pd2.get("records") or [])]
    check("两页数据无重复", not set(ids_page1) & set(ids_page2), "%s | %s" % (ids_page1, ids_page2))
    check("两页合计覆盖全部收藏", len(ids_page1 + ids_page2) == 3)

    call(base, "PUT", "/api/product/%s/offline" % p2, token=seller_token)
    _, my2 = call(base, "GET", "/api/favorite/my?size=10", token=buyer_token)
    offline_row = next((r for r in ((my2.get("data") or {}).get("records") or [])
                        if r.get("id") == p2), None)
    check("已下架商品仍保留在收藏列表", offline_row is not None)
    check("下架商品带状态文案用于蒙层",
          bool(offline_row) and offline_row.get("status") == 2
          and offline_row.get("statusLabel") == "已下架",
          "%s/%s" % (offline_row.get("status") if offline_row else None,
                     offline_row.get("statusLabel") if offline_row else None))

    print("\n== 5. 关注切换与卖家信息 ==")
    _, fol1 = call(base, "POST", "/api/follow/%s" % seller_id, token=buyer_token)
    fd1 = fol1.get("data") or {}
    check("关注卖家成功", fol1.get("code") == 200 and fd1.get("followed") is True, fol1.get("msg"))
    check("关注后卖家粉丝数为 1", fd1.get("fansCount") == 1, fd1.get("fansCount"))

    _, fol_chk = call(base, "GET", "/api/follow/check/%s" % seller_id, token=buyer_token)
    check("检查关注状态返回 true", fol_chk.get("data") is True, fol_chk.get("data"))

    _, detail2 = call(base, "GET", "/api/product/%s" % p1, token=buyer_token)
    seller2 = (detail2.get("data") or {}).get("seller") or {}
    check("商品详情卖家区返回已关注与粉丝数",
          seller2.get("followed") is True and seller2.get("fansCount") == 1,
          "followed=%s fans=%s" % (seller2.get("followed"), seller2.get("fansCount")))

    _, self_follow = call(base, "POST", "/api/follow/%s" % buyer_id, token=buyer_token)
    check("不能关注自己", self_follow.get("code") != 200, self_follow.get("msg"))
    check("自关注提示文案正确", "不能关注自己" in (self_follow.get("msg") or ""), self_follow.get("msg"))

    _, ghost = call(base, "POST", "/api/follow/99999999", token=buyer_token)
    check("关注不存在的用户被拒绝", ghost.get("code") != 200, ghost.get("msg"))

    _, anon_detail = call(base, "GET", "/api/product/%s" % p1)
    anon = anon_detail.get("data") or {}
    anon_seller = anon.get("seller") or {}
    check("未登录访问详情收藏态为 false", anon.get("favorited") is False)
    check("未登录访问详情关注态为 false", anon_seller.get("followed") is False)

    _, owner_detail = call(base, "GET", "/api/product/%s" % p1, token=seller_token)
    owner_seller = (owner_detail.get("data") or {}).get("seller") or {}
    check("卖家看自己的商品不返回已关注", owner_seller.get("followed") is False,
          owner_seller.get("followed"))

    _, following = call(base, "GET", "/api/follow/following?pageNum=1&pageSize=10", token=buyer_token)
    fol_data = following.get("data") or {}
    fol_records = fol_data.get("records") or []
    check("关注列表包含该卖家", any(r.get("userId") == seller_id for r in fol_records),
          [r.get("userId") for r in fol_records])
    target = next((r for r in fol_records if r.get("userId") == seller_id), {})
    check("关注列表带在售商品数", target.get("onSaleCount") == 2, target.get("onSaleCount"))
    check("关注列表按钮状态为已关注", target.get("followed") is True)
    check("关注列表带昵称与认证标识",
          bool(target.get("nickname")) and target.get("studentVerified") is True,
          "%s/%s" % (target.get("nickname"), target.get("studentVerified")))

    _, buyer_fans = call(base, "GET", "/api/follow/followers", token=buyer_token)
    check("买家暂时没有粉丝", ((buyer_fans.get("data") or {}).get("total") or 0) == 0)

    _, seller_fans = call(base, "GET", "/api/follow/followers", token=seller_token)
    fans_records = (seller_fans.get("data") or {}).get("records") or []
    fan = next((r for r in fans_records if r.get("userId") == buyer_id), None)
    check("卖家粉丝列表包含买家", fan is not None, [r.get("userId") for r in fans_records])
    check("粉丝列表未回关时 followed 为 false", bool(fan) and fan.get("followed") is False,
          fan.get("followed") if fan else None)

    _, back = call(base, "POST", "/api/follow/%s" % buyer_id, token=seller_token)
    check("卖家回关买家成功", (back.get("data") or {}).get("followed") is True)
    _, seller_fans2 = call(base, "GET", "/api/follow/followers", token=seller_token)
    fan2 = next((r for r in ((seller_fans2.get("data") or {}).get("records") or [])
                 if r.get("userId") == buyer_id), None)
    check("回关后粉丝列表标记为已关注", bool(fan2) and fan2.get("followed") is True)

    _, unfol = call(base, "POST", "/api/follow/%s" % seller_id, token=buyer_token)
    uf = unfol.get("data") or {}
    check("取消关注成功", unfol.get("code") == 200 and uf.get("followed") is False, unfol.get("msg"))
    check("取消关注后粉丝数归零", uf.get("fansCount") == 0, uf.get("fansCount"))
    _, fol_chk2 = call(base, "GET", "/api/follow/check/%s" % seller_id, token=buyer_token)
    check("取消后关注状态为 false", fol_chk2.get("data") is False)
    _, following2 = call(base, "GET", "/api/follow/following", token=buyer_token)
    check("取消后关注列表不再包含卖家",
          not any(r.get("userId") == seller_id
                  for r in ((following2.get("data") or {}).get("records") or [])))

    # 恢复两条关系，供个人中心统计断言
    call(base, "POST", "/api/follow/%s" % seller_id, token=buyer_token)

    print("\n== 6. 个人中心统计 ==")
    _, me_buyer = call(base, "GET", "/api/auth/me", token=buyer_token)
    m = me_buyer.get("data") or {}
    check("个人中心返回收藏数/关注数/粉丝数",
          all(k in m for k in ("favoriteCount", "followCount", "fansCount")), list(m.keys()))
    check("买家收藏数为 3", m.get("favoriteCount") == 3, m.get("favoriteCount"))
    check("买家关注数为 1", m.get("followCount") == 1, m.get("followCount"))
    check("买家粉丝数为 1（卖家回关）", m.get("fansCount") == 1, m.get("fansCount"))

    _, me_seller = call(base, "GET", "/api/auth/me", token=seller_token)
    s = me_seller.get("data") or {}
    check("卖家粉丝数为 1", s.get("fansCount") == 1, s.get("fansCount"))
    check("卖家关注数为 1", s.get("followCount") == 1, s.get("followCount"))

    print("\n== 7. 清理 ==")
    for pid in product_ids:
        call(base, "POST", "/api/favorite/%s" % pid, token=buyer_token)
    _, me_after = call(base, "GET", "/api/auth/me", token=buyer_token)
    check("清空收藏后计数归零", (me_after.get("data") or {}).get("favoriteCount") == 0,
          (me_after.get("data") or {}).get("favoriteCount"))

    call(base, "POST", "/api/follow/%s" % seller_id, token=buyer_token)
    call(base, "POST", "/api/follow/%s" % buyer_id, token=seller_token)
    _, me_final = call(base, "GET", "/api/auth/me", token=buyer_token)
    mf = me_final.get("data") or {}
    check("清空关注后关注数与粉丝数归零",
          mf.get("followCount") == 0 and mf.get("fansCount") == 0,
          "%s/%s" % (mf.get("followCount"), mf.get("fansCount")))

    db_fav_final = mysql_query("SELECT COUNT(*) FROM favorite WHERE user_id IN (%s,%s);"
                               % (buyer_id, seller_id))
    if db_fav_final is not None:
        check("收藏表已清空", db_fav_final == "0", db_fav_final)
    db_fol_final = mysql_query("SELECT COUNT(*) FROM follow WHERE follower_id IN (%s,%s);"
                               % (buyer_id, seller_id))
    if db_fol_final is not None:
        check("关注表已清空", db_fol_final == "0", db_fol_final)

    cleaned = 0
    for pid in product_ids:
        call(base, "PUT", "/api/product/%s/offline" % pid, token=seller_token)
        _, removed = call(base, "DELETE", "/api/product/%s" % pid, token=seller_token)
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
