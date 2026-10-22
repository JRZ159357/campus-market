#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""模块7 管理端截图用演示数据播种脚本。

用途：管理端「评价管理」「信誉分管理」需要真实数据才能截出有内容的图。
本脚本复用 smoke_module7 的建数流程（注册学生 → 发商品 → 下单支付收货 → 评价 → 追评 → 回复 → 调分），
但**不清理**，并把生成的 ID 写进 tests/.module7_demo.json，供清理脚本使用。

用法：
    python tests/seed_module7_demo.py            # 播种
    python tests/seed_module7_demo.py --clean    # 按上次的 ID 清理
"""
import json
import os
import sys
import uuid

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from smoke_module7 import (call, login_user, make_student, publish_product,  # noqa: E402
                           finish_order, mysql_exec, mysql_query)

BASE = "http://127.0.0.1:8080"
STATE = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".module7_demo.json")


def load_state():
    if not os.path.exists(STATE):
        return None
    with open(STATE, "r", encoding="utf-8") as f:
        return json.load(f)


def save_state(state):
    with open(STATE, "w", encoding="utf-8") as f:
        json.dump(state, f, ensure_ascii=False, indent=2)


def clean(state):
    ids = state
    order_ids = ",".join(str(i) for i in ids.get("orderIds", []))
    user_ids = ",".join(str(i) for i in ids.get("userIds", []))
    if order_ids:
        mysql_exec("DELETE FROM review WHERE order_id IN (%s);" % order_ids)
    if user_ids:
        mysql_exec("DELETE FROM credit_log WHERE user_id IN (%s);" % user_ids)
    if order_ids:
        mysql_exec("DELETE FROM order_log WHERE order_id IN (%s);" % order_ids)
        mysql_exec("DELETE FROM trade_order WHERE id IN (%s);" % order_ids)
    for uid in ids.get("userIds", []):
        mysql_exec("DELETE FROM address WHERE user_id = %s;" % uid)
        mysql_exec("DELETE FROM product_image WHERE product_id IN "
                   "(SELECT id FROM product WHERE seller_id = %s);" % uid)
        mysql_exec("DELETE FROM product WHERE seller_id = %s;" % uid)
        mysql_exec("DELETE FROM student_auth WHERE user_id = %s;" % uid)
    if user_ids:
        mysql_exec("DELETE FROM sys_user WHERE id IN (%s);" % user_ids)
    left = mysql_query("SELECT COUNT(*) FROM review WHERE order_id IN (%s);" % (order_ids or "0"))
    print("清理完成，剩余评价行数：", left)


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
    _, admin_res = call(BASE, "POST", "/api/auth/admin-login",
                        body={"username": "admin", "password": "123456"})
    admin_token = (admin_res.get("data") or {}).get("token")
    if not admin_token:
        print("管理员登录失败")
        return 1

    buyer_token, buyer_id, _ = login_user(BASE, "m7d" + suffix)
    seller_token, seller_id, cert = make_student(BASE, admin_token, suffix)
    other_token, other_id, _ = login_user(BASE, "m7e" + suffix)

    p1 = publish_product(BASE, seller_token, admin_token, cert, "九成新山地自行车", 120)
    p2 = publish_product(BASE, seller_token, admin_token, cert, "考研全套教材（九成新）", 45)
    p3 = publish_product(BASE, seller_token, admin_token, cert, "宿舍护眼台灯", 35)

    _, addr = call(BASE, "POST", "/api/address", token=buyer_token,
                   body={"receiverName": "演示买家", "phone": "13900139000",
                         "address": "北华大学 1 号楼 101", "isDefault": True})
    address_id = addr.get("data")

    o1_no, o1_id = finish_order(BASE, buyer_token, seller_token, p1, address_id)
    o2_no, o2_id = finish_order(BASE, buyer_token, seller_token, p2, address_id)
    o3_no, o3_id = finish_order(BASE, buyer_token, seller_token, p3, address_id)

    # 1）五星好评 + 追评 + 卖家回复
    _, r1 = call(BASE, "POST", "/api/review", token=buyer_token,
                 body={"orderId": o1_id, "reviewType": 1, "descStars": 5, "shipStars": 5,
                       "attitudeStars": 5, "content": "车况跟描述完全一致，卖家发货很快，沟通也耐心，会回购！",
                       "images": [cert, cert, cert]})
    rid1 = (r1.get("data") or {}).get("id")
    call(BASE, "POST", "/api/review/follow", token=buyer_token,
         body={"reviewId": rid1, "content": "骑了一个月，除了正常磨损没有任何问题，继续推荐。",
               "images": [cert]})
    call(BASE, "POST", "/api/review/reply", token=seller_token,
         body={"reviewId": rid1, "content": "感谢支持！车锁和车灯都送你了，骑的时候注意定期上油。"})
    # 2）四星中评
    call(BASE, "POST", "/api/review", token=buyer_token,
         body={"orderId": o2_id, "reviewType": 1, "descStars": 4, "shipStars": 4,
               "attitudeStars": 4, "content": "教材基本齐全，有两本有少量笔记，可以接受。",
               "images": [cert]})
    # 3）三星匿名
    call(BASE, "POST", "/api/review", token=buyer_token,
         body={"orderId": o3_id, "reviewType": 1, "descStars": 3, "shipStars": 3,
               "attitudeStars": 3, "content": "台灯亮度一般，色温偏高，凑合用。", "isAnonymous": True})
    # 4）卖家反向评价买家
    call(BASE, "POST", "/api/review", token=seller_token,
         body={"orderId": o1_id, "reviewType": 2, "payStars": 5, "attitudeStars": 5,
               "content": "买家付款非常快，沟通也很顺畅，是个优质买家。"})

    # 管理员调分，让信誉分页面有流水历史
    call(BASE, "PUT", "/api/credit/%s/adjust" % seller_id, token=admin_token,
         body={"changeScore": -10, "reason": "举报核实：三次未按时发货"})
    call(BASE, "PUT", "/api/credit/%s/adjust" % seller_id, token=admin_token,
         body={"changeScore": 15, "reason": "申诉成立，撤销扣分并给予补偿"})

    state = {
        "suffix": suffix,
        "orderIds": [o1_id, o2_id, o3_id],
        "orderNos": [o1_no, o2_no, o3_no],
        "userIds": [buyer_id, seller_id, other_id],
        "productIds": [p1, p2, p3],
    }
    save_state(state)
    print("演示数据已播种：", json.dumps(state, ensure_ascii=False))
    print("演示数据记录文件：", STATE)
    return 0


if __name__ == "__main__":
    sys.exit(main())
