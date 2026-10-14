# Nginx Proxy Manager 接入

本项目不接管 Ubuntu 的 Nginx、UFW、Fail2ban 或证书。使用 Nginx Proxy Manager 完成 Proxy Host、HTTPS 证书、强制 HTTPS 和访问控制。

## 代理目标

- 控制台：`web:80`（NPM 与 Compose 在同一个 Docker 网络），或宿主机 `127.0.0.1:9090`
- 项目 HTTP runtime：`http:7070`（同网络），或宿主机 `127.0.0.1:7070`
- SDK runtime：默认只供内网使用，使用 `sdk:7071` 或宿主机 `127.0.0.1:7071`
- 管理后端：由 web 容器内部的 Docker DNS upstream 发现 `server` 的多个副本；不要把 server 8080 端口映射到宿主机。

如果 NPM 是独立 Docker Compose，执行 `docker network connect tianshu-engine <npm-container>`，或把 NPM Compose 加入名为 `${DEPLOY_PROJECT}-engine` 的 external network。

## HTTPS 与访问控制

在 NPM 中分别创建控制台域名和项目 API 域名，申请证书并启用 Force SSL、HTTP/2、HSTS。项目 API 的 Access List、来源 IP 限制和请求频率限制应在 NPM 中按业务客户端来源配置；服务端仍会校验 runtime Bearer Token、项目白名单和 Redis 失败锁定。

MySQL、Redis 和 Kafka 是 TCP 服务，不能通过普通 Proxy Host 代理。它们是否允许外部访问由 Compose 端口绑定和 Ubuntu 现有防火墙/云安全组决定；本包不会修改后者。Kafka 默认通过宿主机 `9092` 提供 SASL/PLAINTEXT 调试入口，生产环境应改成 SASL_SSL/TLS。
