# Elasticsearch 与 MySQL 日志存储

服务端可以通过 `RULE_SEARCH_ENABLED=true` 启用混合存储。MySQL 提交执行身份、当前修订、可恢复的原始事实和正式账本。Elasticsearch（ES）承担读取量较大的日志正文、历史特征和规则集追踪。SDK 的 HTTP 和 Kafka 上报继续走原有鉴权及逻辑日志入口。服务端写 ES 直接使用 Bulk HTTP。

设计来源见[原始架构讨论](https://chatgpt.com/share/6b0801b5-4978-83ec-accc-ed2dc108d71c)。批次中每个条目单独确认，读取时处理分片级部分失败，协议参见 [Elastic Bulk API](https://www.elastic.co/guide/en/elasticsearch/reference/8.17/docs-bulk.html) 和 [Multi get API](https://www.elastic.co/guide/en/elasticsearch/reference/8.17/docs-multi-get.html)。

## 数据与事务

| 数据 | 写入和读取 |
| --- | --- |
| 执行日志 | 每个物理执行行对应一个 `execution-log` 文档。逻辑重试更新代表行并递增检索修订。控制台列表通过 `summaryOnly=true` 只读取 MySQL 摘要。打开详情时按日志 ID 和分区时间鉴权读取当前正文。原完整列表和回放入口仍可读取 ES 正文。 |
| 调用日志 | 外数逻辑调用、尝试、步骤、数据库、名单、模型和 SFTP 调用按原事件粒度写 `runtime-call`。混合模式在接收时落库，随后异步投递。正文开关、脱敏副本与原始报文的读取权限沿用原接口。 |
| 历史特征 | `history-feature` 按逻辑执行修订替换。只有带受管快照、执行成功的正式根记录参与统计。来源为 `CLIENT_SERVER` 或 `EXPERIMENT_LIVE`。失败重试使上一成功修订退出。控制台试算保留审计记录。 |
| 规则集统计 | `rule-step` 在一个文档内替换同一执行的所有规则集帧、条件项及其耗时。统计按当前修订计算精确频数、命中率和 P95/P99。窗口超过 5 万行或投影不完整时走已有 SQL 聚合。追踪解析与统计明细展开交由已有后台补齐器，待提取修订在统计覆盖数中显示。其他设计器的完整节点树在执行文档中。 |
| 看板和计费 | 运行耗时与模块调用指标支持 ES 维度读取。可变业务映射、进件去重、管理配置、计费流水和账单继续使用 MySQL。 |

物理日志行同时是检索投递待办。行内修订、下次可领取时间和领取次数随原记录一起提交。历史数据获得初始待办，启动后逐批补齐。各节点在短事务内用 `FOR UPDATE SKIP LOCKED` 领取，提交后才做网络 I/O。固定月索引、物理身份和 ES 外部版本共同防止迟到写入覆盖新修订。一个执行的全部索引项均收到成功且可见的确认后才清除待办。部分失败、超时和进程退出由共享租约与退避重试恢复。

正式根事实与待计费事件在同一个 MySQL 事务中提交后才确认请求。下一笔请求能在任一 server 上读取它。已有的计费消费者仍负责幂等入账。ES 停止或索引延迟期间，决策和查询可以继续读取 MySQL。MySQL 无法提交正式事实时，请求会报告持久化失败。

## 历史计算

查询先用 MySQL 的逻辑视图、账户和项目权限、根规则集合、时间窗及当前 trace 排除条件确定候选身份。ES 只接受这些候选的当前修订。批内可见部分由 ES 计算，待投递部分取同一 MySQL 读快照。缺项、版本不匹配、超时和分片失败均转入精确读取路径。系统不以最大日志时间或最大自增 ID 推断可见性。

- 普通计数和单字段关联计数可合并最多 32 个同条件时间窗口。单字段标量或列表关联采用带类型的 HMAC 等值键。数字与字符串保持区别。密钥轮换或字段令牌超出上限时回读完整快照。
- 半径计数先检查候选是否都有可用的数值坐标，以保守纬度范围缩小结果，再用引擎原有球面距离与边界容差复核。缺少坐标投影时使用已有数据库索引与历史字段解析。
- 多步关联、复合主体、主体去重、最新一条、字段时间窗、金额、方差、Unicode 字符长度和自定义函数继续用原有精确计算器。ES 批量提供受管字段原始 JSON。旧记录所需的外数结果仍通过当次逻辑调用日志补齐。保留原有 10 万条、64 MiB 及查询超时保护。
- 原始输入、中间结果、模型输出和再次衍生的字段遵循执行时冻结的字段 ID 与记录开关。显式 null、缺失、零、空串在快照中保留。索引投递不会重新执行规则、调用供应商或补造历史字段。

月索引名为 `<前缀>-<种类>-v1-yyyy.MM`。执行日志、调用日志和符合条件的历史特征快照在写入 ES 时将 `body` 保存为明文 JSON 字符串，`value` 中是正文。`body` 仅存于 `_source`，按 `trace_id` 等外层字段检索。拥有索引读取权限的账号可直接查看其中留存的业务数据，包括调用日志中留存的原始报文。请按该范围分配 ES 读取权限。

升级后新投递的文档使用明文。已投递的旧文档继续使用原格式，服务端可读取普通密文及压缩后加密的正文。如需在 Elasticvue 中以明文查看历史文档，先升级所有写入节点，再按下文的流程分批重新投递。索引映射和月索引名均兼容两种格式。多节点使用同一数据库、同一索引前缀、相同的业务时区及相同的主密钥配置，主密钥仍用于读取旧密文和计算历史字段匹配键。各安装的索引前缀应独立。

## 部署和回切

在 `.env.server` 中设置 `RULE_SEARCH_ENABLED=true`、`RULE_SEARCH_URL`、`RULE_SEARCH_INDEX_PREFIX`。生产集群可以使用 HTTPS 和限定前缀权限的 `RULE_SEARCH_API_KEY`，也支持 `RULE_SEARCH_USERNAME` 与 `RULE_SEARCH_PASSWORD`。服务账户需要模板管理、创建索引、写入和读取对应索引的权限。自签发 CA 应加入服务器 JVM 信任库。

单节点部署使用安装目录内的 `docker/docker-compose.elasticsearch.yml`，宿主机的 `docker/rule-engine-elasticsearch/data/` 挂载到容器的 `/usr/share/elasticsearch/data`。首次创建目录并赋予镜像用户写权限，设置 `ELASTICSEARCH_BOOTSTRAP_PASSWORD`、核对已有 `ENGINE_NETWORK` 后启动。server 使用 `http://elasticsearch:9200`，宿主机端口绑定回环。旧命名卷切换前需先完成快照迁移和核验。[ES 部署、权限和旧卷迁移](../docker/rule-engine-elasticsearch/README.md)

```bash
sudo install -d -o 1000 -g 0 -m 0770 docker/rule-engine-elasticsearch/{data,logs}
docker compose --env-file config/.env.server -f docker/docker-compose.elasticsearch.yml up -d elasticsearch
```

平铺安装将上面命令的 `config/.env.server` 换为 `.env.server`。

需要浏览索引、查询文档时，可单独启用 Elasticvue。控制台入口位于 **执行日志 → 运行状态 → 打开 Elasticvue**。部署、个人 ES 授权与按追踪号查询的步骤见 [Elasticvue 接入说明](../docker/rule-engine-elasticsearch/README.md#elasticvue)。

Elasticvue 的未分配副本告警取自 ES 的真实分片状态。明确配置的单节点可在日志索引上使用弹性副本，高可用集群按部署方的冗余目标设置固定副本。已有环境升级时需要同时处理历史月索引和后续月份的索引模板，见[日志副本策略与升级命令](../docker/rule-engine-elasticsearch/README.md#4-验收和回切)。

首次上线先升级所有写入节点并复核 schema，再统一启用检索。已有安装使用外层固定 Compose 时，需将新版 server 的 `RULE_SEARCH_*` 环境映射合并到现有定义并重建应用容器。普通安装脚本保留已有 Compose。ES 投递列和索引由 server 按包内 schema 自动升级。

可选 `RULE_SEARCH_COMPACT_LOGS=true` 会在 ES 确认之后，将至少 4 KiB 的追踪正文无损压缩到 `rule_execution_log.es_trace_archive`，读取、重建和回放均支持数据库副本。两个字段的合并 SQL 为 `COALESCE(trace_info,CONVERT(UNCOMPRESS(es_trace_archive) USING utf8mb4))`。调用日志也会逐字节比较供应商原件与相应的留存副本。达到 4 KiB 且完全相同时，物理表仅保存一份，受控报文接口仍按原契约返回。直连表的旧报表需使用上述追踪表达式，并按 `es_body_aliases` 的请求位（1）、响应位（2）读取对应的 `raw_*_body`。切回当前版本的 MySQL 读取只需关闭 `RULE_SEARCH_ENABLED`。若回滚到不识别压缩列的旧二进制，需要先停写，将归档追踪解压回原字段，并按标志将共用的大报文复制回 `original_*_body` 后清零标志。

运行指标接口 `GET /api/rule/ops/execution-metrics` 的 `logSearch` 显示本节点投递、读取、失败与回退，以及两类表的共享待办数量。重建丢失的索引或切换集群时，保留 MySQL 备份，选择独立索引前缀，按表的实际分区或主键范围分批将 `es_due_at` 设为 `CURRENT_TIMESTAMP(6)`。这会重新投递原行和原修订。已确认的数据仍能从 MySQL 重建，包括归档追踪。日志保留期和 ES 快照按部署配置，程序不会自动清除历史进件。生产容量、快照恢复时间及多机吞吐需按实际日志规模验收。

## 回归入口

- 常规 `mvn test` 包括序列化、精确关联、明文读写、旧密文读取、部分 Bulk 失败、修订覆盖和数据库增量合并。设置本机 MySQL 的 `MYSQL_*` 连接信息、回环 `RULE_SEARCH_URL` 与 ES 认证后，`mvn -DlogSearch.integration=true test` 会进一步用独立测试库与唯一 ES 索引前缀验证并发投递、存量补齐、压缩、替换与统计一致性。
- 在装有三个样例项目及模拟供应商的演示环境，设置 `E2E_DEMO_BASE_URL`、`E2E_USERNAME`、`E2E_PASSWORD`。在 `rule-engine-web` 运行 `npx playwright test tests/e2e/elasticsearch-demo.spec.js`，通过页面执行供应商、授信及三项 KYC 规则，并逐条按 trace 核对结果和日志。授信历史阈值的正式进件准备说明见样例目录。
- 同一测试文件还接受 `E2E_DEMO_SERVER_URL`、`E2E_DEMO_PEER_URL` 和 `E2E_DEMO_CREDIT_API_KEY`。配置后会交替向两台 server 提交合成进件，核对同设备第 4 笔、同区域第 12 笔触发的具体规则。请使用隔离演示库，这些是正常记账的正式请求。

## 2026-11-26 本地验收

两台 server 连接同一 MySQL 8.4.6、Redis、ES 8.17.3 和演示供应商。供应商与授信的 145 个启用固定用例全部通过，五组白名单实验分别核对分组和业务结果。KYC 的三项草稿在隔离库经控制台审批发布后，使用合成图片通过正式接口完成定位、活体检测和比对。两台 server 的直接内存上限均为 256 MiB。

停止 ES 后，浏览器仍能执行并查看五条代表规则，跨节点两种拒绝阈值仍命中。恢复后逐条比较物理身份、追踪号和修订。1,257 条执行日志、3,991 条调用日志及 753 笔符合口径的历史进件全部对齐，待投递为零。631 份执行追踪的 MySQL 正文由 62,167,308 字节无损压缩到 6,550,556 字节。直接通过 SQL 删除业务行、还原数据库或回滚旧写入程序后，可按上面的独立前缀流程重建索引，使直接访问 ES 的分析工具也与新事实集一致。

在固定的 368 条授信日志中读取同一页 20 条记录，每轮预热后交替测量完整列表与摘要各 25 次，并逐项核对显示字段。响应正文约从 5.25 MB 降至 14.3 KB。两轮完整列表 P95 分别为 112、139 毫秒，摘要为 61、43 毫秒。这组数据衡量的是本机日志列表，不据此推算生产决策吞吐。

## 冻结制品和订单恢复

运行时按不可变文件引用、包摘要与制品摘要共享已校验的目录，目录缓存只保留业务配置和模型描述。模型原件按项目、文件名、长度及摘要绑定；各请求仍独立装配变量、模型和部署映射。首次冷读合并，同一节点上的推理使用有界工作队列，排队时间计入模型截止时间。容器中默认每个 ONNX 会话用一个算子线程，可用 `ONNX_INTRA_OP_THREADS` 调整。

根日志保留请求明确提交的空数组、空对象和 null。已完成订单使用保持 JSON 值的结果快照，查单、按原单恢复和幂等重复请求共用该表示。读取旧成功快照时，只从项目、根规则、追踪、尝试号、修订及制品摘要全部相符的成功审计行补回业务输出。

## 模拟供应商的回调与断点

两台 server 可在配置中填写相同的 `RULE_EXECUTION_CALLBACK_PROJECT_IDS`，值为目标库中已授权的模拟供应商项目 ID。该项目独占 `RULE_EXECUTION_CALLBACK_PARALLELISM` 个有界回调执行槽，默认四个。原有项目鉴权、QPS 和并发限制照常生效。模拟供应商可用 `EXAMPLE_ENGINE_URLS` 列出逗号分隔的两个内部引擎地址；按请求轮换，建立连接失败时尝试备用地址。

仅当提供方承诺按 Idempotency-Key 保存并返回同一次结果、同键只计费一次时，才在同步单步接口的“请求字段”勾选“供应商幂等”。引擎在发送前持久化随机调用键，恢复使用同一业务请求、调用键和计费键。演示供应商使用专用的 `example-supplier-receipts` 卷保留回执，复用该卷才能跨容器重建查回原结果。多步征信仍凭原受理回执继续轮询。旧版冻结模型的传输端点如具备同一供应商承诺，可通过部署配置 `RULE_PROVIDER_IDEMPOTENT_ENDPOINTS` 逐项明确声明，例如 `POST http://example-supplier:28084/data/income`。引擎在真正发出请求前复核方法与目的地址。此前没有可靠键或票据的未知调用仍需原单对账。
