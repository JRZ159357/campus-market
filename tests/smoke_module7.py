#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""模块7「评价与信誉分」接口冒烟测试。

用法（后端需以较短的订单超时参数启动）：
    mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8080 \
        --app.order.pay-timeout-minutes=2 --app.order.timeout-scan-ms=10000"
    python tests/smoke_module7.py

覆盖流程：
    权限（未登录 401 / 客服只读 403 / 非参与方 403）-> 评价前置校验（订单未完成、方向不符、维度缺失） ->
    双向评价与信誉分增减 -> 重复评价拦截 -> 追评（窗口内 / 重复 / 超期）-> 卖家回复（越权 / 重复） ->
    匿名脱敏（用户端脱敏、管理端不脱敏）-> 商品 / 用户 / 待评价列表 -> 管理端筛选与删除（不回退分数） ->
    管理员调分（截断 0-150、原因必填、边界拒绝）-> 信誉等级 -> 商品评价统计 -> DB 落库校验 -> 数据清理

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
from datetime import datetime, timedelta
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


def make_student(base, admin_token, suffix):
    """注册微信用户并认证成学生（只有学生能发布商品）。"""
    token, user_id, _ = login_user(base, "m7s" + suffix)
    cert = (upload(base, token).get("data") or "")
    call(base, "POST", "/api/student-auth", token=token,
         body={"studentNo": "M7" + suffix, "realName": "模块七卖家",
               "school": "北华大学", "certImg": cert})
    _, page = call(base, "GET", "/api/student-auth/page?pageNum=1&pageSize=50&status=0",
                   token=admin_token)
    target = next((r for r in ((page.get("data") or {}).get("records") or [])
                   if r.get("userId") == user_id), None)
    if target:
        call(base, "PUT", "/api/student-auth/%s/audit" % target["id"],
             token=admin_token, body={"status": 1})
    return token, user_id, cert


def publish_product(base, token, admin_token, cert, title, price):
    _, cat = call(base, "GET", "/api/category/list")
    category_id = (cat.get("data") or [{}])[0].get("id")
    _, res = call(base, "POST", "/api/product", token=token,
                  body={"title": title, "categoryId": category_id, "price": price,
                        "originalPrice": price * 3, "description": "模块七测试商品",
                        "images": [cert]})
    pid = res.get("data")
    call(base, "PUT", "/api/product/%s/audit" % pid, token=admin_token, body={"status": 1})
    return pid


def finish_order(base, buyer_token, seller_token, product_id, address_id):
    """下单 → 支付 → 发货 → 收货，返回 (订单号, 订单ID)。"""
    _, res = call(base, "POST", "/api/order/create", token=buyer_token,
                  body={"productId": product_id, "addressId": address_id})
    order_no = (res.get("data") or {}).get("orderNo")
    call(base, "POST", "/api/order/%s/pay" % order_no, token=buyer_token)
    call(base, "POST", "/api/order/%s/ship" % order_no, token=seller_token)
    call(base, "POST", "/api/order/%s/receive" % order_no, token=buyer_token)
    _, detail = call(base, "GET", "/api/order/%s" % order_no, token=buyer_token)
    return order_no, (detail.get("data") or {}).get("id")


def credit_of(base, user_id):
    _, res = call(base, "GET", "/api/credit/%s" % user_id)
    return res.get("data") or {}


def nickname_of(base, token):
    """取当前登录用户的真实昵称。
    注意：make_student 里填的 realName（实名认证姓名）不会写进 sys_user.nickname，
    按昵称搜索必须用 /api/auth/me 拿到的 nickname，否则筛选结果恒为 0。
    """
    _, res = call(base, "GET", "/api/auth/me", token=token)
    return ((res.get("data") or {}).get("nickname") or "").strip()


