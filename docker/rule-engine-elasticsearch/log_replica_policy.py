"""按部署拓扑维护天枢日志索引的副本策略。使用部署管理员凭据，支持远程 HTTPS。"""
import argparse
import base64
import getpass
import json
import re
import ssl
import urllib.error
import urllib.parse
import urllib.request


KINDS = ("execution-log", "runtime-call", "history-feature", "rule-step")


def reconcile(api, prefix, replicas=None):
    if not re.fullmatch(r"[a-z0-9][a-z0-9-]{0,62}", prefix) or (replicas is not None and replicas < 0):
        raise ValueError("索引前缀或副本数无效")

    component = prefix + "-log-replicas-v1"
    path = "/_component_template/" + component
    found = api("GET", path, optional=True).get("component_templates", [])
    previous = found[0]["component_template"] if found else {}
    meta = previous.get("_meta", {})
    if found and meta.get("managed_by") != "tianshu-log-replicas":
        raise ValueError("已有同名组件模板，请核对归属：" + component)

    if replicas is None:
        if meta.get("mode") == "fixed":
            print("沿用部署管理员指定的副本策略。")
            return
        nodes = api("GET", "/_nodes/settings?filter_path=_nodes,nodes.*.settings.discovery.type")
        counts = nodes["_nodes"]
        if not counts["total"] or counts["successful"] != counts["total"]:
            raise ValueError("节点配置返回不完整，请稍后重试")
        single = counts["total"] == 1 and any(n.get("settings", {}).get("discovery", {}).get("type") == "single-node"
                                               for n in nodes.get("nodes", {}).values())
        if not single and not found:
            print("集群使用多节点发现，保留部署方的副本策略。")
            return
        settings = {"index.auto_expand_replicas": "0-1"} if single else {
            "index.auto_expand_replicas": "false", "index.number_of_replicas": "1"}
    else:
        settings = {"index.auto_expand_replicas": "false", "index.number_of_replicas": str(replicas)}

    def put(resource, body):
        if api("PUT", resource, body).get("acknowledged") is not True:
            raise RuntimeError("ES 尚未确认更新，请重试：" + resource)

    put(path, {"template": {"settings": settings},
               "_meta": {"managed_by": "tianshu-log-replicas", "mode": "discovery" if replicas is None else "fixed"}})

    # 新 server 在初始化映射时也会声明这个组件。已存在的旧模板仅补上引用。
    for kind in KINDS:
        name = f"{prefix}-{kind}-v1"
        template_path = "/_index_template/" + name
        existing = api("GET", template_path, optional=True).get("index_templates", [])
        if existing:
            template = existing[0]["index_template"]
            if template["index_patterns"] != [name + "-*"]:
                raise ValueError("同名索引模板的匹配范围与部署不符：" + name)
            if component not in template.get("composed_of", []):
                template.setdefault("composed_of", []).append(component)
                put(template_path, template)

        indices = api("GET", "/" + name + "-*/_settings/index.number_of_replicas,index.auto_expand_replicas"
                      "?flat_settings=true&expand_wildcards=open,closed&allow_no_indices=true&ignore_unavailable=true")
        for index, data in indices.items():
            if not re.fullmatch(re.escape(name) + r"-[0-9]{4}\.(0[1-9]|1[0-2])", index):
                continue
            actual = data["settings"]
            if all(actual.get(key, "false") == value for key, value in settings.items()):
                continue
            # Only adopt the ES default, or transition back from our single-node policy.
            if replicas is None and not (
                (single and actual.get("index.auto_expand_replicas", "false") == "false"
                 and actual.get("index.number_of_replicas") == "1")
                or (not single and actual.get("index.auto_expand_replicas") == "0-1")
            ):
                print("保留索引已有的自定义副本设置", index)
                continue
            put("/" + index + "/_settings", settings)
            print("已更新", index)
    print(component, settings)


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def client(url, username, pw, ca_file=None):
    transport = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect(),
                                            urllib.request.HTTPSHandler(context=ssl.create_default_context(cafile=ca_file)))
    auth = base64.b64encode(f"{username}:{pw}".encode()).decode()

    def api(method, path, body=None, *, optional=False):
        req = urllib.request.Request(url.rstrip("/") + path, method=method,
                                     data=json.dumps(body).encode() if body is not None else None,
                                     headers={"Authorization": "Basic " + auth, "Content-Type": "application/json"})
        try:
            with transport.open(req, timeout=45) as response:
                return json.load(response)
        except urllib.error.HTTPError as error:
            if optional and error.code == 404:
                return {}
            raise RuntimeError(f"{method} {path} 返回 HTTP {error.code}") from None

    return api


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--url", default="http://127.0.0.1:9200")
    parser.add_argument("--ca-file", help="HTTPS 私有 CA 证书")
    parser.add_argument("--username", default="elastic")
    parser.add_argument("--prefix", default="tianshu")
    parser.add_argument("--replicas", type=int, help="显式指定固定副本数，默认根据 discovery.type 处理")
    args = parser.parse_args()
    url = urllib.parse.urlsplit(args.url)
    if (url.scheme not in ("http", "https") or not url.hostname or url.username is not None
            or url.query or url.fragment):
        parser.error("--url 需要不带凭据、查询串和片段的 ES 地址")
    if url.scheme == "http" and url.hostname not in ("127.0.0.1", "::1", "localhost"):
        parser.error("远程管理员连接请使用 HTTPS，或通过 SSH 转发连接回环地址")
    api = client(args.url, args.username, getpass.getpass(f"{args.username} 当前 ES 密码："), args.ca_file)
    reconcile(api, args.prefix, args.replicas)


if __name__ == "__main__":
    main()
