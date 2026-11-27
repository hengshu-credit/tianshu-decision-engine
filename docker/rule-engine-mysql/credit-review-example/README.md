# 衡枢真信授信分流演示

[HENGSHUCREDIT.zip](./HENGSHUCREDIT.zip) 是通过控制台导出的项目配置。它包含 26 条已发布规则和 2 个已启用实验。演示使用衡枢模拟供应商。首次安装整套演示时，在控制台的「离线迁移」上传三项目合集，预检后勾选执行固定用例并恢复发布。

| 入口 | 行为 |
| --- | --- |
| `CREDIT_ADMISSION` | 原授信流程结束前依次执行决策表、简单交叉表、复杂评分卡和 QL 脚本，输出授信分层、复核间隔、跟踪评分和复核动作。提前拒绝的进件也有这四项结果。 |
| `CREDIT_ADMISSION_PLUS` | 复用四个收尾子规则，再检查原额度。额度小于等于零时拒绝，额度归零。已通过且额度为正时在原额度上增加 1,000 元。 |
| `CREDIT_WHITELIST_ROUTE` | 按已有手机号和身份证白名单配置分流。 |
| `CREDIT_LIMIT_30_70` | 30% 进入 `BASE_30`，70% 进入 `PLUS_70`。提交相同请求键和相同入参可取得原实验订单。引擎在调用策略前记录组、修订和制品摘要。 |

`HS_INCOME` 启用 300 秒响应缓存和 Redis 二级缓存。同一申请人复用同一份收入报告时，两个独立进件可传相同的可选入参 `income_report_id`。未提供该字段时，供应商查询编号取本次 `request_id`。同时使用姓名、证件、手机号和实际报告编号区分有效请求。其他外数接口沿用原缓存配置。

混合存储验收启用了 `RULE_SEARCH_ENABLED=true`。MySQL 保存订单、断点和账单，ES 承接日志与历史特征查询。[连接参数及索引核对方法](../../../docs/elasticsearch-log-storage.md)

## 复测

将一个能在原策略下取得 20,000 元额度的演示进件存为 `approved.json`。传入两个节点的根地址和本环境的项目凭据。脚本为每笔进件生成独立订单号、设备和位置，并使用同一份收入报告覆盖跨订单复用。

```shell
python scripts/verify-credit-routing.py --node http://node-a:8080 --node http://node-b:8080 --params approved.json --orders 40 --clients 12 --check-mysql --report routing-result.json
```

鉴权使用环境变量 `TIANSHU_PROJECT_TOKEN`，或 `TIANSHU_AUTH_CODE` 和 `TIANSHU_CREDIT_API_KEY`。启用数据库对账时需要 PyMySQL，以及 `TIANSHU_QA_MYSQL_HOST`、`TIANSHU_QA_MYSQL_PORT`、`TIANSHU_QA_MYSQL_USER`、`TIANSHU_QA_MYSQL_PASSWORD`、`TIANSHU_QA_MYSQL_DATABASE`。对账账户只需读取调用日志和账单明细。

维护演示设计时，可以从尚未增加随机额度实验的衡枢真信项目导出包生成配置草稿。

```shell
python scripts/package-credit-review-demo.py HENGSHUCREDIT-before.zip HENGSHUCREDIT-drafts.zip
```

生成器校验源包和组件摘要，并把新增引用绑定到资源 ID。当前目录中的交付包来自实际导入、固定用例回归和审批发布后的控制台导出。
