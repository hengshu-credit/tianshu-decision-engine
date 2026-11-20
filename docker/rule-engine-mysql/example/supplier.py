"""本地合成外数的协议与 SFTP 服务。业务值由已发布的天枢供应商规则产生。"""

from __future__ import annotations
import base64, hashlib, hmac, io, json, logging, os, re, secrets, socket, stat, threading, time
from urllib.request import parse_http_list, parse_keqv_list
import flask, jwt, paramiko, requests
from cryptography.hazmat.primitives import serialization
from werkzeug.serving import make_server

from credentials import read_supplier_keys

app = flask.Flask(__name__)
app.config["MAX_CONTENT_LENGTH"] = 65536
ctx = flask.request

keys = read_supplier_keys()
mutex = threading.RLock()
host_key = paramiko.RSAKey.from_private_key(io.StringIO(keys["sftp_host_key"]))
public_key = serialization.load_pem_private_key(
    keys["fraud"].encode(), password=None
).public_key()
catalog = {
    "court": ("法院数据", "Basic", 0.10, ("court_dishonest", "court_enforced")),
    "black": ("黑名单数据", "Bearer", 0.14, ("external_black",)),
    "late": ("逾期数据", "API Key", 0.18, ("external_overdue_days",)),
    "multi": (
        "多头数据",
        "HMAC-SHA256 请求签名",
        0.22,
        ("multi_1m", "multi_3m", "multi_6m", "multi_12m"),
    ),
    "income": ("收入数据", "OAuth 2.0 客户端凭证", 0.26, ("verified_income",)),
    "fraud": ("欺诈评分数据", "RSA 签名 JWT", 0.30, ("fraud_score",)),
    "credit": ("信用评分数据", "Cookie 会话", 0.35, ("credit_score",)),
    "pboc": ("人行征信数据", "Digest SHA-256", 0.40, ("pboc_bad_debt",)),
}
engine_endpoint = os.environ["EXAMPLE_ENGINE_URL"].rstrip("/") + "/api/rule/sync/execute/"
routes = {
    "court": ("MOCK_COURT", "matched"),
    "black": ("MOCK_BLACK", "isHitData"),
    "late": ("MOCK_OVERDUE", "located"),
    "multi": ("MOCK_MULTI", "available"),
    "income": ("MOCK_INCOME", "found"),
    "fraud": ("MOCK_FRAUD", "matched"),
    "credit": ("MOCK_CREDIT", "available"),
    "pboc": ("MOCK_PBOC", "reportFound"),
}
http_session = requests.Session()
http_session.trust_env = False
# 所有令牌、查询和文件读取仅在本机演示窗口内生效。
oauth, session_tokens, digest_nonces, seen, jobs = {}, {}, {}, {}, {}
counters = {k: {"accepted": 0, "denied": 0} for k in catalog}


def eq(a, b):
    return hmac.compare_digest(str(a).encode(), str(b).encode())


def gc():
    now = time.time()
    for d in [oauth, session_tokens, digest_nonces, seen, jobs]:
        for key, entry in list(d.items()):
            if entry[0] < now:
                del d[key]


def new_token(store, ttl, info=""):
    key = secrets.token_urlsafe(26)
    with mutex:
        gc()
        store[key] = (time.time() + ttl, info)
    return key


def new_use(key, ttl):
    with mutex:
        gc()
        if key in seen:
            return False
        seen[key] = (time.time() + ttl, True)
        return True


def sha(s):
    return hashlib.sha256(s.encode()).hexdigest()


def valid(store, key):
    with mutex:
        gc()
        return key in store


def challenge():
    nonce = new_token(digest_nonces, 180)
    challenge_value = (
        f'Digest realm="demo-pboc", nonce="{nonce}", algorithm=SHA-256, qop="auth"'
    )
    return nonce, challenge_value


