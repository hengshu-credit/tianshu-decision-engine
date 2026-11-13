# 天枢决策引擎文档

策略配置人员可以从[在线操作手册](https://hengshu-credit.github.io/tianshu-decision-engine/)开始。九类规则、外数 API 和分流实验均有可放大的配置图和追踪图。[仓库首页演示](../README.md#功能展示)

| 我想做什么 | 阅读入口 |
| --- | --- |
| 配置字段和决策输入 | [变量与数据对象导入](variable-data-object-import.md) · [对象引用取值](data-object-reference-resolution.md) · [衍生变量](derived-variables.md) |
| 接入外数并配置规则动作 | [外数请求与响应映射](external-source-execution.md) · [异步征信接口样例](examples/huarong-credit/README.md) · [调用外数与写入名单](rule-side-effect-actions.md) |
| 把规则接入业务系统 | [Java 服务与 SDK](java-service-integration.md) · [平台接口契约](platform-api-contract.md) · [服务端执行与缓存](api-execution-unification.md) |
| 部署、升级或回退 | [首次部署与参数](deployment.md) · [发布包构建](release-packaging.md) · [服务器更新](server-upload.md) · [数据库迁移](../rule-engine-server/src/main/resources/sql/migrations/README.md) |
| 维护生产服务 | [就绪检查与运行验收](quality-gates/runtime-quality-gates.md) · [容量与备份恢复](quality-gates/operations-runbook.md) · [组件扩容实测](quality-gates/server-resource-scaling.md) |
| 配置日志和凭据 | [Kafka 日志上报](kafka-execution-logs.md) · [日志保留](log-retention.md) · [凭据存储与脚本隔离](credential-storage-and-script-isolation.md) |
| 管理工作台和策略变更记录 | [系统配置](system-settings.md) · [台账、文章与版本](ledger-records.md) |

## 本地阅读与截图维护

在仓库根目录运行 `python -m http.server 4173 --directory docs`，用浏览器打开 [本地操作手册](http://localhost:4173/)。GitHub Pages 在 `master` 推送的 CI 成功后发布同一份静态手册。

按[截图与样例生成方法](../scripts/docs/README.md)编译并执行九个规则样例，然后采集首页及手册引用的原图。只有首页双栏展示的图片才生成 WebP 预览，采集清单记录本次生成的全部原图。
