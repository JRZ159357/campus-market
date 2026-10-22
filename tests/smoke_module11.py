#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""模块11「系统管理收尾」接口冒烟测试（敏感词过滤 + 账号管理）。

用法：
    mvn spring-boot:run
    python tests/smoke_module11.py

覆盖流程：
    权限（未登录401 / 普通用户403 / 客服可读不可写 / 管理员全通）->
    敏感词CRUD（新增/重复/超长/非法类型/编辑/启停用/删除/分页筛选）->
    批量导入（识别数 / 新增 / 重复跳过 / 超长非法 / 空文本）->
    内存缓存（增删后立即生效，无需重启）+ 归一化（空格与符号规避）->
    商品发布联动（拦截拒绝 / 警告标记待复核 / 干净放行 / 通知）+
    编辑绕过防护 + 审核页筛选 + 审核后标记清除->
    评价联动（拦截拒绝 / 警告放行）->
    聊天联动（拦截替换 ** / 警告原样）->
    账号管理（新增/重复/非法角色/编辑/重置密码/禁用/删除/
             不能改自己角色、不能禁自己、不能删自己、至少留一个管理员）->
    操作日志（敏感词与账号写操作留痕）-> 数据清理

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

# 预置词，来自 sql/V11__sensitive_word_account.sql
WORD_BLOCK = "走私"      # type=1 违禁品 level=2 拦截
WORD_WARN = "加微信"      # type=3 广告   level=1 警告


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


def check(name, ok, detail=""):
    global PASSED
    RESULTS.append((name, ok, detail))
    if ok:
        PASSED += 1
    print(("  [PASS] " if ok else "  [FAIL] ") + name + (("  -> " + str(detail)) if detail else ""))
    return ok


# ============================================================
# 通用工具
# ============================================================

def login_user(base, code):
    _, res = call(base, "POST", "/api/auth/wx-login", body={"code": code})
    data = res.get("data") or {}
    return data.get("token"), data.get("userId"), res


def upload(base, token, png=None):
    boundary = "----smoke" + uuid.uuid4().hex
    if png is None:
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


