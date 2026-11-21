# 多节点模型文件与台账素材

天枢默认使用 **MySQL 共享模型文件库**。server、副本和导入器连到同一管理库，相同原始字节的 SHA-256 对应一份逻辑原件。模型版本、项目绑定和文件名可以不同。几十 MiB 的模型不需要额外部署 MongoDB、对象存储服务或网络文件系统。

## 文件如何写入和读取

| 存储对象 | 保存内容 |
|---|---|
| `rule_file_reference` | 业务存储标识与原件摘要的绑定。保留项目作用域和原有 `ts-file-v1:` 引用，读取必须有对应绑定。 |
| `rule_file_object` | 原始 SHA-256、原始大小、编码、载荷数和存储大小。同内容跨项目、版本和节点共享一条目录记录。 |
| `rule_file_object_part` | 文件还原顺序，常规模型原件仅引用一条完整载荷。 |
| `rule_file_chunk` | LONGBLOB 存放完整的无损压缩原件，按载荷摘要复用。只有单个压缩载荷超过当前 MySQL 服务端包容量（预留 256 字节）才拆分。对于已确认内存紧张的 MySQL 实例，可设置 `RULE_MODEL_WRITE_PAYLOAD_LIMIT_BYTES` 降低单次写入上限；默认 0，完整载荷优先。 |

上传先经过真实扫描和模型格式校验，再按内容摘要在事务中安装。并发写入相同文件由数据库唯一键与行锁串行化。读取时在同一 BLOB 上按小窗口传输，依次核对载荷摘要，最后校验解压长度与原始文件摘要。每个 JVM 的字节缓存和运行时模型用于加速执行，Redis 传递发布通知。清空缓存、重启容器或更换宿主机后仍能从同一管理库恢复。[文件仓库](../rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/ModelFileStore.java) · [MySQL 二进制读写](../rule-engine-server/src/main/java/com/hengshucredit/rule/server/service/MysqlModelBlobStore.java)

原件只按内容保存一份。历史审批证据、签名 ZIP 和向外部系统导出的可携带制品有各自的保真要求。ZIP 按嵌入的 PMML/ONNX 条目边界划分，可复用相同的压缩数据块，取回时包摘要保持不变。旧签名包中内嵌的 Base64 JSON 或不同编码的历史证据仍可能占用空间。数据库副本和备份也属于独立的恢复副本。

## 部署与迁移

默认 `RULE_MODEL_STORAGE_BACKEND=MYSQL`。每台 server、导入器和迁移进程使用同一个 MySQL 实例和库。旧文件卷通过 `RULE_MODEL_STORAGE_DIRECTORY` 或 Compose 的 `MODEL_FILES_MOUNT` 提供给迁移进程。已有 NAS 的部署可显式使用 `FILESYSTEM`，该模式下所有机器必须挂载同一份支持同目录原子重命名的目录。不同宿主机上的同名本地 Docker 卷分别占用各自磁盘。[部署说明](deployment.md#模型原件上传检测与热加载) · [Docker 跨主机卷](https://docs.docker.com/engine/storage/volumes/#share-data-between-machines)

旧库迁移顺序为停写、备份并验证恢复、从可读旧卷的节点执行 `java -jar server.jar --migrate-model-files`，再启动新版本。备份要覆盖管理库和当时仍承担存储的目录；SQL 转储使用 `--hex-blob`，还原到新的库名检查每张表的行数和内容。保留迁移前备份供旧版 JAR 回滚，保留原卷直到验证冷读。完成迁移后，共享模型库的内容随 MySQL 备份；导出 SQL 和传输制品仍应按原件与包摘要校验。

## 容量与压缩

当前引擎默认模型原件上限为 256 MiB，冻结制品另有解包限制，执行时需要将模型装入内存。整份保存覆盖几十 MiB 的常规模型，超过实际单包上限才分段，模型加载仍需足够 JVM 或原生运行时内存。业务模型 JSON 仍按其原有结构处理；文件分块不会自动扩大所有业务 JSON 字段。对未来的 GiB 级文件，应将流式上传、扫描、解析和模型加载一起设计并验证。GridFS 也采用分块；S3 兼容对象存储适合已经有对象服务或需要大规模分段传输的场景。[MySQL 包长度](https://dev.mysql.com/doc/refman/8.4/en/packet-too-large.html) · [MongoDB GridFS](https://www.mongodb.com/docs/manual/core/gridfs/) · [S3 分段上传](https://docs.aws.amazon.com/AmazonS3/latest/userguide/mpuoverview.html)

本地样本的整文件压缩字节数如下，均解压并逐字节核对。表中是对比实验；实际数据库还包含文件目录和索引。

| 样本 | 原始字节 | gzip 9 | Zstandard 9 | Zstandard 19 |
|---|---:|---:|---:|---:|
| MAIN_A V9 PMML | 232,796 | 14,319 | 14,533 | 12,146 |
| MAIN_A V9 冻结 ZIP | 27,525 | 24,538 | 24,391 | 24,262 |
| ONNX 样本 2d106det | 5,030,888 | 4,642,592 | 4,642,481 | 4,644,694 |
| ONNX 样本 det_10g | 16,923,827 | 15,707,970 | 15,709,895 | 15,712,689 |

本批 PMML 的无损收益约 94%，两份 ONNX 约 7%～8%。运行时沿用 JDK 压缩能力，避免为原件重新量化而改变推理结果。已压缩的 ZIP 按原字节存储，以保留固定版本摘要。

## 实机核验

两个独立 JVM 通过同一 MySQL 保存和读取 MAIN_A，读节点关闭字节缓存并且没有挂载模型目录。校验得到 232,796 个原始字节，对应一个原件摘要。V9 在本机库实际保存为一条 14,679 字节的二进制载荷。另以 33,554,446 字节的 JSON 测试存取，压缩后 25,416,590 字节。在约 256 MiB 的服务端单包预算下是 **一条 BLOB**，配置为 4 MiB 的读取客户端也能读回；仅在另一次隔离测试将服务端单包上限降至 4 MiB 时，才保存在 7 条载荷中。两种配置都逐字节还原了原件及冻结包。节点测试使用同宿主机上的独立容器；物理机间的网络延迟和断连恢复需在实际集群验收。

## 台账图片

台账继续使用管理库的 `rule_ledger_asset`。上传暂存于 `raw_content`，扫描和安全解码通过后，将 PNG 写到 `content` 并清空暂存。每个逻辑素材有自己的 ID、权限和历史引用。隔离环境中，从节点 A 上传后，节点 B 使用已授权会话读到了完全相同的 PNG；无会话访问为 HTTP 401。重复上传同一张图片产生两个素材 ID，当前两行的 `content` 合计为 650,470 字节。[素材服务](../rule-engine-server/src/main/java/com/hengshucredit/rule/server/ledger/LedgerAssetService.java)

若需要进一步缩小台账，建议保留素材元数据与授权，按规范化字节摘要复用内容，再按图片实测选择更小的无损编码。三张实际控制台截图按当前 Java ImageIO 转成 PNG 合计 1,051,519 字节，无损 WebP 合计 436,860 字节，减少 58.5%，解码后逐像素一致。当前 WebP 插件用于读取，实现该优化还需增加写入器并同步调整 MIME 和备份。该测量仅适用于这三张截图。[WebP 编码](https://developers.google.com/speed/webp/docs/compression)
