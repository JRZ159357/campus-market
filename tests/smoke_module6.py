#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""模块6「消息与通知」接口冒烟测试。

用法（后端需以较短的订单超时参数启动，便于验证订单相关通知）：
    mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8080 \
        --app.order.pay-timeout-minutes=2 --app.order.timeout-scan-ms=10000"
    python tests/smoke_module6.py

覆盖流程：
    权限（未登录 401 / 客服只读 403 / 越权访问他人会话）-> 会话创建与幂等（唯一索引）-> 商品卡片自动消息 ->
    发消息 / 未读数 / 标记已读 -> 图片与卡片消息 -> 游标分页 -> 单向删除会话 ->
    订单状态变更触发的系统通知与系统消息 -> 商品审核 / 学生认证通知 ->
    个人通知列表与已读 -> 公告发布（全部 / 指定用户）与删除 -> 客服工作台只读监控 -> 数据清理

WebSocket 实时推送由 tests/ws_check.cjs 单独验证（Node 原生 WebSocket 客户端）。

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
    """注册一个微信用户并把它认证成学生（只有学生能发布商品）。"""
    token, user_id, _ = login_user(base, "m6s" + suffix)
    cert = (upload(base, token).get("data") or "")
    call(base, "POST", "/api/student-auth", token=token,
         body={"studentNo": "M6" + suffix, "realName": "模块六学生",
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


def unread_of(base, token):
    _, res = call(base, "GET", "/api/chat/unread", token=token)
    return res.get("data") or {}


def notice_unread_of(base, token):
    _, res = call(base, "GET", "/api/notice/unread-count", token=token)
    return res.get("data") or {}


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

    buyer_token, buyer_id, _ = login_user(base, "m6a" + suffix)
    check("买家微信登录成功", bool(buyer_token) and bool(buyer_id))
    other_token, other_id, _ = login_user(base, "m6b" + suffix)
    check("第三人微信登录成功（用于越权校验）", bool(other_token) and bool(other_id))

    seller_token, seller_id, cert = make_student(base, admin_token, suffix)
    check("学生卖家认证通过", bool(seller_token) and bool(seller_id))

    # 学生认证通过会给自己发一条审核通知，正好用来验证「认证通知」触发点
    _, seller_notices = call(base, "GET", "/api/notice/list?pageNum=1&pageSize=20&type=2",
                             token=seller_token)
    audit_rows = (seller_notices.get("data") or {}).get("records") or []
    check("学生认证通过触发审核通知", len(audit_rows) >= 1 and audit_rows[0]["type"] == 2,
          audit_rows[0]["title"] if audit_rows else "无通知")

    _, cats = call(base, "GET", "/api/category/list")
    category_id = (cats.get("data") or [{}])[0].get("id")

    _, created = call(base, "POST", "/api/product", token=seller_token,
                      body={"title": "模块六聊天测试品" + suffix, "categoryId": category_id,
                            "price": 66.5, "originalPrice": 199,
                            "description": "用于聊天与通知冒烟测试的商品",
                            "images": [cert]})
    product_id = created.get("data")
    check("卖家发布商品成功", bool(product_id), product_id)

    _, audit_page = call(base, "GET", "/api/product/audit/page?pageNum=1&pageSize=50&status=0",
                         token=admin_token)
    audit_item = next((r for r in ((audit_page.get("data") or {}).get("records") or [])
                       if r.get("id") == product_id), None)
    if audit_item:
        call(base, "PUT", "/api/product/%s/audit" % product_id, token=admin_token,
             body={"status": 1})
    _, product_detail = call(base, "GET", "/api/product/%s" % product_id, token=seller_token)
    check("商品审核通过并上架", (product_detail.get("data") or {}).get("status") == 1,
          (product_detail.get("data") or {}).get("status"))

    _, admin_notices = call(base, "GET", "/api/notice/list?pageNum=1&pageSize=20", token=seller_token)

    # ------------------------------------------------------------------
    print("\n== 2. 权限校验 ==")
    _, res = call(base, "GET", "/api/chat/session/list")
    check("未登录查看会话列表 401", res.get("code") == 401, res.get("msg"))
    _, res = call(base, "GET", "/api/notice/list")
    check("未登录查看通知列表 401", res.get("code") == 401, res.get("msg"))
    _, res = call(base, "POST", "/api/chat/session", token=cs_token,
                  body={"sellerId": seller_id, "productId": product_id})
    check("客服创建会话 403（只读）", res.get("code") == 403, res.get("msg"))
    _, res = call(base, "GET", "/api/chat/admin/session/list?pageNum=1&pageSize=5", token=buyer_token)
    check("普通用户访问客服工作台 403", res.get("code") == 403, res.get("msg"))
    _, res = call(base, "GET", "/api/chat/admin/session/list?pageNum=1&pageSize=5", token=cs_token)
    check("客服可访问客服工作台（只读）", res.get("code") == 200, res.get("msg"))

    # ------------------------------------------------------------------
    print("\n== 3. 会话创建与幂等 ==")
    _, res = call(base, "POST", "/api/chat/session", token=buyer_token,
                  body={"sellerId": seller_id, "productId": product_id})
    session = res.get("data") or {}
    session_id = session.get("id")
    check("买家创建会话成功", bool(session_id), session_id)
    check("会话返回对方昵称", bool(session.get("peerNickname")), session.get("peerNickname"))
    check("会话标记我为买家", session.get("buyer") is True, session.get("buyer"))
    check("会话带关联商品标题", bool(session.get("productTitle")), session.get("productTitle"))

    _, res2 = call(base, "POST", "/api/chat/session", token=buyer_token,
                   body={"sellerId": seller_id, "productId": product_id})
    check("重复创建返回同一会话（唯一索引生效）", (res2.get("data") or {}).get("id") == session_id,
          (res2.get("data") or {}).get("id"))

    # 卖家从订单侧「联系买家」：角色反转，但必须复用同一个会话而不是新建反向会话
    _, res3 = call(base, "POST", "/api/chat/session", token=seller_token,
                   body={"sellerId": buyer_id, "productId": product_id})
    check("卖家反向发起复用同一会话", (res3.get("data") or {}).get("id") == session_id,
          (res3.get("data") or {}).get("id"))

    _, res = call(base, "POST", "/api/chat/session", token=buyer_token,
                  body={"sellerId": buyer_id, "productId": product_id})
    check("不能和自己聊天", res.get("code") == 500, res.get("msg"))
    _, res = call(base, "POST", "/api/chat/session", token=buyer_token,
                  body={"sellerId": other_id, "productId": product_id})
    check("商品不属于该卖家时拒绝", res.get("code") == 500, res.get("msg"))

    _, detail = call(base, "GET", "/api/chat/session/%s" % session_id, token=buyer_token)
    first_msgs = (detail.get("data") or {}).get("messages") or []
    check("首次创建自动带 1 条商品卡片消息", len(first_msgs) == 1 and first_msgs[0]["msgType"] == 4,
          len(first_msgs))
    check("商品卡片解析出标题", bool((first_msgs[0].get("card") or {}).get("title")),
          (first_msgs[0].get("card") or {}).get("title"))

    _, res = call(base, "GET", "/api/chat/session/%s" % session_id, token=other_token)
    check("非参与者查看会话被拒绝", res.get("code") == 500, res.get("msg"))

    # ------------------------------------------------------------------
    print("\n== 4. 发消息与未读数 ==")
    _, res = call(base, "POST", "/api/chat/message", token=buyer_token,
                  body={"sessionId": session_id, "msgType": 1, "content": "同学你好，这本教材还有吗？"})
    msg = res.get("data") or {}
    check("买家发送文本消息成功", res.get("code") == 200 and bool(msg.get("id")), res.get("msg"))
    check("消息标记 self=true", msg.get("self") is True)
    check("消息标记 fromBuyer=true", msg.get("fromBuyer") is True)

    _, seller_unread = call(base, "GET", "/api/chat/unread", token=seller_token)
    # 首次建会话时自动发出的商品卡片也是买家发的，所以此时未读是 2（卡片 + 文本）
    check("卖家未读消息数累加（商品卡片 + 文本）",
          (seller_unread.get("data") or {}).get("chatUnread") == 2,
          (seller_unread.get("data") or {}).get("chatUnread"))
    check("卖家有未读会话 1 个", (seller_unread.get("data") or {}).get("unreadSessions") == 1)

    _, seller_sessions = call(base, "GET", "/api/chat/session/list?pageNum=1&pageSize=10",
                              token=seller_token)
    rows = (seller_sessions.get("data") or {}).get("records") or []
    target_row = next((r for r in rows if r.get("id") == session_id), None)
    check("卖家会话列表出现该会话", target_row is not None)
    check("卖家会话未读数为 2", target_row and target_row.get("unread") == 2,
          target_row.get("unread") if target_row else None)
    check("卖家会话列表摘要是最后一条消息",
          target_row and "教材" in (target_row.get("lastMessage") or ""),
          target_row.get("lastMessage") if target_row else None)
    check("卖家视角 buyer=false", target_row and target_row.get("buyer") is False)

    _, res = call(base, "PUT", "/api/chat/message/read/%s" % session_id, token=seller_token)
    check("卖家标记会话已读", res.get("code") == 200, res.get("msg"))
    _, seller_unread2 = call(base, "GET", "/api/chat/unread", token=seller_token)
    check("已读后未读消息数归零", (seller_unread2.get("data") or {}).get("chatUnread") == 0,
          (seller_unread2.get("data") or {}).get("chatUnread"))

    _, seller_view = call(base, "GET", "/api/chat/message/%s?size=10" % session_id, token=seller_token)
    seller_msgs = seller_view.get("data") or []
    check("卖家能看到买家发的消息", any(m["content"].find("教材") >= 0 for m in seller_msgs))
    check("卖家视角该消息 self=false",
          all(m.get("self") is False for m in seller_msgs if m["msgType"] != 3))

    # 图片消息
    _, res = call(base, "POST", "/api/chat/message", token=seller_token,
                  body={"sessionId": session_id, "msgType": 2, "content": "/uploads/demo.png"})
    check("发送图片消息成功", res.get("code") == 200 and (res.get("data") or {}).get("msgType") == 2)
    check("图片消息类型名称正确", (res.get("data") or {}).get("msgTypeLabel") == "图片",
          (res.get("data") or {}).get("msgTypeLabel"))

    # 商品卡片消息（客户端只传商品ID，服务端重新生成卡片内容，避免伪造）
    _, res = call(base, "POST", "/api/chat/message", token=buyer_token,
                  body={"sessionId": session_id, "msgType": 4, "content": str(product_id)})
    card = ((res.get("data") or {}).get("card") or {})
    check("发送商品卡片成功", res.get("code") == 200 and card.get("productId") == product_id,
          card.get("productId"))
    check("商品卡片含价格与状态", card.get("price") is not None and bool(card.get("statusLabel")),
          card.get("statusLabel"))

    # 系统消息不允许手动发送
    _, res = call(base, "POST", "/api/chat/message", token=buyer_token,
                  body={"sessionId": session_id, "msgType": 3, "content": "伪造系统消息"})
    check("客户端不能手动发系统消息", res.get("code") == 500, res.get("msg"))
    _, res = call(base, "POST", "/api/chat/message", token=buyer_token,
                  body={"sessionId": session_id, "msgType": 1, "content": ""})
    check("空消息被参数校验拦截", res.get("code") == 400, res.get("msg"))
    _, res = call(base, "POST", "/api/chat/message", token=other_token,
                  body={"sessionId": session_id, "msgType": 1, "content": "我来插一句"})
    check("非参与者发消息被拒绝", res.get("code") == 500, res.get("msg"))

    # ------------------------------------------------------------------
    print("\n== 5. 历史消息游标分页 ==")
    # 先堆够 50 条以上，才能验证会话详情的 hasMore（首屏固定取 50 条）
    for i in range(50):
        call(base, "POST", "/api/chat/message", token=buyer_token,
             body={"sessionId": session_id, "msgType": 1, "content": "连续消息 %02d" % i})
    _, page1 = call(base, "GET", "/api/chat/message/%s?size=10" % session_id, token=buyer_token)
    rows1 = page1.get("data") or []
    check("第一页返回 10 条", len(rows1) == 10, len(rows1))
    check("第一页按时间倒序", rows1[0]["id"] > rows1[-1]["id"], (rows1[0]["id"], rows1[-1]["id"]))
    check("第一页最新一条是最后发送的", rows1[0]["content"] == "连续消息 49", rows1[0]["content"])

    cursor = rows1[-1]["id"]
    _, page2 = call(base, "GET", "/api/chat/message/%s?size=10&cursor=%s" % (session_id, cursor),
                    token=buyer_token)
    rows2 = page2.get("data") or []
    check("游标分页返回更早的 10 条", len(rows2) == 10, len(rows2))
    ids1 = {r["id"] for r in rows1}
    ids2 = {r["id"] for r in rows2}
    check("两页消息无重复", not (ids1 & ids2), len(ids1 & ids2))
    check("第二页全部早于游标", all(r["id"] < cursor for r in rows2))

    _, detail2 = call(base, "GET", "/api/chat/session/%s" % session_id, token=buyer_token)
    detail_data = detail2.get("data") or {}
    check("会话详情首屏只返回 50 条", len(detail_data.get("messages") or []) == 50,
          len(detail_data.get("messages") or []))
    check("超过 50 条时 hasMore=true", detail_data.get("hasMore") is True, detail_data.get("hasMore"))
    msgs = detail_data.get("messages") or []
    check("会话详情消息按时间正序", msgs[0]["id"] < msgs[-1]["id"], (msgs[0]["id"], msgs[-1]["id"]))

    # ------------------------------------------------------------------
    print("\n== 6. 删除会话（单向） ==")
    _, other_session = call(base, "POST", "/api/chat/session", token=buyer_token,
                            body={"sellerId": seller_id})
    other_session_id = (other_session.get("data") or {}).get("id")
    check("创建无商品的通用会话成功", bool(other_session_id), other_session_id)

    _, res = call(base, "DELETE", "/api/chat/session/%s" % other_session_id, token=buyer_token)
    check("买家删除会话成功", res.get("code") == 200, res.get("msg"))
    _, buyer_list = call(base, "GET", "/api/chat/session/list?pageNum=1&pageSize=20", token=buyer_token)
    buyer_ids = [r["id"] for r in ((buyer_list.get("data") or {}).get("records") or [])]
    check("买家列表已看不到被删会话", other_session_id not in buyer_ids)
    _, seller_list = call(base, "GET", "/api/chat/session/list?pageNum=1&pageSize=20", token=seller_token)
    seller_ids = [r["id"] for r in ((seller_list.get("data") or {}).get("records") or [])]
    check("对方列表仍能看到该会话", other_session_id in seller_ids)

    call(base, "POST", "/api/chat/message", token=seller_token,
         body={"sessionId": other_session_id, "msgType": 1, "content": "新消息应该让会话回到列表"})
    _, buyer_list2 = call(base, "GET", "/api/chat/session/list?pageNum=1&pageSize=20", token=buyer_token)
    buyer_ids2 = [r["id"] for r in ((buyer_list2.get("data") or {}).get("records") or [])]
    check("收到新消息后会话重新出现在列表", other_session_id in buyer_ids2)

    # ------------------------------------------------------------------
    print("\n== 7. 订单触发的通知与系统消息 ==")
    _, addr = call(base, "POST", "/api/address", token=buyer_token,
                   body={"receiverName": "模块六买家", "phone": "13800000006",
                         "address": "北华大学 6 号楼 601", "isDefault": 1})
    address_id = addr.get("data")
    _, order_res = call(base, "POST", "/api/order/create", token=buyer_token,
                        body={"productId": product_id, "addressId": address_id})
    order_no = (order_res.get("data") or {}).get("orderNo")
    check("买家下单成功", bool(order_no), order_res.get("msg"))

    _, seller_notices = call(base, "GET", "/api/notice/list?pageNum=1&pageSize=20&type=1",
                             token=seller_token)
    rows = (seller_notices.get("data") or {}).get("records") or []
    check("下单后卖家收到订单通知", any("新订单" in r["title"] for r in rows),
          [r["title"] for r in rows][:3])
    order_notice = next((r for r in rows if "新订单" in r["title"]), None)
    check("订单通知带 relatedNo（可跳订单详情）",
          order_notice and order_notice.get("relatedNo") == order_no,
          order_notice.get("relatedNo") if order_notice else None)
    check("订单通知 target=order", order_notice and order_notice.get("target") == "order",
          order_notice.get("target") if order_notice else None)

    _, sys_msgs = call(base, "GET", "/api/chat/message/%s?size=10" % session_id, token=buyer_token)
    sys_rows = [m for m in (sys_msgs.get("data") or []) if m["msgType"] == 3]
    check("下单后会话里出现系统消息", any("下单" in m["content"] for m in sys_rows),
          [m["content"] for m in sys_rows][:2])

    call(base, "POST", "/api/order/%s/pay" % order_no, token=buyer_token)
    _, seller_notices = call(base, "GET", "/api/notice/list?pageNum=1&pageSize=20&type=1",
                             token=seller_token)
    check("付款后卖家收到付款通知",
          any("已付款" in r["title"] for r in ((seller_notices.get("data") or {}).get("records") or [])))

    call(base, "POST", "/api/order/%s/ship" % order_no, token=seller_token)
    _, buyer_notices = call(base, "GET", "/api/notice/list?pageNum=1&pageSize=20&type=1",
                            token=buyer_token)
    check("发货后买家收到发货通知",
          any("已发货" in r["title"] for r in ((buyer_notices.get("data") or {}).get("records") or [])))

    call(base, "POST", "/api/order/%s/receive" % order_no, token=buyer_token)
    _, seller_notices2 = call(base, "GET", "/api/notice/list?pageNum=1&pageSize=20&type=1",
                              token=seller_token)
    check("收货后卖家收到完成通知",
          any("确认收货" in r["title"] for r in ((seller_notices2.get("data") or {}).get("records") or [])))

    # 订单卡片消息：客户端只传订单号，服务端校验归属后生成卡片
    _, res = call(base, "POST", "/api/chat/message", token=buyer_token,
                  body={"sessionId": session_id, "msgType": 5, "content": order_no})
    order_card = ((res.get("data") or {}).get("card") or {})
    check("发送订单卡片成功", res.get("code") == 200 and order_card.get("orderNo") == order_no,
          order_card.get("orderNo"))
    check("订单卡片含状态文案", order_card.get("statusLabel") == "已完成", order_card.get("statusLabel"))
    _, res = call(base, "POST", "/api/chat/message", token=other_token,
                  body={"sessionId": other_session_id, "msgType": 5, "content": order_no})
    check("不能发送不属于自己的订单卡片", res.get("code") == 500, res.get("msg"))

    # ------------------------------------------------------------------
    print("\n== 8. 通知列表与已读 ==")
    _, all_notices = call(base, "GET", "/api/notice/list?pageNum=1&pageSize=50", token=buyer_token)
    notice_rows = (all_notices.get("data") or {}).get("records") or []
    check("买家通知列表非空", len(notice_rows) >= 1, len(notice_rows))
    unread_flags = [r["isRead"] for r in notice_rows]
    check("未读通知排在前面", unread_flags == sorted(unread_flags), unread_flags)

    _, unread_only = call(base, "GET", "/api/notice/list?pageNum=1&pageSize=50&onlyUnread=true",
                          token=buyer_token)
    check("onlyUnread 只返回未读",
          all(r["isRead"] == 0 for r in ((unread_only.get("data") or {}).get("records") or [])))

    target_notice = notice_rows[0]
    _, res = call(base, "PUT", "/api/notice/read/%s" % target_notice["id"], token=buyer_token)
    check("标记单条已读成功", res.get("code") == 200, res.get("msg"))
    _, notice_detail = call(base, "GET", "/api/notice/%s" % target_notice["id"], token=buyer_token)
    check("通知详情返回 isRead=1", (notice_detail.get("data") or {}).get("isRead") == 1,
          (notice_detail.get("data") or {}).get("isRead"))

    _, res = call(base, "GET", "/api/notice/%s" % target_notice["id"], token=other_token)
    check("查看他人通知被拒绝", res.get("code") == 500, res.get("msg"))

    _, res = call(base, "PUT", "/api/notice/read-all", token=buyer_token)
    check("全部已读成功", res.get("code") == 200, res.get("msg"))
    summary = notice_unread_of(base, buyer_token)
    check("全部已读后未读通知数归零", summary.get("noticeUnread") == 0, summary.get("noticeUnread"))

    # ------------------------------------------------------------------
    print("\n== 9. 系统公告 ==")
    title = "模块六公告" + suffix
    _, res = call(base, "POST", "/api/notice/admin/publish", token=admin_token,
                  body={"title": title, "content": "这是一条用于冒烟测试的系统公告，收到即代表链路通畅。",
                        "scope": 1})
    check("管理员发布全体公告成功", res.get("code") == 200, res.get("msg"))

    _, buyer_notices = call(base, "GET", "/api/notice/list?pageNum=1&pageSize=50&type=3",
                            token=buyer_token)
    check("买家收到系统公告",
          any(r["title"] == title for r in ((buyer_notices.get("data") or {}).get("records") or [])))
    _, seller_notices = call(base, "GET", "/api/notice/list?pageNum=1&pageSize=50&type=3",
                             token=seller_token)
    check("卖家也收到系统公告",
          any(r["title"] == title for r in ((seller_notices.get("data") or {}).get("records") or [])))

    _, admin_list = call(base, "GET", "/api/notice/admin/page?pageNum=1&pageSize=10", token=admin_token)
    groups = (admin_list.get("data") or {}).get("records") or []
    group = next((g for g in groups if g["title"] == title), None)
    check("公告列表出现该次发布", group is not None)
    check("公告接收人数与用户数一致", group and group["receiverCount"] >= 3,
          group.get("receiverCount") if group else None)

    _, res = call(base, "GET", "/api/notice/admin/page?pageNum=1&pageSize=10", token=cs_token)
    check("客服可查看公告列表", res.get("code") == 200)
    _, res = call(base, "POST", "/api/notice/admin/publish", token=cs_token,
                  body={"title": "客服越权公告", "content": "客服不应该能发布公告", "scope": 1})
    check("客服发布公告 403", res.get("code") == 403, res.get("msg"))

    _, res = call(base, "POST", "/api/notice/admin/publish", token=admin_token,
                  body={"title": "定向公告" + suffix, "content": "只发给指定用户的公告内容",
                        "scope": 2, "userIds": [other_id]})
    check("发布定向公告成功", res.get("code") == 200, res.get("msg"))
    _, other_notices = call(base, "GET", "/api/notice/list?pageNum=1&pageSize=50&type=3",
                            token=other_token)
    check("指定用户收到定向公告",
          any(r["title"] == "定向公告" + suffix
              for r in ((other_notices.get("data") or {}).get("records") or [])))
    _, buyer_notices2 = call(base, "GET", "/api/notice/list?pageNum=1&pageSize=50&type=3",
                             token=buyer_token)
    check("非指定用户收不到定向公告",
          not any(r["title"] == "定向公告" + suffix
                  for r in ((buyer_notices2.get("data") or {}).get("records") or [])))

    _, res = call(base, "POST", "/api/notice/admin/publish", token=admin_token,
                  body={"title": "", "content": "标题为空", "scope": 1})
    check("公告标题为空被参数校验拦截", res.get("code") == 400, res.get("msg"))
    _, res = call(base, "POST", "/api/notice/admin/publish", token=admin_token,
                  body={"title": "空范围", "content": "没有指定用户", "scope": 2, "userIds": []})
    check("定向公告未选用户被拒绝", res.get("code") == 500, res.get("msg"))

    _, res = call(base, "DELETE", "/api/notice/admin/%s" % group["id"], token=cs_token)
    check("客服删除公告 403", res.get("code") == 403, res.get("msg"))
    _, res = call(base, "DELETE", "/api/notice/admin/%s" % group["id"], token=admin_token)
    check("管理员删除公告成功", res.get("code") == 200, res.get("msg"))
    _, buyer_notices3 = call(base, "GET", "/api/notice/list?pageNum=1&pageSize=50&type=3",
                             token=buyer_token)
    check("删除后用户通知里不再有该公告",
          not any(r["title"] == title
                  for r in ((buyer_notices3.get("data") or {}).get("records") or [])))

    # ------------------------------------------------------------------
    print("\n== 10. 客服工作台 ==")
    _, cs_list = call(base, "GET", "/api/chat/admin/session/list?pageNum=1&pageSize=20",
                      token=cs_token)
    cs_rows = (cs_list.get("data") or {}).get("records") or []
    check("客服能看到全部会话", len(cs_rows) >= 1, len(cs_rows))
    row = next((r for r in cs_rows if r["id"] == session_id), None)
    check("客服视图含买家昵称", row and bool(row.get("buyerNickname")), row.get("buyerNickname") if row else None)
    check("客服视图含卖家昵称", row and bool(row.get("sellerNickname")), row.get("sellerNickname") if row else None)
    check("客服视图带关联商品", row and row.get("productTitle") == "模块六聊天测试品" + suffix,
          row.get("productTitle") if row else None)

    _, me = call(base, "GET", "/api/auth/me", token=seller_token)
    seller_nickname = (me.get("data") or {}).get("nickname") or ""
    check("拿到卖家昵称用于搜索", bool(seller_nickname), seller_nickname)

    _, search = call(base, "GET", "/api/chat/admin/session/list?pageNum=1&pageSize=20&keyword="
                     + quote(seller_nickname), token=cs_token)
    check("按卖家昵称搜索命中", ((search.get("data") or {}).get("total") or 0) >= 1,
          (search.get("data") or {}).get("total"))
    _, search_empty = call(base, "GET",
                           "/api/chat/admin/session/list?pageNum=1&pageSize=20&keyword=不存在的昵称xyz",
                           token=cs_token)
    check("昵称无匹配时返回空页", ((search_empty.get("data") or {}).get("total") or 0) == 0,
          (search_empty.get("data") or {}).get("total"))

    _, cs_msgs = call(base, "GET", "/api/chat/admin/message/%s?size=100" % session_id, token=cs_token)
    cs_messages = cs_msgs.get("data") or []
    check("客服能查看完整聊天记录", len(cs_messages) >= 10, len(cs_messages))
    check("客服视图 self 恒为 false", all(m.get("self") is False for m in cs_messages))
    check("客服视图用 fromBuyer 区分买卖家",
          any(m.get("fromBuyer") is True for m in cs_messages)
          and any(m.get("fromBuyer") is False for m in cs_messages))

    _, res = call(base, "POST", "/api/chat/message", token=cs_token,
                  body={"sessionId": session_id, "msgType": 1, "content": "客服不应该能发消息"})
    check("客服不能主动发消息 403", res.get("code") == 403, res.get("msg"))

    # ------------------------------------------------------------------
    print("\n== 11. 落库校验 ==")
    count = mysql_query("SELECT COUNT(*) FROM chat_session WHERE buyer_id = %s AND seller_id = %s;"
                        % (buyer_id, seller_id))
    if count is None:
        print("  [SKIP] 未找到 mysql 客户端，跳过落库校验")
    else:
        check("会话唯一索引保证同对用户只有 2 个会话（含1个通用会话）", int(count) == 2, count)
        msg_count = mysql_query("SELECT COUNT(*) FROM chat_message WHERE session_id = %s;" % session_id)
        check("消息已落库", int(msg_count) >= 28, msg_count)
        notice_count = mysql_query("SELECT COUNT(*) FROM notice WHERE user_id = %s;" % buyer_id)
        check("通知已落库", int(notice_count) >= 1, notice_count)

    # ------------------------------------------------------------------
    print("\n== 12. 数据清理 ==")
    mysql_exec("DELETE FROM chat_message WHERE session_id IN "
               "(SELECT id FROM chat_session WHERE buyer_id = %s OR seller_id = %s);"
               % (buyer_id, seller_id))
    mysql_exec("DELETE FROM chat_session WHERE buyer_id = %s OR seller_id = %s;" % (buyer_id, seller_id))
    mysql_exec("DELETE FROM notice WHERE user_id IN (%s, %s, %s) OR title LIKE '%%%s%%';"
               % (buyer_id, other_id, seller_id, suffix))
    mysql_exec("DELETE FROM order_log WHERE order_id IN "
               "(SELECT id FROM trade_order WHERE product_id = %s);" % product_id)
    mysql_exec("DELETE FROM trade_order WHERE product_id = %s;" % product_id)
    mysql_exec("DELETE FROM address WHERE user_id = %s;" % buyer_id)
    mysql_exec("DELETE FROM product_image WHERE product_id = %s;" % product_id)
    mysql_exec("DELETE FROM product WHERE id = %s;" % product_id)
    mysql_exec("DELETE FROM student_auth WHERE user_id = %s;" % seller_id)
    mysql_exec("DELETE FROM sys_user WHERE id IN (%s, %s, %s);" % (buyer_id, other_id, seller_id))

    left = mysql_query("SELECT COUNT(*) FROM chat_session WHERE buyer_id = %s OR seller_id = %s;"
                       % (buyer_id, seller_id))
    if left is not None:
        check("测试会话已清理", int(left) == 0, left)
    left_notice = mysql_query("SELECT COUNT(*) FROM notice WHERE user_id IN (%s, %s, %s);"
                              % (buyer_id, other_id, seller_id))
    if left_notice is not None:
        check("测试通知已清理", int(left_notice) == 0, left_notice)

    # ------------------------------------------------------------------
    total = len(RESULTS)
    print("\n" + "=" * 56)
    print("结果：%d/%d 通过" % (PASSED, total))
    if PASSED == total:
        print("全部通过")
    else:
        print("失败用例：")
        for name, ok, detail in RESULTS:
            if not ok:
                print("  - " + name + (("  -> " + str(detail)) if detail else ""))
    print("=" * 56)
    return 0 if PASSED == total else 1


if __name__ == "__main__":
    sys.exit(main())
