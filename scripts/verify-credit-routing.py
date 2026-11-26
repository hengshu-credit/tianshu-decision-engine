"""Verify the published credit-limit experiment, same-report caching and cross-node order replay."""

import argparse
import concurrent.futures
import hashlib
import json
import os
from pathlib import Path
import secrets
import time
import urllib.error
import urllib.request

EXPERIMENT = "CREDIT_LIMIT_30_70"


def request(node, path, headers, body=None):
    encoded = None if body is None else json.dumps(body, ensure_ascii=False).encode()
    req = urllib.request.Request(
        node.rstrip("/") + "/api/rule/runtime/" + path,
        data=encoded,
        headers={**headers, "Content-Type": "application/json"},
    )
    try:
        with urllib.request.urlopen(req, timeout=150) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        result = json.load(error)
        raise RuntimeError(f"HTTP {error.code}, 平台码 {result.get('code')}") from error


def routed(headers, node, payload):
    receipt = request(node, "experiment/execute/" + EXPERIMENT, headers, payload)
    data = receipt.get("data") or {}
    trace = data.get("experimentTraceId") or receipt.get("traceId")
    if not data.get("success") and trace:
        end = time.monotonic() + 150
        while time.monotonic() < end:
            status = request(node, "executions/" + trace, headers).get("data") or {}
            if status.get("status") == "SUCCEEDED":
                data = request(node, "executions/" + trace + "/result", headers)["data"]
                break
            if status.get("status") not in {"RUNNING", "WAITING_EXTERNAL", "QUEUED"}:
                raise ValueError(f"订单 {trace} 停在 {status.get('status')}")
            time.sleep(0.5)
    group = data.get("productionGroup") or {}
    if not data.get("success") or not group.get("success"):
        raise ValueError(f"实验执行未成功 {trace} {receipt.get('code')}")
    return data["experimentTraceId"], group


def expected_group(key):
    first = hashlib.md5(f"{EXPERIMENT}:{key}".encode()).digest()[:4]
    return (
        "BASE_30" if int.from_bytes(first, "big", signed=True) % 100 < 30 else "PLUS_70"
    )


def billing_snapshot(connection, child_trace):
    with connection.cursor() as cursor:
        cursor.execute(
            "SELECT action_type,cache_status,provider_request,call_id,success FROM rule_runtime_call_log "
            "WHERE root_trace_id=%s AND target_code='HS_INCOME' "
            "AND action_type IN ('API_INVOKE','API_ATTEMPT') ORDER BY id",
            (child_trace,),
        )
        calls = cursor.fetchall()
        cursor.execute(
            "SELECT billing_code,billing_dedup_key,amount FROM rule_billing_record "
            "WHERE root_trace_id=%s AND api_code='HS_INCOME' ORDER BY id",
            (child_trace,),
        )
        charges = cursor.fetchall()
    return calls, charges


