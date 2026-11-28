# 天枢部署速查

以下命令适用于 Linux Bash，服务配置文件名为 **.env.server**。从发布包部署，服务器需要 Docker Engine、支持 up --wait 的 Docker Compose 插件、Python 3 和 sha256sum；直接运行 JAR 还需 Java 17。命令中的 ZIP 以已交付版本 bb568f75 为例，发新版时换成实际文件名。

| 方式 | 基础服务 | 天枢应用 | 日常更新 |
| --- | --- | --- | --- |
| A · JAR 直跑 | 可用已有服务，也可按第 2 节启动 | 宿主机 Java 运行 server，静态站点提供 Web | 停 Java 服务，切换 JAR 与 dist，再启动 |
| B · 基础服务与天枢分开 | MySQL、Redis、Kafka、ES、SFTP、ClamD 独立 Compose | server Compose 提供 Web 和多个 server 副本 | 安装目录执行 install-release.sh |
| C · full | full 提供 MySQL、Redis、Kafka；ES、SFTP、ClamD 单独启动 | full 同时提供 Web、多个 server、可选 SDK | 同一个 install-release.sh，自动识别已运行的 full 拓扑 |

## 1. 打包与首次准备

**每次发版**在源码仓库根目录运行。Docker 构建输出在 output/，包括引擎 ZIP、同名 .sha256 和 install-release.sh。若本次打包也要执行测试，使用第二条命令，两条任选其一。

~~~bash
node scripts/package-deployment.mjs
# 或
node scripts/package-deployment.mjs --with-tests
~~~

**首次部署**将引擎 ZIP 和同名 .sha256 上传到新建的空安装目录，在该目录执行。以下选用平铺布局；已整理为共享布局的现有服务器，生效文件为 config/.env.server 和 config/.env.client，保留其原项目名、Compose 覆盖文件、运行目录和卷。全新安装用下面的准备命令一次即可。

~~~bash
set -e
ZIP=tianshu-deployment-20261128T165733035Z.zip
if [ -e .env.server ] || [ -e docker ]; then echo '请在全新的安装目录执行首次准备'; exit 1; fi
sha256sum -c "$ZIP.sha256"
STAGE=$(mktemp -d)
python3 -X utf8 -m zipfile -e "$ZIP" "$STAGE"
(cd "$STAGE" && sha256sum --quiet -c SHA256SUMS.txt)
cp -a "$STAGE/docker" .
install -m 0755 "$STAGE/install-release.sh" ./install-release.sh
install -m 0600 "$STAGE/.env.server.example" .env.server
# full 或另行部署 SDK 时执行
install -m 0600 "$STAGE/.env.client.example" .env.client
# 给独立 SFTP 保留稳定的 JAR 路径
install -D -m 0644 docker/runtime/server.jar docker/rule-engine-sftp/server.jar
~~~

编辑配置后再启动。模板中的示例密码、密钥和域名须换成现场值。

| 配置 | 如何填 |
| --- | --- |
| 拓扑与端口 | 同机示例统一使用 DEPLOY_PROJECT=tianshu-server、ENGINE_NETWORK=tianshu-server-engine、RUNTIME_DIR=./runtime、SERVER_REPLICAS=2。Web 默认 9090，API 默认 8080。 |
| MySQL、Redis | 设置 MYSQL_DATABASE、MYSQL_USERNAME、MYSQL_PASSWORD、MYSQL_ROOT_PASSWORD、REDIS_PASSWORD。应用数据库账号需有本库初始化和升级 DDL 权限。 |
| Kafka | 设置 KAFKA_USERNAME、KAFKA_PASSWORD、KAFKA_ADVERTISED_HOST。最后一项填调用方可达的宿主机地址。broker 可先启动。启用日志消费时再设置 RULE_KAFKA_ENABLED=true，创建每个项目的主题和对应的 .DLT 主题，并通过 KAFKA_SERVER_APPLICATION_JSON 把主题绑定到目标库的真实项目 ID。 |
| ES | 设置 ELASTICSEARCH_BOOTSTRAP_PASSWORD、RULE_SEARCH_ENABLED=true、RULE_SEARCH_INDEX_PREFIX、RULE_SEARCH_USERNAME 和 RULE_SEARCH_PASSWORD。默认前缀 tianshu 对应应用账号 tianshu_logs。 |
| SFTP、ClamD | 设置 ENGINE_SFTP_USERNAME、ENGINE_SFTP_PASSWORD、SFTP_SERVER_JAR=./rule-engine-sftp/server.jar。首次启动后填真实主机指纹。ClamD 容器服务名为 upload-clamd，默认端口 3310。 |
| 引擎鉴权 | 为安装生成 RULE_AUTH_MASTER_KEY，所有副本共用，备份并长期保留。CORS_ALLOWED_ORIGIN_PATTERNS 填控制台实际来源。启用全局签名时配置本安装的 Ed25519 公钥；仅用项目鉴权可以设置 RULE_GLOBAL_ACCESS_ENABLED=false。 |
| full 配置 | 同时读取 .env.client、.env.server，同名值以最后的 .env.server 为准。full 在解析 Compose 时还要求 RUNTIME_RULE_SERVER_URL、SDK_ACCESS_TOKEN 和 RUNTIME_CORS_ALLOWED_ORIGINS 有值；容器内引擎地址可用 http://web:8080。 |