def authorize(source, body):
    auth = ctx.headers.get("Authorization", "")
    if source == "court":
        return eq(
            auth,
            "Basic "
            + base64.b64encode(("court_client:" + keys["court"]).encode()).decode(),
        )
    if source == "black":
        return eq(auth, "Bearer " + keys["black"])
    if source == "late":
        return eq(ctx.headers.get("X-Supplier-Key", ""), keys["late"])
    if source == "income":
        return auth.startswith("Bearer ") and valid(oauth, auth[7:])
    if source == "credit":
        return valid(session_tokens, ctx.cookies.get("hscredit_session", ""))
    if source == "multi":
        ts = ctx.headers.get("X-Request-Time", "")
        nonce = ctx.headers.get("X-Request-Nonce", "")
        if (
            not re.fullmatch(r"[0-9]{13}", ts)
            or abs(time.time() - int(ts) / 1000) > 90
            or not re.fullmatch(r"[0-9a-zA-Z_-]{16,80}", nonce)
        ):
            return False
        source_text = "\n".join(
            [ctx.method, ctx.path, ts, nonce]
            + [
                str(body.get(k, ""))
                for k in ["name", "idcard_no", "mobile_no", "request_id"]
            ]
        )
        sig = base64.b64encode(
            hmac.digest(keys["multi"].encode(), source_text.encode(), "sha256")
        ).decode()
        return eq(ctx.headers.get("X-Request-Signature", ""), sig) and new_use(
            ("hmac", nonce), 180
        )
    if source == "fraud":
        try:
            if not auth.startswith("JWT "):
                return False
            claims = jwt.decode(
                auth[4:],
                public_key,
                algorithms=["RS256"],
                issuer="demo-fraud-client",
                audience="demo-fraud",
                options={"require": ["exp", "iat", "jti", "req"]},
            )
            if (
                not 0 < claims["exp"] - claims["iat"] <= 120
                or abs(time.time() - claims["iat"]) > 120
            ):
                return False
            return eq(claims["req"], body.get("request_id", "")) and new_use(
                ("jwt", str(claims["jti"])), 180
            )
        except (ValueError, TypeError, jwt.PyJWTError):
            return False
    if source == "pboc":
        if not auth.startswith("Digest "):
            return False
        a = parse_keqv_list(parse_http_list(auth[7:]))
        if (
            a.get("username") != "pboc_client"
            or a.get("realm") != "demo-pboc"
            or a.get("algorithm", "").upper() != "SHA-256"
            or a.get("uri") != ctx.path
            or a.get("qop") != "auth"
        ):
            return False
        nonce = a.get("nonce", "")
        cn = a.get("cnonce", "")
        nc = a.get("nc", "")
        if (
            not valid(digest_nonces, nonce)
            or not re.fullmatch("[a-fA-F0-9]{8}", nc)
            or not cn
        ):
            return False
        ha1 = sha("pboc_client:demo-pboc:" + keys["pboc"])
        ha2 = sha(ctx.method + ":" + ctx.path)
        expected = sha(":".join([ha1, nonce, nc, cn, "auth", ha2]))
        return eq(expected, a.get("response", "")) and new_use(
            ("digest", nonce, cn, nc), 180
        )
    return False


@app.after_request
def cache_control(response):
    response.headers["Cache-Control"] = "no-store"
    response.headers["X-Content-Type-Options"] = "nosniff"
    response.headers["X-Frame-Options"] = "DENY"
    return response


@app.post("/oauth/token")
def token():
    inp = ctx.get_json(silent=True) or ctx.form
    if (
        inp.get("grant_type") != "client_credentials"
        or inp.get("client_id") != "income_client"
        or not eq(inp.get("client_secret", ""), keys["income"])
    ):
        return {"error": "invalid_client"}, 401
    return dict(access_token=new_token(oauth, 300), expires_in=300, token_type="Bearer")


@app.post("/credit/login")
def credit_login():
    inp = ctx.get_json(silent=True) or ctx.form
    if inp.get("username") != "credit_client" or not eq(
        inp.get("password", ""), keys["credit"]
    ):
        return {"error": "invalid_login"}, 401
    token = new_token(session_tokens, 300)
    res = flask.jsonify(cookie="hscredit_session=" + token, expires_in=300)
    res.set_cookie(
        "hscredit_session",
        token,
        httponly=True,
        samesite="Strict",
        max_age=300,
        path="/data/credit",
    )
    return res


@app.route("/pboc/challenge", methods=["GET", "POST"])
def pboc_challenge():
    nonce, h = challenge()
    return (
        {"realm": "demo-pboc", "nonce": nonce, "algorithm": "SHA-256", "qop": "auth"},
        200,
        {"WWW-Authenticate": h},
    )


