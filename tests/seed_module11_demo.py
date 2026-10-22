#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""模块11 管理端截图用演示数据播种脚本。

用途：商品审核页的「待人工复核」Tab 在干净数据下是空的，截出来没内容。
本脚本造一个认证学生，发布两件商品：
    1) 标题命中「警告级」敏感词（加微信）→ 待审核 + needManualReview=1
    2) 标题干净 → 待审核 + needManualReview=0（用于对照）

**不清理**，生成的 ID 写入 tests/.module11_demo.json，供 --clean 清理。

用法：
    python tests/seed_module11_demo.py            # 播种
    python tests/seed_module11_demo.py --clean    # 按上次的 ID 清理
"""
import json
import os
import sys
import uuid

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from smoke_module11 import (call, first_category, login_user, make_student,  # noqa: E402
                            mysql_exec, publish)

BASE = "http://127.0.0.1:8080"
STATE = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".module11_demo.json")


def load_state():
    if os.path.exists(STATE):
        with open(STATE, "r", encoding="utf-8") as f:
            return json.load(f)
    return {}


def save_state(state):
    with open(STATE, "w", encoding="utf-8") as f:
        json.dump(state, f, ensure_ascii=False, indent=2)


def clean():
    state = load_state()
    ids = state.get("userIds") or []
    if ids:
        joined = ",".join(str(i) for i in ids)
        mysql_exec("DELETE FROM product_image WHERE product_id IN "
                   "(SELECT id FROM product WHERE seller_id IN (%s));" % joined)
        mysql_exec("DELETE FROM product WHERE seller_id IN (%s);" % joined)
        mysql_exec("DELETE FROM notice WHERE user_id IN (%s);" % joined)
        mysql_exec("DELETE FROM student_auth WHERE user_id IN (%s);" % joined)
        mysql_exec("UPDATE sys_user SET username = CONCAT('del_', id) WHERE id IN (%s);" % joined)
        mysql_exec("DELETE FROM sys_user WHERE id IN (%s);" % joined)
    print("已清理模块11演示数据：", ids)
    if os.path.exists(STATE):
        os.remove(STATE)


def seed():
    suffix = uuid.uuid4().hex[:6]
    _, admin_res = call(BASE, "POST", "/api/auth/login",
                        body={"username": "admin", "password": "123456"})
    admin_token = (admin_res.get("data") or {}).get("token")
    if not admin_token:
        print("管理员登录失败，请确认后端已启动")
        return

    category_id = first_category(BASE)
    student_token, student_id, cert = make_student(BASE, admin_token, suffix, "演示卖家" + suffix)

    ids = []
    _, r1 = publish(BASE, student_token, category_id,
                    "考研英语真题可加微信详聊", "九成新，做过一遍，笔记可擦除", cert)
    if r1.get("data"):
        ids.append(r1["data"])
    _, r2 = publish(BASE, student_token, category_id,
                    "宿舍小台灯（九成新）", "护眼台灯，用了一学期，功能正常", cert)
    if r2.get("data"):
        ids.append(r2["data"])
    _, r3 = publish(BASE, student_token, category_id,
                    "闲置篮球 加微信送护腕", "打了几次，成色新", cert)
    if r3.get("data"):
        ids.append(r3["data"])

    save_state({"suffix": suffix, "userId": student_id, "userIds": [student_id],
                "productIds": ids})
    print("已播种演示数据：学生 %s，商品 %s" % (student_id, ids))
    print("其中标题含「加微信」的商品会被标记为待人工复核")


if __name__ == "__main__":
    if "--clean" in sys.argv:
        clean()
    else:
        seed()
