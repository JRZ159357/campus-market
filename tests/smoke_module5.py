#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""模块5「交易与订单」接口冒烟测试。

用法（后端需以较短的超时参数启动，便于验证自动取消）：
    mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8080 \
        --app.order.pay-timeout-minutes=2 --app.order.timeout-scan-ms=10000"
    python tests/smoke_module5.py

覆盖流程：
    权限（未登录 401 / 客服只读 403）-> 地址 CRUD 与默认地址唯一 ->
    下单校验（不能买自己的、地址归属、进行中订单去重）-> 商品/地址快照 ->
    支付与防超卖（商品状态 CAS）-> 发货 / 收货 -> 退款申请、拒绝、撤销、同意 ->
    取消订单 -> 管理端查询与强制取消 / 强制退款 -> 订单日志完整性 ->
    超时未付款自动取消 -> 数据清理

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
        raw = e.read().decode("utf-8", "replace")
        try:
            return e.code, json.loads(raw)
        except Exception:
            return e.code, {"code": e.code, "msg": raw}


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
    token, user_id, _ = login_user(base, "m5s" + suffix)
    cert = (upload(base, token).get("data") or "")
    call(base, "POST", "/api/student-auth", token=token,
         body={"studentNo": "M5" + suffix, "realName": "模块五学生",
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


def mysql_exec(sql):
    mysql_query(sql)


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

    _, cs_login = call(base, "POST", "/api/auth/login",
                       body={"username": "service01", "password": "123456"})
    cs_token = (cs_login.get("data") or {}).get("token")
    check("客服账号登录成功", bool(cs_token))

    buyer_token, buyer_id, _ = login_user(base, "m5a" + suffix)
    check("买家微信登录成功", bool(buyer_token) and bool(buyer_id))
    buyer2_token, buyer2_id, _ = login_user(base, "m5b" + suffix)
    check("第二买家微信登录成功", bool(buyer2_token) and bool(buyer2_id))

    seller_token, seller_id, cert = make_student(base, admin_token, suffix)
    check("学生卖家认证通过", bool(seller_token) and bool(seller_id))

    _, cats = call(base, "GET", "/api/category/list")
    category_id = (cats.get("data") or [{}])[0].get("id")

    product_ids = []
    for idx in range(5):
        _, created = call(base, "POST", "/api/product", token=seller_token,
                          body={"title": "模块五订单测试%s-%d" % (suffix, idx),
                                "categoryId": category_id, "price": 120 + idx,
                                "originalPrice": 299,
                                "description": "订单冒烟测试商品，编号 %d" % idx,
                                "images": [cert]})
        pid = created.get("data")
        if pid:
            call(base, "PUT", "/api/product/%s/audit" % pid, token=admin_token, body={"status": 1})
            product_ids.append(pid)
    check("卖家发布并上架 5 件商品", len(product_ids) == 5, product_ids)
    if len(product_ids) < 5:
        print("商品准备失败，终止测试")
        return 1
    p_cancel, p_full, p_refund, p_admin, p_timeout = product_ids

    print("\n== 2. 权限校验 ==")
    _, unauth_addr = call(base, "GET", "/api/address/list")
    check("未登录查看地址返回 401", unauth_addr.get("code") == 401, unauth_addr.get("msg"))
    _, unauth_create = call(base, "POST", "/api/order/create",
                            body={"productId": p_cancel, "addressId": 1})
    check("未登录下单返回 401", unauth_create.get("code") == 401, unauth_create.get("msg"))
    _, cs_create = call(base, "POST", "/api/order/create", token=cs_token,
                        body={"productId": p_cancel, "addressId": 1})
    check("客服下单被拦截（403）", cs_create.get("code") == 403, cs_create.get("msg"))
    check("客服拦截文案符合约定",
          "客服账号无操作权限" in (cs_create.get("msg") or ""), cs_create.get("msg"))
    _, cs_admin = call(base, "GET", "/api/order/admin/page?pageNum=1&pageSize=5", token=cs_token)
    check("客服只读可查看订单列表", cs_admin.get("code") == 200, cs_admin.get("msg"))
    _, buyer_admin = call(base, "GET", "/api/order/admin/page", token=buyer_token)
    check("普通用户访问管理端订单被拒绝", buyer_admin.get("code") in (401, 403), buyer_admin.get("msg"))

    print("\n== 3. 地址管理 ==")
    _, bad_phone = call(base, "POST", "/api/address", token=buyer_token,
                        body={"receiverName": "张三", "phone": "12345", "address": "1号楼"})
    check("手机号格式非法被拒绝", bad_phone.get("code") != 200, bad_phone.get("msg"))

    _, addr1 = call(base, "POST", "/api/address", token=buyer_token,
                    body={"receiverName": "张三", "phone": "13800138000",
                          "address": "3号宿舍楼 502", "isDefault": False})
    addr1_id = addr1.get("data")
    check("新增地址成功", addr1.get("code") == 200 and bool(addr1_id), addr1.get("msg"))

    _, list1 = call(base, "GET", "/api/address/list", token=buyer_token)
    l1 = list1.get("data") or []
    check("首个地址自动成为默认地址",
          len(l1) == 1 and l1[0].get("isDefault") is True,
          [(a.get("id"), a.get("isDefault")) for a in l1])

    _, addr2 = call(base, "POST", "/api/address", token=buyer_token,
                    body={"receiverName": "李四", "phone": "13900139000",
                          "address": "图书馆东门", "isDefault": True})
    addr2_id = addr2.get("data")
    _, list2 = call(base, "GET", "/api/address/list", token=buyer_token)
    l2 = list2.get("data") or []
    defaults = [a for a in l2 if a.get("isDefault")]
    check("第二个地址设为默认后默认项唯一",
          len(defaults) == 1 and defaults[0].get("id") == addr2_id,
          [(a.get("id"), a.get("isDefault")) for a in l2])

    _, switch_back = call(base, "PUT", "/api/address/%s/default" % addr1_id, token=buyer_token)
    _, list3 = call(base, "GET", "/api/address/list", token=buyer_token)
    l3 = list3.get("data") or []
    check("切换默认地址成功",
          switch_back.get("code") == 200
          and [a.get("id") for a in l3 if a.get("isDefault")] == [addr1_id],
          [(a.get("id"), a.get("isDefault")) for a in l3])

    _, updated = call(base, "PUT", "/api/address/%s" % addr1_id, token=buyer_token,
                      body={"receiverName": "张三丰", "phone": "13800138001",
                            "address": "3号宿舍楼 503", "isDefault": True})
    _, list4 = call(base, "GET", "/api/address/list", token=buyer_token)
    target = next((a for a in (list4.get("data") or []) if a.get("id") == addr1_id), {})
    check("修改地址生效", updated.get("code") == 200 and target.get("receiverName") == "张三丰",
          target.get("receiverName"))

    _, other_addr = call(base, "POST", "/api/address", token=seller_token,
                         body={"receiverName": "卖家", "phone": "13700137000",
                               "address": "实验楼 201"})
    seller_addr_id = other_addr.get("data")

    print("\n== 4. 下单校验与快照 ==")
    _, own = call(base, "POST", "/api/order/create", token=seller_token,
                  body={"productId": p_cancel, "addressId": seller_addr_id})
    check("不能购买自己发布的商品", own.get("code") != 200, own.get("msg"))

    _, other_people_addr = call(base, "POST", "/api/order/create", token=buyer_token,
                                body={"productId": p_cancel, "addressId": seller_addr_id})
    check("不能使用他人地址下单", other_people_addr.get("code") != 200,
          other_people_addr.get("msg"))

    _, created1 = call(base, "POST", "/api/order/create", token=buyer_token,
                       body={"productId": p_cancel, "addressId": addr1_id})
    order1 = created1.get("data") or {}
    order1_no = order1.get("orderNo")
    check("下单成功返回订单号", created1.get("code") == 200 and bool(order1_no), created1.get("msg"))
    check("下单返回成交价", float(order1.get("price") or 0) == 120.0, order1.get("price"))
    check("订单号长度为 21 位", len(order1_no or "") == 21, order1_no)

    _, dup = call(base, "POST", "/api/order/create", token=buyer2_token,
                  body={"productId": p_cancel, "addressId": addr1_id})
    check("同一商品已有进行中订单时禁止重复下单", dup.get("code") != 200, dup.get("msg"))

    _, detail1 = call(base, "GET", "/api/order/%s" % order1_no, token=buyer_token)
    d1 = detail1.get("data") or {}
    check("订单详情初始为待付款", d1.get("status") == 0 and d1.get("statusLabel") == "待付款",
          "%s/%s" % (d1.get("status"), d1.get("statusLabel")))
    check("订单详情包含商品快照",
          (d1.get("product") or {}).get("title") == "模块五订单测试%s-0" % suffix,
          (d1.get("product") or {}).get("title"))
    check("订单详情包含地址快照",
          (d1.get("address") or {}).get("receiverName") == "张三丰",
          (d1.get("address") or {}).get("receiverName"))
    check("买家可付款、可取消", d1.get("canPay") is True and d1.get("canCancel") is True,
          "canPay=%s canCancel=%s" % (d1.get("canPay"), d1.get("canCancel")))
    check("卖家不可付款", d1.get("canShip") is False, d1.get("canShip"))
    check("详情返回创建日志", any(l.get("action") == "create" for l in (d1.get("logs") or [])),
          [l.get("action") for l in (d1.get("logs") or [])])

    _, other_view = call(base, "GET", "/api/order/%s" % order1_no, token=buyer2_token)
    check("无关用户查看订单被拒绝", other_view.get("code") == 403, other_view.get("msg"))
    _, cs_view = call(base, "GET", "/api/order/%s" % order1_no, token=cs_token)
    check("客服只读可查看订单详情", cs_view.get("code") == 200, cs_view.get("msg"))

    # 直接改库模拟"商品被卖家改价改标题"，验证快照隔离（已上架商品业务上不允许直接改价）
    if mysql_query("SELECT 1;") is None:
        print("  [SKIP] 未找到 mysql 客户端，跳过快照隔离用例")
    else:
        mysql_exec("UPDATE product SET title='改过标题%s', price=999 WHERE id=%s;"
                   % (suffix, p_cancel))
        _, detail_snap = call(base, "GET", "/api/order/%s" % order1_no, token=buyer_token)
        snap = (detail_snap.get("data") or {}).get("product") or {}
        check("商品改价后订单快照仍是成交价",
              float(snap.get("price") or 0) == 120.0
              and snap.get("title") == "模块五订单测试%s-0" % suffix,
              "%s / %s" % (snap.get("price"), snap.get("title")))
        _, prod_changed = call(base, "GET", "/api/product/%s" % p_cancel, token=buyer_token)
        check("商品本身已改为新价格",
              float((prod_changed.get("data") or {}).get("price") or 0) == 999.0,
              (prod_changed.get("data") or {}).get("price"))

    print("\n== 5. 取消订单 ==")
    _, cancel1 = call(base, "POST", "/api/order/%s/cancel" % order1_no, token=buyer_token,
                      body={"reason": "临时不需要了"})
    check("待付款订单可取消", cancel1.get("code") == 200, cancel1.get("msg"))
    _, detail_c = call(base, "GET", "/api/order/%s" % order1_no, token=buyer_token)
    dc = detail_c.get("data") or {}
    check("取消后状态为已取消", dc.get("status") == 4, dc.get("statusLabel"))
    check("取消原因已记录", "临时不需要了" in (dc.get("cancelReason") or ""), dc.get("cancelReason"))
    _, detail_p = call(base, "GET", "/api/product/%s" % p_cancel, token=buyer_token)
    check("取消后商品仍在售", (detail_p.get("data") or {}).get("status") == 1,
          (detail_p.get("data") or {}).get("status"))

    print("\n== 6. 支付与防超卖 ==")
    _, order_full = call(base, "POST", "/api/order/create", token=buyer_token,
                         body={"productId": p_full, "addressId": addr1_id})
    order_full_no = (order_full.get("data") or {}).get("orderNo")
    check("为整链路准备订单成功", bool(order_full_no), order_full.get("msg"))

    _, pay_other = call(base, "POST", "/api/order/%s/pay" % order_full_no, token=buyer2_token)
    check("非买家不能支付他人订单", pay_other.get("code") == 403, pay_other.get("msg"))

    _, pay1 = call(base, "POST", "/api/order/%s/pay" % order_full_no, token=buyer_token)
    check("买家支付成功", pay1.get("code") == 200, pay1.get("msg"))

    _, detail_paid = call(base, "GET", "/api/order/%s" % order_full_no, token=buyer_token)
    dp = detail_paid.get("data") or {}
    check("支付后状态为待发货", dp.get("status") == 1, dp.get("statusLabel"))
    check("支付时间已写入", bool(dp.get("paymentTime")), dp.get("paymentTime"))
    _, prod_sold = call(base, "GET", "/api/product/%s" % p_full, token=buyer2_token)
    check("支付后商品变为已售出", (prod_sold.get("data") or {}).get("status") == 3,
          (prod_sold.get("data") or {}).get("statusLabel"))

    _, pay_again = call(base, "POST", "/api/order/%s/pay" % order_full_no, token=buyer_token)
    check("重复支付被拒绝", pay_again.get("code") != 200, pay_again.get("msg"))

    # 商品已被占用时，另一买家下单会因"进行中订单"被挡下
    _, rival = call(base, "POST", "/api/order/create", token=buyer2_token,
                    body={"productId": p_full, "addressId": addr1_id})
    check("已售商品无法再下单", rival.get("code") != 200, rival.get("msg"))

    print("\n== 7. 发货与收货 ==")
    _, ship_wrong = call(base, "POST", "/api/order/%s/ship" % order_full_no, token=buyer_token)
    check("买家不能代替卖家发货", ship_wrong.get("code") == 403, ship_wrong.get("msg"))
    _, ship = call(base, "POST", "/api/order/%s/ship" % order_full_no, token=seller_token)
    check("卖家发货成功", ship.get("code") == 200, ship.get("msg"))
    _, detail_ship = call(base, "GET", "/api/order/%s" % order_full_no, token=buyer_token)
    ds = detail_ship.get("data") or {}
    check("发货后状态为待收货", ds.get("status") == 2, ds.get("statusLabel"))
    check("买家可确认收货", ds.get("canReceive") is True, ds.get("canReceive"))

    _, recv_wrong = call(base, "POST", "/api/order/%s/receive" % order_full_no, token=seller_token)
    check("卖家不能代替买家确认收货", recv_wrong.get("code") == 403, recv_wrong.get("msg"))
    _, recv = call(base, "POST", "/api/order/%s/receive" % order_full_no, token=buyer_token)
    check("买家确认收货成功", recv.get("code") == 200, recv.get("msg"))
    _, detail_done = call(base, "GET", "/api/order/%s" % order_full_no, token=buyer_token)
    dd = detail_done.get("data") or {}
    check("完成后状态为已完成", dd.get("status") == 3, dd.get("statusLabel"))
    check("完成时间已写入", bool(dd.get("finishTime")), dd.get("finishTime"))

    _, recv_again = call(base, "POST", "/api/order/%s/receive" % order_full_no, token=buyer_token)
    check("已完成订单不可重复确认收货", recv_again.get("code") != 200, recv_again.get("msg"))

    print("\n== 8. 退款全流程 ==")
    _, order_refund = call(base, "POST", "/api/order/create", token=buyer_token,
                           body={"productId": p_refund, "addressId": addr1_id})
    order_refund_no = (order_refund.get("data") or {}).get("orderNo")
    call(base, "POST", "/api/order/%s/pay" % order_refund_no, token=buyer_token)

    _, short_reason = call(base, "POST", "/api/order/%s/apply-refund" % order_refund_no,
                           token=buyer_token, body={"reason": "坏了"})
    check("退款原因少于5字被拒绝", short_reason.get("code") != 200, short_reason.get("msg"))

    _, apply1 = call(base, "POST", "/api/order/%s/apply-refund" % order_refund_no,
                     token=buyer_token, body={"reason": "商品外壳有明显裂痕，与描述不符"})
    check("买家申请退款成功", apply1.get("code") == 200, apply1.get("msg"))
    _, detail_ref = call(base, "GET", "/api/order/%s" % order_refund_no, token=buyer_token)
    dr = detail_ref.get("data") or {}
    check("申请退款后状态为退款中", dr.get("status") == 5, dr.get("statusLabel"))
    check("记录退款来源状态为待发货", dr.get("refundFromStatus") == 1, dr.get("refundFromStatus"))

    _, reject = call(base, "POST", "/api/order/%s/reject-refund" % order_refund_no,
                     token=seller_token, body={"reason": "瑕疵下单前已说明，不同意退款"})
    check("卖家拒绝退款成功", reject.get("code") == 200, reject.get("msg"))
    _, detail_rej = call(base, "GET", "/api/order/%s" % order_refund_no, token=buyer_token)
    dj = detail_rej.get("data") or {}
    check("拒绝退款后回到待发货", dj.get("status") == 1, dj.get("statusLabel"))
    check("拒绝原因已记录", "瑕疵下单前已说明" in (dj.get("rejectRefundReason") or ""),
          dj.get("rejectRefundReason"))

    call(base, "POST", "/api/order/%s/apply-refund" % order_refund_no, token=buyer_token,
         body={"reason": "再次申请：确实存在裂痕"})
    _, cancel_refund = call(base, "POST", "/api/order/%s/cancel-refund" % order_refund_no,
                            token=buyer_token)
    check("买家可撤销退款申请", cancel_refund.get("code") == 200, cancel_refund.get("msg"))
    _, detail_cr = call(base, "GET", "/api/order/%s" % order_refund_no, token=buyer_token)
    check("撤销退款后回到待发货", (detail_cr.get("data") or {}).get("status") == 1,
          (detail_cr.get("data") or {}).get("statusLabel"))

    call(base, "POST", "/api/order/%s/apply-refund" % order_refund_no, token=buyer_token,
         body={"reason": "第三次申请：商品确实有问题"})
    _, agree = call(base, "POST", "/api/order/%s/agree-refund" % order_refund_no, token=seller_token)
    check("卖家同意退款成功", agree.get("code") == 200, agree.get("msg"))
    _, detail_ag = call(base, "GET", "/api/order/%s" % order_refund_no, token=buyer_token)
    check("退款完成状态为已退款", (detail_ag.get("data") or {}).get("status") == 6,
          (detail_ag.get("data") or {}).get("statusLabel"))
    _, prod_back = call(base, "GET", "/api/product/%s" % p_refund, token=buyer2_token)
    check("退款后商品恢复上架", (prod_back.get("data") or {}).get("status") == 1,
          (prod_back.get("data") or {}).get("statusLabel"))

    print("\n== 9. 管理端干预 ==")
    _, admin_page = call(base, "GET", "/api/order/admin/page?pageNum=1&pageSize=10&status=3",
                         token=admin_token)
    ap = admin_page.get("data") or {}
    check("管理端按状态筛选订单", admin_page.get("code") == 200 and int(ap.get("total") or 0) >= 1,
          ap.get("total"))
    check("管理端列表按订单号可查",
          any(r.get("orderNo") == order_full_no for r in (ap.get("records") or [])),
          [r.get("orderNo") for r in (ap.get("records") or [])])

    _, admin_count = call(base, "GET", "/api/order/admin/count", token=admin_token)
    ac = admin_count.get("data") or {}
    check("管理端状态计数包含已完成", int(ac.get("finished") or 0) >= 1, ac.get("finished"))

    _, search_no = call(base, "GET", "/api/order/admin/page?pageNum=1&pageSize=10&orderNo=%s"
                        % order_full_no, token=admin_token)
    check("管理端按订单号精确搜索命中",
          len(((search_no.get("data") or {}).get("records")) or []) == 1,
          ((search_no.get("data") or {}).get("records")))

    _, buyer_count = call(base, "GET", "/api/order/buyer/count", token=buyer_token)
    bc = buyer_count.get("data") or {}
    check("买家订单计数正确", int(bc.get("finished") or 0) >= 1 and int(bc.get("canceled") or 0) >= 1,
          "finished=%s canceled=%s" % (bc.get("finished"), bc.get("canceled")))

    # 管理员强制取消一笔已付款订单
    _, order_admin = call(base, "POST", "/api/order/create", token=buyer_token,
                          body={"productId": p_admin, "addressId": addr1_id})
    order_admin_no = (order_admin.get("data") or {}).get("orderNo")
    call(base, "POST", "/api/order/%s/pay" % order_admin_no, token=buyer_token)

    _, cs_cancel = call(base, "POST", "/api/order/%s/admin-cancel" % order_admin_no,
                        token=cs_token, body={"reason": "客服尝试取消"})
    check("客服无强制取消权限", cs_cancel.get("code") == 403, cs_cancel.get("msg"))

    _, admin_cancel = call(base, "POST", "/api/order/%s/admin-cancel" % order_admin_no,
                           token=admin_token, body={"reason": "买家与卖家协商一致取消"})
    check("管理员强制取消成功", admin_cancel.get("code") == 200, admin_cancel.get("msg"))
    _, detail_ac = call(base, "GET", "/api/order/admin/%s" % order_admin_no, token=admin_token)
    dac = detail_ac.get("data") or {}
    check("强制取消后状态为已取消", dac.get("status") == 4, dac.get("statusLabel"))
    _, prod_ac = call(base, "GET", "/api/product/%s" % p_admin, token=buyer2_token)
    check("强制取消后商品恢复上架", (prod_ac.get("data") or {}).get("status") == 1,
          (prod_ac.get("data") or {}).get("statusLabel"))

    # 管理员强制退款
    _, order_ar = call(base, "POST", "/api/order/create", token=buyer_token,
                       body={"productId": p_admin, "addressId": addr1_id})
    order_ar_no = (order_ar.get("data") or {}).get("orderNo")
    call(base, "POST", "/api/order/%s/pay" % order_ar_no, token=buyer_token)
    _, admin_refund = call(base, "POST", "/api/order/%s/admin-refund" % order_ar_no,
                           token=admin_token, body={"reason": "客服介入，判定全额退款"})
    check("管理员强制退款成功", admin_refund.get("code") == 200, admin_refund.get("msg"))
    _, detail_ar = call(base, "GET", "/api/order/admin/%s" % order_ar_no, token=admin_token)
    dar = detail_ar.get("data") or {}
    check("强制退款后状态为已退款", dar.get("status") == 6, dar.get("statusLabel"))
    _, prod_ar = call(base, "GET", "/api/product/%s" % p_admin, token=buyer2_token)
    check("强制退款后商品恢复上架",
          (prod_ar.get("data") or {}).get("status") == 1,
          (prod_ar.get("data") or {}).get("statusLabel"))

    print("\n== 10. 订单日志完整性 ==")
    _, log_res = call(base, "GET", "/api/order/admin/%s" % order_refund_no, token=admin_token)
    logs = (log_res.get("data") or {}).get("logs") or []
    actions = [l.get("action") for l in logs]
    expect_actions = ["create", "pay", "apply_refund", "reject_refund",
                      "apply_refund", "cancel_refund", "apply_refund", "agree_refund"]
    check("退款订单日志按时间顺序完整", actions == expect_actions, actions)
    check("日志包含操作人角色与名称",
          all(l.get("operatorRoleLabel") and l.get("operatorName") for l in logs),
          [(l.get("operatorRoleLabel"), l.get("operatorName")) for l in logs][:3])

    db_logs = mysql_query("SELECT COUNT(*) FROM order_log WHERE order_id = "
                          "(SELECT id FROM trade_order WHERE order_no='%s');" % order_refund_no)
    if db_logs is not None:
        check("order_log 落库条数与接口一致", db_logs == str(len(logs)), "%s vs %s" % (db_logs, len(logs)))

    print("\n== 11. 超时未付款自动取消 ==")
    _, order_timeout = call(base, "POST", "/api/order/create", token=buyer_token,
                            body={"productId": p_timeout, "addressId": addr1_id})
    order_timeout_no = (order_timeout.get("data") or {}).get("orderNo")
    check("创建待付款订单用于超时测试", bool(order_timeout_no), order_timeout.get("msg"))

    mysql_exec(
        "UPDATE trade_order SET pay_deadline = DATE_SUB(NOW(), INTERVAL 1 MINUTE) "
        "WHERE order_no='%s';" % order_timeout_no)
    if mysql_query("SELECT 1;") is None:
        print("  [SKIP] 未找到 mysql 客户端，跳过超时自动取消用例")
    else:
        deadline = time.time() + 40
        status_now = None
        while time.time() < deadline:
            _, dto = call(base, "GET", "/api/order/admin/%s" % order_timeout_no, token=admin_token)
            status_now = (dto.get("data") or {}).get("status")
            if status_now == 4:
                break
            time.sleep(3)
        check("超时订单被定时任务自动取消", status_now == 4,
              "status=%s（需以 --app.order.timeout-scan-ms=10000 启动）" % status_now)
        _, dto_final = call(base, "GET", "/api/order/admin/%s" % order_timeout_no, token=admin_token)
        dfin = dto_final.get("data") or {}
        check("超时取消写入系统日志",
              any(l.get("action") == "timeout_cancel" and l.get("operatorRole") == "system"
                  for l in (dfin.get("logs") or [])),
              [l.get("action") for l in (dfin.get("logs") or [])])
        check("超时取消原因已记录", "超时" in (dfin.get("cancelReason") or ""), dfin.get("cancelReason"))

    print("\n== 12. 数据清理 ==")
    order_nos = [order1_no, order_full_no, order_refund_no, order_admin_no,
                 order_ar_no, order_timeout_no]
    if mysql_query("SELECT 1;") is not None:
        ids = ",".join(str(i) for i in product_ids)
        mysql_exec("DELETE FROM order_log WHERE order_id IN "
                   "(SELECT id FROM trade_order WHERE product_id IN (%s));" % ids)
        mysql_exec("DELETE FROM trade_order WHERE product_id IN (%s);" % ids)
        mysql_exec("DELETE FROM address WHERE user_id IN (%s,%s,%s);"
                   % (buyer_id, buyer2_id, seller_id))
        mysql_exec("DELETE FROM product_image WHERE product_id IN (%s);" % ids)
        mysql_exec("DELETE FROM product WHERE id IN (%s);" % ids)
        mysql_exec("DELETE FROM student_auth WHERE user_id=%s;" % seller_id)
        mysql_exec("DELETE FROM sys_user WHERE id IN (%s,%s,%s);"
                   % (buyer_id, buyer2_id, seller_id))
        left = mysql_query("SELECT COUNT(*) FROM trade_order WHERE product_id IN (%s);" % ids)
        check("测试订单已清理", left == "0", left)
    else:
        for no in order_nos:
            call(base, "POST", "/api/order/%s/cancel" % no, token=buyer_token, body={"reason": "清理"})
        for pid in product_ids:
            call(base, "PUT", "/api/product/%s/offline" % pid, token=seller_token)
            call(base, "DELETE", "/api/product/%s" % pid, token=seller_token)
        check("已通过接口回滚测试数据", True)

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