def check_charge(connection, child, first):
    end = time.monotonic() + 15
    while time.monotonic() < end:
        calls, charges = billing_snapshot(connection, child)
        finished = [call for call in calls if call[0] == "API_INVOKE" and call[4]]
        if finished and (not first or charges):
            break
        time.sleep(0.2)
    else:
        raise AssertionError(f"缺少收入调用或账单 {child}")
    if first:
        actual = [call for call in finished if call[1] == "MISS" and call[2]]
        assert len(actual) == len(charges) == 1, child
        code, dedup, amount = charges[0]
        assert (
            dedup == hashlib.sha256(f"API:{actual[0][3]}:{code}".encode()).hexdigest()
            and amount > 0
        ), child
    else:
        assert all(
            call[1] in {"HIT", "COALESCED"} and not call[2] for call in finished
        ), child
        assert not charges and all(call[0] != "API_ATTEMPT" for call in calls), child
    return calls, charges


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--node", action="append", required=True, help="引擎根地址，可重复传入两个节点"
    )
    parser.add_argument(
        "--params", required=True, type=Path, help="可正常通过原授信策略的演示进件 JSON"
    )
    parser.add_argument("--orders", type=int, default=40)
    parser.add_argument("--clients", type=int, default=12)
    parser.add_argument("--base-limit", type=int, default=20000)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument(
        "--check-mysql",
        action="store_true",
        help="用 TIANSHU_QA_MYSQL_* 环境变量连接验收库对账",
    )
    args = parser.parse_args()
    if args.orders < 4 or args.clients < 1:
        parser.error("至少需要 4 笔订单，并发数须为正数")
    if args.report.exists():
        parser.error("请指定新的报告文件")
    data = json.loads(args.params.read_text("utf-8-sig"))
    source = data.get("params", data)
    token = os.environ.get("TIANSHU_PROJECT_TOKEN")
    headers = (
        {"X-Rule-Token": token}
        if token
        else {
            "X-Auth-Code": os.environ.get("TIANSHU_AUTH_CODE", "CREDIT_HTTP"),
            "X-Rule-Api-Key": os.environ["TIANSHU_CREDIT_API_KEY"],
        }
    )
    run = secrets.token_hex(8)
    origin = int(run[:6], 16) % 420

    def execute(i):
        key = f"QA-{run}-{i}"
        rejected = i >= 3 and i % 2 == 1
        params = {
            **source,
            "request_id": key,
            "income_report_id": "REPORT-" + run,
            "device_id": key,
            "gps_latitude": -77 + origin * 0.27 + (i // 900) * 0.09,
            "gps_longitude": -160 + (i % 900) * 0.34,
        }
        if rejected:
            params["contact"] = {
                **source.get("contact", {}),
                "mobile_no": "13900002104",
            }
        payload = {"params": params, "requestKey": key, "clientAppName": key}
        trace, group = routed(headers, args.node[i % len(args.node)], payload)
        output = group["result"]
        expected = expected_group(key)
        assert group["groupCode"] == expected and group["ruleCode"] == (
            "CREDIT_ADMISSION_PLUS" if expected == "PLUS_70" else "CREDIT_ADMISSION"
        ), trace
        assert str(output["result"]) == ("1" if rejected else "0"), trace
        assert output["credit_limit"] == (
            0 if rejected else args.base_limit + (1000 if expected == "PLUS_70" else 0)
        ), trace
        if expected == "PLUS_70":
            assert output["post_limit_blocked"] is rejected, trace
        return {
            "order": key,
            "trace": trace,
            "child": group["traceId"],
            "group": expected,
            "revision": group["revisionId"],
            "result": output,
        }, payload

    connection = None
    if args.check_mysql:
        import pymysql

        prefix = "TIANSHU_QA_MYSQL_"
        connection = pymysql.connect(
            host=os.environ[prefix + "HOST"],
            port=int(os.environ.get(prefix + "PORT", "3306")),
            user=os.environ[prefix + "USER"],
            password=os.environ[prefix + "PASSWORD"],
            database=os.environ[prefix + "DATABASE"],
            autocommit=True,
        )
    try:
        first, _ = execute(0)
        if connection:
            check_charge(connection, first["child"], True)
        second, payload = execute(1)
        before = (
            check_charge(connection, second["child"], False) if connection else None
        )
        trace, replay = routed(headers, args.node[0], payload)
        assert (trace, replay["traceId"], replay["groupCode"], replay["result"]) == (
            second["trace"],
            second["child"],
            second["group"],
            second["result"],
        )
        if connection:
            assert billing_snapshot(connection, second["child"]) == before
        with concurrent.futures.ThreadPoolExecutor(args.clients) as pool:
            rows = [
                first,
                second,
                *(row for row, _ in pool.map(execute, range(2, args.orders))),
            ]
        assert len({row["trace"] for row in rows}) == args.orders
        with args.report.open("x", encoding="utf8") as report:
            json.dump(
                {
                    "experiment": EXPERIMENT,
                    "mysqlChecked": bool(connection),
                    "orders": rows,
                },
                report,
                ensure_ascii=False,
                indent=2,
            )
    finally:
        if connection:
            connection.close()


if __name__ == "__main__":
    main()
