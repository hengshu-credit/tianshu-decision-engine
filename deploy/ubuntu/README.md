# Ubuntu 主机与 Nginx Proxy Manager 部署

本部署方式不安装、修改或启用 UFW、Fail2ban、宿主机 Nginx、Certbot 和 Ubuntu 现有端口规则。Ubuntu 当前的入站/出站策略由管理员或云安全组维护；本项目只配置 Compose 服务本身。

先准备 `.env`：

```bash
cp .env.example .env
vi .env
```

远程调试需要外部访问 MySQL、Redis 时，将下面两个值保持为 `0.0.0.0`：

```dotenv
MYSQL_BIND_ADDRESS=0.0.0.0
REDIS_BIND_ADDRESS=0.0.0.0
```

server、web 和 HTTP/SDK runtime 仍可保持 `127.0.0.1`，由 Nginx Proxy Manager 反向代理。若 Nginx Proxy Manager 运行在 Docker 中，把它加入 Compose 的 `${DEPLOY_PROJECT}-engine` 网络，直接代理到 `web:80` 和 `http:7070`；若运行在宿主机，则代理到 `127.0.0.1:9090` 和 `127.0.0.1:7070`。

## Nginx Proxy Manager

在 Nginx Proxy Manager 中创建两个 Proxy Host：

| 域名 | Scheme | Forward Host | Forward Port |
|---|---|---|---:|
| `console.example.com` | `http` | `web` 或 `127.0.0.1` | `80` 或 `9090` |
| `api.example.com` | `http` | `http` 或 `127.0.0.1` | `7070` |

分别在 SSL 页面申请证书并启用 `Force SSL`、HTTP/2 和 HSTS。管理端使用 `https://console.example.com/`，项目执行使用 `https://api.example.com/api/rule/execute/{ruleCode}`。

Nginx Proxy Manager 的 Advanced 配置中应保留以下转发头，并按 NPM 版本支持情况加入限流配置：

```nginx
proxy_set_header X-Real-IP $remote_addr;
proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
proxy_set_header X-Forwarded-Proto $scheme;
proxy_set_header Host $host;
```

项目执行必须携带 runtime 的 `Authorization: Bearer ...`。服务端项目 Token 已有 Redis 失败计数、锁定和执行保护；MySQL、Redis、Kafka 的网络访问由 Ubuntu 现有安全组/防火墙策略负责，本部署不会覆盖这些规则。

## Kafka

完整 Compose 和 `docker-compose.kafka.yml` 都提供一个单节点 KRaft Kafka。配置 `KAFKA_ADVERTISED_HOST`、`KAFKA_USERNAME`、`KAFKA_PASSWORD` 后，容器内使用 `kafka:19092`，外部调试使用 `KAFKA_ADVERTISED_HOST:KAFKA_HOST_PORT`。默认是 SASL/PLAINTEXT，生产应使用 Compose override 配置 SASL_SSL/TLS。Nginx Proxy Manager 只代理 HTTP/HTTPS，不能代替 Kafka TCP 代理。

## 启动与重启

```bash
docker compose --project-name tianshu-full --env-file .env -f docker/docker-compose.full.yml up -d --scale server=2
docker compose --project-name tianshu-full --env-file .env -f docker/docker-compose.full.yml ps
```

容器使用 `restart: unless-stopped`。需要服务器重启后自动拉起时，可按现有运维规范安装 `tianshu-compose.service`；该单元只管理 Compose，不修改 Ubuntu 端口策略。