def check(source, body):
    ok = authorize(source, body)
    with mutex:
        counters[source]["accepted" if ok else "denied"] += 1
    if not ok:
        if source == "pboc":
            return (
                {"error": "digest_challenge"},
                401,
                {"WWW-Authenticate": challenge()[1]},
            )
        return {"error": "invalid_credentials"}, 401


def supplier_logic(source, body):
    if not keys["engine"]:
        raise RuntimeError("供应商项目令牌待绑定")
    params = {k: body[k] for k in ["request_id", "name", "idcard_no", "mobile_no"]}
    rule_code, found_field = routes[source]
    try:
        res = http_session.post(
            engine_endpoint + rule_code,
            json={
                "params": params,
                "clientAppName": "本地模拟供应商",
                "traceEnabled": False,
            },
            auth=("demo_supplier_gateway", keys["engine"]),
            allow_redirects=False,
            timeout=20,
        )
        out = res.json()
        if res.status_code != 200 or out.get("success") is not True:
            raise RuntimeError("引擎供应商规则尚未成功执行")
        data = out.get("data") or {}
        if data.get("success") is not True:
            raise RuntimeError("引擎供应商规则执行失败")
        result = data.get("result")
        if not isinstance(result, dict):
            raise RuntimeError("引擎供应商规则未返回对象")
        payload = result.get("supplier_payload")
        if not isinstance(payload, dict) or not isinstance(payload.get(found_field), bool):
            raise RuntimeError("供应商输出缺少结构化报文或明确的查得标志")
        return payload
    except (requests.RequestException, ValueError) as ex:
        raise RuntimeError("引擎供应商服务不可用") from ex


def validate_body(b):
    if not isinstance(b, dict) or not all(
        isinstance(b.get(k), str) and b[k].strip() and len(b[k]) <= 128
        for k in ["request_id", "name", "idcard_no", "mobile_no"]
    ):
        return {"error": "requires request_id, name, idcard_no, mobile_no"}, 400


@app.post("/data/<source>")
def data(source):
    if source not in catalog or source == "pboc":
        flask.abort(404)
    body = ctx.get_json(silent=True) or {}
    err = check(source, body) or validate_body(body)
    if err:
        return err
    try:
        return supplier_logic(source, body)
    except RuntimeError as e:
        return {"code": "SUPPLIER_UNAVAILABLE", "message": str(e)}, 503


@app.post("/pboc/apply")
def pboc_apply():
    b = ctx.get_json(silent=True) or {}
    err = check("pboc", b) or validate_body(b)
    if err:
        return err
    ident = hmac.digest(
        keys["pboc"].encode(),
        json.dumps(
            [b[k] for k in ["request_id", "name", "idcard_no", "mobile_no"]],
            ensure_ascii=False,
        ).encode(),
        "sha256",
    ).hex()[:32]
    with mutex:
        gc()
        if ident not in jobs:
            try:
                values = supplier_logic("pboc", b)
            except RuntimeError as e:
                return {"code": "SUPPLIER_UNAVAILABLE", "message": str(e)}, 503
            if not isinstance(values.get("billable"), bool):
                return {"code": "INVALID_SUPPLIER_RESPONSE", "message": "缺少布尔受理计费标志"}, 502
            delivery_failed = values.get("deliveryFailed", False)
            if not isinstance(delivery_failed, bool):
                return {"code": "INVALID_SUPPLIER_RESPONSE", "message": "报告交付状态类型错误"}, 502
            report = None if delivery_failed else json.dumps(
                {"serial": ident, "reportFound": values["reportFound"], "report": values["report"]},
                ensure_ascii=False,
            ).encode()
            jobs[ident] = (
                time.time() + 3600,
                {
                    "ready": time.time() + (15 if b["request_id"].startswith("DEMO-PBOC-RESUME-") else 2.5),
                    "test_resume": b["request_id"].startswith("DEMO-PBOC-RESUME-"),
                    "data": report,
                    "found": values["reportFound"],
                    "billable": values["billable"],
                },
            )
        if jobs[ident][1]["test_resume"]:
            print(json.dumps({"step": "apply", "serial": ident, "billable": jobs[ident][1]["billable"]}), flush=True)
        return {"code": "0000", "serial": ident, "billable": jobs[ident][1]["billable"]}


