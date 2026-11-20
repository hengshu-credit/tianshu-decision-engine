# 衡枢真信授信样例

在仓库或发布包根目录运行。完整 Compose 读取同目录的 `.env.client` 和 `.env.server`。服务端 JAR、引擎代码和数据库 schema 使用同一版本。

```bash
python docker/rule-engine-mysql/load-example.py
```

MySQL 独立 Compose、服务端在宿主机运行时，指定原 Compose 项目名。Redis 须能从导入容器访问。默认使用同一引擎网络中的 `redis:6379`。

```bash
python docker/rule-engine-mysql/load-example.py --compose mysql --project rule-engine
```

入口启动模拟供应商并运行一次性导入器。它优先使用 `docker/runtime/server.jar` 或源码构建的 JAR，也接受 `--server-jar 文件`。SQL 文件为 `data-tianshu-example.sql`。导入器在同一事务和数据库命名锁内执行 SQL、绑定真实 ID，使用引擎编译器校验 20 份规则制品，并用目标库主密钥重新签名主 A 卡 V9。直接向 mysql 客户端重定向 SQL 会提示使用导入入口。

## 数据与账号

- **HENGSHUCREDIT** 包含授信流 V2、七个准入规则集、多头评分卡、额度和期数交叉表、利率树、主 A 卡、名单、历史进件取数、八个外数及其计费配置。**HENGSHU_SUPPLIER** 提供八张独立的供应商决策表。
- 导出保留当前授信样例实际使用的设计和发布版本、资源依赖、发布所需审批证据以及一条人工配置的合成 GPS 名单记录。执行历史、测试调用、计费明细、审批事件和规则运行写入的名单记录均不随样例分发。
- 首次加载为本次安装生成项目 API Key、供应商 Basic 口令、各接口秘密、RSA 签名密钥、SFTP 密码及主机密钥。安装凭据与供应商运行凭据分别存放在 Compose 命名卷中，文件权限为 `0600`。重启或重跑复用原凭据。备份时保留这两个卷和引擎的部署主密钥。
- MySQL 生成随机名称的演示只读账号。四条演示查询仅对合成常量行执行带占位符的 SELECT，账号仅需 USAGE，可以连接并执行这些查询，没有业务表读取、写入或建表权限。脚本会验证实际授权及登录，发现额外权限即中止。
- SFTP 用户 `pboc_sftp` 在模拟服务中创建。只读目录为 `/reports`，仅可读取已生成的征信 JSON；不提供宿主机用户、Shell、上传或其他目录的访问。引擎使用生成的主机公钥严格校验连接。
- 完整 Compose 内通过 `example-supplier:28084` 和 `example-supplier:22084` 访问模拟服务。独立 MySQL 模式只向宿主机回环地址发布这两个端口。模拟供应商默认回调 `http://host.docker.internal:8080`，宿主机引擎须监听容器可达的地址。可通过 `EXAMPLE_ENGINE_URL` 设置回调，`EXAMPLE_SUPPLIER_HOST`、`EXAMPLE_HTTP_PORT`、`EXAMPLE_SFTP_PORT` 指定引擎使用的供应商地址，`EXAMPLE_DB_HOST`、`EXAMPLE_DB_PORT` 指定只读查询连接。
- 重复执行保留现有项目及名单。遇到其他来源的同编码项目、缺少原凭据卷或依赖校验失败时会报告原因，业务配置整体回滚。MySQL 的 CREATE USER 独立于业务事务，失败重试会复用并校验同一个只读账号。

## 调用

在控制台打开“衡枢真信 → 项目鉴权 → CREDIT_HTTP”，查看本次安装生成的 API Key。向服务端 `POST /api/rule/openapi/CREDIT_ADMISSION` 提交 JSON，请求头使用 `X-Auth-Code: CREDIT_HTTP` 和 `X-Rule-Api-Key`。

```json
{"request_id":"DEMO-PASS","name":"合成申请人","idcard_no":"110105199002030718","mobile_no":"13862097542","idcard_expiry_date":"长期","contact":{"mobile_no":"13928046173"},"device_id":"sample-device-1","gps_latitude":28.489,"gps_longitude":101.351}
```

正常响应的 data 为 `{"result":"0","credit_limit":20000,"credit_period":12,"priced_rate":16}`。将 request_id 改为 `DEMO-COURT-REJECT` 可验证法院拒绝和后续费用短路。日常请求使用独立申请号，重复调用同一申请时保留相同请求体。
