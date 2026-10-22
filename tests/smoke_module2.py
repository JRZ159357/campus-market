#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""模块2「商品分类与商品发布」接口冒烟测试。

用法（后端已在 8080 启动，且已执行 sql/V2__category_product.sql）：
    python tests/smoke_module2.py

覆盖流程：
    分类列表(免登录) -> 未认证发布被拒 -> 学生认证 -> 参数校验 ->
    发布商品 -> 我的发布 -> 待审核商品对外不可见 -> 审核驳回/通过 ->
    详情浏览计数 -> 列表筛选排序 -> 卖家下架/编辑重提 -> 强制下架 ->
    分类增删改查与禁用 -> 发布额度上限 -> 删除商品

退出码：0 全部通过，1 存在失败。
"""

import argparse
import json
import sys
import time
import urllib.error
import urllib.request
import uuid
from urllib.parse import quote

RESULTS = []
PASSED = 0


def call(base, method, path, token=None, body=None):
    # 查询串可能含中文关键词，需先做 URL 编码
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
    """走完整学生认证流程，返回 (token, userId, cert)"""
    token, user_id, _ = login_user(base, "m2s" + suffix)
    up = upload(base, token)
    cert = up.get("data") or ""
    apply_body = {"studentNo": "M2" + suffix, "realName": "模块二学生",
                  "school": "北华大学", "certImg": cert}
    call(base, "POST", "/api/student-auth", token=token, body=apply_body)

    _, page = call(base, "GET", "/api/student-auth/page?pageNum=1&pageSize=50&status=0",
                   token=admin_token)
    target = next((r for r in ((page.get("data") or {}).get("records") or [])
                   if r.get("userId") == user_id), None)
    if target:
        call(base, "PUT", "/api/student-auth/%s/audit" % target["id"],
             token=admin_token, body={"status": 1})
    return token, user_id, cert


def product_body(category_id, images, title="九成新自行车", price=260.00, original=899.00,
                 description="骑行一年，车况良好，可小刀。"):
    return {
        "title": title,
        "categoryId": category_id,
        "price": price,
        "originalPrice": original,
        "description": description,
        "images": images,
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="http://localhost:8080")
    args = parser.parse_args()
    base = args.base.rstrip("/")

    print("=" * 70)
    print("模块2 商品分类与商品发布 冒烟测试  |  " + base)
    print("=" * 70)

    suffix = str(int(time.time()))[-6:]

    # ---------- 1. 分类列表免登录 ----------
    print("\n[1] 分类列表（免登录）")
    _, cats = call(base, "GET", "/api/category/list")
    cat_list = cats.get("data") or []
    check("免登录可访问分类列表", cats.get("code") == 200, cats.get("msg", ""))
    check("预置分类数量为 7", len(cat_list) == 7, "count=" + str(len(cat_list)))
    check("仅返回启用分类", all(c.get("status") == 1 for c in cat_list))
    check("按 sort 升序返回", [c["sort"] for c in cat_list] == sorted(c["sort"] for c in cat_list))
    if not cat_list:
        return finish()
    category_id = cat_list[0]["id"]

    # ---------- 2. 商品列表免登录 ----------
    print("\n[2] 商品列表（免登录）")
    _, plist = call(base, "GET", "/api/product/page?pageNum=1&pageSize=10")
    check("免登录可访问商品列表", plist.get("code") == 200, plist.get("msg", ""))
    check("返回分页结构", "records" in (plist.get("data") or {}))

    # ---------- 3. 未认证用户发布被拒 ----------
    print("\n[3] 权限：普通用户不可发布")
    user_token, user_id, _ = login_user(base, "m2u" + suffix)
    _, deny = call(base, "POST", "/api/product", token=user_token,
                   body=product_body(category_id, ["/uploads/a.png"]))
    check("普通用户发布返回 403", deny.get("code") == 403, deny.get("msg", ""))

    _, deny_admin = call(base, "GET", "/api/product/audit/page")
    check("未登录访问审核列表返回 401", deny_admin.get("code") == 401, deny_admin.get("msg", ""))

    # ---------- 4. 管理员登录 + 准备认证学生 ----------
    print("\n[4] 准备认证学生账号")
    _, admin = call(base, "POST", "/api/auth/admin-login",
                    body={"username": "admin", "password": "123456"})
    admin_token = (admin.get("data") or {}).get("token")
    check("管理员登录成功", admin.get("code") == 200 and bool(admin_token), admin.get("msg", ""))
    if not admin_token:
        return finish()

    token, student_id, cert = make_student(base, admin_token, suffix)
    _, me = call(base, "GET", "/api/auth/me", token=token)
    me_data = me.get("data") or {}
    check("学生认证通过并升级角色", me_data.get("role") == "student", "role=" + str(me_data.get("role")))
    check("发布上限为 20", me_data.get("publishLimit") == 20, "limit=" + str(me_data.get("publishLimit")))

    # ---------- 5. 参数校验 ----------
    print("\n[5] 发布参数校验")
    _, no_img = call(base, "POST", "/api/product", token=token,
                     body=product_body(category_id, []))
    check("未上传图片被拒绝", no_img.get("code") == 400, no_img.get("msg", ""))

    _, too_many = call(base, "POST", "/api/product", token=token,
                       body=product_body(category_id, ["/uploads/%d.png" % i for i in range(10)]))
    check("超过9张图片被拒绝", too_many.get("code") == 400, too_many.get("msg", ""))

    _, long_title = call(base, "POST", "/api/product", token=token,
                         body=product_body(category_id, [cert], title="标" * 31))
    check("标题超过30字被拒绝", long_title.get("code") == 400, long_title.get("msg", ""))

    _, bad_price = call(base, "POST", "/api/product", token=token,
                        body=product_body(category_id, [cert], price=0))
    check("价格为0被拒绝", bad_price.get("code") == 400, bad_price.get("msg", ""))

    _, bad_cat = call(base, "POST", "/api/product", token=token,
                      body=product_body(999999, [cert]))
    check("不存在的分类被拒绝", bad_cat.get("code") == 500, bad_cat.get("msg", ""))

    # ---------- 6. 发布商品 ----------
    print("\n[6] 发布商品")
    body = product_body(category_id, [cert, "/uploads/detail1.png", "/uploads/detail2.png"])
    _, pub = call(base, "POST", "/api/product", token=token, body=body)
    product_id = pub.get("data")
    check("认证学生发布成功", pub.get("code") == 200 and product_id is not None, pub.get("msg", ""))

    # ---------- 7. 我的发布 ----------
    print("\n[7] 我的发布")
    _, mine = call(base, "GET", "/api/product/my?pageNum=1&pageSize=10", token=token)
    my_records = ((mine.get("data") or {}).get("records") or [])
    target = next((r for r in my_records if r.get("id") == product_id), None)
    check("我的发布包含刚发布的商品", target is not None)
    if target:
        check("初始状态为待审核(0)", target.get("status") == 0, target.get("statusLabel"))
        check("主图为第一张上传图", target.get("cover") == cert, target.get("cover"))
        check("状态文案正确", target.get("statusLabel") == "待审核", target.get("statusLabel"))

    # ---------- 8. 待审核商品对外不可见 ----------
    print("\n[8] 待审核商品的可见性")
    _, pub_detail = call(base, "GET", "/api/product/%s" % product_id)
    check("未登录查看待审核商品被拒绝", pub_detail.get("code") == 500, pub_detail.get("msg", ""))

    _, owner_detail = call(base, "GET", "/api/product/%s" % product_id, token=token)
    od = owner_detail.get("data") or {}
    check("卖家本人可查看待审核商品", owner_detail.get("code") == 200, owner_detail.get("msg", ""))
    check("返回 isOwner=true", od.get("isOwner") is True)
    check("返回全部3张图片", len(od.get("images") or []) == 3, len(od.get("images") or []))
    check("卖家信息含认证标识", (od.get("seller") or {}).get("studentVerified") is True)

    _, guest_bad_token = call(base, "GET", "/api/product/%s" % product_id, token="bad.token.value")
    check("免登录接口携带无效 token 不报401", guest_bad_token.get("code") == 500,
          guest_bad_token.get("msg", ""))

    _, not_in_page = call(base, "GET", "/api/product/page?pageNum=1&pageSize=100&keyword=九成新自行车")
    not_in_records = ((not_in_page.get("data") or {}).get("records") or [])
    check("待审核商品不出现在公开列表",
          all(r.get("id") != product_id for r in not_in_records))

    # ---------- 9. 审核 ----------
    print("\n[9] 管理端审核")
    _, audit_page = call(base, "GET", "/api/product/audit/page?pageNum=1&pageSize=50&status=0",
                         token=admin_token)
    ap_records = ((audit_page.get("data") or {}).get("records") or [])
    ap_target = next((r for r in ap_records if r.get("id") == product_id), None)
    check("待审核列表命中该商品", ap_target is not None)
    if ap_target:
        check("审核列表含卖家昵称", bool(ap_target.get("sellerNickname")), ap_target.get("sellerNickname"))
        check("审核列表含分类名称", bool(ap_target.get("categoryName")), ap_target.get("categoryName"))

    _, rej_bad = call(base, "PUT", "/api/product/%s/audit" % product_id,
                      token=admin_token, body={"status": 2})
    check("驳回未填原因被拒绝", rej_bad.get("code") == 500, rej_bad.get("msg", ""))

    _, reject = call(base, "PUT", "/api/product/%s/audit" % product_id,
                     token=admin_token, body={"status": 2, "auditReason": "图片不清晰，请重新拍摄"})
    check("驳回成功", reject.get("code") == 200, reject.get("msg", ""))

    _, after_reject = call(base, "GET", "/api/product/%s" % product_id, token=token)
    ard = after_reject.get("data") or {}
    check("驳回后状态为已下架(2)", ard.get("status") == 2, ard.get("statusLabel"))
    check("驳回原因回显给卖家", "图片不清晰" in str(ard.get("auditReason")), ard.get("auditReason"))

    _, audit_again = call(base, "PUT", "/api/product/%s/audit" % product_id,
                          token=admin_token, body={"status": 1})
    check("已审核商品不能重复审核", audit_again.get("code") == 500, audit_again.get("msg", ""))

    # ---------- 10. 编辑后重新提交 ----------
    print("\n[10] 编辑已下架商品并重新提交")
    _, edit = call(base, "PUT", "/api/product/%s" % product_id, token=token,
                   body=product_body(category_id, [cert, "/uploads/detail1.png"],
                                     title="九成新山地自行车", price=240.00, original=None,
                                     description="换过一次刹车片，其余正常。"))
    check("已下架商品可编辑", edit.get("code") == 200, edit.get("msg", ""))

    _, after_edit = call(base, "GET", "/api/product/%s" % product_id, token=token)
    aed = after_edit.get("data") or {}
    check("编辑后重新进入待审核(0)", aed.get("status") == 0, aed.get("statusLabel"))
    check("编辑后驳回原因被清空", aed.get("auditReason") in (None, ""), aed.get("auditReason"))
    check("编辑后图片被替换为2张", len(aed.get("images") or []) == 2, len(aed.get("images") or []))
    check("原价清空生效", aed.get("originalPrice") is None, aed.get("originalPrice"))

    _, pass_audit = call(base, "PUT", "/api/product/%s/audit" % product_id,
                         token=admin_token, body={"status": 1})
    check("审核通过", pass_audit.get("code") == 200, pass_audit.get("msg", ""))

    # ---------- 11. 详情与浏览计数 ----------
    print("\n[11] 商品详情与浏览计数")
    _, detail = call(base, "GET", "/api/product/%s" % product_id)
    dd = detail.get("data") or {}
    check("免登录查看已上架商品", detail.get("code") == 200, detail.get("msg", ""))
    check("状态为已上架(1)", dd.get("status") == 1, dd.get("statusLabel"))
    check("isVerifiedSeller=true", dd.get("isVerifiedSeller") is True)
    check("分类名称已拼装", bool(dd.get("categoryName")), dd.get("categoryName"))
    views_1 = dd.get("viewCount") or 0
    time.sleep(0.2)
    _, detail2 = call(base, "GET", "/api/product/%s" % product_id)
    views_2 = ((detail2.get("data") or {}).get("viewCount") or 0)
    check("浏览数每次访问+1", views_2 == views_1 + 1, "%s -> %s" % (views_1, views_2))

    _, owner_view = call(base, "GET", "/api/product/%s" % product_id, token=token)
    check("卖家自己查看不增加浏览数",
          ((owner_view.get("data") or {}).get("viewCount") or 0) == views_2,
          (owner_view.get("data") or {}).get("viewCount"))

    # ---------- 12. 列表筛选与排序 ----------
    print("\n[12] 列表筛选与排序")
    _, by_cat = call(base, "GET", "/api/product/page?pageNum=1&pageSize=100&categoryId=%s" % category_id)
    check("分类筛选可用", by_cat.get("code") == 200 and
          all(r.get("categoryId") == category_id for r in ((by_cat.get("data") or {}).get("records") or [])))

    _, by_kw = call(base, "GET", "/api/product/page?pageNum=1&pageSize=100&keyword=山地自行车")
    check("关键词命中标题", any(r.get("id") == product_id
                            for r in ((by_kw.get("data") or {}).get("records") or [])))

    _, by_price = call(base, "GET", "/api/product/page?pageNum=1&pageSize=100&minPrice=200&maxPrice=250")
    bp = ((by_price.get("data") or {}).get("records") or [])
    check("价格区间筛选可用", all(200 <= float(r["price"]) <= 250 for r in bp), "count=" + str(len(bp)))

    _, bad_range = call(base, "GET", "/api/product/page?minPrice=500&maxPrice=100")
    check("最低价高于最高价被拒绝", bad_range.get("code") == 400, bad_range.get("msg", ""))

    _, sorted_asc = call(base, "GET", "/api/product/page?pageNum=1&pageSize=20&sort=priceAsc")
    prices = [float(r["price"]) for r in ((sorted_asc.get("data") or {}).get("records") or [])]
    check("价格升序排序生效", prices == sorted(prices), prices[:5])

    _, sorted_new = call(base, "GET", "/api/product/page?pageNum=1&pageSize=5&sort=new")
    times = [r["createTime"] for r in ((sorted_new.get("data") or {}).get("records") or [])]
    check("最新排序生效", times == sorted(times, reverse=True), times[:3])

    # ---------- 13. 越权与下架 ----------
    print("\n[13] 越权操作与卖家下架")
    other_token, _, _ = login_user(base, "m2o" + suffix)
    _, other_edit = call(base, "PUT", "/api/product/%s" % product_id, token=other_token,
                         body=product_body(category_id, [cert]))
    check("非卖家编辑被拒绝", other_edit.get("code") == 403, other_edit.get("msg", ""))

    _, edit_onsale = call(base, "PUT", "/api/product/%s" % product_id, token=token,
                          body=product_body(category_id, [cert]))
    check("已上架商品不可直接编辑", edit_onsale.get("code") == 500, edit_onsale.get("msg", ""))

    _, off = call(base, "PUT", "/api/product/%s/offline" % product_id, token=token)
    check("卖家下架成功", off.get("code") == 200, off.get("msg", ""))

    _, after_off = call(base, "GET", "/api/product/%s" % product_id)
    check("下架后公开详情不可见", after_off.get("code") == 500, after_off.get("msg", ""))

    _, off_again = call(base, "PUT", "/api/product/%s/offline" % product_id, token=token)
    check("重复下架被拒绝", off_again.get("code") == 500, off_again.get("msg", ""))

    # ---------- 14. 强制下架 ----------
    print("\n[14] 管理员强制下架")
    call(base, "PUT", "/api/product/%s" % product_id, token=token,
         body=product_body(category_id, [cert], title="九成新山地自行车"))
    call(base, "PUT", "/api/product/%s/audit" % product_id, token=admin_token, body={"status": 1})

    _, force_bad = call(base, "PUT", "/api/product/%s/force-offline" % product_id,
                        token=admin_token, body={"reason": ""})
    check("强制下架未填原因被拒绝", force_bad.get("code") == 400, force_bad.get("msg", ""))

    _, force = call(base, "PUT", "/api/product/%s/force-offline" % product_id,
                    token=admin_token, body={"reason": "涉嫌违规商品"})
    check("强制下架成功", force.get("code") == 200, force.get("msg", ""))

    _, after_force = call(base, "GET", "/api/product/%s" % product_id, token=token)
    afd = after_force.get("data") or {}
    check("状态变为违规下架(4)", afd.get("status") == 4, afd.get("statusLabel"))
    check("违规原因回显", "涉嫌违规" in str(afd.get("auditReason")), afd.get("auditReason"))

    _, non_admin = call(base, "PUT", "/api/product/%s/force-offline" % product_id,
                        token=token, body={"reason": "test"})
    check("非管理员不能强制下架", non_admin.get("code") == 403, non_admin.get("msg", ""))

    # ---------- 15. 分类管理 ----------
    print("\n[15] 分类管理（管理端）")
    _, cat_create = call(base, "POST", "/api/category", token=admin_token,
                         body={"name": "测试分类" + suffix, "icon": "🧪", "sort": 99})
    new_cat_id = (cat_create.get("data") or {}).get("id")
    check("新增分类成功", cat_create.get("code") == 200 and new_cat_id is not None,
          cat_create.get("msg", ""))

    _, cat_dup = call(base, "POST", "/api/category", token=admin_token,
                      body={"name": "测试分类" + suffix, "icon": "🧪"})
    check("分类重名被拒绝", cat_dup.get("code") == 400, cat_dup.get("msg", ""))

    _, cat_by_user = call(base, "POST", "/api/category", token=token,
                          body={"name": "越权分类" + suffix})
    check("非管理员不能新增分类", cat_by_user.get("code") == 403, cat_by_user.get("msg", ""))

    _, cat_update = call(base, "PUT", "/api/category/%s" % new_cat_id, token=admin_token,
                         body={"name": "测试分类改名" + suffix, "icon": "🧪", "sort": 2})
    check("编辑分类成功", cat_update.get("code") == 200, cat_update.get("msg", ""))

    _, cat_disable = call(base, "PUT", "/api/category/%s/status?status=0" % new_cat_id,
                          token=admin_token)
    check("禁用分类成功", cat_disable.get("code") == 200, cat_disable.get("msg", ""))

    _, public_after_disable = call(base, "GET", "/api/category/list")
    check("禁用分类从小程序列表消失",
          all(c.get("id") != new_cat_id for c in (public_after_disable.get("data") or [])))

    _, admin_all = call(base, "GET", "/api/category/list?all=true", token=admin_token)
    check("管理员可查看含禁用的全部分类",
          any(c.get("id") == new_cat_id for c in (admin_all.get("data") or [])))

    _, guest_all = call(base, "GET", "/api/category/list?all=true")
    check("免登录带 all=true 仍只返回启用分类",
          all(c.get("status") == 1 for c in (guest_all.get("data") or [])))

    _, pub_to_disabled = call(base, "POST", "/api/product", token=token,
                              body=product_body(new_cat_id, [cert], title="停用分类商品"))
    check("不能发布到已停用的分类", pub_to_disabled.get("code") == 500,
          pub_to_disabled.get("msg", ""))

    call(base, "PUT", "/api/category/%s/status?status=1" % new_cat_id, token=admin_token)
    _, cat_delete_empty = call(base, "DELETE", "/api/category/%s" % new_cat_id, token=admin_token)
    check("删除空分类成功", cat_delete_empty.get("code") == 200, cat_delete_empty.get("msg", ""))

    _, cat_delete_used = call(base, "DELETE", "/api/category/%s" % category_id, token=admin_token)
    check("有商品的分类禁止删除", cat_delete_used.get("code") == 500, cat_delete_used.get("msg", ""))

    _, cat_list_count = call(base, "GET", "/api/category/list?all=true", token=admin_token)
    used = next((c for c in (cat_list_count.get("data") or []) if c.get("id") == category_id), None)
    check("分类列表返回商品数量", used is not None and (used.get("productCount") or 0) >= 1,
          used.get("productCount") if used else "missing")

    # ---------- 16. 发布额度 ----------
    print("\n[16] 发布额度上限（20 件，待审核也占额度）")
    ok_count, last_msg = 0, ""
    for i in range(20):
        _, r = call(base, "POST", "/api/product", token=token,
                    body=product_body(category_id, [cert], title="额度测试商品%02d" % i, price=10.00 + i))
        if r.get("code") == 200:
            ok_count += 1
        else:
            last_msg = r.get("msg", "")
    check("可连续发布至20件", ok_count == 20, "成功 %d 件，最后失败原因：%s" % (ok_count, last_msg))

    _, over_limit = call(base, "POST", "/api/product", token=token,
                         body=product_body(category_id, [cert], title="第21件"))
    check("超过20件被拒绝", over_limit.get("code") == 500 and "上限" in str(over_limit.get("msg")),
          over_limit.get("msg", ""))

    # ---------- 17. 删除商品 ----------
    print("\n[17] 删除商品")
    _, my_all = call(base, "GET", "/api/product/my?pageNum=1&pageSize=50&status=0", token=token)
    pending = ((my_all.get("data") or {}).get("records") or [])
    victim = pending[0] if pending else None
    if victim:
        _, del_ok = call(base, "DELETE", "/api/product/%s" % victim["id"], token=token)
        check("删除待审核商品成功", del_ok.get("code") == 200, del_ok.get("msg", ""))
        _, gone = call(base, "GET", "/api/product/%s" % victim["id"], token=token)
        check("删除后详情不可访问", gone.get("code") == 500, gone.get("msg", ""))
    else:
        check("删除待审核商品成功", False, "没有可删除的待审核商品")

    _, on_sale_list = call(base, "GET", "/api/product/my?pageNum=1&pageSize=50&status=1", token=token)
    on_sale = ((on_sale_list.get("data") or {}).get("records") or [])
    if not on_sale:
        # 上一步发布的全是待审核，这里审核通过一件，构造"已上架不可删除"的场景
        _, pending_page = call(base, "GET", "/api/product/my?pageNum=1&pageSize=50&status=0", token=token)
        pending_records = ((pending_page.get("data") or {}).get("records") or [])
        if pending_records:
            call(base, "PUT", "/api/product/%s/audit" % pending_records[0]["id"],
                 token=admin_token, body={"status": 1})
            _, on_sale_list = call(base, "GET", "/api/product/my?pageNum=1&pageSize=50&status=1", token=token)
            on_sale = ((on_sale_list.get("data") or {}).get("records") or [])
    if on_sale:
        _, del_on_sale = call(base, "DELETE", "/api/product/%s" % on_sale[0]["id"], token=token)
        check("已上架商品删除被拒绝", del_on_sale.get("code") == 500, del_on_sale.get("msg", ""))
    else:
        check("已上架商品删除被拒绝", False, "没有已上架商品")

    return finish()


def finish():
    total = len(RESULTS)
    print("\n" + "=" * 70)
    print("用例 %d 个，通过 %d 个，失败 %d 个" % (total, PASSED, total - PASSED))
    if PASSED == total:
        print("结论：全部通过")
    else:
        print("结论：存在失败用例")
        for name, ok, detail in RESULTS:
            if not ok:
                print("  失败: %s  %s" % (name, detail))
    print("=" * 70)
    return 0 if PASSED == total else 1


if __name__ == "__main__":
    sys.exit(main())