def review_of_order(base, order_id, token):
    _, res = call(base, "GET", "/api/review/order/%s" % order_id, token=token)
    return res.get("data") or {}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="http://127.0.0.1:8080")
    args = parser.parse_args()
    base = args.base.rstrip("/")
    suffix = uuid.uuid4().hex[:6]

    print("=" * 60)
    print("模块7 评价与信誉分 接口冒烟测试  base =", base)
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

    buyer_token, buyer_id, _ = login_user(base, "m7b" + suffix)
    seller_token, seller_id, cert = make_student(base, admin_token, suffix)
    other_token, other_id, _ = login_user(base, "m7o" + suffix)

    seller_nickname = nickname_of(base, seller_token)
    buyer_nickname = nickname_of(base, buyer_token)
    check("拿到卖家/买家真实昵称用于昵称筛选", bool(seller_nickname) and bool(buyer_nickname),
          [seller_nickname, buyer_nickname])

    p1 = publish_product(base, seller_token, admin_token, cert, "模块七-评价测试-自行车", 120)
    p2 = publish_product(base, seller_token, admin_token, cert, "模块七-评价测试-台灯", 45)
    p3 = publish_product(base, seller_token, admin_token, cert, "模块七-评价测试-教材", 25)

    _, addr = call(base, "POST", "/api/address", token=buyer_token,
                   body={"receiverName": "评价买家", "phone": "13900139000",
                         "address": "北华大学 1 号楼 101", "isDefault": True})
    address_id = addr.get("data")

    o1_no, o1_id = finish_order(base, buyer_token, seller_token, p1, address_id)
    o2_no, o2_id = finish_order(base, buyer_token, seller_token, p2, address_id)
    o3_no, o3_id = finish_order(base, buyer_token, seller_token, p3, address_id)

    # 未完成的订单（用于校验"只有已完成才能评价"）
    _, pending_order = call(base, "POST", "/api/order/create", token=buyer_token,
                            body={"productId": publish_product(base, seller_token, admin_token,
                                                               cert, "模块七-未完成-水杯", 15),
                                  "addressId": address_id})
    unpaid_no = (pending_order.get("data") or {}).get("orderNo")
    _, unpaid_detail = call(base, "GET", "/api/order/%s" % unpaid_no, token=buyer_token)
    unpaid_id = (unpaid_detail.get("data") or {}).get("id")

    print("\n== 1. 权限 ==")
    # 本项目约定：业务异常 / 鉴权失败统一以 HTTP 200 返回，真正的结果码在 body.code
    _, res = call(base, "POST", "/api/review", body={"orderId": o1_id, "reviewType": 1})
    check("未登录提交评价 401", res.get("code") == 401, res.get("msg"))
    _, res = call(base, "GET", "/api/review/my/pending")
    check("未登录查看待评价 401", res.get("code") == 401, res.get("msg"))
    _, res = call(base, "GET", "/api/review/product/%s" % p1)
    check("未登录可看商品评价（免登录）", res.get("code") == 200, res.get("msg"))
    _, res = call(base, "GET", "/api/credit/%s" % seller_id)
    check("未登录可看信誉分（免登录）", res.get("code") == 200 and (res.get("data") or {}).get("score") == 100,
          (res.get("data") or {}).get("score"))

    _, res = call(base, "POST", "/api/review", token=cs_token, body={"orderId": o1_id, "reviewType": 1})
    check("客服提交评价 403", res.get("code") == 403, res.get("msg"))
    _, res = call(base, "DELETE", "/api/review/1", token=cs_token)
    check("客服删除评价 403", res.get("code") == 403, res.get("msg"))
    _, res = call(base, "GET", "/api/review/admin/page", token=cs_token)
    check("客服可看评价管理列表（只读）", res.get("code") == 200, res.get("msg"))
    _, res = call(base, "PUT", "/api/credit/%s/adjust" % buyer_id, token=cs_token,
                  body={"changeScore": 5, "reason": "测试"})
    check("客服调整信誉分 403", res.get("code") == 403, res.get("msg"))

    print("\n== 2. 评价前置校验 ==")
    _, res = call(base, "POST", "/api/review", token=buyer_token,
                  body={"orderId": unpaid_id, "reviewType": 1, "descStars": 5,
                        "shipStars": 5, "attitudeStars": 5})
    check("未完成订单不能评价", res.get("code") != 200 and "已完成" in (res.get("msg") or ""), res.get("msg"))
    _, res = call(base, "POST", "/api/review", token=other_token,
                  body={"orderId": o1_id, "reviewType": 1, "descStars": 5,
                        "shipStars": 5, "attitudeStars": 5})
    check("非订单参与方不能评价", res.get("code") == 403, res.get("msg"))
    _, res = call(base, "POST", "/api/review", token=buyer_token,
                  body={"orderId": o1_id, "reviewType": 2, "payStars": 5, "attitudeStars": 5})
    check("评价方向与角色不符被拒", res.get("code") != 200 and "方向" in (res.get("msg") or ""), res.get("msg"))
    _, res = call(base, "POST", "/api/review", token=buyer_token,
                  body={"orderId": o1_id, "reviewType": 1, "descStars": 5, "shipStars": 5})
    check("缺少沟通态度评分被拒", res.get("code") != 200 and "沟通态度" in (res.get("msg") or ""), res.get("msg"))
    _, res = call(base, "POST", "/api/review", token=buyer_token,
                  body={"orderId": o1_id, "reviewType": 1, "descStars": 6,
                        "shipStars": 5, "attitudeStars": 5})
    check("星级越界被拒", res.get("code") != 200 and "1-5" in (res.get("msg") or ""), res.get("msg"))
    _, res = call(base, "POST", "/api/review", token=buyer_token,
                  body={"orderId": o1_id, "reviewType": 1, "descStars": 5,
                        "shipStars": 5, "attitudeStars": 5, "content": "字" * 501})
    check("评价内容超 500 字被拒", res.get("code") == 400, res.get("msg"))

    print("\n== 3. 买家评价卖家 + 信誉分 ==")
    _, res = call(base, "POST", "/api/review", token=buyer_token,
                  body={"orderId": o1_id, "reviewType": 1, "descStars": 5, "shipStars": 5,
                        "attitudeStars": 5, "content": "车况很好，卖家很爽快"})
    r1 = res.get("data") or {}
    check("提交评价成功", res.get("code") == 200 and r1.get("id"), res.get("msg"))
    check("综合星级按平均四舍五入 = 5", r1.get("stars") == 5, r1.get("stars"))
    check("买家评卖家三维度齐全", r1.get("descStars") == 5 and r1.get("shipStars") == 5
          and r1.get("attitudeStars") == 5, r1)
    check("卖家评买家维度为空", r1.get("payStars") is None, r1.get("payStars"))
    credit = credit_of(base, seller_id)
    check("卖家信誉分 100 -> 102（5星+2）", credit.get("score") == 102, credit.get("score"))
    check("信誉等级为「优秀」", credit.get("levelLabel") == "优秀", credit.get("levelLabel"))
    _, logs = call(base, "GET", "/api/credit/%s/logs" % seller_id, token=seller_token)
    log_records = (logs.get("data") or {}).get("records") or []
    check("信誉分流水写入了+2记录",
          bool(log_records) and log_records[0].get("changeScore") == 2
          and log_records[0].get("beforeScore") == 100 and log_records[0].get("afterScore") == 102,
          log_records[0] if log_records else None)

    _, res = call(base, "POST", "/api/review", token=buyer_token,
                  body={"orderId": o1_id, "reviewType": 1, "descStars": 5,
                        "shipStars": 5, "attitudeStars": 5})
    check("同一订单重复评价被拒", res.get("code") != 200 and "已经评价" in (res.get("msg") or ""), res.get("msg"))

    print("\n== 4. 卖家评价买家 ==")
    _, res = call(base, "POST", "/api/review", token=seller_token,
                  body={"orderId": o1_id, "reviewType": 2, "payStars": 4, "attitudeStars": 3,
                        "content": "付款及时"})
    r2 = res.get("data") or {}
    check("卖家评价买家成功", res.get("code") == 200, res.get("msg"))
    check("二维度平均 3.5 四舍五入为 4", r2.get("stars") == 4, r2.get("stars"))
    check("卖家评买家时描述维度为空", r2.get("descStars") is None, r2.get("descStars"))
    buyer_credit = credit_of(base, buyer_id)
    check("买家信誉分 100 -> 101（4星+1）", buyer_credit.get("score") == 101, buyer_credit.get("score"))

    info = review_of_order(base, o1_id, buyer_token)
    check("订单评价详情返回双方评价", bool(info.get("buyerReview")) and bool(info.get("sellerReview")),
          [bool(info.get("buyerReview")), bool(info.get("sellerReview"))])
    check("买家视角 canReview=false", info.get("canReview") is False, info.get("canReview"))
    check("买家视角 myReviewId 有值", info.get("myReviewId") == r1.get("id"), info.get("myReviewId"))
    check("买家视角 myReviewType=1", info.get("myReviewType") == 1, info.get("myReviewType"))
    pending = call(base, "GET", "/api/review/my/pending", token=buyer_token)[1].get("data") or {}
    pending_ids = [item.get("orderId") for item in (pending.get("records") or [])]
    check("已评价订单不再出现在待评价列表", o1_id not in pending_ids, pending_ids[:6])

    print("\n== 5. 追评 ==")
    _, res = call(base, "POST", "/api/review/follow", token=seller_token,
                  body={"reviewId": r1.get("id"), "content": "不是我的评价"})
    check("非评价人不能追评", res.get("code") == 403, res.get("msg"))
    _, res = call(base, "POST", "/api/review/follow", token=buyer_token,
                  body={"reviewId": r1.get("id"), "content": "  "})
    check("追评内容与图片都为空被拒", res.get("code") != 200 and "至少" in (res.get("msg") or ""), res.get("msg"))
    _, res = call(base, "POST", "/api/review/follow", token=buyer_token,
                  body={"reviewId": r1.get("id"), "content": "骑了两周依然很好骑，追加好评"})
    check("追评成功", res.get("code") == 200, res.get("msg"))
    check("追评内容已保存", (res.get("data") or {}).get("followContent") == "骑了两周依然很好骑，追加好评",
          (res.get("data") or {}).get("followContent"))
    _, res = call(base, "POST", "/api/review/follow", token=buyer_token,
                  body={"reviewId": r1.get("id"), "content": "再追一次"})
    check("不能重复追评", res.get("code") != 200 and "追评过" in (res.get("msg") or ""), res.get("msg"))
    credit_after_follow = credit_of(base, seller_id)
    check("追评不重复加分", credit_after_follow.get("score") == 102, credit_after_follow.get("score"))

    # 超期追评：把订单完成时间改到 8 天前
    _, res = call(base, "POST", "/api/review", token=buyer_token,
                  body={"orderId": o3_id, "reviewType": 1, "descStars": 3, "shipStars": 3,
                        "attitudeStars": 3, "content": "教材一般"})
    r3 = res.get("data") or {}
    check("3 星评价提交成功", res.get("code") == 200, res.get("msg"))
    check("3 星综合星级 = 3", r3.get("stars") == 3, r3.get("stars"))
    check("3 星不加分（信誉分仍为 102）", credit_of(base, seller_id).get("score") == 102,
          credit_of(base, seller_id).get("score"))
    moved = mysql_exec("UPDATE trade_order SET finish_time = DATE_SUB(NOW(), INTERVAL 8 DAY) WHERE id = %s;"
                       % o3_id)
    if moved is None:
        print("  [SKIP] 未找到 mysql 客户端，跳过超期追评校验")
    else:
        _, res = call(base, "POST", "/api/review/follow", token=buyer_token,
                      body={"reviewId": r3.get("id"), "content": "超期追评"})
        check("超过 7 天不能追评", res.get("code") != 200 and "期限" in (res.get("msg") or ""), res.get("msg"))

    print("\n== 6. 卖家回复 ==")
    _, res = call(base, "POST", "/api/review/reply", token=buyer_token,
                  body={"reviewId": r1.get("id"), "content": "买家自己回复"})
    check("非被评价卖家不能回复", res.get("code") == 403, res.get("msg"))
    _, res = call(base, "POST", "/api/review/reply", token=buyer_token,
                  body={"reviewId": r2.get("id"), "content": "回复卖家评买家的评价"})
    check("不能回复卖家对买家的评价", res.get("code") != 200 and "只能回复" in (res.get("msg") or ""), res.get("msg"))
    _, res = call(base, "POST", "/api/review/reply", token=seller_token,
                  body={"reviewId": r1.get("id"), "content": "感谢支持，车锁记得定期上油"})
    check("卖家回复成功", res.get("code") == 200, res.get("msg"))
    check("回复内容已保存", (res.get("data") or {}).get("replyContent") == "感谢支持，车锁记得定期上油",
          (res.get("data") or {}).get("replyContent"))
    _, res = call(base, "POST", "/api/review/reply", token=seller_token,
                  body={"reviewId": r1.get("id"), "content": "再回复一次"})
    check("不能重复回复", res.get("code") != 200 and "回复过" in (res.get("msg") or ""), res.get("msg"))

    print("\n== 7. 匿名评价脱敏 ==")
    _, res = call(base, "POST", "/api/review", token=buyer_token,
                  body={"orderId": o2_id, "reviewType": 1, "descStars": 4, "shipStars": 4,
                        "attitudeStars": 4, "content": "台灯亮度不错", "isAnonymous": True})
    anon_review = res.get("data") or {}
    check("匿名评价提交成功", res.get("code") == 200, res.get("msg"))
    check("匿名评价返回「匿名用户」", (anon_review.get("reviewerNickname") == "匿名用户"),
          anon_review.get("reviewerNickname"))
    _, page = call(base, "GET", "/api/review/product/%s" % p2)
    records = (page.get("data") or {}).get("records") or []
    check("商品评价列表匿名脱敏", bool(records) and records[0].get("reviewerNickname") == "匿名用户",
          records[0].get("reviewerNickname") if records else None)
    check("匿名评价头像为空", records[0].get("reviewerAvatar") is None if records else False,
          records[0].get("reviewerAvatar") if records else None)
    check("匿名标记 isAnonymous=true", bool(records) and records[0].get("isAnonymous") is True,
          records[0].get("isAnonymous") if records else None)
    _, admin_page = call(base, "GET", "/api/review/admin/page?productKeyword=%s" % quote("台灯"),
                         token=admin_token)
    admin_records = (admin_page.get("data") or {}).get("records") or []
    check("管理端不脱敏（显示真实昵称）",
          bool(admin_records) and admin_records[0].get("reviewerNickname") != "匿名用户",
          admin_records[0].get("reviewerNickname") if admin_records else None)

    print("\n== 8. 列表与筛选 ==")
    # p1 只有 o1 一笔订单，商品页只应出现「买家评卖家」那一条（review_type=1），
    # 卖家对买家的打分共用同一个 product_id，不能混进商品评价里
    _, page = call(base, "GET", "/api/review/product/%s?minStars=4&maxStars=5" % p1)
    check("商品评价按好评筛选", (page.get("data") or {}).get("total") == 1,
          (page.get("data") or {}).get("total"))
    _, page = call(base, "GET", "/api/review/product/%s?minStars=1&maxStars=2" % p1)
    check("商品评价按差评筛选为空", (page.get("data") or {}).get("total") == 0,
          (page.get("data") or {}).get("total"))
    _, page = call(base, "GET", "/api/review/product/%s" % p2)
    p2_records = (page.get("data") or {}).get("records") or []
    check("商品评价列表返回被评价人信誉等级",
          bool(p2_records) and p2_records[0].get("revieweeLevelLabel") is not None,
          p2_records[0].get("revieweeLevelLabel") if p2_records else None)
    _, page = call(base, "GET", "/api/review/user/%s" % seller_id)
    check("用户收到的评价列表 total=3", (page.get("data") or {}).get("total") == 3,
          (page.get("data") or {}).get("total"))
    _, stat = call(base, "GET", "/api/review/product/%s/stat" % p1)
    stat_data = stat.get("data") or {}
    check("商品评价统计 total=1", stat_data.get("total") == 1, stat_data.get("total"))
    check("商品评价统计平均分 5.0", stat_data.get("avgStars") == 5.0, stat_data.get("avgStars"))
    _, pending_page = call(base, "GET", "/api/review/my/pending", token=seller_token)
    check("卖家待评价包含 o2（卖家未评买家）",
          o2_id in [item.get("orderId") for item in ((pending_page.get("data") or {}).get("records") or [])],
          (pending_page.get("data") or {}).get("total"))
    _, count = call(base, "GET", "/api/review/my/pending/count", token=seller_token)
    check("待评价数量角标 >= 1", (count.get("data") or 0) >= 1, count.get("data"))

    _, filtered = call(base, "GET", "/api/review/admin/page?reviewerKeyword=%s" % quote(buyer_nickname),
                      token=admin_token)
    check("管理端按评价人昵称筛选有结果", ((filtered.get("data") or {}).get("total") or 0) >= 1,
          (filtered.get("data") or {}).get("total"))
    _, filtered = call(base, "GET", "/api/review/admin/page?revieweeKeyword=%s" % quote(seller_nickname),
                      token=admin_token)
    check("管理端按被评价人昵称筛选有结果", ((filtered.get("data") or {}).get("total") or 0) >= 1,
          (filtered.get("data") or {}).get("total"))
    _, filtered = call(base, "GET", "/api/review/admin/page?hasFollow=true", token=admin_token)
    check("管理端只看有追评", ((filtered.get("data") or {}).get("total") or 0) >= 1,
          (filtered.get("data") or {}).get("total"))
    _, filtered = call(base, "GET", "/api/review/admin/page?reviewerKeyword=%s" % quote("不存在的昵称zzz"),
                      token=admin_token)
    check("管理端筛选无匹配返回空页", (filtered.get("data") or {}).get("total") == 0,
          (filtered.get("data") or {}).get("total"))

    print("\n== 9. 管理端删除违规评价 ==")
    _, res = call(base, "DELETE", "/api/review/%s" % r1.get("id"), token=cs_token)
    check("客服删除仍被拦截 403", res.get("code") == 403, res.get("msg"))
    _, res = call(base, "DELETE", "/api/review/%s" % r1.get("id"), token=admin_token)
    check("管理员删除评价成功", res.get("code") == 200, res.get("msg"))
    _, res = call(base, "GET", "/api/review/%s" % r1.get("id"))
    check("已删除评价详情不可见", res.get("code") != 200, res.get("msg"))
    # 102（o1 五星）+1（o2 四星匿名）= 103，删除评价明确不回退，仍应为 103
    check("删除评价不回退信誉分", credit_of(base, seller_id).get("score") == 103,
          credit_of(base, seller_id).get("score"))
    _, logs = call(base, "GET", "/api/credit/%s/logs" % seller_id, token=admin_token)
    audit_logs = [item for item in ((logs.get("data") or {}).get("records") or [])
                  if item.get("type") == 4]
    check("删除评价写入审计流水", bool(audit_logs) and audit_logs[0].get("changeScore") == 0,
          audit_logs[0] if audit_logs else None)
    _, info2 = call(base, "GET", "/api/review/order/%s" % o1_id, token=buyer_token)
    check("删除后订单仅剩卖家评价", (info2.get("data") or {}).get("buyerReview") is None
          and (info2.get("data") or {}).get("sellerReview") is not None,
          [(info2.get("data") or {}).get("buyerReview"), (info2.get("data") or {}).get("sellerReview")])

    print("\n== 10. 管理员调整信誉分 ==")
    _, res = call(base, "PUT", "/api/credit/%s/adjust" % buyer_id, token=admin_token,
                  body={"changeScore": 0, "reason": "零变动"})
    check("调整 0 分被拒", res.get("code") != 200 and "不能为 0" in (res.get("msg") or ""), res.get("msg"))
    _, res = call(base, "PUT", "/api/credit/%s/adjust" % buyer_id, token=admin_token,
                  body={"changeScore": 101, "reason": "超范围"})
    check("单次调整超 ±100 被拒", res.get("code") == 400, res.get("msg"))
    _, res = call(base, "PUT", "/api/credit/%s/adjust" % buyer_id, token=admin_token,
                  body={"changeScore": 5, "reason": ""})
    check("调整原因为空被拒", res.get("code") == 400, res.get("msg"))
    _, res = call(base, "PUT", "/api/credit/%s/adjust" % buyer_id, token=admin_token,
                  body={"changeScore": 10, "reason": "举报核实存在恶意砍价"})
    check("管理员加 10 分成功", res.get("code") == 200 and (res.get("data") or {}).get("score") == 111,
          (res.get("data") or {}).get("score"))
    _, logs = call(base, "GET", "/api/credit/%s/logs?type=3" % buyer_id, token=admin_token)
    adjust_logs = (logs.get("data") or {}).get("records") or []
    check("调整历史只含管理员调整",
          bool(adjust_logs) and all(item.get("type") == 3 for item in adjust_logs),
          [item.get("type") for item in adjust_logs])
    check("调整历史记录操作人", bool(adjust_logs) and adjust_logs[0].get("operatorName") is not None,
          adjust_logs[0].get("operatorName") if adjust_logs else None)

    _, res = call(base, "PUT", "/api/credit/%s/adjust" % buyer_id, token=admin_token,
                  body={"changeScore": 100, "reason": "测试上限截断"})
    check("超上限自动截断到 150", (res.get("data") or {}).get("score") == 150,
          (res.get("data") or {}).get("score"))
    _, logs = call(base, "GET", "/api/credit/%s/logs?type=3" % buyer_id, token=admin_token)
    latest = ((logs.get("data") or {}).get("records") or [{}])[0]
    check("流水记录实际生效变动 39（111->150）", latest.get("changeScore") == 39,
          latest.get("changeScore"))
    _, res = call(base, "PUT", "/api/credit/%s/adjust" % buyer_id, token=admin_token,
                  body={"changeScore": 10, "reason": "已封顶再加"})
    check("已达上限再加分被拒", res.get("code") != 200 and "边界" in (res.get("msg") or ""), res.get("msg"))
    credit_top = credit_of(base, buyer_id)
    check("上限等级为「极佳」", credit_top.get("levelLabel") == "极佳", credit_top.get("levelLabel"))
    check("上限进度为 100", credit_top.get("progress") == 100, credit_top.get("progress"))

    _, res = call(base, "PUT", "/api/credit/%s/adjust" % other_id, token=admin_token,
                  body={"changeScore": -100, "reason": "测试下限"})
    check("从 100 扣到下限 0", (res.get("data") or {}).get("score") == 0,
          (res.get("data") or {}).get("score"))
    check("0 分等级为「差」", (res.get("data") or {}).get("levelLabel") == "差",
          (res.get("data") or {}).get("levelLabel"))

    _, users = call(base, "GET", "/api/credit/admin/users?keyword=%s" % quote(seller_nickname), token=admin_token)
    check("管理端按昵称搜用户有结果", ((users.get("data") or {}).get("total") or 0) >= 1,
          (users.get("data") or {}).get("total"))
    _, users = call(base, "GET", "/api/credit/admin/users?keyword=%s" % seller_id, token=admin_token)
    check("管理端按用户ID搜用户命中",
          (users.get("data") or {}).get("records") and
          (users.get("data") or {}).get("records")[0].get("userId") == seller_id,
          (users.get("data") or {}).get("total"))

    print("\n== 11. 数据落库校验 ==")
    rows = mysql_query("SELECT COUNT(*) FROM review WHERE order_id IN (%s, %s, %s);" % (o1_id, o2_id, o3_id))
    if rows is None:
        print("  [SKIP] 未找到 mysql 客户端，跳过落库校验")
    else:
        # o1 买评卖（被逻辑删除但仍占行）+ o1 卖评买 + o2 匿名评 + o3 评 = 4 行
        check("review 表落库 4 条（含 1 条逻辑删除）", rows.strip() == "4", rows)
        flags = mysql_query("SELECT buyer_reviewed, seller_reviewed FROM trade_order WHERE id = %s;" % o1_id)
        check("订单两侧评价标记均为 1", flags is not None and flags.strip() == "1\t1", flags)
        # 102（o1 五星 +2）+1（o2 四星 +1）= 103
        score = mysql_query("SELECT credit_score FROM sys_user WHERE id = %s;" % seller_id)
        check("卖家信誉分落库 103", score is not None and score.strip() == "103", score)

    # ---------------- 清理 ----------------
    print("\n== 12. 数据清理 ==")
    order_ids = [o1_id, o2_id, o3_id, unpaid_id]
    id_list = ",".join(str(i) for i in order_ids)
    product_ids = [p1, p2, p3]
    mysql_exec("DELETE FROM review WHERE order_id IN (%s);" % id_list)
    mysql_exec("DELETE FROM credit_log WHERE user_id IN (%s, %s, %s);" % (buyer_id, seller_id, other_id))
    mysql_exec("DELETE FROM order_log WHERE order_id IN (%s);" % id_list)
    mysql_exec("DELETE FROM trade_order WHERE id IN (%s);" % id_list)
    mysql_exec("DELETE FROM address WHERE user_id = %s;" % buyer_id)
    extra = mysql_query("SELECT id FROM product WHERE seller_id = %s;" % seller_id) or ""
    for pid in [line.strip() for line in extra.splitlines() if line.strip()]:
        product_ids.append(int(pid))
    mysql_exec("DELETE FROM product_image WHERE product_id IN (%s);"
               % ",".join(str(i) for i in set(product_ids)))
    mysql_exec("DELETE FROM product WHERE seller_id = %s;" % seller_id)
    mysql_exec("DELETE FROM student_auth WHERE user_id = %s;" % seller_id)
    mysql_exec("DELETE FROM sys_user WHERE id IN (%s, %s, %s);" % (buyer_id, seller_id, other_id))
    left = mysql_query("SELECT COUNT(*) FROM review WHERE order_id IN (%s);" % id_list)
    if left is not None:
        check("测试评价已清理", left.strip() == "0", left)
        left_user = mysql_query("SELECT COUNT(*) FROM sys_user WHERE id = %s;" % buyer_id)
        check("测试用户已清理", left_user.strip() == "0", left_user)

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