同机部署时，服务端填写以下地址。端口按发布包默认值列出：

| 连接 | A · 宿主机 JAR | B / C · 容器内 server |
| --- | --- | --- |
| MySQL、Redis | MYSQL_HOST=127.0.0.1、REDIS_HOST=127.0.0.1，端口用宿主机映射端口 | mysql:3306、redis:6379；分离模式填 MYSQL_HOST=mysql、REDIS_HOST=redis |
| Kafka | 对外 SASL 监听，默认 127.0.0.1:9092 | KAFKA_BOOTSTRAP_SERVERS=kafka:19092；full 已内置此地址 |
| ES | RULE_SEARCH_URL=http://127.0.0.1:9200 | RULE_SEARCH_URL=http://elasticsearch:9200 |
| SFTP | ENGINE_SFTP_HOST=127.0.0.1，默认 2222 | ENGINE_SFTP_HOST=sftp，端口 2222 |
| ClamD | UPLOAD_ANTIVIRUS_HOST=127.0.0.1，使用下节回环端口覆盖 | UPLOAD_ANTIVIRUS_HOST=upload-clamd、UPLOAD_ANTIVIRUS_PORT=3310 |

## 2. 启动六个基础服务

**先启动 MySQL、Redis、Kafka。方式 A 或 B** 使用独立文件，MySQL 首次启动会创建共享网络。已有远程服务时直接填写连接信息。

~~~bash
docker compose --env-file .env.server -f docker/docker-compose.mysql.yml up -d --wait mysql
docker compose --env-file .env.server -f docker/docker-compose.redis.yml up -d --wait redis
docker compose --env-file .env.server -f docker/docker-compose.kafka.yml up -d --wait kafka
~~~

**方式 C** 用 full 的服务定义启动这三个组件。后续应用使用相同的 full 文件、env 顺序和项目名。

~~~bash
docker compose --env-file .env.client --env-file .env.server -f docker/docker-compose.full.yml up -d --wait mysql redis kafka
~~~

**三种方式接下来都准备 ES、SFTP 和 ClamD。** ES 和 ClamD 的 Compose 需要上面已经建立的 ENGINE_NETWORK。以下示例假定基础服务位于同一 Docker 主机。

**Elasticsearch** 挂载索引和日志到 docker/rule-engine-elasticsearch/ 下。标准 Linux Docker 的目录属主为 1000:0；ES 8.17 宿主机核对 vm.max_map_count 至少为 1048576。

~~~bash
install -d -o 1000 -g 0 -m 0770 docker/rule-engine-elasticsearch/{data,logs}
docker compose --env-file .env.server -f docker/docker-compose.elasticsearch.yml up -d elasticsearch
curl --fail --retry 30 --retry-connrefused --retry-delay 2 -u elastic 'http://127.0.0.1:9200/_cluster/health?wait_for_status=yellow&timeout=60s'
python3 docker/rule-engine-elasticsearch/setup_es_user.py --port 9200 --prefix tianshu
~~~

健康响应应为 yellow 或 green 且 timed_out=false。账号工具提示输入 elastic 当前密码及 tianshu_logs 密码，后者应与 RULE_SEARCH_PASSWORD 相同。已有集群沿用实际凭据和前缀。

**SFTP** 使用数据库做账号校验。包内独立 SFTP Compose 默认有自己的网络；首次在安装目录保存下面这个覆盖文件，让 SFTP 通过共享网络访问 mysql，也让容器内的 server 可以解析 sftp。覆盖文件中的 MYSQL_HOST 只对 SFTP 生效，因此宿主机 JAR 仍可使用 127.0.0.1 连接数据库。

