#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""模块3.5「权限体系升级」接口冒烟测试。

用法（后端已在 8080 启动，且已执行 sql/V4__auth_upgrade.sql）：
    python tests/smoke_module3_5.py

覆盖流程：
    预置账号登录（admin / service01 / service02）-> 错误密码 -> 非管理角色被拒管理端 ->
    用户名唯一性校验 -> 注册 -> 重复注册 -> 账号登录 -> 当前账号信息 ->
    客服只读（GET 放行 / POST PUT DELETE 一律 403）-> 客服白名单接口 ->
    管理员写接口放行 -> 微信用户绑定账号 -> 绑定后账号登录 -> 修改密码 ->
    新密码登录 -> 退出登录 -> 前端权限产物静态检查

退出码：0 全部通过，1 存在失败。
"""

import argparse
import json
import os
import random
import string
import sys
import urllib.error
import urllib.request
from urllib.parse import quote

RESULTS = []
PASSED = 0

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


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
        return e.code, {"code": e.code, "msg": e.read().decode("utf-8", "replace")}


def check(name, ok, detail=""):
    global PASSED
    RESULTS.append((name, ok, detail))
    if ok:
        PASSED += 1
    print(("  [PASS] " if ok else "  [FAIL] ") + name + (("  -> " + str(detail)) if detail else ""))
    return ok


def rand_suffix(n=6):
    return "".join(random.choice(string.ascii_lowercase + string.digits) for _ in range(n))


def login(base, username, password, admin=False):
    path = "/api/auth/admin-login" if admin else "/api/auth/login"
    return call(base, "POST", path, body={"username": username, "password": password})


def token_of(body):
    return (body.get("data") or {}).get("token") or ""


def read_text(*parts):
    with open(os.path.join(ROOT, *parts), encoding="utf-8") as f:
        return f.read()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="http://127.0.0.1:8080")
    args = parser.parse_args()
    base = args.base.rstrip("/")

    print("\n== 1. 预置账号与账号密码登录 ==")
    _, admin_body = login(base, "admin", "123456", admin=True)
    admin_token = token_of(admin_body)
    check("admin/123456 管理端登录成功", admin_body.get("code") == 200 and admin_token,
          admin_body.get("msg"))
    check("admin 角色为 admin", (admin_body.get("data") or {}).get("role") == "admin")

    _, cs_body = login(base, "service01", "123456", admin=True)
    cs_token = token_of(cs_body)
    check("service01/123456 客服登录成功", cs_body.get("code") == 200 and cs_token,
          cs_body.get("msg"))
    check("service01 角色为 customer_service",
          (cs_body.get("data") or {}).get("role") == "customer_service")

    _, cs2_body = login(base, "service02", "123456", admin=True)
    check("service02 预置账号可用",
          cs2_body.get("code") == 200 and (cs2_body.get("data") or {}).get("role") == "customer_service",
          cs2_body.get("msg"))

    _, wrong = login(base, "admin", "000000", admin=True)
    check("错误密码被拒绝", wrong.get("code") != 200, wrong.get("msg"))

    _, unknown = login(base, "nobody9999", "123456", admin=True)
    check("不存在账号被拒绝", unknown.get("code") != 200, unknown.get("msg"))

    print("\n== 2. 账号注册 / 唯一性 / 账号登录 ==")
    suffix = rand_suffix()
    username = "smoke" + suffix
    password = "abc12345"

    _, avail = call(base, "GET", "/api/auth/check-username?username=" + username)
    check("未注册账号可用", avail.get("code") == 200 and avail.get("data") is True, avail.get("data"))

    _, taken = call(base, "GET", "/api/auth/check-username?username=admin")
    check("admin 账号不可注册", taken.get("data") is False, taken.get("data"))

    _, bad_fmt = call(base, "GET", "/api/auth/check-username?username=ab")
    check("非法格式账号判为不可用", bad_fmt.get("data") is False)

    _, reg = call(base, "POST", "/api/auth/register",
                  body={"username": username, "password": password, "nickname": "冒烟用户" + suffix[:3]})
    reg_token = token_of(reg)
    check("账号注册成功并直接返回 token", reg.get("code") == 200 and reg_token, reg.get("msg"))
    check("注册默认角色为 user", (reg.get("data") or {}).get("role") == "user")
    user_id = (reg.get("data") or {}).get("userId")

    _, dup = call(base, "POST", "/api/auth/register",
                  body={"username": username, "password": password, "nickname": "重复注册"})
    check("重复账号注册被拒绝", dup.get("code") != 200 and "已被注册" in (dup.get("msg") or ""),
          dup.get("msg"))

    _, dup_admin = call(base, "POST", "/api/auth/register",
                        body={"username": "admin", "password": password})
    check("注册已存在的 admin 被拒绝",
          dup_admin.get("code") != 200 and "已被注册" in (dup_admin.get("msg") or ""),
          dup_admin.get("msg"))

    _, bad_user = call(base, "POST", "/api/auth/register",
                       body={"username": "a b", "password": password})
    check("注册账号格式校验生效", bad_user.get("code") == 400, bad_user.get("msg"))

    _, bad_pwd = call(base, "POST", "/api/auth/register",
                      body={"username": "smoke" + rand_suffix(), "password": "123"})
    check("注册密码长度校验生效", bad_pwd.get("code") == 400, bad_pwd.get("msg"))

    _, user_login = login(base, username, password)
    user_token = token_of(user_login)
    check("新账号可登录", user_login.get("code") == 200 and user_token, user_login.get("msg"))

    _, wrong_pwd = login(base, username, "wrong123")
    check("新账号错误密码被拒绝", wrong_pwd.get("code") != 200, wrong_pwd.get("msg"))

    _, user_me = call(base, "GET", "/api/auth/me", token=user_token)
    me = user_me.get("data") or {}
    check("me 返回账号与注册方式",
          me.get("username") == username and me.get("registerType") == 2 and me.get("hasAccount") is True,
          {"username": me.get("username"), "registerType": me.get("registerType")})

    _, user_admin_login = login(base, username, password, admin=True)
    check("普通用户无法登录管理端", user_admin_login.get("code") == 403,
          user_admin_login.get("msg"))

    print("\n== 3. 客服只读权限 ==")
    _, cs_cat = call(base, "GET", "/api/category/list?all=true", token=cs_token)
    check("客服 GET 分类列表放行", cs_cat.get("code") == 200 and isinstance(cs_cat.get("data"), list))

    _, cs_product = call(base, "GET", "/api/product/audit/page?pageNum=1&pageSize=5", token=cs_token)
    check("客服 GET 商品审核列表放行", cs_product.get("code") == 200, cs_product.get("msg"))

    _, cs_auth = call(base, "GET", "/api/student-auth/page?pageNum=1&pageSize=5", token=cs_token)
    check("客服 GET 学生认证列表放行", cs_auth.get("code") == 200, cs_auth.get("msg"))

    _, cs_banner = call(base, "GET", "/api/banner/page?pageNum=1&pageSize=5", token=cs_token)
    check("客服 GET 轮播图列表放行", cs_banner.get("code") == 200, cs_banner.get("msg"))

    _, cs_create_cat = call(base, "POST", "/api/category", token=cs_token,
                            body={"name": "客服不该建", "sort": 99, "status": 1})
    check("客服 POST 新增分类被拒 403",
          cs_create_cat.get("code") == 403
          and cs_create_cat.get("msg") == "客服账号无操作权限，请联系管理员",
          cs_create_cat.get("msg"))

    _, cs_update_cat = call(base, "PUT", "/api/category/1", token=cs_token,
                            body={"name": "改名", "sort": 1, "status": 1})
    check("客服 PUT 修改分类被拒 403", cs_update_cat.get("code") == 403, cs_update_cat.get("msg"))

    _, cs_del_cat = call(base, "DELETE", "/api/category/1", token=cs_token)
    check("客服 DELETE 删除分类被拒 403", cs_del_cat.get("code") == 403, cs_del_cat.get("msg"))

    _, cs_audit = call(base, "PUT", "/api/product/1/audit", token=cs_token,
                       body={"status": 1})
    check("客服 审核商品被拒 403", cs_audit.get("code") == 403, cs_audit.get("msg"))

    _, cs_banner_create = call(base, "POST", "/api/banner", token=cs_token,
                               body={"title": "客服不该建", "imageUrl": "/uploads/x.png",
                                     "linkType": 3, "linkValue": "https://example.com"})
    check("客服 新增轮播图被拒 403", cs_banner_create.get("code") == 403,
          cs_banner_create.get("msg"))

    _, cs_bind = call(base, "POST", "/api/auth/bind-account", token=cs_token,
                      body={"username": "cscs" + rand_suffix(), "password": "abc12345"})
    check("客服 绑定账号走白名单（业务层提示已绑定）",
          cs_bind.get("code") != 403, cs_bind.get("msg"))

    _, cs_change = call(base, "POST", "/api/auth/change-password", token=cs_token,
                        body={"oldPassword": "000000", "newPassword": "abc12345"})
    check("客服 改密走白名单（原密码错误返回400）", cs_change.get("code") == 400,
          cs_change.get("msg"))

    _, cs_me = call(base, "GET", "/api/auth/me", token=cs_token)
    check("客服 可获取个人信息", cs_me.get("code") == 200)

    print("\n== 4. 管理员全权 ==")
    _, admin_cat = call(base, "POST", "/api/category", token=admin_token,
                        body={"name": "冒烟分类" + suffix[:4], "sort": 98, "status": 1})
    new_cat_id = (admin_cat.get("data") or {}).get("id")
    check("管理员 新增分类成功", admin_cat.get("code") == 200 and new_cat_id, admin_cat.get("msg"))

    _, admin_cat_upd = call(base, "PUT", "/api/category/%s" % new_cat_id, token=admin_token,
                            body={"name": "冒烟分类改" + suffix[:4], "sort": 98, "status": 1})
    check("管理员 修改分类成功", admin_cat_upd.get("code") == 200, admin_cat_upd.get("msg"))

    _, admin_banner = call(base, "POST", "/api/banner", token=admin_token,
                           body={"title": "冒烟轮播" + suffix[:4], "imageUrl": "/uploads/smoke.png",
                                 "linkType": 3, "linkValue": "https://example.com",
                                 "sort": 99, "status": 0})
    new_banner_id = (admin_banner.get("data") or {}).get("id")
    check("管理员 新增轮播图成功", admin_banner.get("code") == 200 and new_banner_id,
          admin_banner.get("msg"))

    _, admin_banner_del = call(base, "DELETE", "/api/banner/%s" % new_banner_id, token=admin_token)
    check("管理员 删除轮播图成功", admin_banner_del.get("code") == 200, admin_banner_del.get("msg"))

    _, admin_cat_del = call(base, "DELETE", "/api/category/%s" % new_cat_id, token=admin_token)
    check("管理员 删除分类成功", admin_cat_del.get("code") == 200, admin_cat_del.get("msg"))

    print("\n== 5. 微信用户绑定账号密码 ==")
    wx_code = "mock_bind_" + suffix
    _, wx_body = call(base, "POST", "/api/auth/wx-login", body={"code": wx_code})
    wx_token = token_of(wx_body)
    check("微信登录（mock）成功", wx_body.get("code") == 200 and wx_token, wx_body.get("msg"))

    _, wx_me = call(base, "GET", "/api/auth/me", token=wx_token)
    wx_me_data = wx_me.get("data") or {}
    check("微信用户初始无账号密码",
          wx_me_data.get("hasAccount") is False and wx_me_data.get("registerType") == 1,
          {"hasAccount": wx_me_data.get("hasAccount")})

    bind_username = "wx" + suffix
    _, bind = call(base, "POST", "/api/auth/bind-account", token=wx_token,
                   body={"username": bind_username, "password": "abc12345"})
    check("微信用户绑定账号成功", bind.get("code") == 200, bind.get("msg"))

    _, bind_again = call(base, "POST", "/api/auth/bind-account", token=wx_token,
                         body={"username": "wx2" + suffix, "password": "abc12345"})
    check("已绑定账号不可重复绑定", bind_again.get("code") != 200, bind_again.get("msg"))

    _, wx_account_login = login(base, bind_username, "abc12345")
    check("绑定后可用账号密码登录", wx_account_login.get("code") == 200,
          wx_account_login.get("msg"))

    _, wx_me2 = call(base, "GET", "/api/auth/me", token=wx_token)
    check("绑定后 hasAccount 变为 true", (wx_me2.get("data") or {}).get("hasAccount") is True)

    print("\n== 6. 修改密码 ==")
    _, change = call(base, "POST", "/api/auth/change-password", token=user_token,
                     body={"oldPassword": password, "newPassword": "newpass123"})
    check("修改密码成功", change.get("code") == 200, change.get("msg"))

    _, old_login = login(base, username, password)
    check("旧密码无法登录", old_login.get("code") != 200, old_login.get("msg"))

    _, new_login = login(base, username, "newpass123")
    new_token = token_of(new_login)
    check("新密码可登录", new_login.get("code") == 200 and new_token, new_login.get("msg"))

    _, same = call(base, "POST", "/api/auth/change-password", token=new_token,
                   body={"oldPassword": "newpass123", "newPassword": "newpass123"})
    check("新旧密码相同被拒绝", same.get("code") != 200, same.get("msg"))

    _, wrong_old = call(base, "POST", "/api/auth/change-password", token=new_token,
                        body={"oldPassword": "bad12345", "newPassword": "abc12345"})
    check("原密码错误被拒绝", wrong_old.get("code") == 400, wrong_old.get("msg"))

    print("\n== 7. 退出登录 ==")
    _, logout = call(base, "POST", "/api/auth/logout", token=cs_token)
    check("客服退出登录成功", logout.get("code") == 200, logout.get("msg"))

    _, after_logout = call(base, "GET", "/api/auth/me", token=cs_token)
    check("退出后旧 token 失效", after_logout.get("code") == 401, after_logout.get("msg"))

    print("\n== 8. 前端权限产物静态检查 ==")
    main_js = read_text("admin-web", "src", "main.js")
    check("管理端已注册 v-permission 指令",
          "app.directive('permission'" in main_js)

    router_js = read_text("admin-web", "src", "router", "index.js")
    check("系统管理路由标注 roles: ['admin']",
          "roles: ['admin']" in router_js and "currentRole" in router_js)

    layout_vue = read_text("admin-web", "src", "layout", "index.vue")
    check("布局含客服只读提示条与角色标签",
          "readonly-bar" in layout_vue and "role-tag--admin" in layout_vue
          and "role-tag--cs" in layout_vue)
    check("布局顶部菜单支持按角色过滤",
          "item.meta?.roles" in layout_vue)

    views = [
        ("student-auth", "index.vue"),
        ("product", "audit", "index.vue"),
        ("category", "index.vue"),
        # 模块12 起「轮播图」页升级为「首页配置」，只读控制同样要覆盖
        ("home", "index.vue"),
    ]
    for view in views:
        content = read_text("admin-web", "src", "views", *view)
        check("只读控制已接入：views/" + "/".join(view),
              ("v-permission" in content) or ("canWrite" in content))

    system_vue = read_text("admin-web", "src", "views", "system", "index.vue")
    check("系统管理页含修改密码入口与权限矩阵",
          "ChangePasswordDialog" in system_vue and "角色权限矩阵" in system_vue)

    login_vue = read_text("admin-web", "src", "views", "login", "index.vue")
    check("登录页含品牌区与初始账号提示",
          "校园二手交易管理系统" in login_vue
          and "admin / 123456" in login_vue and "service01 / 123456" in login_vue)

    mp_app = read_text("miniprogram", "app.json")
    check("小程序已注册注册页与账号安全页",
          "pages/login/register" in mp_app and "pages/profile/security" in mp_app)

    mp_login = read_text("miniprogram", "pages", "login", "index.wxml")
    check("小程序登录页含双 Tab 与注册入口",
          "微信登录" in mp_login and "账号登录" in mp_login and "立即注册" in mp_login)

    mp_register = read_text("miniprogram", "pages", "login", "register.js")
    check("小程序注册页含唯一性校验与注册即登录",
          "checkUsername" in mp_register and "setLogin" in mp_register)

    mp_security = read_text("miniprogram", "pages", "profile", "security.wxml")
    check("小程序账号安全页含绑定与改密表单",
          "设置账号密码" in mp_security and "修改密码" in mp_security)

    total = len(RESULTS)
    print("\n" + "=" * 56)
    print("测试结果：%d/%d 通过，%d 失败" % (PASSED, total, total - PASSED))
    if PASSED != total:
        print("失败用例：")
        for name, ok, detail in RESULTS:
            if not ok:
                print("  - %s  -> %s" % (name, detail))
    print("=" * 56)
    return 0 if PASSED == total else 1


if __name__ == "__main__":
    sys.exit(main())
