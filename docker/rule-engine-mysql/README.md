# 衡枢真信授信样例

在仓库或发布包根目录运行。完整 Compose 读取同目录的 `.env.client` 和 `.env.server`。服务端 JAR、引擎代码和数据库 schema 使用同一版本。

```bash
docker compose --env-file .env.client --env-file .env.server -f docker/docker-compose.full.yml --profile example-data run --rm --build example-data-import
```

MySQL 独立 Compose、服务端在宿主机运行时，指定原 Compose 项目名。Redis 须能从导入容器访问。默认使用同一引擎网络中的 `redis:6379`。

```bash
docker compose --project-name rule-engine --env-file .env.server -f docker/docker-compose.mysql.yml --profile example-data run --rm --build example-data-import
```

Compose 启动 Java 模拟供应商和 OpenSSH 只读 SFTP，再运行一次性 Java 导入器。默认使用 `docker/runtime/server.jar`。源码运行可先执行 `mvn -pl rule-engine-server -am package -DskipTests`，将 `EXAMPLE_SERVER_JAR` 指向构建产物的绝对路径。SQL 文件为 `data-tianshu-example.sql`。导入器在同一事务和数据库命名锁内执行 SQL、绑定真实 ID，使用引擎编译器校验 20 份规则制品，并用目标库主密钥重新签名主 A 卡 V9。直接向 mysql 客户端重定向 SQL 会提示使用导入入口。

## 主 A 卡原件与扫描服务

MAIN_A V9 的原始 PMML 为 232,796 字节。SQL 内只携带一份无损压缩的原件，MySQL 解压，导入器核对文件长度和 SHA-256，使用引擎上传扫描与 PMML 检查，随后写入共享 MySQL 文件库。主表与 V9 使用同一原件引用，版本快照引用冻结包。7 个直接取数输入来自多头、收入、欺诈、信用 4 个接口；完整授信项目配置 8 个外数接口。重复导入会校验 V9。旧版内联或文件卷中的样例原件、冻结包按原摘要迁移，版本、用例、引用 ID 和外数绑定保留。

**已有 ClamD 时**，在 `.env.server` 设置 `UPLOAD_ANTIVIRUS_HOST`、`UPLOAD_ANTIVIRUS_PORT`，使用前述命令。**需要本地扫描器时**，可启用随包附带的可选 Compose 文件。完整栈将 `UPLOAD_ANTIVIRUS_HOST=example-clamd` 和 `UPLOAD_ANTIVIRUS_PORT=3310` 配给 server，然后执行：

```bash
docker compose --env-file .env.client --env-file .env.server -f docker/docker-compose.full.yml -f docker/credit-example-antivirus.compose.yml --profile example-data run --rm --build example-data-import
```

独立 MySQL 按上文使用原项目名和 `docker-compose.mysql.yml`，同样追加 `-f docker/credit-example-antivirus.compose.yml`。宿主机直接启动的 server 连接 `127.0.0.1:${EXAMPLE_CLAM_HOST_PORT:-33310}`。默认 MySQL 文件库由同一数据库的所有节点共用。全新安装无需在节点间同步模型目录。已有文件卷的安装须在能读取原件和冻结包的节点执行迁移，为迁移进程的 `RULE_MODEL_STORAGE_DIRECTORY` 提供旧卷（Compose 可用 `MODEL_FILES_MOUNT`）。迁移前保留数据库和旧卷的匹配备份；迁移并验证冷读后，MySQL 备份包含共享模型内容。

首次启动扫描器需要加载病毒库，导入器会等待健康检查通过；扫描未通过时导入失败并回滚。只在演示期间需要临时扫描器时，结束后可用相同 Compose 文件执行 `stop example-clamd`，签名卷保留。引擎后续接收新模型或台账附件时仍需有可用的扫描服务。

## 数据与账号

