#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""模块1「用户认证」接口冒烟测试。

用法（后端已在 8080 启动）：
    python tests/smoke_test.py
    python tests/smoke_test.py --base http://localhost:8080

覆盖流程：
    微信登录 -> 查询个人信息 -> 查询认证状态 -> 上传学生证 ->
    提交认证 -> 管理员登录 -> 认证列表 -> 审核通过 ->
    校验角色升级 -> 权限校验（普通用户越权访问应 403）-> 退出登录

退出码：0 全部通过，1 存在失败。
"""

import argparse
import json
import mimetypes
import os
import sys
import time
import urllib.error
import urllib.request
import uuid

RESULTS = []


def call(base, method, path, token=None, body=None, raw=False):
    """发起 JSON 请求，返回 (http_status, parsed_json)。"""
    url = base + path
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
            text = resp.read().decode("utf-8")
            return resp.status, (text if raw else json.loads(text))
    except urllib.error.HTTPError as e:
        return e.code, {"code": e.code, "msg": e.read().decode("utf-8", "replace")}


def upload(base, path, token):
    """multipart 上传一张临时 PNG，返回服务器相对地址。"""
    boundary = "----smoke" + uuid.uuid4().hex
    # 1x1 透明 PNG
    png = bytes.fromhex(
        "89504e470d0a1a0a0000000d49484452000000010000000108060000001f15c489"
        "0000000a49444154789c6300010000050001a5f645400000000049454e44ae426082"
    )
    body = (
        ("--" + boundary + "\r\n"
         'Content-Disposition: form-data; name="file"; filename="cert.png"\r\n'
         "Content-Type: image/png\r\n\r\n").encode("utf-8")
        + png
        + ("\r\n--" + boundary + "--\r\n").encode("utf-8")
    )
    req = urllib.request.Request(
        base + path,
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
    RESULTS.append((name, ok, detail))
    print(("  [PASS] " if ok else "  [FAIL] ") + name + (("  -> " + detail) if detail else ""))
    return ok


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="http://localhost:8080")
    args = parser.parse_args()
    base = args.base.rstrip("/")

    print("=" * 66)
    print("模块1 用户认证 冒烟测试  |  " + base)
    print("=" * 66)

    suffix = str(int(time.time()))[-6:]

    # 1. 微信登录（wechat.mock=true，任意 code 即可）
    print("\n[1] 小程序微信登录")
    status, res = call(base, "POST", "/api/auth/wx-login", body={"code": "smoke" + suffix})
    token = (res.get("data") or {}).get("token")
    check("wx-login 返回 code=200", res.get("code") == 200, res.get("msg", ""))
    check("返回 token 且角色为 user", bool(token) and res["data"]["role"] == "user",
          "role=" + str((res.get("data") or {}).get("role")))
    if not token:
        return finish()

    user_id = res["data"]["userId"]

    # 2. 查询个人信息
    print("\n[2] 查询个人信息")
    _, me = call(base, "GET", "/api/auth/me", token=token)
    check("GET /api/auth/me 成功", me.get("code") == 200)
    check("初始未认证", (me.get("data") or {}).get("studentVerified") == 0)

    # 3. 普通用户越权访问管理端接口应被拒绝
    print("\n[3] 权限校验：普通用户访问管理端接口")
    _, forbidden = call(base, "GET", "/api/student-auth/page?pageNum=1&pageSize=10", token=token)
    check("普通用户访问认证列表返回 403", forbidden.get("code") == 403, forbidden.get("msg", ""))

    # 4. 未登录访问受保护接口
    print("\n[4] 权限校验：未携带 token")
    _, noauth = call(base, "GET", "/api/auth/me")
    check("未登录返回 401", noauth.get("code") == 401, noauth.get("msg", ""))

    # 5. 认证状态
    print("\n[5] 查询认证状态")
    _, st = call(base, "GET", "/api/student-auth/status", token=token)
    check("status 接口成功", st.get("code") == 200)
    check("初始状态为未提交", (st.get("data") or {}).get("verified") is False)

    # 6. 上传学生证
    print("\n[6] 上传学生证照片")
    up = upload(base, "/api/file/upload", token)
    cert = (up.get("data") or "")
    check("上传成功并返回 /uploads/ 地址", up.get("code") == 200 and cert.startswith("/uploads/"), cert)

    # 7. 提交认证
    print("\n[7] 提交学生认证申请")
    apply_body = {
        "studentNo": "2023" + suffix,
        "realName": "测试学生",
        "school": "北华大学",
        "certImg": cert,
    }
    _, ap = call(base, "POST", "/api/student-auth", token=token, body=apply_body)
    check("提交认证成功", ap.get("code") == 200, ap.get("msg", ""))

    # 8. 重复提交应被拦截
    print("\n[8] 重复提交校验")
    _, dup = call(base, "POST", "/api/student-auth", token=token, body=apply_body)
    check("重复提交被拒绝", dup.get("code") == 500 and "待审核" in str(dup.get("msg")), dup.get("msg", ""))

    # 9. 管理员登录
    print("\n[9] 管理端登录")
    _, admin = call(base, "POST", "/api/auth/admin-login",
                    body={"username": "admin", "password": "123456"})
    admin_token = (admin.get("data") or {}).get("token")
    check("管理员登录成功", admin.get("code") == 200 and bool(admin_token), admin.get("msg", ""))
    if not admin_token:
        return finish()

    # 10. 错误密码
    print("\n[10] 管理端错误密码")
    _, bad = call(base, "POST", "/api/auth/admin-login",
                  body={"username": "admin", "password": "wrong-password"})
    check("错误密码返回 400", bad.get("code") == 400, bad.get("msg", ""))

    # 11. 认证列表
    print("\n[11] 管理端查询待审核列表")
    _, page = call(base, "GET", "/api/student-auth/page?pageNum=1&pageSize=10&status=0",
                   token=admin_token)
    records = ((page.get("data") or {}).get("records") or [])
    target = next((r for r in records if r.get("userId") == user_id), None)
    check("列表查询成功", page.get("code") == 200, "total=" + str((page.get("data") or {}).get("total")))
    check("命中刚提交的申请", target is not None)
    if not target:
        return finish()

    # 12. 驳回不填原因应失败
    print("\n[12] 审核校验：驳回未填原因")
    _, reject_bad = call(base, "PUT", "/api/student-auth/%s/audit" % target["id"],
                         token=admin_token, body={"status": 2})
    check("驳回未填原因被拒绝", reject_bad.get("code") == 500, reject_bad.get("msg", ""))

    # 13. 审核通过
    print("\n[13] 审核通过")
    _, audit = call(base, "PUT", "/api/student-auth/%s/audit" % target["id"],
                    token=admin_token, body={"status": 1})
    check("审核通过成功", audit.get("code") == 200, audit.get("msg", ""))

    # 14. 角色升级是否立即生效（缓存已清除）
    print("\n[14] 校验角色升级实时生效")
    _, me2 = call(base, "GET", "/api/auth/me", token=token)
    me_data = me2.get("data") or {}
    check("用户角色升级为 student", me_data.get("role") == "student", "role=" + str(me_data.get("role")))
    check("学生认证标记为已认证", me_data.get("studentVerified") == 1)
    check("发布上限设为 20", me_data.get("publishLimit") == 20, "limit=" + str(me_data.get("publishLimit")))

    # 15. 学号唯一性
    print("\n[15] 学号占用校验（另一账号使用同一学号）")
    _, res2 = call(base, "POST", "/api/auth/wx-login", body={"code": "smoke2" + suffix})
    token2 = (res2.get("data") or {}).get("token")
    if token2:
        _, dup_no = call(base, "POST", "/api/student-auth", token=token2, body={
            "studentNo": apply_body["studentNo"], "realName": "占号学生",
            "school": "北华大学", "certImg": cert})
        check("已认证学号不能被再次认证", dup_no.get("code") == 500 and "学号" in str(dup_no.get("msg")),
              dup_no.get("msg", ""))

    # 16. 退出登录后 token 失效
    print("\n[16] 退出登录后 token 失效")
    call(base, "POST", "/api/auth/logout", token=token)
    _, after = call(base, "GET", "/api/auth/me", token=token)
    check("退出后访问返回 401", after.get("code") == 401, after.get("msg", ""))

    return finish()


def finish():
    total = len(RESULTS)
    passed = sum(1 for _, ok, _ in RESULTS if ok)
    print("\n" + "=" * 66)
    print("用例 %d 个，通过 %d 个，失败 %d 个" % (total, passed, total - passed))
    if passed == total:
        print("结论：全部通过")
    else:
        print("结论：存在失败用例，请检查后端日志")
        for name, ok, detail in RESULTS:
            if not ok:
                print("  失败: %s  %s" % (name, detail))
    print("=" * 66)
    return 0 if passed == total else 1


if __name__ == "__main__":
    sys.exit(main())
