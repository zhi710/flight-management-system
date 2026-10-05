# -*- coding: utf-8 -*-
"""压测配套工具：取 token / 单次预检 / 结果汇总。

用法：
    python lt_api.py tokens            # 登录压测账号，打印 -JADMIN_TOKEN=.. -JMEMBER_TOKEN=..
    python lt_api.py preflight         # 每个采样器各打一次，确认 200 且业务 code=200
    python lt_api.py summary           # 把 results/*.csv 汇总成 reports/summary.md
"""
import csv
import json
import os
import sys
import urllib.error
import urllib.request
from collections import defaultdict

BASE = os.environ.get("LT_BASE", "http://127.0.0.1:8080/api")
ADMIN_USER = "lt_admin"
MEMBER_PHONE = "13900000001"
PASSWORD = "LoadTest@2026"
FLIGHT_DATE = "2026-09-15"
SEARCH_QS = ("tripType=ONEWAY&departure=PEK&arrival=SHA&departDate=" + FLIGHT_DATE
             + "&adults=1&children=0&infants=0&cabinClass=ECONOMY")


def call(method, path, body=None, token=None):
    """返回 (http_status, 解析后的 json 或原始文本)"""
    data = json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    if data:
        req.add_header("Content-Type", "application/json; charset=utf-8")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            raw = r.read().decode("utf-8")
            status = r.status
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", "replace")
        status = e.code
    try:
        return status, json.loads(raw)
    except Exception:
        return status, raw


def code_of(body):
    return body.get("code") if isinstance(body, dict) else None


def login_admin():
    st, b = call("POST", "/admin/auth/login", {"username": ADMIN_USER, "password": PASSWORD})
    if code_of(b) != 200:
        raise SystemExit("管理员登录失败: %s %s" % (st, b))
    return b["data"]["token"]


def login_member():
    st, b = call("POST", "/auth/login", {"phone": MEMBER_PHONE, "password": PASSWORD})
    if code_of(b) != 200:
        raise SystemExit("会员登录失败: %s %s" % (st, b))
    return b["data"]["token"]


def cmd_tokens():
    print("-JADMIN_TOKEN=%s -JMEMBER_TOKEN=%s" % (login_admin(), login_member()))


def cmd_preflight():
    admin = login_admin()
    member = login_member()
    ok = True

    def check(tag, method, path, body=None, token=None, expect_code=200):
        nonlocal ok
        st, b = call(method, path, body, token)
        good = (st == expect_code) and (code_of(b) == expect_code)
        if not good:
            ok = False
        extra = ""
        if isinstance(b, dict) and isinstance(b.get("data"), dict):
            d = b["data"]
            if "flights" in d:
                extra = "flights=%d" % len(d["flights"])
            elif "orderId" in d:
                extra = "orderId=%s" % d["orderId"]
        print("  [%s] %-6s %-52s http=%s code=%s %s"
              % ("OK " if good else "FAIL", method, path[:52], st, code_of(b), extra))
        return b

    print("== 前台（旅客端）==")
    check("search", "GET", "/flights/search?" + SEARCH_QS)
    b = check("order", "POST", "/orders", {
        "flightId": "900000000000000002", "cabinClass": "ECONOMY",
        "passengers": [{"name": "压测旅客", "gender": "MALE", "idType": "ID_CARD",
                        "idNumber": "110101199001011234", "passengerType": "ADULT"}],
        "contactInfo": {"name": "压测旅客", "phone": "13900000001"},
    }, member)
    order_id = (b.get("data") or {}).get("orderId") if isinstance(b, dict) else None
    if order_id:
        check("order-detail", "GET", "/orders/" + str(order_id), None, member)
    check("order-list", "GET", "/orders?page=1&pageSize=10", None, member)

    print("== 后台（管理端只读）==")
    check("monitor", "GET", "/admin/monitor/dashboard", None, admin)
    check("admin-flights", "GET", "/admin/flights?page=1&pageSize=20", None, admin)
    check("admin-crew", "GET", "/admin/crew/list", None, admin)
    check("admin-passengers", "GET", "/admin/passengers?page=1&pageSize=20", None, admin)

    print("\n==== 预检 %s ====" % ("全部通过" if ok else "存在失败项"))
    return 0 if ok else 1


