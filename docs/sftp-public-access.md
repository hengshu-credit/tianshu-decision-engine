# 天枢 SFTP 公网接入

外部上传方使用 SSH/SFTP 客户端连接“域名 + TCP 端口”。例如 `sftp.hengshucredit.com:2222`。域名仅为示例，需解析到实际的公网入口。天枢 Web 和 Elasticvue 可以继续使用原来的 HTTPS 域名和路径。

## 方式一 · 直接发布 SFTP 端口

适用于公网地址或边界 NAT 可将流量送到存储主机的环境。Docker 发布包默认绑定 `127.0.0.1`。在**存储主机生效的** `.env.server` 中设置：

~~~dotenv
ENGINE_SFTP_BIND_ADDRESS=0.0.0.0
ENGINE_SFTP_PORT=2222
~~~

`0.0.0.0` 监听宿主机全部 IPv4 接口。多网卡环境可改成实际接收入站流量的本机地址。云主机经公网 NAT 时通常是其私网网卡地址。客户端通过公网地址连接，引擎仍可按部署方式使用 `sftp:2222`（共享 Docker 网络）、宿主机回环（同机 JAR 且监听覆盖回环）或存储主机内网地址。

在原安装目录按原项目名、原卷和原覆盖文件重建 SFTP。下例对应[三种部署方式速查](deployment-quickstart.md)的共享网络配置。已整理布局将 `.env.server` 替换为 `config/.env.server`。如果原部署仅使用主 Compose 文件，省略第二个 `-f`。

~~~bash
docker compose --env-file .env.server -f docker/docker-compose.sftp.yml -f docker/docker-compose.sftp.engine-network.yml up -d --wait sftp
docker compose --env-file .env.server -f docker/docker-compose.sftp.yml -f docker/docker-compose.sftp.engine-network.yml port sftp 2222
~~~

预期映射为 `0.0.0.0:2222` 或指定接口的 `IP:2222`。在云安全组或边界防火墙允许合作方出口 IP 访问 TCP 2222；如有 NAT，配置“公网 TCP 2222 → 存储主机 TCP 2222”。对外需要用其他端口时，例如 22222，可在边界设置“TCP 22222 → TCP 2222”，引擎的内部端口继续填 2222。包内 SFTP 容器固定监听 2222，共用 Docker 网络的业务容器也会读取 ENGINE_SFTP_PORT；将公网端口填到这里会让业务容器连错端口。

## 方式二 · 通过现有 Nginx Proxy Manager 转发

适用于公网 IP 位于 NPM 网关的环境。SFTP 使用 NPM 的 **Streams → Add Stream**，走独立 TCP 端口；HTTP Proxy Host 的 Custom Location 用于 Web 路径。此例使用公网 `22222` 和 SFTP 容器 `2222`。

1. 在 NPM 自身 Compose 服务原有的 `ports` 列表追加 `"22222:22222/tcp"`，按该 NPM 部署的原 Compose 参数重建代理服务。安全组在 NPM 入口允许合作方出口 IP 访问 TCP 22222。
2. 同一 Docker 主机上，若 NPM 与 SFTP 已共享引擎网络，转发主机可直接填 `sftp`。若 NPM 使用独立网络，可在天枢服务端 env 中新增 `NPM_NETWORK=实际代理网络名`，并使用下面的覆盖文件。在这种方式下，SFTP 的宿主机监听可以保留 `ENGINE_SFTP_BIND_ADDRESS=127.0.0.1`。
3. 在 Stream 中设置 **Incoming Port** 为 `22222`，**Forward Host** 为共享网络中的 SFTP 名称，**Forward Port** 为 `2222`，启用 **TCP Forwarding**，关闭 UDP。下面的覆盖文件为 SFTP 指定了代理侧别名 `tianshu-sftp`，使用该文件时 Forward Host 填 `tianshu-sftp`。
4. 若 NPM 和 SFTP 在不同主机，将 SFTP 绑定到其存储主机可达的私网接口，Forward Host 填该私网 IP，并允许来自 NPM 的 TCP 2222。Docker 服务名只在相应 Docker 网络内可用。经过 TCP 代理时，SFTP 的来源 IP 限速和封禁可能把多个上传方计作同一来源，按来源放行应在公网入口执行。

同机独立网络示例，沿用速查手册里名为 `engine` 的 SFTP 后端网络：

~~~bash
cat > docker/docker-compose.sftp.npm-network.yml <<'YAML'
services:
  sftp:
    networks:
      engine: {}
      npm-ingress:
        aliases: [tianshu-sftp]
networks:
  npm-ingress:
    external: true
    name: ${NPM_NETWORK:?Set NPM_NETWORK to the existing NPM network}
YAML
docker compose --env-file .env.server -f docker/docker-compose.sftp.yml -f docker/docker-compose.sftp.engine-network.yml -f docker/docker-compose.sftp.npm-network.yml up -d --wait sftp
~~~

## 开通外部账号并验收

合作机构使用 Basic、API Key 或 HMAC 凭据时，在规则管理或分流管理的「机构鉴权」中开通 SFTP，可直接使用标准客户端进行一次 SSH 密码登录。[机构凭据与登录名](institution-sftp.md)

在 **SFTP 管理 → 引擎 SFTP 服务配置 → 上传账户** 中，选择获授权的控制台账户，登记对方的单行 OpenSSH 公钥，并选择只读或读写。上传方保管私钥。个人目录示例登录名为 `u42`，已获项目 17 权限的账户可用 `u42-p17` 进入自己的项目交件目录。实际登录名以控制台和项目授权为准。`engine-storage` 和 `ENGINE_SFTP_PASSWORD` 是引擎服务凭据，只保存在部署侧。

从可信存储主机查看并向对方提供主机指纹。首次连接时，请核对客户端显示的 SHA-256 指纹。

~~~bash
docker compose --env-file .env.server -f docker/docker-compose.sftp.yml logs --no-color sftp | grep 'SFTP host fingerprint' | tail -1
~~~

用合作方的**外网网络**执行下列命令，替换用户名、域名和私钥路径。直接发布用 2222，NPM 示例用 22222。进入后执行 `pwd`、`ls`；有写入权限的账号可用唯一文件名上传并读回一个测试文件。

~~~bash
sftp -o IdentitiesOnly=yes -P 2222 -i ~/.ssh/tianshu_upload u42-p17@sftp.hengshucredit.com
~~~

如果连接超时，沿“DNS → 公网 TCP 放行/NAT → NPM Stream 或宿主机映射”检查；如果已经出现 SSH 主机指纹但登录失败，核对登录名、公钥、项目授权，以及控制台中的封禁记录。保留原 SFTP 数据卷，避免重建服务时改变文件和主机身份。
