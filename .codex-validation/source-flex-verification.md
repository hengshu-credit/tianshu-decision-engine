# 2026-09-16 数据库与异步外数验证

- 分支：master；保留工作区已有其他修改。
- 后端 `mvn clean install -DskipTests` 完整构建通过。
- 后端 `mvn test -DargLine=-Djdk.net.unixdomain.tmpdir=E:/workspace`：1420 个测试，16 个外部资产/诊断测试跳过，0 失败/错误。短临时目录用于避开当前 Windows JDK Unix Domain Socket 的 Invalid argument 环境问题。
- 前端 `npm test`：186 个文件、2269 个用例全部通过；`npm run lint` 与 `npm run build` 通过。
- 浏览器通过真实 UI 创建并审批验证连接、模拟供应商和轮询接口，没有通过后端写入跳过配置步骤。
- Docker rule-engine-mysql：[::1]:3306；本机 127.0.0.1:3306 另有转发进程。
- 查询实际返回 600 行（设置上限 2000）；1 秒超时中止 sleep(2)；8 秒超时允许 sleep(6) 正常返回。
- 轮询接口配置 16000ms 间隔和 20000ms 总超时，经过 PENDING/SUCCESS 后返回 body.score=730，未被原前端 15 秒超时截断。
- 回调试调用：本地供应商实际 POST 动态回调地址，HMAC-SHA256 验签通过，提交任务号与回调任务号不同字段，返回 body.score=740。
- Token 试调用：HTTP 200 的 body.code=TOKEN_EXPIRED 触发刷新，再次请求得到 body.score=720；模拟供应商记录两次 Token 获取。
- 变量草稿 UI 选择已生效轮询 API，resultPath=body.score，预览 resolvedValue=730；草稿未保存。
- 集成测试覆盖回调先于提交回执、结果注入变量并参与 QLExpress 判断、超时不重复提交任务、错误签名、重复通知、依赖刷新和截止时间传递。
- 本地保留带“调用链验证0916”标记的数据库连接、供应商、轮询 API，便于检查；模拟服务停止后外数测试需重新启动对应模拟服务。
- 本次启动的前端、后端和模拟服务已主动停止；原有 8080/9090 服务未停止。

使用方式见 `docs/external-source-execution.md`。