# ------------------------------------------------------------------ summary

def percentile(sorted_vals, p):
    if not sorted_vals:
        return 0.0
    k = (len(sorted_vals) - 1) * p
    lo, hi = int(k), min(int(k) + 1, len(sorted_vals) - 1)
    return sorted_vals[lo] + (sorted_vals[hi] - sorted_vals[lo]) * (k - lo)


def cmd_summary():
    results_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), "results")
    reports_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), "reports")
    os.makedirs(reports_dir, exist_ok=True)
    files = sorted(f for f in os.listdir(results_dir)
                   if f.endswith(".csv") and not f.startswith("_") and f != "smoke.csv") \
        if os.path.isdir(results_dir) else []
    if not files:
        raise SystemExit("results/ 下没有 csv，先跑压测")

    # 聚合键：(场景, 档位, 采样器)
    agg = defaultdict(lambda: {"n": 0, "err": 0, "elapsed": [], "codes": defaultdict(int),
                               "t0": None, "t1": None})
    for fn in files:
        with open(os.path.join(results_dir, fn), newline="", encoding="utf-8") as fh:
            for row in csv.DictReader(fh):
                label = row.get("label", "")
                step = label.rsplit("_", 1)[-1]
                if not step.isdigit():
                    step = "?"
                key = (fn.rsplit(".", 1)[0], step, label)
                a = agg[key]
                a["n"] += 1
                if str(row.get("success") or "").lower() != "true":
                    a["err"] += 1
                try:
                    a["elapsed"].append(float(row["elapsed"]))
                except Exception:
                    pass
                a["codes"][row.get("bizCode", "NA") or "NA"] += 1
                try:
                    ts = int(row["timeStamp"])
                    a["t0"] = ts if a["t0"] is None else min(a["t0"], ts)
                    a["t1"] = ts if a["t1"] is None else max(a["t1"], ts)
                except Exception:
                    pass

    lines = ["# 高并发压测结果汇总", "", "（TPS = 采样数 / 该档实际持续秒数；p95/p99 为响应时间毫秒）", ""]
    by_plan = defaultdict(list)
    for (plan, step, label), a in agg.items():
        by_plan[plan].append((int(step) if step.isdigit() else -1, label, a))

    for plan in sorted(by_plan):
        lines.append("## %s" % plan)
        lines.append("")
        lines.append("| 档位 | 采样器 | 并发 | 请求数 | TPS | 平均(ms) | p95(ms) | p99(ms) | HTTP错误 | 业务码分布 |")
        lines.append("|---|---|---|---|---|---|---|---|---|---|")
        for step, label, a in sorted(by_plan[plan]):
            el = sorted(a["elapsed"])
            dur = ((a["t1"] - a["t0"]) / 1000.0) if (a["t0"] and a["t1"] and a["t1"] > a["t0"]) else 0
            tps = (a["n"] / dur) if dur > 0 else 0
            avg = (sum(el) / len(el)) if el else 0
            codes = ", ".join("%s:%d" % (k, v) for k, v in sorted(a["codes"].items(), key=lambda kv: -kv[1]))
            lines.append("| %s | %s | %s | %d | %.1f | %.1f | %.1f | %.1f | %d | %s |"
                         % (step, label, step, a["n"], tps, avg,
                            percentile(el, 0.95), percentile(el, 0.99), a["err"], codes))
        lines.append("")

    out = os.path.join(reports_dir, "summary.md")
    with open(out, "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines))
    print("已写出 " + out)
    print("\n".join(lines[:40]))


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else "preflight"
    if cmd == "tokens":
        cmd_tokens()
    elif cmd == "preflight":
        sys.exit(cmd_preflight())
    elif cmd == "summary":
        cmd_summary()
    else:
        raise SystemExit("usage: tokens | preflight | summary")
