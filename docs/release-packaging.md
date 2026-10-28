# Windows、macOS、Linux 生成发布包

三个系统使用同一份 `scripts/package-deployment.mjs`，Windows 提供 PowerShell 入口，macOS/Linux 提供 POSIX Shell 入口。宿主机安装 Node.js 20.19+、Git（源码检出时）和可运行 Linux 容器的 Docker，并确保 `docker-compose version` 可直接执行。要求现代 Compose v2+（也支持 v5），不支持旧 Python Compose v1。只有命令名为 `docker-compose` 的兼容入口时才运行，不自动切换到其他命令。

Windows/macOS 可使用 Docker Desktop，Linux 可使用 Docker Engine 与 Compose standalone。Docker 需要能拉取 Maven、Node 和运行镜像；首次构建需访问 Maven/npm 仓库。无需在宿主机安装 Java、Maven 或 npm 项目依赖。

## 构建

在源码根目录执行，正式交付建议带 `--with-tests`：

Windows PowerShell：

```powershell
.\scripts\package-deployment.ps1 --with-tests
```

macOS / Linux：

```sh
sh scripts/package-deployment.sh --with-tests
```

三端也可直接运行，Windows 若限制 PowerShell 脚本执行，可使用此入口，无需修改系统策略：

```sh
node scripts/package-deployment.mjs --with-tests
```

`--help` 显示用法；省略 `--with-tests` 仅构建。入口允许从其他目录使用脚本绝对路径调用，路径可包含空格。构建容器统一由 `docker-compose` 启动，完成后自动移除；只保留 Maven/npm 缓存卷。构建目录与应用服务隔离，不停止现有应用，不覆盖工作区的 `target`、`rule-engine-web/dist`、`docker/runtime`。

默认使用 `linux/amd64` 构建，适用于当前 x86-64 Linux 服务器；Apple Silicon 构建此目标需要 Docker 的 amd64 仿真支持。这里的“三端”指构建宿主机，产物部署到 Linux 容器，不是 Windows/macOS 原生可执行程序。需要原生 ARM 构建时可设置 `PACKAGE_PLATFORM=linux/arm64`，并在对应目标上验证模型运行库。本次实测环境为 Windows + Linux amd64 Docker；macOS 和原生 ARM 未实机验收。

## 输出与配置

`output/` 中生成独立的时间戳目录、同名 ZIP 和 `.zip.sha256`。ZIP 包含：

- `docker/runtime/server.jar`、`runtime.jar`（CPU ONNX）及 `dist/`。
- 应用 Compose、Nginx/Kafka 配置和两份 `.env.*.example` 模板。
- `README-部署.md`、完整文档、许可证、`BUILD-INFO.txt`。
- 与构建一致的 `source/` 和逐文件 `SHA256SUMS.txt`。

历史包保留，不自动删除。默认不包含本机 `.env.server` / `.env.client`、数据库数据或日志。构建前冻结源码清单，再使用同一份快照编译和交付。解压后的 `source/` 含 `.release-source-files.json`，无需初始化 Git 即可使用相同入口重建；新增源码文件时需更新该清单，或将源码纳入 Git 管理。

可选环境变量：

| 变量 | 含义 |
|---|---|
| `PACKAGE_CONFIG_DIR` | 显式携带指定目录的 `.env.server` 和 `.env.client`；默认不携带凭据。包含凭据的 ZIP 仅供私下交付 |
| `PACKAGE_RUN_TESTS=true` | 等同 `--with-tests` |
| `PACKAGE_MAVEN_IMAGE` / `PACKAGE_NODE_IMAGE` | 覆盖构建镜像，默认 `maven:3.9.9-eclipse-temurin-17` / `node:26-bookworm` |
| `PACKAGE_PLATFORM` | 默认 `linux/amd64`，可选 `linux/arm64` |
| `PACKAGE_SKIP_JAVA=true` | 仅在明确需要复用本地可执行 JAR 时使用，不重建或测试后端；本次正式包不使用 |

Windows 用 `$env:PACKAGE_CONFIG_DIR = 'E:\private\production'`；macOS/Linux 用 `export PACKAGE_CONFIG_DIR='/private/production'`，然后执行相同构建入口。既有服务器升级始终保留原配置与主密钥。

ZIP 校验：Windows 用 `Get-FileHash -Algorithm SHA256 包文件名.zip`；macOS 用 `shasum -a 256 -c 包文件名.zip.sha256`；Linux 用 `sha256sum -c 包文件名.zip.sha256`。解压后 Linux 运行 `sha256sum -c SHA256SUMS.txt` 验证包内所有文件。

服务器安装、升级、备份、启动、状态和回退按 [部署说明](server-upload.md) 执行，服务操作统一使用 `docker-compose`。