@app.post("/pboc/status")
def pboc_status():
    b = ctx.get_json(silent=True) or {}
    err = check("pboc", b)
    if err:
        return err
    with mutex:
        gc()
        j = jobs.get(b.get("serial", ""))
    if not j:
        return {"code": "UNKNOWN_SERIAL"}, 404
    if j[1]["test_resume"]:
        print(json.dumps({"step": "poll", "serial": b["serial"], "pending": time.time() < j[1]["ready"]}), flush=True)
    if time.time() < j[1]["ready"]:
        return {"code": "PENDING", "serial": b["serial"]}
    if j[1]["data"] is None:
        return {"code": "REPORT_FAILED", "serial": b["serial"], "message": "已受理，报告生成失败"}
    return {
        "code": "READY",
        "reportFound": j[1]["found"],
        "serial": b["serial"],
        "report_path": "/reports/" + b["serial"] + ".json",
    }


class SshAuth(paramiko.ServerInterface):
    def check_auth_password(self, username, password):
        return (
            paramiko.AUTH_SUCCESSFUL
            if username == "pboc_sftp" and eq(password, keys["sftp"])
            else paramiko.AUTH_FAILED
        )

    def get_allowed_auths(self, username):
        return "password"

    def check_channel_request(self, kind, chanid):
        return (
            paramiko.OPEN_SUCCEEDED
            if kind == "session"
            else paramiko.OPEN_FAILED_ADMINISTRATIVELY_PROHIBITED
        )


class Reports(paramiko.SFTPServerInterface):
    def _read(self, path):
        m = re.fullmatch(r"/reports/([0-9a-f]{32})\.json", path)
        if not m:
            return None
        with mutex:
            gc()
            j = jobs.get(m.group(1))
            if j and time.time() >= j[1]["ready"]:
                return j[1]["data"]

    def stat(self, path):
        attr = paramiko.SFTPAttributes()
        if path in ["/", "/reports"]:
            attr.st_mode = stat.S_IFDIR | 0o550
            return attr
        data = self._read(path)
        if data is None:
            return paramiko.SFTP_NO_SUCH_FILE
        attr.st_mode = stat.S_IFREG | 0o440
        attr.st_size = len(data)
        return attr

    lstat = stat

    def open(self, path, flags, attr):
        if flags & (os.O_WRONLY | os.O_RDWR | os.O_APPEND | os.O_CREAT | os.O_TRUNC):
            return paramiko.SFTP_PERMISSION_DENIED
        data = self._read(path)
        if data is None:
            return paramiko.SFTP_NO_SUCH_FILE
        handle = paramiko.SFTPHandle(flags)
        handle.readfile = io.BytesIO(data)
        return handle

    def list_folder(self, path):
        if path not in ["/", "/reports"]:
            return paramiko.SFTP_NO_SUCH_FILE
        with mutex:
            gc()
            names = (
                ["reports"]
                if path == "/"
                else [
                    k + ".json"
                    for k, v in jobs.items()
                    if v[1]["data"] is not None and v[1]["ready"] <= time.time() < v[0]
                ]
            )
        out = []
        for n in names:
            a = self.stat("/reports" if path == "/" else "/reports/" + n)
            if not isinstance(a, int):
                a.filename = n
                out.append(a)
        return out


def serve_sftp(sock):
    def worker(conn):
        with paramiko.Transport(conn) as t:
            t.add_server_key(host_key)
            t.set_subsystem_handler("sftp", paramiko.SFTPServer, Reports)
            try:
                t.start_server(server=SshAuth())
                while t.is_active():
                    time.sleep(0.25)
            except (paramiko.SSHException, OSError, EOFError):
                pass

    while True:
        conn, _ = sock.accept()
        threading.Thread(target=worker, args=(conn,), daemon=True).start()


if __name__ == "__main__":
    logging.getLogger("werkzeug").setLevel(logging.ERROR)
    logging.getLogger("paramiko").setLevel(logging.CRITICAL)
    # 先占用两个端口再对外提供服务，避免只启动一半。
    httpd = make_server("0.0.0.0", 28084, app, threaded=True)
    with socket.socket() as sock:
        sock.bind(("0.0.0.0", 22084))
        sock.listen(16)
        threading.Thread(target=serve_sftp, args=(sock,), daemon=True).start()
        httpd.serve_forever()
