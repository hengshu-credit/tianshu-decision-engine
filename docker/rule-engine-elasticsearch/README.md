# 天枢 Elasticsearch 部署

本发布包配套 Elasticsearch 8.17.3。下面的 Compose 启动单节点，将 `docker/rule-engine-elasticsearch/data/` 绑定到 `/usr/share/elasticsearch/data`，使用 512 MiB Java 堆和天枢已有的 Docker 网络。HTTP 发布在宿主机回环地址，引擎通过容器网络连接。同机单节点适合演示或接受单机故障的环境。已有高可用 ES 的生产环境可直接接入现有集群。

以下命令在服务器的天枢安装目录执行。已整理布局使用 `config/.env.server`，平铺布局将命令中的该路径换为 `.env.server`。保留实际的项目名、网络名、端口、覆盖文件和副本数。

## 1. 准备网络和生效配置

首次安装时先按引擎发布包根目录的 `README-DEPLOY.md` 准备 MySQL、Redis、默认 SFTP、主密钥和 CORS 等应用配置，使用发布包的安装脚本启动一次引擎，待应用网络建立后再启用 ES。已有环境通过下面的命令核对 server 的网络，`ENGINE_NETWORK` 使用显示的实际网络名。

```bash
docker ps --filter label=com.docker.compose.service=server --format '{{.Names}} {{.Networks}}'
```

在天枢安装根目录使用 `docker/docker-compose.elasticsearch.yml`。安装脚本会补齐缺少的 ES 定义和说明。若需先部署 ES，将交付包暂存目录中的这两个 Compose 文件及 `docker/rule-engine-elasticsearch/` 下的 README 和账号工具按原相对路径放入安装目录。已经运行过 ES 的环境，先检查挂载并按下方“旧命名卷迁移”处理。

首次部署准备数据和文件日志目录。`data/` 保存索引和节点元数据，`logs/` 接收镜像写入 `/usr/share/elasticsearch/logs` 的文件（例如 JVM GC 日志）。容器标准输出仍可通过 Compose 查看。官方镜像进程使用 UID 1000、GID 0，目录须允许该身份写入。以下 Bash 命令适用于标准 Linux Docker。使用 rootless 或用户映射时，按宿主机实际映射赋权。

```bash
sudo install -d -o 1000 -g 0 -m 0770 docker/rule-engine-elasticsearch/{data,logs}
```

下列变量写入生效的服务端 env。

```dotenv
ENGINE_NETWORK=tianshu-server-engine
ELASTICSEARCH_PROJECT=tianshu-elasticsearch
ELASTICSEARCH_HOST_PORT=9200
ELASTICSEARCH_IMAGE=docker.elastic.co/elasticsearch/elasticsearch:8.17.3
ELASTICSEARCH_BOOTSTRAP_PASSWORD=<从本安装的密钥管理系统取得的强密码>
```

首次使用空数据目录时，ES 使用该密码初始化管理员 `elastic`。复用已有数据目录时，使用已有管理员密码。env 文件中保留本安装的真实值并限制文件权限。更改索引前缀或 ES 项目名前，请先确认要使用的索引和实际挂载路径。

目标主机无法访问镜像仓库时，可在可联网的 Linux amd64 主机用 `docker image save -o elasticsearch-8.17.3.tar docker.elastic.co/elasticsearch/elasticsearch:8.17.3` 导出相同版本的镜像，传至目标机后执行 `docker image load -i elasticsearch-8.17.3.tar`，再运行下面的 Compose 命令。

## 2. 启动 ES 并创建应用账号

```bash
docker compose --env-file config/.env.server -f docker/docker-compose.elasticsearch.yml config --quiet
docker compose --env-file config/.env.server -f docker/docker-compose.elasticsearch.yml up -d elasticsearch
docker compose --env-file config/.env.server -f docker/docker-compose.elasticsearch.yml ps elasticsearch
curl --fail -u elastic 'http://127.0.0.1:9200/_cluster/health?wait_for_status=yellow&timeout=60s'
python3 docker/rule-engine-elasticsearch/setup_es_user.py --port 9200 --prefix tianshu
```

等到 `curl` 返回 `timed_out: false` 且状态为 `yellow` 或 `green` 再继续。若启动期间连接尚未就绪，可查看容器日志并重新检查。修改了 9200 映射端口时，一并调整上面的回环地址和 `--port`。

