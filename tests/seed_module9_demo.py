#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""模块9 管理端截图用演示数据播种脚本。

用途：举报管理页需要覆盖 5 个状态（待处理/处理中/已处理/已驳回）与 3 类举报
（商品/用户/评价）才能截出有内容的图；客服工作台与操作日志页也需要
若干条真实的处理记录。

本脚本复用 smoke_module9 的建数流程，但**不清理**，并把生成的 ID 写进
tests/.module9_demo.json，供 --clean 清理。

用法：
    python tests/seed_module9_demo.py            # 播种
    python tests/seed_module9_demo.py --clean    # 按上次的 ID 清理
"""
import json
import os
import sys
import uuid

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from smoke_module9 import (call, login_user, make_student, make_service,  # noqa: E402
                           publish_product, mysql_exec, mysql_query, upload)
import demo_image  # noqa: E402

BASE = "http://127.0.0.1:8080"
STATE = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".module9_demo.json")


def load_state():
    if not os.path.exists(STATE):
        return None
    with open(STATE, "r", encoding="utf-8") as f:
        return json.load(f)


def save_state(state):
    with open(STATE, "w", encoding="utf-8") as f:
        json.dump(state, f, ensure_ascii=False, indent=2)


def clean(state):
    user_ids = ",".join(str(i) for i in state.get("userIds", []))
    product_ids = ",".join(str(i) for i in state.get("productIds", []))
    report_ids = ",".join(str(i) for i in state.get("reportIds", []))
    order_ids = ",".join(str(i) for i in state.get("orderIds", []))
    review_ids = ",".join(str(i) for i in state.get("reviewIds", []))
    if report_ids:
        mysql_exec("DELETE FROM credit_log WHERE related_id IN (%s);" % report_ids)
        mysql_exec("DELETE FROM report WHERE id IN (%s);" % report_ids)
    # 演示库的操作日志也一并清掉：不清理的话每播一次就多一批，
    # 截图里会出现上一轮的重复记录，看起来像系统在重复处置
    mysql_exec("DELETE FROM sys_operation_log;")
    if review_ids:
        mysql_exec("DELETE FROM review WHERE id IN (%s);" % review_ids)
    if order_ids:
        mysql_exec("DELETE FROM trade_order WHERE id IN (%s);" % order_ids)
    if product_ids:
        mysql_exec("DELETE FROM product_image WHERE product_id IN (%s);" % product_ids)
        mysql_exec("DELETE FROM product WHERE id IN (%s);" % product_ids)
    if user_ids:
        mysql_exec("DELETE FROM follow WHERE user_id IN (%s) OR followed_id IN (%s);" % (user_ids, user_ids))
        mysql_exec("DELETE FROM favorite WHERE user_id IN (%s);" % user_ids)
        mysql_exec("DELETE FROM student_auth WHERE user_id IN (%s);" % user_ids)
        mysql_exec("DELETE FROM credit_log WHERE user_id IN (%s);" % user_ids)
        mysql_exec("DELETE FROM notice WHERE user_id IN (%s);" % user_ids)
        mysql_exec("DELETE FROM address WHERE user_id IN (%s);" % user_ids)
        mysql_exec("DELETE FROM sys_user WHERE id IN (%s);" % user_ids)
    left = mysql_query("SELECT COUNT(*) FROM report WHERE id IN (%s);" % (report_ids or "0"))
    print("清理完成，剩余举报行数：", left)


def main():
    if "--clean" in sys.argv:
        state = load_state()
        if not state:
            print("没有找到演示数据记录，无需清理")
            return 0
        clean(state)
        os.remove(STATE)
        return 0

    suffix = uuid.uuid4().hex[:6]
    # 用 admin-login 而不是再走 wx-login：单端登录会作废调用方手里的 token
    _, admin_res = call(BASE, "POST", "/api/auth/admin-login",
                        body={"username": "admin", "password": "123456"})
    admin_token = (admin_res.get("data") or {}).get("token")
    if not admin_token:
        print("管理员登录失败")
        return 1

    created_users = []
    created_products = []
    created_reports = []
    created_orders = []
    created_reviews = []

    # 1）违规卖家：卖违禁品，会被举报并最终封禁
    # 认证图会被 publish_product 当作商品首图，所以这里直接传一张肉眼可见的封面。
    # 冒烟测试用 1×1 透明图是为了跑得快，截图却会得到一片空白 —— 两者诉求不同。
    seller_token, seller_id, seller_cert = make_student(
        BASE, admin_token, suffix + "s", "张无良", cert_png=demo_image.make_cover(0))
    created_users.append(seller_id)
    bad_pid = publish_product(BASE, seller_token, admin_token, seller_cert,
                              "自制四氯化碳 500ml 工业溶剂", 25)
    created_products.append(bad_pid)

    # 2）疑似刷好评的卖家：有商品、被举报用户违规
    mid_token, mid_id, mid_cert = make_student(
        BASE, admin_token, suffix + "m", "李刷单", cert_png=demo_image.make_cover(1))
    created_users.append(mid_id)
    mid_pid = publish_product(BASE, mid_token, admin_token, mid_cert,
                              "全新未拆封蓝牙耳机 送小样", 199)
    created_products.append(mid_pid)

    # 3）正常卖家：会有一条被恶意差评举报
    good_token, good_id, good_cert = make_student(
        BASE, admin_token, suffix + "g", "王老实", cert_png=demo_image.make_cover(2))
    created_users.append(good_id)
    good_pid = publish_product(BASE, good_token, admin_token, good_cert,
                                "九成新考研英语全套真题", 45)
    created_products.append(good_pid)

    # 4）三个举报人：普通用户
    reporters = []
    for i, nickname in enumerate(["赵见义勇为", "钱较真", "孙较真"]):
        token, uid, _ = login_user(BASE, "m9r%d%s" % (i, suffix))
        call(BASE, "PUT", "/api/user/profile", token=token, body={"nickname": nickname})
        created_users.append(uid)
        reporters.append((token, uid, nickname))

    # 5）客服账号
    cs_token, cs_id = make_service(BASE, admin_token, suffix + "c", "客服小周")
    created_users.append(cs_id)

    # ---- 举报 1：商品举报，待处理 ----
    _, res = call(BASE, "POST", "/api/report", token=reporters[0][0],
                  body={"reportType": 1, "targetId": bad_pid, "reason": 2,
                        "content": "这是化工溶剂，学校明令禁止带进宿舍，卖家还包邮"})
    r1 = res.get("data")
    if r1:
        created_reports.append(r1)

    # ---- 举报 2：商品举报，处理中（已接单） ----
    _, res = call(BASE, "POST", "/api/report", token=reporters[1][0],
                  body={"reportType": 1, "targetId": mid_pid, "reason": 1,
                        "content": "商品描述写着全新，实际拆封过还缺充电线"})
    r2 = res.get("data")
    if r2:
        created_reports.append(r2)
        call(BASE, "PUT", "/api/report/admin/%s/accept" % r2, token=cs_token)

    # ---- 举报 3：用户举报，已处理（封禁 + 扣分） ----
    _, res = call(BASE, "POST", "/api/report", token=reporters[0][0],
                  body={"reportType": 2, "targetId": mid_id, "reason": 3,
                        "content": "卖家收款后直接拉黑我，订单一直不发货，要求退款无果"})
    r3 = res.get("data")
    if r3:
        created_reports.append(r3)
        call(BASE, "PUT", "/api/report/admin/%s/accept" % r3, token=cs_token)
        call(BASE, "PUT", "/api/report/admin/%s/handle" % r3, token=cs_token,
             body={"status": 2, "handleAction": 2,
                   "handleResult": "核实存在收款不发货且拉黑消费者的行为，已封禁账号并扣除信誉分"})

    # ---- 举报 4：用户举报，已驳回 ----
    _, res = call(BASE, "POST", "/api/report", token=reporters[1][0],
                  body={"reportType": 2, "targetId": good_id, "reason": 4,
                        "content": "卖家回复消息语气很冲，感觉在骂人"})
    r4 = res.get("data")
    if r4:
        created_reports.append(r4)
        call(BASE, "PUT", "/api/report/admin/%s/accept" % r4, token=cs_token)
        call(BASE, "PUT", "/api/report/admin/%s/handle" % r4, token=cs_token,
             body={"status": 3, "handleAction": 0,
                   "handleResult": "调取聊天记录核实，卖家语气略急躁但无辱骂内容，不构成违规，驳回"})

    # ---- 举报 5：评价举报，已处理（删评价 + 扣评价人 5 分） ----
    # 需要一笔完成的订单才能评价
    _, res = call(BASE, "POST", "/api/address", token=reporters[0][0],
                  body={"receiverName": "赵同学", "phone": "13800000001",
                        "address": "东区 3 号楼 402"})
    address_id = res.get("data")
    _, res = call(BASE, "POST", "/api/order/create", token=reporters[0][0],
                  body={"productId": good_pid, "addressId": address_id})
    order_no = (res.get("data") or {}).get("orderNo")
    if order_no:
        created_orders.append(order_no)
        call(BASE, "POST", "/api/order/%s/pay" % order_no, token=reporters[0][0])
        call(BASE, "POST", "/api/order/%s/ship" % order_no, token=good_token)
        call(BASE, "POST", "/api/order/%s/receive" % order_no, token=reporters[0][0])
        _, res = call(BASE, "GET", "/api/order/%s" % order_no, token=reporters[0][0])
        order_id = (res.get("data") or {}).get("id")
        if order_id:
            created_orders.append(order_id)
        # 给这条广告评价配一张图：举报详情的「评价配图」区才有东西可看
        review_img = (upload(BASE, reporters[0][0], demo_image.make_cover(3)).get("data") or "")
        review_body = {"orderId": order_id, "reviewType": 1,
                       "descStars": 1, "shipStars": 1, "attitudeStars": 1,
                       "content": "加微信 abc123456 领内部优惠券，全网最低"}
        if review_img:
            review_body["images"] = [review_img]
        _, res = call(BASE, "POST", "/api/review", token=reporters[0][0], body=review_body)
        review_id = (res.get("data") or {}).get("id")
        if review_id:
            created_reviews.append(review_id)
            _, res = call(BASE, "POST", "/api/report", token=reporters[1][0],
                          body={"reportType": 3, "targetId": review_id, "reason": 5,
                                "content": "这条评价是导流广告，留了微信号，不是真实购买体验"})
            r5 = res.get("data")
            if r5:
                created_reports.append(r5)
                call(BASE, "PUT", "/api/report/admin/%s/accept" % r5, token=cs_token)
                call(BASE, "PUT", "/api/report/admin/%s/handle" % r5, token=cs_token,
                     body={"status": 2, "handleAction": 3,
                           "handleResult": "核实为广告导流评价，已删除该评价并扣除评价人信誉分"})

    # ---- 举报 6：商品举报，待处理（用于多页列表分页） ----
    for i in range(3):
        _, res = call(BASE, "POST", "/api/report", token=reporters[2][0],
                      body={"reportType": 1, "targetId": good_pid, "reason": 6,
                            "content": "第%d条：怀疑卖家用重复图片盗用别人的实拍图，麻烦核实一下" % (i + 1)})
        rid = res.get("data")
        if rid:
            created_reports.append(rid)

    state = {
        "suffix": suffix,
        "userIds": created_users,
        "productIds": created_products,
        "reportIds": created_reports,
        "orderIds": created_orders,
        "reviewIds": created_reviews,
    }
    save_state(state)
    print("演示数据已播种：", json.dumps(state, ensure_ascii=False))
    print("演示数据记录文件：", STATE)
    return 0


if __name__ == "__main__":
    sys.exit(main())