~~~bash
cat > docker/docker-compose.sftp.engine-network.yml <<'YAML'
services:
  sftp:
    environment:
      MYSQL_HOST: mysql
      MYSQL_PORT: "3306"
    networks: [engine]
networks:
  engine:
    external: true
    name: ${ENGINE_NETWORK:?Set ENGINE_NETWORK}
YAML
docker compose --env-file .env.server -f docker/docker-compose.sftp.yml -f docker/docker-compose.sftp.engine-network.yml up -d --wait sftp
docker compose --env-file .env.server -f docker/docker-compose.sftp.yml -f docker/docker-compose.sftp.engine-network.yml logs --no-color sftp | grep 'SFTP host fingerprint' | tail -1
~~~

把日志中完整的 SHA256:… 填入 .env.server 的 ENGINE_SFTP_HOST_FINGERPRINT，保存后再启动 server。保留 SFTP 的 sftp-data 命名卷，重建容器后继续使用同一主机密钥。

外部合作方需要上传文件时，可直接发布 TCP 2222，或由 Nginx Proxy Manager 的 Stream 转发到 SFTP。按项目开通公钥账号，并在外网核对主机指纹和登录结果，见 [SFTP 公网接入](sftp-public-access.md)。

**ClamD** 是 ClamAV 的扫描服务，上传 ONNX 等模型和导入含模型的项目时会使用。**方式 B / C** 直接通过容器网络连接：

~~~bash
docker compose --env-file .env.server -f docker/docker-compose.clamd.yml up -d --wait --wait-timeout 900 upload-clamd
~~~

**方式 A** 选择下面这条带回环端口的启动方式，宿主机 Java 才能连接容器内的 3310。首次病毒库下载需要可用的软件源或内部镜像。

~~~bash
cat > docker/docker-compose.clamd.host.yml <<'YAML'
services:
  upload-clamd:
    ports: ["127.0.0.1:3310:3310"]
YAML
docker compose --env-file .env.server -f docker/docker-compose.clamd.yml -f docker/docker-compose.clamd.host.yml up -d --wait --wait-timeout 900 upload-clamd
~~~

**跨主机分离部署**时，在基础服务主机启动这些 Compose，并把应用端地址换成可达的内网 DNS/IP 和实际监听端口。Docker 服务名只能在对应网络内解析。MySQL、Redis、SFTP 和 Kafka 使用各自的绑定地址与映射端口；包内 ES 默认只发布 127.0.0.1:9200，ClamD 默认只有容器端口。跨主机时，为 ES 提供内网认证入口，为 ClamD 增加仅应用主机可达的内网端口。若 MySQL 也与 SFTP 分机，覆盖文件里的 MYSQL_HOST 和 MYSQL_PORT 同样填真实数据库地址。Kafka 对外公布的地址必须能从应用主机访问。

## 3. 三选一，启动天枢应用

### A · JAR 直接部署

在安装目录执行。server 启动时读取当前目录的 .env.server。示例使用 current 指向成套的 JAR 和静态页面，首次在空安装目录创建一次。

~~~bash
ln -s docker/runtime current
java -jar current/server.jar
~~~

生产可用 systemd 托管同一条命令。将 WorkingDirectory 设为安装目录，ExecStart 使用 java 与 current/server.jar 的绝对路径，服务用户需能读取 .env.server 并写入配置的模型和日志目录。将 current/dist/ 作为控制台静态站点根目录；同域 /api/ 保留完整请求路径代理到 Java 的 8080，前端路由回退到 index.html。由既有 Nginx Proxy Manager 提供 HTTPS。JAR 本身提供后端 API，Web 需要单独提供静态文件。

<details>
<summary>首次使用 systemd 时的最小服务定义</summary>

例如使用安装目录 /opt/tianshu 和已创建的系统用户 tianshu。在 /etc/systemd/system/tianshu-server.service 保存以下内容，替换实际路径和服务用户。确认该用户可读权限为 600 的 .env.server，且宿主机上只有一个进程占用所选端口。

~~~ini
[Unit]
Description=Tianshu decision engine
After=network-online.target

[Service]
User=tianshu
WorkingDirectory=/opt/tianshu
ExecStart=/usr/bin/java -jar /opt/tianshu/current/server.jar
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
~~~

~~~bash
systemctl daemon-reload
systemctl enable --now tianshu-server.service
~~~

</details>