def make_student(base, admin_token, suffix, nickname):
    """造一个认证学生（只有认证学生能发布商品）。"""
    token, user_id, _ = login_user(base, "m11s" + suffix)
    call(base, "PUT", "/api/user/profile", token=token, body={"nickname": nickname})
    cert = (upload(base, token).get("data") or "")
    call(base, "POST", "/api/student-auth", token=token,
         body={"studentNo": "M11" + suffix, "realName": "模块十一卖家",
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
    return mysql_query(sql)


def count_rows(table, where):
    rows = mysql_query("SELECT COUNT(*) FROM %s WHERE %s;" % (table, where))
    if not rows:
        return -1
    return int(rows.splitlines()[0].split("\t")[0])


def query_one(sql):
    rows = mysql_query(sql)
    if not rows:
        return None
    return rows.splitlines()[0].split("\t")


def first_category(base):
    _, res = call(base, "GET", "/api/category/list")
    items = res.get("data") or []
    return items[0]["id"] if items else None


def publish(base, token, category_id, title, desc, image):
    return call(base, "POST", "/api/product", token=token, body={
        "title": title,
        "categoryId": category_id,
        "price": 12.5,
        "originalPrice": 30,
        "description": desc,
        "images": [image],
    })


# ============================================================
# 主流程
# ============================================================

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="http://127.0.0.1:8080")
    args = parser.parse_args()
    base = args.base.rstrip("/")
    suffix = uuid.uuid4().hex[:6]
    marker = "M11" + suffix

    print("=" * 66)
    print("模块11 系统管理收尾（敏感词过滤 + 账号管理）接口冒烟测试  base =", base)
    print("=" * 66)

    created_word_ids = []
    created_account_ids = []
    created_product_ids = []

    # ---------------- 准备数据 ----------------
    print("\n== 准备测试账号 ==")
    _, admin_res = call(base, "POST", "/api/auth/login",
                        body={"username": "admin", "password": "123456"})
    admin_token = (admin_res.get("data") or {}).get("token")
    admin_id = (admin_res.get("data") or {}).get("userId")
    check("管理员登录成功", bool(admin_token), admin_res.get("msg"))

    user_token, user_id, _ = login_user(base, "m11u" + suffix)
    check("普通用户注册成功", bool(user_token), user_id)

    student_token, student_id, student_cert = make_student(
        base, admin_token, suffix, "敏感词卖家" + suffix)
    check("认证学生创建成功", bool(student_token), student_id)

    # 客服账号：用后台账号接口创建，顺带验证「新增账号 → 能登录」
    cs_username = "cs" + suffix
    _, cs_res = call(base, "POST", "/api/admin/account", token=admin_token, body={
        "username": cs_username, "password": "123456",
        "nickname": "敏感词客服" + suffix, "role": "customer_service"
    })
    cs_id = cs_res.get("data")
    if cs_id:
        created_account_ids.append(cs_id)
    check("后台客服账号创建成功", cs_res.get("code") == 200 and bool(cs_id), cs_res.get("msg"))

    _, cs_login = call(base, "POST", "/api/auth/login",
                       body={"username": cs_username, "password": "123456"})
    cs_token = (cs_login.get("data") or {}).get("token")
    check("新建账号可以用初始密码登录", bool(cs_token), cs_login.get("msg"))

    category_id = first_category(base)
    check("取到可用分类", bool(category_id), category_id)

    try:
        # ========================================================
        # 一、权限
        # ========================================================
        print("\n== 一、权限矩阵 ==")
        _, res = call(base, "GET", "/api/sensitive-word/page")
        check("未登录访问敏感词列表被拒（401）", res.get("code") == 401, res.get("code"))

        _, res = call(base, "GET", "/api/sensitive-word/page", token=user_token)
        check("普通用户访问敏感词列表被拒（403）", res.get("code") == 403, res.get("code"))

        _, res = call(base, "GET", "/api/sensitive-word/page?pageNum=1&pageSize=5",
                      token=cs_token)
        check("客服可以查看敏感词（只读）", res.get("code") == 200,
              (res.get("code"), res.get("msg")))

        _, res = call(base, "POST", "/api/sensitive-word", token=cs_token,
                      body={"word": "客服不该能加的词" + suffix})
        check("客服新增敏感词被拒（403）", res.get("code") == 403, res.get("code"))

        _, res = call(base, "GET", "/api/admin/account/page", token=cs_token)
        check("客服访问账号管理被拒（403）", res.get("code") == 403, res.get("code"))

        _, res = call(base, "GET", "/api/admin/account/page")
        check("未登录访问账号管理被拒（401）", res.get("code") == 401, res.get("code"))

        # ========================================================
        # 二、预置词与分页筛选
        # ========================================================
        print("\n== 二、预置词与筛选 ==")
        _, res = call(base, "GET", "/api/sensitive-word/page?pageNum=1&pageSize=200",
                      token=admin_token)
        data = res.get("data") or {}
        total = int(data.get("total") or 0)
        check("预置敏感词已加载（>=20 条）", total >= 20, total)
        words = [r.get("word") for r in (data.get("records") or [])]
        check("预置违禁品类词存在", WORD_BLOCK in words, words[:8])
        check("预置广告类词存在", WORD_WARN in words, words[:8])
        rec = next((r for r in (data.get("records") or []) if r.get("word") == WORD_BLOCK), {})
        check("词条带类型/等级中文名",
              bool(rec.get("typeLabel")) and bool(rec.get("levelLabel")),
              (rec.get("typeLabel"), rec.get("levelLabel")))
        check("违禁品=类型1且等级2（拦截）",
              rec.get("type") == 1 and rec.get("level") == 2,
              (rec.get("type"), rec.get("level")))

        _, res = call(base, "GET", "/api/sensitive-word/page?pageNum=1&pageSize=200&type=1",
                      token=admin_token)
        type1 = (res.get("data") or {}).get("records") or []
        check("按类型筛选只返回该类型",
              bool(type1) and all(r.get("type") == 1 for r in type1), len(type1))

        _, res = call(base, "GET", "/api/sensitive-word/page?pageNum=1&pageSize=200&keyword="
                      + quote(WORD_WARN, safe=""), token=admin_token)
        kw = (res.get("data") or {}).get("records") or []
        check("关键词筛选命中", len(kw) >= 1 and kw[0].get("word") == WORD_WARN, len(kw))

        # ========================================================
        # 三、敏感词 CRUD
        # ========================================================
        print("\n== 三、敏感词增删改 ==")
        new_word = "测试违禁词" + suffix
        _, res = call(base, "POST", "/api/sensitive-word", token=admin_token, body={
            "word": new_word, "type": 1, "level": 2, "status": 1
        })
        new_word_id = res.get("data")
        if new_word_id:
            created_word_ids.append(new_word_id)
        check("新增敏感词成功", res.get("code") == 200 and bool(new_word_id), res.get("msg"))

        _, res = call(base, "POST", "/api/sensitive-word", token=admin_token,
                      body={"word": new_word})
        check("重复新增被拒", res.get("code") != 200 and "已存在" in (res.get("msg") or ""),
              res.get("msg"))

        _, res = call(base, "POST", "/api/sensitive-word", token=admin_token, body={"word": "  "})
        check("空敏感词被拒", res.get("code") == 400, (res.get("code"), res.get("msg")))

        _, res = call(base, "POST", "/api/sensitive-word", token=admin_token,
                      body={"word": "长" * 51})
        check("超长敏感词被拒", res.get("code") == 400, (res.get("code"), res.get("msg")))

        _, res = call(base, "POST", "/api/sensitive-word", token=admin_token,
                      body={"word": "类型非法词" + suffix, "type": 9})
        check("非法类型被拒", res.get("code") == 400, (res.get("code"), res.get("msg")))

        _, res = call(base, "POST", "/api/sensitive-word", token=admin_token,
                      body={"word": "等级非法词" + suffix, "level": 3})
        check("非法等级被拒", res.get("code") == 400, (res.get("code"), res.get("msg")))

        # 编辑：改成「警告」级并换词
        edited_word = "测试警告词" + suffix
        _, res = call(base, "PUT", "/api/sensitive-word/%s" % new_word_id, token=admin_token,
                      body={"word": edited_word, "type": 3, "level": 1, "status": 1})
        check("编辑敏感词成功", res.get("code") == 200, res.get("msg"))

        row = query_one("SELECT word, type, level FROM sensitive_word WHERE id = %s;" % new_word_id)
        check("编辑结果已落库",
              row is not None and row[0] == edited_word and row[1] == "3" and row[2] == "1",
              row)

        _, res = call(base, "PUT", "/api/sensitive-word/%s" % new_word_id, token=admin_token,
                      body={"word": WORD_BLOCK, "type": 1, "level": 2})
        check("编辑成已存在的词被拒", res.get("code") != 200, res.get("msg"))

        _, res = call(base, "PUT", "/api/sensitive-word/99999999", token=admin_token,
                      body={"word": "不存在的词"})
        check("编辑不存在的词返回 404", res.get("code") == 404,
              (res.get("code"), res.get("msg")))

        # 启停用
        _, res = call(base, "PUT", "/api/sensitive-word/%s/status?status=0" % new_word_id,
                      token=admin_token)
        check("禁用敏感词成功", res.get("code") == 200, res.get("msg"))
        row = query_one("SELECT status FROM sensitive_word WHERE id = %s;" % new_word_id)
        check("禁用状态已落库", row is not None and row[0] == "0", row)

        _, res = call(base, "GET", "/api/sensitive-word/enabled", token=admin_token)
        enabled = res.get("data") or []
        check("禁用词已从生效词表中移除", edited_word not in enabled, len(enabled))

        _, res = call(base, "PUT", "/api/sensitive-word/%s/status?status=0" % new_word_id,
                      token=admin_token)
        check("重复禁用被拒", res.get("code") != 200, res.get("msg"))

        _, res = call(base, "PUT", "/api/sensitive-word/%s/status?status=1" % new_word_id,
                      token=admin_token)
        check("重新启用成功", res.get("code") == 200, res.get("msg"))
        _, res = call(base, "GET", "/api/sensitive-word/enabled", token=admin_token)
        check("启用词重新进入词表", edited_word in (res.get("data") or []), edited_word)

        # 删除：单独造一个词，删掉后应彻底消失
        del_word = "待删除词" + suffix
        _, res = call(base, "POST", "/api/sensitive-word", token=admin_token,
                      body={"word": del_word, "type": 4, "level": 1})
        del_id = res.get("data")
        check("删除用例的准备数据创建成功", bool(del_id), res.get("msg"))
        _, res = call(base, "DELETE", "/api/sensitive-word/%s" % del_id, token=admin_token)
        check("删除敏感词成功", res.get("code") == 200, res.get("msg"))
        row = query_one("SELECT COUNT(*) FROM sensitive_word WHERE word = '%s';" % del_word)
        check("删除为物理删除（行已不存在）", row is not None and row[0] == "0", row)
        _, res = call(base, "DELETE", "/api/sensitive-word/%s" % del_id, token=admin_token)
        check("重复删除返回 404", res.get("code") == 404,
              (res.get("code"), res.get("msg")))
        # 删掉后同一个词可以重新录入（物理删除才有的效果）
        _, res = call(base, "POST", "/api/sensitive-word", token=admin_token,
                      body={"word": del_word, "type": 4, "level": 1})
        check("删除后同名敏感词可重新添加", res.get("code") == 200, res.get("msg"))
        if res.get("data"):
            created_word_ids.append(res.get("data"))

        # ========================================================
        # 四、批量导入
        # ========================================================
        print("\n== 四、批量导入 ==")
        batch_words = ["批量词甲" + suffix, "批量词乙" + suffix, "批量词丙" + suffix]
        batch_text = "\n".join(batch_words + [WORD_BLOCK, "", "  ", batch_words[0]])
        _, res = call(base, "POST", "/api/sensitive-word/batch", token=admin_token, body={
            "text": batch_text, "type": 4, "level": 1
        })
        br = res.get("data") or {}
        check("批量导入成功", res.get("code") == 200, res.get("msg"))
        check("识别数按去空行去重计算", br.get("total") == 4, br)
        check("新增 3 条", br.get("inserted") == 3, br)
        check("已存在跳过 1 条", br.get("duplicated") == 1, br)
        check("重复明细回显包含已存在词", WORD_BLOCK in (br.get("duplicatedWords") or []),
              br.get("duplicatedWords"))

        _, res = call(base, "GET", "/api/sensitive-word/page?pageNum=1&pageSize=10&keyword="
                      + quote(batch_words[0], safe=""), token=admin_token)
        recs = (res.get("data") or {}).get("records") or []
        if recs:
            created_word_ids.append(recs[0]["id"])

        _, res = call(base, "POST", "/api/sensitive-word/batch", token=admin_token,
                      body={"text": "\n \n  \n"})
        check("批量导入空文本被拒", res.get("code") == 400, (res.get("code"), res.get("msg")))

        _, res = call(base, "POST", "/api/sensitive-word/batch", token=admin_token, body={
            "text": "正常长度词" + suffix + "\n" + "长" * 51, "type": 4, "level": 1
        })
        br = res.get("data") or {}
        check("超长词计入非法并回显", br.get("invalid") == 1 and br.get("invalidWords"),
              (br.get("invalid"), br.get("invalidWords")))

        # ========================================================
        # 五、商品发布联动
        # ========================================================
        print("\n== 五、商品发布联动 ==")
        # 5.1 拦截级：直接拒绝
        _, res = publish(base, student_token, category_id,
                         "走私手表低价出" + suffix, "全新未拆封", student_cert)
        check("命中断拦截级词被拒绝发布", res.get("code") == 400, res.get("code"))
        check("拦截提示包含命中的词", WORD_BLOCK in (res.get("msg") or ""), res.get("msg"))

        blocked_count = count_rows("product", "title LIKE '%%走私手表低价出%s%%'" % suffix)
        check("被拦截的商品没有落库", blocked_count == 0, blocked_count)

        # 5.2 拦截时给用户发通知
        time.sleep(0.3)
        _, res = call(base, "GET", "/api/notice/list?pageNum=1&pageSize=5", token=student_token)
        titles = [n.get("title") for n in ((res.get("data") or {}).get("records") or [])]
        check("拦截时给用户发了系统通知",
              any("拦截" in (t or "") for t in titles), titles)

        # 5.3 警告级：放行 + 标记待人工复核
        warn_title = "二手教材可加微信聊" + suffix
        _, res = publish(base, student_token, category_id,
                         warn_title, "九成新，有意者联系", student_cert)
        warn_pid = res.get("data")
        if warn_pid:
            created_product_ids.append(warn_pid)
        check("命中警告级词仍可发布", res.get("code") == 200 and bool(warn_pid), res.get("msg"))

        row = query_one("SELECT need_manual_review, sensitive_hit FROM product WHERE id = %s;"
                        % warn_pid) if warn_pid else None
        check("警告级商品被标记待人工复核",
              row is not None and row[0] == "1", row)
        check("命中的敏感词已记录", row is not None and WORD_WARN in (row[1] or ""), row)

        # 5.4 归一化：插空格与符号也能命中
        _, res = publish(base, student_token, category_id,
                         "走私  名表" + suffix, "支持验货", student_cert)
        check("带空格变体的违禁词仍被拦截", res.get("code") == 400, res.get("code"))

        _, res = publish(base, student_token, category_id,
                         "走*私名表" + suffix, "支持验货", student_cert)
        check("带符号变体的违禁词仍被拦截", res.get("code") == 400, res.get("code"))

        # 5.5 干净内容不受影响
        clean_title = "普通教材一本" + suffix
        _, res = publish(base, student_token, category_id,
                         clean_title, "无笔记，九成新", student_cert)
        clean_pid = res.get("data")
        if clean_pid:
            created_product_ids.append(clean_pid)
        check("干净内容正常发布", res.get("code") == 200 and bool(clean_pid), res.get("msg"))
        row = query_one("SELECT need_manual_review, sensitive_hit FROM product WHERE id = %s;"
                        % clean_pid) if clean_pid else None
        check("干净商品未被标记", row is not None and row[0] == "0" and row[1] == "NULL", row)

        # 5.6 编辑绕过防护：把干净商品标题改成违禁词
        _, res = call(base, "PUT", "/api/product/%s" % clean_pid, token=student_token, body={
            "title": "走私耳机" + suffix, "categoryId": category_id,
            "price": 15, "originalPrice": 40,
            "description": "编辑测试", "images": [student_cert]
        })
        check("编辑商品时同样拦截敏感词", res.get("code") == 400, res.get("code"))

        # 5.7 审核页「待人工复核」Tab 能筛出来
        _, res = call(base, "GET",
                      "/api/product/audit/page?pageNum=1&pageSize=20&status=0&needManualReview=1",
                      token=admin_token)
        recs = (res.get("data") or {}).get("records") or []
        check("待人工复核筛选命中该商品",
              any(r.get("id") == warn_pid for r in recs), len(recs))
        target = next((r for r in recs if r.get("id") == warn_pid), {})
        check("列表返回敏感词命中信息", WORD_WARN in (target.get("sensitiveHit") or ""),
              target.get("sensitiveHit"))

        # 5.8 审核通过后标记清除
        _, res = call(base, "PUT", "/api/product/%s/audit" % warn_pid, token=admin_token,
                      body={"status": 1})
        check("审核通过成功", res.get("code") == 200, res.get("msg"))
        row = query_one("SELECT need_manual_review FROM product WHERE id = %s;" % warn_pid)
        check("审核后复核标记已清除", row is not None and row[0] == "0", row)
        row = query_one("SELECT sensitive_hit FROM product WHERE id = %s;" % warn_pid)
        check("命中词仍保留作为追溯依据", row is not None and WORD_WARN in (row[0] or ""), row)

        # ========================================================
        # 六、评价联动
        # ========================================================
        print("\n== 六、评价联动 ==")
        order_no = "M11" + suffix.upper()
        mysql_exec(
            "INSERT INTO trade_order (order_no, buyer_id, seller_id, product_id, product_snapshot,"
            " price, status, pay_deadline, finish_time) VALUES ('%s', %s, %s, %s,"
            " '{\"title\":\"模块11评价测试\"}', 12.50, 3, NOW(), NOW());"
            % (order_no, user_id, student_id, warn_pid))
        row = query_one("SELECT id FROM trade_order WHERE order_no = '%s';" % order_no)
        order_id = int(row[0]) if row else None
        check("构造已完成订单成功", bool(order_id), order_id)

        if order_id:
            _, res = call(base, "POST", "/api/review", token=user_token, body={
                "orderId": order_id, "reviewType": 1,
                "descStars": 5, "shipStars": 5, "attitudeStars": 5,
                "content": "卖家是个傻逼，东西也不行"
            })
            check("评价命中拦截级词被拒绝", res.get("code") == 400, res.get("code"))
            check("评价拦截提示含命中词", "傻逼" in (res.get("msg") or ""), res.get("msg"))

            n = count_rows("review", "order_id = %s" % order_id)
            check("被拦截的评价没有落库", n == 0, n)

            _, res = call(base, "POST", "/api/review", token=user_token, body={
                "orderId": order_id, "reviewType": 1,
                "descStars": 5, "shipStars": 5, "attitudeStars": 5,
                "content": "东西不错，卖家说可以加微信沟通细节"
            })
            check("评价命中警告级词仍可提交", res.get("code") == 200, res.get("msg"))

        # ========================================================
        # 七、聊天联动
        # ========================================================
        print("\n== 七、聊天联动 ==")
        _, res = call(base, "POST", "/api/chat/session", token=user_token,
                      body={"sellerId": student_id, "productId": warn_pid})
        session_id = (res.get("data") or {}).get("id")
        check("创建聊天会话成功", bool(session_id), res.get("msg"))

        if session_id:
            _, res = call(base, "POST", "/api/chat/message", token=user_token,
                          body={"sessionId": session_id, "msgType": 1,
                                "content": "这个走私货还有吗"})
            msg = res.get("data") or {}
            content = msg.get("content") or ""
            check("聊天消息发送成功（不整条拒绝）", res.get("code") == 200, res.get("msg"))
            check("拦截级词被替换为 **", "**" in content and WORD_BLOCK not in content, content)

            _, res = call(base, "POST", "/api/chat/message", token=user_token,
                          body={"sessionId": session_id, "msgType": 1,
                                "content": "可以加微信详聊吗"})
            content = (res.get("data") or {}).get("content") or ""
            check("警告级词原样发送", WORD_WARN in content, content)

        # ========================================================
        # 八、账号管理
        # ========================================================
        print("\n== 八、账号管理 ==")
        _, res = call(base, "GET", "/api/admin/account/page?pageNum=1&pageSize=50",
                      token=admin_token)
        data = res.get("data") or {}
        roles = {r.get("role") for r in (data.get("records") or [])}
        check("账号列表只含后台角色", roles.issubset({"admin", "customer_service"}), roles)
        me = next((r for r in (data.get("records") or []) if r.get("id") == admin_id), {})
        check("当前登录账号被标记 self=true", me.get("self") is True, me.get("self"))

        # 新增校验
        _, res = call(base, "POST", "/api/admin/account", token=admin_token, body={
            "username": cs_username, "password": "123456",
            "nickname": "重复账号", "role": "customer_service"
        })
        check("重复账号被拒", res.get("code") != 200 and "占用" in (res.get("msg") or ""),
              res.get("msg"))

        _, res = call(base, "POST", "/api/admin/account", token=admin_token, body={
            "username": "ab", "password": "123456", "nickname": "太短", "role": "customer_service"
        })
        check("账号过短被拒（参数校验）", res.get("code") == 400,
              (res.get("code"), res.get("msg")))

        _, res = call(base, "POST", "/api/admin/account", token=admin_token, body={
            "username": "m11role" + suffix, "password": "123456",
            "nickname": "非法角色", "role": "user"
        })
        check("新增普通用户角色被拒", res.get("code") == 400,
              (res.get("code"), res.get("msg")))

        _, res = call(base, "POST", "/api/admin/account", token=admin_token, body={
            "username": "m11pwd" + suffix, "password": "123", "nickname": "密码太短",
            "role": "customer_service"
        })
        check("密码过短被拒", res.get("code") == 400, (res.get("code"), res.get("msg")))

        # 编辑昵称
        _, res = call(base, "PUT", "/api/admin/account/%s" % cs_id, token=admin_token, body={
            "nickname": "改名后的客服" + suffix, "role": "customer_service", "status": 0
        })
        check("编辑账号昵称成功", res.get("code") == 200, res.get("msg"))
        row = query_one("SELECT nickname FROM sys_user WHERE id = %s;" % cs_id)
        check("昵称已落库", row is not None and row[0] == "改名后的客服" + suffix, row)

        # 护栏：不能操作自己
        _, res = call(base, "PUT", "/api/admin/account/%s" % admin_id, token=admin_token, body={
            "nickname": "系统管理员", "role": "customer_service", "status": 0
        })
        check("不能修改自己的角色", res.get("code") == 400 and "角色" in (res.get("msg") or ""),
              res.get("msg"))

        _, res = call(base, "PUT", "/api/admin/account/%s" % admin_id, token=admin_token, body={
            "nickname": "系统管理员", "role": "admin", "status": 1
        })
        check("不能禁用自己的账号", res.get("code") == 400 and "自己" in (res.get("msg") or ""),
              res.get("msg"))

        _, res = call(base, "PUT", "/api/admin/account/%s/status" % admin_id, token=admin_token,
                      body={"status": 1})
        check("启停用接口同样禁止禁用自己", res.get("code") == 400, res.get("msg"))

        _, res = call(base, "DELETE", "/api/admin/account/%s" % admin_id, token=admin_token)
        check("不能删除自己的账号", res.get("code") == 400 and "自己" in (res.get("msg") or ""),
              res.get("msg"))

        _, res = call(base, "PUT", "/api/admin/account/%s/password" % admin_id, token=admin_token,
                      body={"password": "newpass123"})
        check("不能重置自己的密码", res.get("code") == 400, res.get("msg"))

        # 目标不是后台账号时要拒绝（防止借账号接口改普通用户角色）
        _, res = call(base, "PUT", "/api/admin/account/%s" % user_id, token=admin_token, body={
            "nickname": "越权改普通用户", "role": "admin", "status": 0
        })
        check("不能通过账号接口改普通用户", res.get("code") == 400,
              (res.get("code"), res.get("msg")))

        # 重置他人密码
        _, res = call(base, "PUT", "/api/admin/account/%s/password" % cs_id, token=admin_token,
                      body={"password": "reset888"})
        check("重置他人密码成功", res.get("code") == 200, res.get("msg"))
        # 重置密码的典型场景是「账号可能已被他人使用」，旧 token 必须立即失效。
        # 本系统鉴权是 JWT + Redis 白名单，每个请求不比对密码，
        # 所以这里必须显式删 token，否则重置等于没做
        _, res = call(base, "GET", "/api/dashboard/summary", token=cs_token)
        check("重置密码后旧登录态立即失效", res.get("code") == 401, res.get("code"))

        _, relogin = call(base, "POST", "/api/auth/login",
                          body={"username": cs_username, "password": "reset888"})
        check("新密码可以登录", relogin.get("code") == 200, relogin.get("msg"))
        _, oldlogin = call(base, "POST", "/api/auth/login",
                           body={"username": cs_username, "password": "123456"})
        check("旧密码已失效", oldlogin.get("code") != 200, oldlogin.get("code"))
        # 重新登录会把 token 换掉，后续用新密码再登一次拿 token
        _, cs_login2 = call(base, "POST", "/api/auth/login",
                            body={"username": cs_username, "password": "reset888"})
        cs_token = (cs_login2.get("data") or {}).get("token")

        # 禁用 → 登录被拒
        _, res = call(base, "PUT", "/api/admin/account/%s/status" % cs_id, token=admin_token,
                      body={"status": 1})
        check("禁用账号成功", res.get("code") == 200, res.get("msg"))
        _, login_disabled = call(base, "POST", "/api/auth/login",
                                 body={"username": cs_username, "password": "reset888"})
        check("被禁用账号无法登录", login_disabled.get("code") != 200,
              login_disabled.get("code"))
        _, res = call(base, "PUT", "/api/admin/account/%s/status" % cs_id, token=admin_token,
                      body={"status": 0})
        check("重新启用账号成功", res.get("code") == 200, res.get("msg"))

        # 角色互转：客服 → 管理员 → 客服
        _, res = call(base, "PUT", "/api/admin/account/%s" % cs_id, token=admin_token, body={
            "nickname": "改名后的客服" + suffix, "role": "admin", "status": 0
        })
        check("角色可提升为管理员", res.get("code") == 200, res.get("msg"))
        row = query_one("SELECT role FROM sys_user WHERE id = %s;" % cs_id)
        check("角色变更已落库", row is not None and row[0] == "admin", row)
        call(base, "PUT", "/api/admin/account/%s" % cs_id, token=admin_token,
             body={"nickname": "改名后的客服" + suffix, "role": "customer_service", "status": 0})

        # 至少保留一个管理员：新建第二个管理员后可以删除它
        second_admin = "adm" + suffix
        _, res = call(base, "POST", "/api/admin/account", token=admin_token, body={
            "username": second_admin, "password": "123456",
            "nickname": "第二管理员" + suffix, "role": "admin"
        })
        second_admin_id = res.get("data")
        if second_admin_id:
            created_account_ids.append(second_admin_id)
        check("新增第二个管理员成功", res.get("code") == 200 and bool(second_admin_id),
              res.get("msg"))

        _, res = call(base, "DELETE", "/api/admin/account/%s" % second_admin_id,
                      token=admin_token)
        check("有多余管理员时可以删除", res.get("code") == 200, res.get("msg"))

        row = query_one("SELECT deleted, username FROM sys_user WHERE id = %s;" % second_admin_id)
        check("删除为逻辑删除", row is not None and row[0] == "1", row)
        check("删除后账号名被改名归档", row is not None and row[1] != second_admin, row)
        # 唯一索引才是真正要防的东西：逻辑删除行若仍占用账号名，
        # 「删了重建同名账号」会直接撞 uk_username
        _, res = call(base, "POST", "/api/admin/account", token=admin_token, body={
            "username": second_admin, "password": "123456",
            "nickname": "重建同名管理员" + suffix, "role": "admin"
        })
        rebuilt_id = res.get("data")
        if rebuilt_id:
            created_account_ids.append(rebuilt_id)
        check("删除后可以用同名账号重新创建", res.get("code") == 200, res.get("msg"))

        # ========================================================
        # 九、操作日志
        # ========================================================
        print("\n== 九、操作日志 ==")
        _, res = call(base, "GET",
                      "/api/operation-log/page?pageNum=1&pageSize=100&module=sensitive_word",
                      token=admin_token)
        logs = (res.get("data") or {}).get("records") or []
        actions = {l.get("action") for l in logs}
        check("敏感词操作已写入日志", len(logs) > 0, len(logs))
        check("日志覆盖新增/编辑/删除/批量导入",
              {"create", "update", "delete", "batch_import"}.issubset(actions), actions)

        _, res = call(base, "GET",
                      "/api/operation-log/page?pageNum=1&pageSize=100&module=account",
                      token=admin_token)
        logs = (res.get("data") or {}).get("records") or []
        actions = {l.get("action") for l in logs}
        check("账号操作已写入日志", len(logs) > 0, len(logs))
        check("日志覆盖新增/重置密码/删除",
              {"create", "reset_password", "delete"}.issubset(actions), actions)

    finally:
        # ---------------- 清理 ----------------
        print("\n== 清理测试数据 ==")
        for pid in created_product_ids:
            if pid:
                mysql_exec("DELETE FROM product_image WHERE product_id = %s;" % pid)
                mysql_exec("DELETE FROM product WHERE id = %s;" % pid)
        for wid in created_word_ids:
            if wid:
                mysql_exec("DELETE FROM sensitive_word WHERE id = %s;" % wid)
        mysql_exec("DELETE FROM sensitive_word WHERE word LIKE '%%%s%%';" % suffix)
        mysql_exec("DELETE FROM trade_order WHERE order_no = 'M11%s';" % suffix.upper())
        mysql_exec("DELETE FROM chat_message WHERE session_id IN "
                   "(SELECT id FROM chat_session WHERE buyer_id IN (%s, %s));"
                   % (user_id or 0, student_id or 0))
        mysql_exec("DELETE FROM chat_session WHERE buyer_id IN (%s, %s);"
                   % (user_id or 0, student_id or 0))
        mysql_exec("DELETE FROM review WHERE reviewer_id IN (%s, %s) OR reviewee_id IN (%s, %s);"
                   % (user_id or 0, student_id or 0, user_id or 0, student_id or 0))
        ids = [str(i) for i in [user_id, student_id] + created_account_ids if i]
        if ids:
            joined = ",".join(ids)
            mysql_exec("DELETE FROM product WHERE seller_id IN (%s);" % joined)
            mysql_exec("UPDATE sys_user SET username = CONCAT('del_', id) WHERE id IN (%s);" % joined)
            mysql_exec("DELETE FROM sys_user WHERE id IN (%s);" % joined)
        # 重新加载词典，避免测试残留的词留在内存里
        call(base, "POST", "/api/sensitive-word/reload", token=admin_token)
        print("  已清理测试产生的敏感词、商品、订单、会话与账号")

    # ---------------- 汇总 ----------------
    total = len(RESULTS)
    failed = total - PASSED
    print("\n" + "=" * 66)
    print("通过 %d / %d，失败 %d" % (PASSED, total, failed))
    if failed:
        print("失败用例：")
        for name, ok, detail in RESULTS:
            if not ok:
                print("  - %s  -> %s" % (name, detail))
    print("=" * 66)
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
