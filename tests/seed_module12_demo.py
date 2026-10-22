#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""模块12 管理端截图/演示用数据播种脚本。

用途：首页配置页的「热门推荐」「新品推荐」预览、以及小程序首页的热门/新品区块，
在干净数据下只有测试遗留的临时商品，截图会很乱、也没有说服力。
本脚本造一个认证学生并发布 6 件真实感的商品（全部审核上架），
每件配一张真彩色块封面，再补一批浏览/收藏数，让「按热度排序」有一个看得出来的顺序。

**不清理**，生成的 ID 写入 tests/.module12_demo.json，供 --clean 清理。

用法：
    python tests/seed_module12_demo.py            # 播种
    python tests/seed_module12_demo.py --tidy     # 收起冒烟测试遗留的在售空壳商品
    python tests/seed_module12_demo.py --clean    # 按上次的 ID 清理
"""
import json
import os
import sys
import uuid
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from smoke_module12 import (call, login_user, make_student, mysql_query,  # noqa: E402
                            publish, audit_pass)
import demo_image  # noqa: E402

BASE = "http://127.0.0.1:8080"
STATE = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".module12_demo.json")

# (标题, 分类名, 价格, 原价, 浏览, 收藏) —— 数量级刻意拉开，热门榜才有明显顺序
DEMO_PRODUCTS = [
    ("九成新山地自行车 送车锁", "交通工具", 380.0, 1299.0, 268, 42),
    ("考研数学全套教材 无笔记", "教材书籍", 45.0, 168.0, 512, 96),
    ("蓝牙降噪耳机 续航30小时", "数码电子", 199.0, 499.0, 431, 71),
    ("宿舍护眼台灯 三档调光", "生活用品", 35.0, 89.0, 187, 23),
    ("羽毛球拍一对 送三只球", "运动户外", 60.0, 159.0, 96, 11),
    ("帆布双肩包 通勤上课都能用", "服饰鞋包", 48.0, 129.0, 74, 9),
]


def upload_png(base, token, png):
    """上传任意 PNG 字节，返回相对地址。smoke_module12.upload 只发 1×1 透明图，
    截图里就是一片空白，演示数据必须换成肉眼可见的色块封面。"""
    boundary = "----demo" + uuid.uuid4().hex
    body = (
        ("--" + boundary + "\r\n"
         'Content-Disposition: form-data; name="file"; filename="cover.png"\r\n'
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
        return json.loads(resp.read().decode("utf-8")).get("data") or ""


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
    if not ids:
        print("没有可清理的数据（未找到 .module12_demo.json）")
        return
    seller_ids = ",".join(str(i) for i in ids)
    mysql_query("DELETE FROM product_image WHERE product_id IN "
                "(SELECT id FROM product WHERE seller_id IN (%s));" % seller_ids)
    mysql_query("DELETE FROM product WHERE seller_id IN (%s);" % seller_ids)
    mysql_query("DELETE FROM student_auth WHERE user_id IN (%s);" % seller_ids)
    mysql_query("DELETE FROM sys_operation_log WHERE operator_id IN (%s);" % seller_ids)
    mysql_query("DELETE FROM notice WHERE user_id IN (%s);" % seller_ids)
    mysql_query("DELETE FROM sys_user WHERE id IN (%s);" % seller_ids)
    print("已清理 %d 个演示账号及其商品" % len(ids))
    if os.path.exists(STATE):
        os.remove(STATE)


def tidy():
    """把冒烟测试遗留的「额度测试商品NN」在售商品收起（deleted=1）。

    这些是模块4 发布额度测试留下的空壳商品（浏览/收藏都是 0，封面是 1×1 透明图），
    在售商品一共只有十几件时，它们会挤满小程序的「最新发布」区块，
    演示截图里出现「额度测试商品04」很不专业。
    只动标题命中测试标记的在售商品，软删除可回滚，不动演示商品。
    """
    out = mysql_query(
        "SELECT id, title FROM product WHERE deleted = 0 AND status = 1 "
        "AND (title LIKE '额度测试%' OR title LIKE '%测试商品%' OR title LIKE '模块%') "
        "ORDER BY id;")
    if not out:
        print("没有需要收起的测试遗留商品")
        return
    ids = [line.split("\t")[0] for line in out.splitlines() if line.strip()]
    mysql_query("UPDATE product SET deleted = 1 WHERE id IN (%s);" % ",".join(ids))
    print("已收起 %d 件测试遗留商品：%s" % (len(ids), ",".join(ids)))


def category_map():
    _, res = call(BASE, "GET", "/api/category/list")
    return {c["name"]: c["id"] for c in (res.get("data") or [])}


def main():
    if "--clean" in sys.argv:
        clean()
        return
    if "--tidy" in sys.argv:
        tidy()
        return

    _, admin = call(BASE, "POST", "/api/auth/admin-login",
                    body={"username": "admin", "password": "123456"})
    admin_token = (admin.get("data") or {}).get("token")
    if not admin_token:
        print("管理员登录失败，请确认后端已启动")
        sys.exit(1)

    cats = category_map()
    suffix = "demo12"
    token, user_id, cert = make_student(BASE, admin_token, suffix, "演示卖家 · 阿哲")
    call(BASE, "PUT", "/api/user/profile", token=token,
         body={"nickname": "演示卖家 · 阿哲", "bio": "大四毕业清仓，都是自用在用的东西"})

    created = []
    for idx, (title, cat_name, price, origin, views, favs) in enumerate(DEMO_PRODUCTS):
        cat_id = cats.get(cat_name)
        if not cat_id:
            print("跳过（分类不存在）：%s" % cat_name)
            continue
        pid = publish(BASE, token, cat_id, title, price, cert)
        if not pid:
            print("发布失败：%s" % title)
            continue
        # 封面换成真彩色块：发布时用的是认证图，六件商品会长得一模一样
        cover = upload_png(BASE, token, demo_image.make_cover(idx))
        if cover:
            mysql_query("UPDATE product_image SET image_url = '%s' "
                        "WHERE product_id = %s;" % (cover, pid))
        # 原价与浏览量/收藏量直接写库：这两个字段没有面向用户的写接口，
        # 演示数据只能这样补，属于「造数据」而不是「改业务」
        mysql_query("UPDATE product SET original_price = %s, view_count = %d, "
                    "favorite_count = %d WHERE id = %s;" % (origin, views, favs, pid))
        audit_pass(BASE, admin_token, pid)
        created.append(pid)

    save_state({"userIds": [user_id], "productIds": created})
    print("已播种 %d 件商品（卖家 %s，认证学生）" % (len(created), user_id))
    print("商品ID：", created)
    print("清理：python tests/seed_module12_demo.py --clean")


if __name__ == "__main__":
    main()