宿主机 JAR 如果启用 Kafka 消费，需要在 Spring 的外部配置中设置 spring.kafka.bootstrap-servers、对外监听所需的 SASL_PLAINTEXT / PLAIN 凭据及 rule-engine.kafka.topic-projects。可通过 `java -jar current/server.jar --spring.config.additional-location=file:./jar-kafka.yml` 加载该文件。Compose 会替容器映射这些参数。配置格式见[项目级 Kafka 日志接入](kafka-execution-logs.md)。

### B · 基础服务与天枢分开部署

六个基础服务就绪、指纹已保存后，运行一个 Web 和两个 server。副本数按机器容量调整，每个副本分别占用 SERVER_MEMORY_LIMIT。

~~~bash
docker compose --env-file .env.server -f docker/docker-compose.server.yml up -d --wait --scale server=2 server web
docker compose --env-file .env.server -f docker/docker-compose.server.yml ps
~~~

Web 内的 Nginx 将 /api/ 分配给多个 server；默认控制台监听宿主机 127.0.0.1:9090，API 检查入口为 127.0.0.1:8080。NPM 使用同网络的 web:80，或使用部署方开放的内网 Web 地址。同机多副本用于进程级扩容，跨主机需另外配置负载均衡和共享依赖。

### C · full 部署

完成第 2 节的 full 基础服务启动及 ES、SFTP、ClamD 准备后，在同一安装目录执行：

~~~bash
docker compose --env-file .env.client --env-file .env.server -f docker/docker-compose.full.yml up -d --wait --scale server=2 server web
docker compose --env-file .env.client --env-file .env.server -f docker/docker-compose.full.yml ps
~~~

需要独立 SDK runtime 时，先在 .env.client 中填入与鉴权方式匹配的真实凭据和相应项目配置，然后启动 sdk（默认 7071，可用 RUNTIME_SDK_PORT 调整）。如果所有配置都已齐备，也可一次启动 full 中的全部默认服务：

~~~bash
docker compose --env-file .env.client --env-file .env.server -f docker/docker-compose.full.yml up -d --wait --scale server=2
~~~

## 4. 每次发版、更新与验收

**方式 B / C** 将新的引擎 ZIP、同名 .zip.sha256 和随包 install-release.sh 上传到同一安装目录。预留维护窗口并备份数据库和生效配置，再执行一条命令：

~~~bash
bash install-release.sh tianshu-deployment-20261128T165733035Z.zip
~~~

安装器校验 ZIP、识别运行中的 Compose 拓扑和副本数、备份并替换应用制品、重建应用、等待健康检查。常规更新为重启式；server 启动时在数据库命名锁内自动升级表结构。改副本数使用第 3 节的 --scale 命令。独立 SFTP 的专用 JAR 在发布说明要求升级该服务时单独更新。

**方式 A** 通过宿主机的进程管理器更新。下面以已配置的 tianshu-server.service 和上述 current 符号链接为例。先验包，再停服务并切换同版 JAR 与 Web 目录。

~~~bash
set -e
ZIP=tianshu-deployment-20261128T165733035Z.zip
sha256sum -c "$ZIP.sha256"
mkdir -p releases
NEW=$(mktemp -d "$PWD/releases/release-XXXXXXXX")
python3 -X utf8 -m zipfile -e "$ZIP" "$NEW"
(cd "$NEW" && sha256sum --quiet -c SHA256SUMS.txt)
chmod 0755 "$NEW"
test -L current
ln -s "$NEW/docker/runtime" "$NEW/current.next"
systemctl stop tianshu-server.service
mv -Tf "$NEW/current.next" current
systemctl start tianshu-server.service
~~~

**验收**先检查 readiness，再从控制台登录并执行一条已发布规则。首次访问按注册页创建超管；开启 ES 时用同一 trace_id 对照执行结果和日志详情。需要演示数据时，在模拟供应商和 ClamD 就绪后通过「离线迁移」导入三项目合集。

~~~bash
curl -fsS --retry 60 --retry-connrefused --retry-delay 2 http://127.0.0.1:8080/actuator/health/readiness
curl -fsS -o /dev/null -w 'Web HTTP %{http_code}\n' http://127.0.0.1:9090/
~~~

预期分别为 UP 和 HTTP 200。JAR 静态站点使用其实际 Web 地址。已有 ES 日志库首次升级到 bb568f75 时，在维护窗口运行一次副本迁移；新装 ES 由账号工具处理。Elasticvue 是可选的 ES 管理页面，部署顺序和副本迁移命令见 [ES 与 Elasticvue 部署说明](../docker/rule-engine-elasticsearch/README.md)。
