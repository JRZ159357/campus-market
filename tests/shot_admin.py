#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""用 Edge 无头模式 + Chrome DevTools Protocol 给管理端页面截图。

不依赖 puppeteer/playwright，只用 websocket-client 驱动 CDP：
    1. 启动 Edge（--headless=new --remote-debugging-port）
    2. 打开管理端登录页，用接口拿 token 写进 localStorage（省去手动点登录）
    3. 依次访问目标路由，等待渲染完成后 Page.captureScreenshot 存 PNG

用法：
    python tests/shot_admin.py --out docs/screenshots/module7 --token <JWT> --url http://127.0.0.1:5173
"""
import argparse
import base64
import json
import os
import subprocess
import sys
import time
import urllib.request

import websocket

EDGE = r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"


def http_json(url, timeout=10):
    with urllib.request.urlopen(url, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8"))


class Cdp:
    """极简 CDP 客户端：只实现本脚本需要的几条命令。"""

    def __init__(self, ws_url):
        self.ws = websocket.create_connection(ws_url, timeout=40)
        self.msg_id = 0

    def send(self, method, params=None, wait=True):
        self.msg_id += 1
        mid = self.msg_id
        self.ws.send(json.dumps({"id": mid, "method": method, "params": params or {}}))
        if not wait:
            return None
        while True:
            raw = self.ws.recv()
            msg = json.loads(raw)
            if msg.get("id") == mid:
                if "error" in msg:
                    raise RuntimeError("%s 失败: %s" % (method, msg["error"]))
                return msg.get("result", {})

    def eval(self, expression, await_promise=False):
        res = self.send("Runtime.evaluate", {
            "expression": expression,
            "returnByValue": True,
            "awaitPromise": await_promise
        })
        return (res.get("result") or {}).get("value")

    def shot(self, path, width, height):
        res = self.send("Page.captureScreenshot", {"format": "png", "captureBeyondViewport": False})
        data = base64.b64decode(res["data"])
        with open(path, "wb") as f:
            f.write(data)
        return len(data)


def wait_port(port, timeout=40):
    end = time.time() + timeout
    while time.time() < end:
        try:
            http_json("http://127.0.0.1:%d/json/version" % port, timeout=2)
            return True
        except Exception:
            time.sleep(1)
    return False


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default="docs/screenshots/module7")
    parser.add_argument("--url", default="http://127.0.0.1:5173")
    parser.add_argument("--token", required=True)
    parser.add_argument("--user-info", default="", help="localStorage 里的 userInfo JSON")
    parser.add_argument("--port", type=int, default=9333)
    parser.add_argument("--shots", required=True,
                        help="截图清单，格式 name=hash路由[:等待毫秒]，逗号分隔")
    parser.add_argument("--width", type=int, default=1600)
    parser.add_argument("--height", type=int, default=1000)
    args = parser.parse_args()

    os.makedirs(args.out, exist_ok=True)
    profile = os.path.join(os.environ.get("TEMP", "."), "edge-shot-profile")
    proc = subprocess.Popen([
        EDGE, "--headless=new", "--disable-gpu", "--no-sandbox",
        # 本机设置了 HTTP(S)_PROXY 指向内网代理，Edge 会走代理访问 127.0.0.1，
        # 结果是 502「拒绝连接」。本地调试必须显式关掉代理。
        "--no-proxy-server",
        "--remote-debugging-port=%d" % args.port,
        # CDP 的 WebSocket 会带 Origin 头，Edge 默认拒绝，需要显式放行
        "--remote-allow-origins=*",
        "--user-data-dir=" + profile,
        "--window-size=%d,%d" % (args.width, args.height),
        "--hide-scrollbars",
        "about:blank"
    ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    try:
        if not wait_port(args.port):
            print("Edge 调试端口未就绪")
            return 1
        # 新开一个标签页并连上去
        target = http_json("http://127.0.0.1:%d/json/new?about:blank" % args.port, timeout=10) \
            if False else None
        # /json/new 需要 PUT 方法，这里直接复用已有 target
        targets = http_json("http://127.0.0.1:%d/json/list" % args.port, timeout=10)
        page = next(t for t in targets if t.get("type") == "page")
        cdp = Cdp(page["webSocketDebuggerUrl"])
        cdp.send("Page.enable")
        cdp.send("Runtime.enable")

        # 先打开登录页拿到同源上下文，再写 localStorage
        cdp.send("Page.navigate", {"url": args.url + "/#/login"})
        time.sleep(2.5)
        info = args.user_info or json.dumps({"role": "admin", "roleLabel": "管理员", "username": "admin"})
        cdp.eval("""(function(){
            localStorage.setItem('token', %s);
            localStorage.setItem('userInfo', %s);
            return true;
        })()""" % (json.dumps(args.token), json.dumps(info)))
        # Pinia 在应用启动时读一次 localStorage，只改存储不重载的话，
        # 页面里的角色仍是上一个账号的（会截成管理员视图）。必须整页重载。
        cdp.send("Page.reload", {"ignoreCache": True})
        time.sleep(3.0)

        count = 0
        for item in args.shots.split(","):
            if not item.strip():
                continue
            name, _, rest = item.partition("=")
            # 规格：name=hash路由[:等待毫秒][|截图前执行的 JS]
            rest, _, js = rest.partition("|")
            route, _, wait_ms = rest.partition(":")
            wait_ms = int(wait_ms or 2600)
            cdp.send("Page.navigate", {"url": args.url + "/#/" + route.lstrip("/")})
            time.sleep(wait_ms / 1000.0)
            # 同一文档内改 hash 不会重新加载，上一张截图打开的弹窗会残留到下一张。
            # 统一 reload 一次，保证每张图都是干净的初始状态。
            cdp.send("Page.reload", {"ignoreCache": True})
            time.sleep(1.6)
            if js.strip():
                cdp.eval(js)
                time.sleep(1.8)
            path = os.path.join(args.out, name if name.endswith(".png") else name + ".png")
            size = cdp.shot(path, args.width, args.height)
            count += 1
            print("已截图 %s（%d 字节）" % (path, size))
        print("共生成 %d 张截图" % count)
        return 0
    finally:
        try:
            proc.terminate()
        except Exception:
            pass


if __name__ == "__main__":
    sys.exit(main())
