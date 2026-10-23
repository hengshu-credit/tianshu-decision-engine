# 外数与数据库的内容选择配置

当前外数 API 仅使用 `executionConfig.version = 2`。请求、响应、条件、步骤和样例统一保存在该配置中；不读取旧请求映射、输入覆盖或旧轮询/回调配置，也不自动迁移。

## 内容选择与协议路径

- 引擎资源通过 `refType + refId` 关联。名称与编码用于展示，修改名称不会改变引用身份。
- 字段、常量、数据对象、模型输出、固定值和表达式使用同一选择交互；各入口按实际能力提供选项。外数表达式只允许无副作用的内置函数。
- 单击查看或展开，双击或 Enter 选择；支持搜索、已选内容和最近使用内容。
- 供应商字段属于协议路径，保留大小写、点号、空格和数组下标。JSON 键 `Mobile.Number` 应选择 `["Mobile.Number"]`；`Mobile.Number` 表示嵌套字段。
- 从请求或响应 JSON 样例生成字段目录后再选择路径。手工路径允许保留，但未在样例中确认、类型不匹配或已从目录移除时会提示。
- 项目规则的外数目录包含启用的全局 API 和当前项目 API；全局规则只提供全局 API。管理列表的项目筛选与这个可引用目录分别生效。

## 请求、响应与变量取值

API 的“请求字段”支持 Header、Query、Cookie、JSON 和表单。每个字段有独立且稳定的 `id`，例如：

```json
{
  "version": 2,
  "requestFields": [{
    "id": "customer-mobile", "location": "JSON", "path": "Customer.Mobile",
    "required": true, "overridable": true,
    "value": { "kind": "REFERENCE", "refType": "VARIABLE", "refId": "7" }
  }],
  "responseBranches": [{
    "id": "result", "mode": "FIELDS", "sample": { "score": 650 },
    "outputFields": [{ "id": "score", "path": "Score", "value": { "kind": "PATH", "value": "response.body.score", "protocolPath": true } }]
  }]
}
```

示例 ID 必须替换为当前项目可见的真实资源。响应可整体取值或逐字段组装：`body` 是组装结果，`response.body` 是原始响应。选择器同时提供请求、响应、鉴权、执行状态与追踪字段。

API 来源变量和对象只覆盖明确开放的请求字段，保存 `requestOverrides: { "customer-mobile": 操作数 }`；不存在或未开放的 ID 会被拒绝。结果路径例如 `body.Score`，从 API 结果目录选择。

内容选择器的“写入路径”面板按“取值来源 → 子路径 → 结果类型”配置。先选择对象字段、整个数据对象、模型或 API 内容，再填写相对于该来源的子路径；留空即取整个内容。子字段可以在运行时赋值，无需提前补齐结构。动态子路径须选择与实际返回值一致的结果类型，以提供正确的比较运算符和参数类型。

例如，选择 `TSRequestBody.params`，子路径填 `customer.score`，结果类型选“数值”；选择模型 `creditModel`，子路径可填 `outputs[0].score`；选择 API 的 `response.body`，子路径可填 `data.score`。数组下标从 0 开始，特殊键名使用 `["User.Name"]`。摘要同时展示来源和子路径，保存、重新打开、改名和版本选择均以稳定资源 ID 关联。

整个数据对象的引用类型为 `DATA_OBJECT_ROOT`，对象字段为 `DATA_OBJECT`；模型整体为 `MODEL`，输出字段为 `MODEL_OUTPUT`，不得混用对象 ID 与字段 ID。`PATH` 的 `valueType` 是所取结果的类型，`anchorType` 是来源类型；`anchorPath`、`anchorRelativePath` 保留所选来源，API 的 `relativePath` 始终相对于完整调用结果。

子路径在所选来源的目录中进行最长匹配。例如选择 `request` 并输入 `customer.stats.score`，若目录声明到 `customer.stats`，面板和输入框摘要会显示该内容的名称、完整编码、版本，以及剩余的 `.score`。匹配按路径段区分普通键、带引号的键和数组下标，不会把 `customerExtra` 当作 `customer`。固定版本只读取该版本目录并保留展示快照；匹配展示不改变已选来源的 ID 和版本。

多步链路的每个 HTTP 步骤均可在“本步骤请求样例与字段目录”中导入 JSON。目录独立保存在该步骤的 `requestSample`，不会覆盖主请求或其他步骤，也不会把样例值自动写入请求取值。

## 鉴权内容

数据源、API 和链路步骤共用鉴权编辑器。Basic、Bearer、API Key、Token 请求头和请求体均支持内容选择。密码、Token 和 Key 固定值在收起时遮罩显示。

Token 请求体支持嵌套对象、数组固定值和表达式。字段名原样保存，重复键、空字段名必须修正后才能保存。导入鉴权响应样例后可选择 Token 和过期秒数路径；Token 前缀中的空格原样保留。

选择 XML 或纯文本 Content-Type 时，请求体使用内容选择器配置完整文本，支持固定文本、字段引用与表达式。

鉴权引用参与依赖解析、作用域校验和请求身份计算；不同鉴权输入不共用同一个请求身份。请求预览只组装并脱敏展示，不发送网络请求。