- **HENGSHUCREDIT** 包含授信流 V3、七个准入规则集、多头评分卡、额度和期数交叉表、利率树、主 A 卡、名单、历史进件取数、八个外数及其计费配置。**HENGSHU_SUPPLIER** 提供八张独立的供应商决策表。
- 导出保留当前授信样例实际使用的设计和发布版本、资源依赖、发布所需审批证据以及一条人工配置的合成 GPS 名单记录。执行历史、测试调用、计费明细、审批事件和规则运行写入的名单记录均不随样例分发。
- 首次加载为本次安装生成项目 API Key、供应商 Basic 口令、各接口秘密、RSA 签名密钥、SFTP 密码及主机密钥。安装凭据与供应商运行凭据分别存放在 Compose 命名卷中，文件权限为 `0600`。重启或重跑复用原凭据。备份时保留这两个卷和引擎的部署主密钥。
- MySQL 生成随机名称的演示只读账号。四条演示查询仅对合成常量行执行带占位符的 SELECT，账号仅需 USAGE，可以连接并执行这些查询，没有业务表读取、写入或建表权限。脚本会验证实际授权及登录，发现额外权限即中止。
- SFTP 用户 `pboc_sftp` 在模拟服务中创建。只读目录为 `/reports`，仅可读取已生成的征信 JSON；不提供宿主机用户、Shell、上传或其他目录的访问。引擎使用生成的主机公钥严格校验连接。
- 完整 Compose 内通过 `example-supplier:28084` 和 `example-supplier:22084` 访问模拟服务。独立 MySQL 模式只向宿主机回环地址发布这两个端口。模拟供应商默认回调 `http://host.docker.internal:8080`，宿主机引擎须监听容器可达的地址。可通过 `EXAMPLE_ENGINE_URL` 设置回调，`EXAMPLE_SUPPLIER_HOST`、`EXAMPLE_HTTP_PORT`、`EXAMPLE_SFTP_PORT` 指定引擎使用的供应商地址，`EXAMPLE_DB_HOST`、`EXAMPLE_DB_PORT` 指定只读查询连接。
- 重复执行保留现有项目、名单和已有用例，按资源与用例标识补充缺失用例。遇到其他来源的同编码项目、缺少原凭据卷或依赖校验失败时会报告原因，业务配置整体回滚。MySQL 的 CREATE USER 独立于业务事务，失败重试会复用并校验同一个只读账号。

## 测试用例

同一条导入命令也适用于旧版导入器创建的两个项目。标准 V2 样例会依次新增 NBQZ V3 和 CREDIT_ADMISSION V3，保留历史版本；已有自定义修订、审批或未满足的回归门禁会提示冲突并回滚。使用本版服务端 JAR，已有安装保留原凭据卷。凭据生成、SQL 导入和供应商 HTTP 均由 Java 运行，SFTP 使用 OpenSSH 只读子系统。

| 控制台入口 | 覆盖范围 | 用例数 |
|---|---|---:|
| 变量管理 → 测试 → 选择固定测试用例 | 24 个支持独立取数的 API、DB、名单、SFTP 和衍生变量 | 95 |
| 外数管理 → 接口详情 → 调用测试 | 8 个接口，每个均含通过、拒绝、阈值、查得但字段为空、未查得，人行另核对提交计费标志 | 46 |
| 模型管理 → 主 A 卡 → 模型测试 | V9 正常与高风险样例，标准分分别为 594.02、−33.90 | 2 |
| 规则测试 → 固定测试用例收藏 | 两项目的 20 个已发布规则，含供应商、准入规则集、评分和定价 | 115 |

选择用例后执行，即可核对实际值与预期。普通入参和计算结果随引用它们的规则测试。NBQZ V3 的 QN0001 直接引用项目数据库字段，当前逾期 9 天时在内部准入阶段拒绝。额度和期数交叉表使用模拟供应商实际返回的收入。

113 个规则用例默认参与回归。“QN0001-DB · 当前逾期取数应拒绝”使用真实数据库取数校验主流程。另有两个需要真实历史进件的手动用例，验证当日同设备第 4 笔、七日内同一区域第 12 笔拒绝。在演示库需要这两项测试时执行：

```bash
docker compose --project-name rule-engine --env-file .env.server -f docker/docker-compose.mysql.yml --profile example-data run --rm -T --entrypoint java example-data-import -jar /app/server.jar --prepare-credit-example-history
```

完整 Compose 使用相同的 `--entrypoint java` 和命令参数，环境文件和 Compose 文件沿用上文的完整栈命令。这一步发送 14 笔正式 HTTP 合成申请，使用同日幂等申请号，在内部规则处拒绝。随后进入 CREDIT_ADMISSION 的固定用例，分别执行 QN0005 和 QN0006。


## 调用

在控制台打开“衡枢真信 → 项目鉴权 → CREDIT_HTTP”，查看本次安装生成的 API Key。向服务端 `POST /api/rule/openapi/CREDIT_ADMISSION` 提交 JSON，请求头使用 `X-Auth-Code: CREDIT_HTTP` 和 `X-Rule-Api-Key`。

```json
{"request_id":"DEMO-PASS","name":"合成申请人","idcard_no":"110105199002030718","mobile_no":"13862097542","idcard_expiry_date":"长期","contact":{"mobile_no":"13928046173"},"device_id":"sample-device-1","gps_latitude":28.489,"gps_longitude":101.351}
```

正常响应的 data 为 `{"result":"0","credit_limit":20000,"credit_period":12,"priced_rate":16}`。将 request_id 改为 `DEMO-COURT-REJECT` 可验证法院拒绝和后续费用短路。日常请求使用独立申请号，重复调用同一申请时保留相同请求体。
