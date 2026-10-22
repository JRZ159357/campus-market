#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""模块8 管理端截图用演示数据播种脚本。

用途：管理端「用户管理」需要多种角色的真实用户才能截出有内容的图：
普通用户、认证学生、客服、管理员、已封禁各一个，外加不同信誉分档位。
本脚本复用 smoke_module8 的建数流程，但**不清理**，并把生成的 ID 写进
tests/.module8_demo.json，供 --clean 清理。

用法：
    python tests/seed_module8_demo.py            # 播种
    python tests/seed_module8_demo.py --clean    # 按上次的 ID 清理
"""
import json
import os
import sys
import uuid

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from smoke_module8 import (call, login_user, make_student, publish_product,  # noqa: E402
                           mysql_exec, mysql_query)

BASE = "http://127.0.0.1:8080"
STATE = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".module8_demo.json")


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
    if product_ids:
        mysql_exec("DELETE FROM product_image WHERE product_id IN (%s);" % product_ids)
        mysql_exec("DELETE FROM product WHERE id IN (%s);" % product_ids)
    if user_ids:
        mysql_exec("DELETE FROM follow WHERE user_id IN (%s) OR followed_id IN (%s);" % (user_ids, user_ids))
        mysql_exec("DELETE FROM favorite WHERE user_id IN (%s);" % user_ids)
        mysql_exec("DELETE FROM student_auth WHERE user_id IN (%s);" % user_ids)
        mysql_exec("DELETE FROM credit_log WHERE user_id IN (%s);" % user_ids)
        mysql_exec("DELETE FROM notice WHERE user_id IN (%s);" % user_ids)
        mysql_exec("DELETE FROM sys_user WHERE id IN (%s);" % user_ids)
    left = mysql_query("SELECT COUNT(*) FROM sys_user WHERE id IN (%s);" % (user_ids or "0"))
    print("清理完成，剩余用户行数：", left)


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

    created_users = []
    created_products = []

    # 1）认证学生卖家：有商品、有粉丝、信誉分优秀
    s1_token, s1_id, s1_cert = make_student(BASE, admin_token, suffix + "a", "林小满")
    created_users.append(s1_id)
    for title, price in [("九成新自行车带锁", 150), ("考研数学全套教材", 60), ("宿舍护眼台灯", 35)]:
        pid = publish_product(BASE, s1_token, admin_token, s1_cert, title, price)
        created_products.append(pid)
    call(BASE, "PUT", "/api/user/profile", token=s1_token,
         body={"gender": 2, "intro": "大三在读，只卖自用闲置，诚信交易欢迎咨询。"})
    call(BASE, "PUT", "/api/credit/%s/adjust" % s1_id, token=admin_token,
         body={"changeScore": 25, "reason": "演示数据：连续好评奖励"})

    # 2）认证学生卖家：信誉分一般
    s2_token, s2_id, s2_cert = make_student(BASE, admin_token, suffix + "b", "陈知远")
    created_users.append(s2_id)
    pid = publish_product(BASE, s2_token, admin_token, s2_cert, "毕业闲置：折叠桌与收纳箱", 80)
    created_products.append(pid)
    call(BASE, "PUT", "/api/user/profile", token=s2_token,
         body={"gender": 1, "intro": "毕业清仓，家具电器都可小刀。"})

    # 3）普通用户（未认证）：不能发布
    u1_token, u1_id, _ = login_user(BASE, "m8d1" + suffix)
    created_users.append(u1_id)
    call(BASE, "PUT", "/api/user/profile", token=u1_token,
         body={"nickname": "赵小雨", "gender": 2, "intro": "想淘点宿舍好物，先逛逛～"})

    # 4）普通用户：信誉分较差
    u2_token, u2_id, _ = login_user(BASE, "m8d2" + suffix)
    created_users.append(u2_id)
    call(BASE, "PUT", "/api/user/profile", token=u2_token,
         body={"nickname": "孙一鸣", "gender": 1, "intro": "闲置出清中"})
    call(BASE, "PUT", "/api/credit/%s/adjust" % u2_id, token=admin_token,
         body={"changeScore": -35, "reason": "演示数据：两次未按时发货"})

    # 5）粉丝与关注：给上面几个账号造关注关系
    for follower_token in (u1_token, u2_token):
        call(BASE, "POST", "/api/follow/%s" % s1_id, token=follower_token)
        call(BASE, "POST", "/api/follow/%s" % s2_id, token=follower_token)
    call(BASE, "POST", "/api/follow/%s" % u1_id, token=s1_token)

    # 6）已封禁账号
    b1_token, b1_id, _ = login_user(BASE, "m8d3" + suffix)
    created_users.append(b1_id)
    call(BASE, "PUT", "/api/user/profile", token=b1_token,
         body={"nickname": "违规用户", "intro": "该账号因违规已被封禁"})
    call(BASE, "PUT", "/api/admin/user/%s/status" % b1_id, token=admin_token,
         body={"status": 1, "reason": "演示数据：发布违禁品"})

    # 7）客服账号（用管理员建出来的普通账号升角色，便于统一清理）
    cs_token, cs_id, _ = login_user(BASE, "m8d4" + suffix)
    created_users.append(cs_id)
    call(BASE, "PUT", "/api/user/profile", token=cs_token, body={"nickname": "客服小周"})
    call(BASE, "PUT", "/api/admin/user/%s/role" % cs_id, token=admin_token,
         body={"role": "customer_service"})

    # 8）另一名管理员
    ad_token, ad_id, _ = login_user(BASE, "m8d5" + suffix)
    created_users.append(ad_id)
    call(BASE, "PUT", "/api/user/profile", token=ad_token, body={"nickname": "运营管理员"})
    call(BASE, "PUT", "/api/admin/user/%s/role" % ad_id, token=admin_token,
         body={"role": "admin"})

    state = {
        "suffix": suffix,
        "userIds": created_users,
        "productIds": created_products,
    }
    save_state(state)
    print("演示数据已播种：", json.dumps(state, ensure_ascii=False))
    print("演示数据记录文件：", STATE)
    return 0


if __name__ == "__main__":
    sys.exit(main())