同一对象的不同子路径、不同版本分别参与调用身份计算；步骤独立鉴权也参与响应缓存隔离。编辑其他鉴权设置时，尚未完成的请求头或请求体行会保留，补齐字段名和取值后再保存。

## 数据库变量与在线查询

数据库变量与对象按 SQL 中 `?` 的顺序配置类型化固定值或稳定字段引用，引号和注释里的 `?` 不计入。支持变量、常量、数据对象和模型输出，参数数量必须匹配。数值文本原样保留，由后端按声明类型解析，避免浏览器大整数精度损失。

数据库变量的 `sourceConfig` 示例：

```json
{
  "dbDatasourceId": 1,
  "sql": "select score from customer_score where customer_id = ?",
  "params": [{ "kind": "REFERENCE", "refType": "VARIABLE", "refId": 2 }],
  "maxRows": 2000,
  "queryTimeoutSeconds": 30
}
```

- `maxRows`：非负整数，不再截断至 500；0 表示不限制。省略时变量仍取 1 行，在线查询取 100 行。
- `queryTimeoutSeconds`：非负整数，默认 5 秒；0 表示不设置 JDBC 查询超时。驱动、网络和上游请求自身的超时仍可能先结束请求。
- `resultPath`：如 `0.score`。留空且最多取 1 行时，单列返回标量、多列返回对象；配置多行或不限行数时返回行集合，避免单列多行仅保留第一行。

## Token 失效判断

API 详情的“鉴权”中启用“鉴权失败刷新”。默认判断 HTTP 401/403；选择“自定义条件”可组合 HTTP 状态、原始响应体业务码和响应头。自定义条件替代默认条件，需要兼容 HTTP 401/403 时请将其加入 OR 条件组。

```json
{
  "type": "group", "operator": "OR", "children": [
    { "path": "httpStatus", "operator": "in", "values": ["401", "403", "498"] },
    { "path": "body.code", "operator": "==", "value": "TOKEN_EXPIRED" }
  ]
}
```

条件在响应脚本和字段映射之前对原始 HTTP 响应判断。仅 TOKEN_API/OAUTH2 等具有可刷新 Token 的鉴权生效；每次完整 API 调用最多强制刷新一次，再次失效立即失败。

## 异步外数在规则中的使用

规则同步等待外数最终结果。配置 API 为异步，完成以下轮询或回调协议，再创建 API 来源变量，结果路径例如 `body.score`，在设计器中按变量 ID 引用该变量。

执行顺序：提交任务 → 读取提交任务号 → 等待轮询或回调完成 → 提取最终结果 → API 响应映射 → 变量结果路径 → 规则计算。任务回执不会当作业务结果。仅最终成功结果进入响应缓存。

API 总超时包含提交、Token 获取、轮询间隔、查询和回调等待。选择 WAIT 策略时可保存待完成链路的检查点，调用方按 root trace 查询或恢复，恢复时继续原任务。同一请求的变量与对象消费者复用结果；改变生效请求字段或鉴权输入会产生不同请求身份。外数试调用同样支持当前链路的暂停、恢复和取消。

SDK 必须使用 `serverSideExecution=true`，并将 `httpTimeoutMs` 设为覆盖整个规则耗时。调用方、代理和项目访问策略的请求超时也应覆盖预计等待时间。控制台外数和变量试调用由服务端时间预算控制，不再被固定 15 秒前端超时截断。

### 引擎轮询

异步模式在 `executionConfig.steps` 中依次配置提交与查询步骤。每个步骤有稳定 `id`；后续步骤通过 `steps.<id>.body` 读取前序结果，轮询地址例如 `/report/${steps.submit.body.taskId}`。请求字段与主请求使用同一结构，可独立配置鉴权、脚本与重试。

查询步骤的 `poll` 示例：

```json
{
  "intervalMs": 1000,
  "maxAttempts": 30,
  "until": { "path": "body.status", "operator": "==", "value": "DONE" }
}
```

`maxAttempts: 0` 表示不限次数，仍受时间预算控制。最终结果经响应分支组装后再由变量或对象取值；超时按异常策略暂停或失败，明确失败与次数耗尽进入终态。

### 外部回调

回调步骤的地址模板为 `https://实际公网地址/api/external-callback/${invocationId}`。每次调用生成独立地址，提交请求通过协议路径 `callbacks.<回调步骤ID>.url` 传递给供应商。

提交任务号路径与回调任务号路径可以不同。回调请求体须为 JSON，并包含配置的任务号、状态和结果。配置签名 Header 及共享密钥，供应商对**原始 HTTP 请求体字节**计算 HMAC-SHA256，以十六进制签名放入该 Header（也接受 `sha256=` 前缀）。

回调在独立 HTTP 请求中验签。Redis 以调用 ID 保存加密的临时状态，可处理跨实例回调以及回调早于提交响应的情况。重复终态通知保留首次结果；非终态通知不结束等待；回调任务号必须与提交回执匹配。等待结束后清理状态，过期通知不再接受。该协议不等同于任意供应商的自定义回调签名协议。

## 规则开放 API

请求来源可从 JSON 请求样例生成目录，也可填写 Header 名；目标选择真实规则入参。响应选择真实引擎输出并填写对外字段名。内侧保存 `refType + varId`，外侧保存协议路径。超出 JavaScript 安全整数范围的资源 ID 保留为字符串。
