"""为本机天枢 Elasticsearch 创建前缀受限的服务账号，重复执行时核对原凭据。"""
import argparse
import getpass
import re

from log_replica_policy import client, reconcile


def provision(port, prefix, admin_password, app_password):
    if not 1 <= port <= 65535 or not re.fullmatch(r"[a-z0-9][a-z0-9-]{0,62}", prefix):
        raise ValueError("请核对端口和索引前缀")
    user = prefix + "_logs"
    role = prefix + "_log_access"

    url = f"http://127.0.0.1:{port}"
    api = client(url, "elastic", admin_password)
    service_api = client(url, user, app_password)

    index_names = [f"{prefix}-{kind}-v1-*" for kind in ("execution-log", "runtime-call", "history-feature", "rule-step")]
    definition = {"cluster": ["manage_index_templates"],
                  "indices": [{"names": index_names, "privileges": ["create_index", "index", "read", "maintenance"]}]}
    old_user = api("GET", "/_security/user/" + user, optional=True).get(user)
    if old_user and (old_user.get("roles") != [role] or not old_user.get("enabled")):
        raise ValueError("同名用户的角色或启用状态与交付配置不符，请先核对")
    if old_user:
        service_api("GET", "/_security/_authenticate")
    old_role = api("GET", "/_security/role/" + role, optional=True).get(role)
    if old_role:
        rules = old_role.get("indices", [])
        if (set(old_role.get("cluster", [])) != set(definition["cluster"]) or len(rules) != 1
            or set(rules[0].get("names", [])) != set(index_names)
            or set(rules[0].get("privileges", [])) != set(definition["indices"][0]["privileges"])
            or any(old_role.get(k) for k in ("run_as", "applications", "global", "remote_indices", "remote_cluster"))
            or any(rules[0].get(k) for k in ("query", "field_security", "allow_restricted_indices"))):
            raise ValueError("同名角色已存在且授权不同，请先核对")
    else:
        api("PUT", "/_security/role/" + role, definition)
    if not old_user:
        api("PUT", "/_security/user/" + user, {"password": app_password, "roles": [role]})
    if service_api("GET", "/_security/_authenticate").get("username") != user:
        raise RuntimeError("服务账号复核失败")
    reconcile(api, prefix)
    return user


if __name__ == "__main__":
    args = argparse.ArgumentParser(description=__doc__)
    args.add_argument("--port", type=int, default=9200)
    args.add_argument("--prefix", default="tianshu")
    opt = args.parse_args()
    boot = getpass.getpass("elastic 当前密码：")
    secret = getpass.getpass("本前缀服务账号的密码（新建时设置，已有时核对）：")
    if len(secret) < 6:
        args.error("服务账号密码至少需要 6 个字符")
    print("服务账号已验证", provision(opt.port, opt.prefix, boot, secret))
