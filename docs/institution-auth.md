# 全局规则与分流实验的机构鉴权

在 **规则管理** 或 **分流管理** 的列表工具栏选择 **机构鉴权**。两个入口管理同一份配置。管理员可以创建机构，或在已有机构下新增凭据。每条凭据有独立的鉴权编码、类型、秘密、访问策略、启用状态和 SFTP 开关。机构可以独立于项目；不同机构也可关联同一个管理项目。既有凭据的机构和管理归属项目固定，改显示名称保留关联。

| 类型 | 规则、实验 HTTP 认证 | 显式开通后的 SFTP 登录 |
| --- | --- | --- |
| Basic | HTTP Basic 用户名、密码 | 同一用户名和密码 |
| API Key | 按配置传入接口密钥，默认请求头 X-Rule-Api-Key | 控制台分配的 SFTP 登录名，密码为同一 API Key |
| HMAC-SHA256 | 使用 Access Key、时间戳、nonce、请求签名 | 用户名为同一 Access Key，密码为原 Secret；SSH 加密传输 |

机构凭据默认可执行所有已发布的全局规则和已启用的全局分流实验。OpenAPI 契约还需该规则已启用并发布开放接口，并传入与实际认证凭据对应的 X-Auth-Code。同步下载完整 SDK 资料属于独立能力。项目私有规则和实验仍使用项目鉴权。原部署级 Ed25519 全局签名沿用原有运维权限范围。

正式执行入口为 `POST /api/rule/sync/execute/{ruleCode}` 和 `POST /api/rule/runtime/experiment/execute/{experimentCode}`。机构还可在 `POST /api/rule/auth/token` 使用原凭据换发短期 HTTP Bearer Token。SFTP 客户端直接登录，无需调用换票接口。SFTP 同一监听端口要求已开通的登录名唯一，Basic 用户名与 HMAC Access Key 应分别分配。

**调用与订单归属。** 机构只能对自己这套凭据受理的订单查单或续跑。由同一长期凭据换发的新 Token 可以继续处理原单。两家机构使用相同进件号时分别建立订单。同一机构使用另一套凭据时也有独立的订单和交件目录。管理归属项目仅用于管理，不向机构开放该项目的私有资源。

**管理权限。** `institution-auth:view` 用于查看脱敏列表，`institution-auth:manage` 用于新增、编辑、显式查看完整值、轮换和停用。管理操作还要求账户可编辑全局内容。项目上的权限覆盖不能授予这些能力。只有明确开通 SFTP 的配置可以用机构凭据上传文件。[SFTP 登录与目录说明](institution-sftp.md)

**升级顺序。** 先备份 MySQL，并保留原主密钥、SFTP 数据卷与主机密钥。正常发布新版 server，启动时会自动升级表结构，再把同版 server.jar 更新到存储主机的 SFTP_SERVER_JAR 稳定路径并重建 SFTP 服务。将新版控制台 dist 与 server 配套部署；多副本场景在各节点均完成升级后向机构发放新凭据。下列命令沿用平铺安装示例；如已有网络覆盖，追加原先的 -f 参数。

~~~bash
docker compose --env-file .env.server -f docker/docker-compose.sftp.yml up -d --no-deps --force-recreate --wait sftp
~~~