账号脚本使用 Python 标准库，以隐藏输入读取管理员密码和应用密码。示例创建 `tianshu_logs`。应用能管理索引模板，并能在本前缀的执行日志、模块调用、历史特征、规则步骤四类月索引上自动建索引、写入、读取和等待刷新。模板管理属于集群级权限。重复执行会核对现有用户和角色。权限依据见 [Elastic 角色接口](https://www.elastic.co/guide/en/elasticsearch/reference/8.17/security-api-put-role.html) 和 [Bulk 写入与刷新权限](https://www.elastic.co/guide/en/elasticsearch/reference/8.17/docs-bulk.html)。

## 3. 启用所有 server 副本

在同一份服务端 env 中填写以下应用连接参数。

```dotenv
RULE_SEARCH_ENABLED=true
RULE_SEARCH_URL=http://elasticsearch:9200
RULE_SEARCH_INDEX_PREFIX=tianshu
RULE_SEARCH_API_KEY=
RULE_SEARCH_USERNAME=tianshu_logs
RULE_SEARCH_PASSWORD=<上一步填写的应用密码>
RULE_SEARCH_COMPACT_LOGS=false
```

容器内使用 `elasticsearch:9200`。前缀须与账号授权一致，所有副本共用同一前缀、业务时区和主密钥。需要压缩已确认投递的大追踪时可将 `RULE_SEARCH_COMPACT_LOGS` 设为 `true`。已有部署先备份数据库，再核对实际 server Compose 的 `services.server.environment` 是否传入以上 `RULE_SEARCH_*` 项。旧定义缺项时，从本包内相同拓扑的 Compose 合并这些映射。安装入口会保留服务器现有 Compose 和 env。

在安装目录执行本次更新，所有副本会加载新配置并核对数据库结构。

```bash
bash install-release.sh 本次引擎发布包.zip
```

**接入已有 ES 集群**时，从本节开始配置即可。将 `RULE_SEARCH_URL` 设为 server 可达的 HTTPS 地址，私有 CA 加入各 server 的 JVM 信任库。可使用预先签发且具备上述权限的 `RULE_SEARCH_API_KEY`，填入 encoded 值并留空用户名和密码。每套独立引擎安装使用独立索引前缀。

## 4. 验收和回切

**日志副本策略。** 首次部署时，随包的 `setup_es_user.py` 在验证账号后读取 ES 的节点配置。只有集群完整返回、且唯一节点明确设置了 `discovery.type=single-node` 时，才为该前缀的执行日志、模块调用、历史特征和规则步骤月索引配置 `auto_expand_replicas=0-1`。单节点保存一份主分片；如需容忍节点故障，应部署多个数据节点并规划固定副本数。多节点发现模式的集群即使暂时只剩一台，也会保留缺失副本告警。

**原有环境升级。** 升级到包含本修复的 server 后，使用 ES 运维账号在维护窗口执行一次下列命令。工具随发布包安装，支持重复执行。前缀使用当前部署的 `RULE_SEARCH_INDEX_PREFIX`。没有自定义该值时为 `tianshu`。

```bash
python3 docker/rule-engine-elasticsearch/log_replica_policy.py --url http://127.0.0.1:9200 --prefix tianshu
```

外部集群可以使用 `--url https://实际集群地址 --username 运维账号`，私有 CA 使用 `--ca-file` 指定。自动模式会保留现有月索引的自定义副本设置。部署方可显式传入 `--replicas 1`（或自身 SLA 要求的副本数）统一更新该前缀四类月索引和后续月份；将单节点部署改成高可用集群时应执行此步骤。显式设置固定副本数后，重复配置服务账号会沿用该策略。运维账号需要查看节点设置、管理模板以及更新目标索引设置的权限。天枢日常日志账号沿用原有权限。

策略存储在 ES 组件模板 `<前缀>-log-replicas-v1` 中。server 重启重建月索引模板时会引用该组件；未配置组件时使用 ES 的原有默认值。现有的更高优先级自定义索引模板由部署方维护。升级后可分别核对实际月索引和未来月份的模板模拟结果（将月份、前缀替换成现场值）：

```bash
curl --fail -u elastic 'http://127.0.0.1:9200/_cat/shards/tianshu-*-v1-*?v'
curl --fail -u elastic -X POST 'http://127.0.0.1:9200/_index_template/_simulate_index/tianshu-execution-log-v1-2030.01?filter_path=template.settings'
```

先核对 ES 的挂载类型和绝对路径。输出应为 `bind` 和以 `/docker/rule-engine-elasticsearch/data` 结尾的安装路径。

```bash
docker inspect --format '{{range .Mounts}}{{if eq .Destination "/usr/share/elasticsearch/data"}}{{.Type}} {{.Source}}{{end}}{{end}}' "$(docker compose --env-file config/.env.server -f docker/docker-compose.elasticsearch.yml ps -q elasticsearch)"
```

以下示例使用独立 server Compose。实际使用 full 或多覆盖文件部署时，沿用该安装原有的 Compose 参数。

```bash
docker compose --env-file config/.env.server -f docker/docker-compose.server.yml exec -T server printenv RULE_SEARCH_ENABLED RULE_SEARCH_URL RULE_SEARCH_INDEX_PREFIX
curl --fail -u elastic 'http://127.0.0.1:9200/_index_template/tianshu-*-v1?filter_path=index_templates.name'
```

应出现 `execution-log`、`runtime-call`、`history-feature`、`rule-step` 四套模板。通过正式入口执行一笔规则，再打开控制台执行日志核对 trace。带数据的月索引会在投递时创建，可用应用账号检查文档数。

```bash
curl --fail -u tianshu_logs 'http://127.0.0.1:9200/tianshu-execution-log-v1-*/_count?filter_path=count'
```

在天枢 MySQL 数据库核对后台投递进度。已有历史数据首次启用时会按批补投，安静窗口内待办应逐步归零。

```sql
SELECT COUNT(*) AS pending_execution_logs FROM rule_execution_log WHERE es_due_at IS NOT NULL;
SELECT COUNT(*) AS pending_runtime_calls FROM rule_runtime_call_log WHERE es_due_at IS NOT NULL;
```

回切时，将所有 server 的 `RULE_SEARCH_ENABLED` 改为 `false`，再次执行同一条安装命令并复核查单。ES 停止或重建后，索引仍保存在上述宿主机目录。若还需回滚到不识别压缩列的历史二进制，请先按引擎包内 `docs/elasticsearch-log-storage.md` 的步骤还原正文。

```bash
docker compose --env-file config/.env.server -f docker/docker-compose.elasticsearch.yml logs --tail=100 elasticsearch
docker compose --env-file config/.env.server -f docker/docker-compose.elasticsearch.yml stop elasticsearch
```

## Elasticvue

### Elasticvue 2 同域账密入口

Elasticvue 2 Beta 的固定版本、天枢补丁与构建文件在 `docker/rule-engine-elasticsearch/elasticvue2/`。在安装目录使用本版独立 Compose 启动。它与旧部署共用服务名和端口；请沿用原 `ELASTICVUE_PROJECT`、`ENGINE_NETWORK` 和需要的 NPM 联网覆盖文件。升级时使用本次发布包内的 Compose 和整个 `elasticvue2/` 目录。旧版通过额外挂载提供的 dist 会遮盖镜像内的新版页面，本命令只指定新版 Compose。首次在线构建需要 GitHub、npm 和镜像源可达。

```bash
docker compose --env-file config/.env.server -f docker/docker-compose.elasticvue2.yml up -d --build elasticvue
```

平铺布局使用 `.env.server`。同机 NPM 有独立网络时，在 env 填入实际的 `NPM_NETWORK`，并追加 `-f docker/docker-compose.elasticvue.npm-network.yml`。NPM 在引擎域名下把 `/es/` 完整转发到 `http://elasticvue:8080`；`ELASTICVUE_PUBLIC_URL` 填写该域名下的完整 `/es/` 页面地址。进入页面只需输入个人 ES 账号密码。前端连接同域 `/es/backend/`，集群认证与授权由 ES 执行。使用镜像交付时，先加载同标签的镜像，再用上述命令中的 `--no-build` 替换 `--build`。

**快照页**需要集群级快照监控权限。普通日志读者可继续使用原有索引权限。经授权的快照查看人员可附加独立角色，权限包括 `monitor_snapshot` 和用于读取生命周期策略的 `read_slm`。这些权限允许查看整个集群的快照元数据，管理员应按人员分配。以下命令在 ES 所在主机创建专用角色（已存在同名自定义角色时，先审阅其权限）。随后在 ES 用户或角色映射上为获批人员追加该角色，保留其原有角色。

```bash
curl --fail -u elastic -X PUT 'http://127.0.0.1:9200/_security/role/tianshu_snapshot_viewer' \
  -H 'Content-Type: application/json' --data-binary @docker/rule-engine-elasticsearch/elasticvue2/snapshot-viewer.json
```

**SQL 和 ES|QL 搜索**支持在编辑器旁选择具体索引、别名或索引模式，也可以直接输入完整语句。未选择具体来源时等待输入。选定索引后会生成查询；自写语句在更换索引时保留。

### Elasticvue 1 独立入口

可选服务使用 [Elasticvue 官方镜像](https://github.com/cars10/elasticvue)。在本机打开 `http://127.0.0.1:8090`，或通过已有的 HTTPS 反向代理为 Elasticvue 分配独立域名。代理转发到 Elasticvue 的 8080 容器端口（同网络）或宿主机回环的 8090 端口（同主机）。端口可通过 `ELASTICVUE_HOST_PORT` 调整。

1. 在 `config/.env.server` 设置 `ELASTICVUE_PUBLIC_URL` 为操作者浏览器能打开的页面地址，如 `https://elasticvue.example.com`。使用同机浏览器或 SSH 本地转发时可填 `http://127.0.0.1:8090`。留空即隐藏入口。使用自有 Elasticvue 实例时直接填写该页面地址。
2. 使用包内服务时，确认它与天枢 ES 共用 `ENGINE_NETWORK`，然后在安装目录启动。

   ```bash
   docker compose --env-file config/.env.server -f docker/docker-compose.elasticvue.yml up -d elasticvue
   ```

3. 将配置应用到所有 server 副本。旧安装保留现场 Compose，需先在当前 server 的 `environment` 中补上 `ELASTICVUE_PUBLIC_URL: "${ELASTICVUE_PUBLIC_URL:-}"`，再按原部署流程重建 server。启用 `RULE_SEARCH_ENABLED=true` 且拥有全局及所有项目日志查看权限的账户，可在 **执行日志 → 运行状态 → 打开 Elasticvue** 新开页面。
4. 首次在 Elasticvue 选择添加集群。对于包内单节点，在集群地址填写同一页面源下的 `/es/` 完整地址，如 `https://elasticvue.example.com/es/`，输入个人 ES 用户名和密码后连接。此路径转发到同网络的 `elasticsearch:9200`。使用外部集群时，填写为 Elasticvue 配置的可达集群地址，并按官方说明为跨域访问设置精确的允许来源。
5. 打开 Search，选择执行日志月索引（默认 `tianshu-execution-log-v1-*`），用 `trace_id:"实际追踪号"` 查询。调用日志选择 `tianshu-runtime-call-v1-*`。展开结果的 `_source.body` 即可查看新投递的明文 JSON。索引读取权限覆盖正文中的留存报文。升级前已投递的密文仍可在天枢日志详情中按权限查看。

Elasticvue 直接使用个人 ES 授权。为查询人员分配限定索引前缀的 `read`、`view_index_metadata` 和所需的集群 `monitor` 权限，索引管理操作按人另行授权；共享索引内的项目隔离需要在 ES 侧实施。天枢服务账号、主密钥和用户的控制台会话均无需填入部署链接。

## 旧命名卷迁移

先用正在运行的 ES 容器和上面的 `docker inspect` 命令查看挂载。类型为 `volume` 时，记录实际卷名、Compose 项目名、旧定义和 env。更新引擎会保留现场已有的 ES 定义。切换 ES 定义属于单独的维护操作，换成新文件后首次启动会打开新目录中的集群。

下面迁移单节点上的天枢日志索引。示例前缀为 `tianshu`，旧主文件位于安装目录的 `docker/`。若使用自己的仓库或共享集群，沿用该集群的快照和恢复流程。完整集群迁移还需覆盖其他索引、系统功能状态、用户及密钥。这里的快照专门选择天枢的四类索引。该流程采用 Elastic 支持的索引快照和恢复接口。[Elastic 快照与恢复](https://www.elastic.co/guide/en/elasticsearch/reference/8.17/snapshot-restore.html)

1. 在维护窗口暂停相关写入，等 MySQL 待投递数归零，再暂停投递节点。保存旧 ES 的索引名、逐索引文档数，并选一笔已知 trace 作为恢复核对样本。保留旧 Compose 和命名卷。
2. 使用现有快照仓库，或建立下列临时文件系统仓库。将交付包的 `docker/docker-compose.elasticsearch.snapshot.yml` 放到旧主文件旁边，仍用**旧主文件**启动 ES。附加文件只增加快照目录和 `path.repo`。

```bash
sudo install -d -o 1000 -g 0 -m 0770 docker/rule-engine-elasticsearch/snapshots
docker compose --env-file config/.env.server -f docker/docker-compose.elasticsearch.yml -f docker/docker-compose.elasticsearch.snapshot.yml up -d elasticsearch
curl --fail -u elastic 'http://127.0.0.1:9200/_cluster/health?wait_for_status=yellow&timeout=60s'
curl --fail -u elastic -X PUT http://127.0.0.1:9200/_snapshot/tianshu_move -H 'Content-Type: application/json' --data-binary '{"type":"fs","settings":{"location":"/mnt/tianshu-snapshots"}}'
snap="move-$(date -u +%Y%m%d%H%M%S)"
curl --fail -u elastic -X PUT "http://127.0.0.1:9200/_snapshot/tianshu_move/$snap?wait_for_completion=true" -H 'Content-Type: application/json' --data-binary '{"indices":"tianshu-execution-log-v1-*,tianshu-runtime-call-v1-*,tianshu-history-feature-v1-*,tianshu-rule-step-v1-*","ignore_unavailable":true,"include_global_state":false,"feature_states":["none"]}'
```

3. 确认返回的快照状态为 `SUCCESS`、失败分片为零、索引清单与记录一致。记下 `$snap` 的值。停止旧 ES，备份旧主文件，并将**新版主文件**放到同一位置。用第 1 节的权限命令准备空的 `data/` 和可写的 `logs/`。原项目名和网络继续使用现场值。

```bash
docker compose --env-file config/.env.server -f docker/docker-compose.elasticsearch.yml -f docker/docker-compose.elasticsearch.snapshot.yml stop elasticsearch
# 在此完成上述主文件备份、替换和空目录准备，再继续。
docker compose --env-file config/.env.server -f docker/docker-compose.elasticsearch.yml -f docker/docker-compose.elasticsearch.snapshot.yml up -d elasticsearch
curl --fail -u elastic 'http://127.0.0.1:9200/_cluster/health?wait_for_status=yellow&timeout=60s'
curl --fail -u elastic -X PUT http://127.0.0.1:9200/_snapshot/tianshu_move -H 'Content-Type: application/json' --data-binary '{"type":"fs","settings":{"location":"/mnt/tianshu-snapshots","readonly":true}}'
curl --fail -u elastic -X POST "http://127.0.0.1:9200/_snapshot/tianshu_move/$snap/_restore?wait_for_completion=true" -H 'Content-Type: application/json' --data-binary '{"include_global_state":false,"feature_states":["none"]}'
python3 docker/rule-engine-elasticsearch/setup_es_user.py --port 9200 --prefix tianshu
```

4. 比对新旧索引数、逐索引文档数和样本 trace，按第 4 节确认实际挂载路径。原服务使用 API Key 时，从新集群签发并更新环境变量。使用前述账号密码时将 `RULE_SEARCH_API_KEY` 留空。恢复引擎时使用同一业务时区、主密钥和索引前缀，验证四套模板、业务查单和新增日志。恢复写入前若核对失败，可停止新 ES，恢复旧主文件并按原 Compose 项目名重新启动旧卷。迁移完成后，在新集群注销临时仓库，再单用主文件重建 ES。注销仓库会保留磁盘上的快照文件。按站点备份策略将快照转存到独立存储。

```bash
curl --fail -u elastic -X DELETE http://127.0.0.1:9200/_snapshot/tianshu_move
docker compose --env-file config/.env.server -f docker/docker-compose.elasticsearch.yml up -d elasticsearch
```
