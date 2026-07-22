# 第三方许可证与开源交付说明

本文记录天枢决策引擎直接引入、且会影响发布方式的第三方组件。完整依赖树仍应在每次发布时通过 Maven 与 npm 锁文件生成 SBOM 并复核；本文不是法律意见。

## JPMML Evaluator Metro 1.7.7

- Maven 坐标：`org.jpmml:pmml-evaluator-metro:1.7.7`
- 许可证：GNU Affero General Public License version 3（AGPL-3.0）
- 许可证全文：[licenses/JPMML-EVALUATOR-AGPL-3.0.txt](licenses/JPMML-EVALUATOR-AGPL-3.0.txt)
- 上游源码：https://github.com/jpmml/jpmml-evaluator
- Maven Central：https://central.sonatype.com/artifact/org.jpmml/pmml-evaluator-metro/1.7.7

本项目选择以 AGPL 开源交付方式使用 JPMML，不采购商业许可证。JPMML 上游代码未在本仓库内修改；本项目仅在调用层适配其公开 API。

### Corresponding Source 交付要求

对外提供包含 JPMML 的可执行制品或网络服务时，发布负责人必须同时：

1. 提供本仓库对应发布版本的完整源代码、构建脚本、依赖锁文件、配置模板和本文件，不得只交付二进制包。
2. 保留 Apache-2.0 项目声明、JPMML 的 AGPL-3.0 声明及许可证全文，并清楚标识各自适用范围。
3. 向通过网络与该程序交互的用户提供明显、可访问的 Corresponding Source 获取入口；下载内容必须与正在运行的版本一致。
4. 若修改 JPMML 或其构建方式，连同修改内容、补丁和重建所需材料一并提供。
5. 发布前由合规负责人复核 AGPL 第 13 条等义务；不能满足开源交付条件时，不得发布包含 JPMML 的制品或服务，应改用已获授权的商业许可或替代实现。

### 重建方式

在 JDK 17 与 Maven 3.6+ 环境中，从交付的源码根目录执行：

```bash
mvn clean package -DskipTests
```

前端需使用 Node.js 20.19+ 单独构建：

```bash
cd rule-engine-builder-ui
npm ci
npm run build
```

精确依赖版本由根 `pom.xml`、各模块 `pom.xml` 以及 `rule-engine-builder-ui/package-lock.json` 共同锁定。

## ONNX Runtime 1.26.0

- Maven 坐标：`com.microsoft.onnxruntime:onnxruntime:1.26.0`
- 许可证：MIT
- 上游源码：https://github.com/microsoft/onnxruntime

ONNX Runtime 与 JPMML 的许可证不同；启用 `onnx-gpu` Maven profile 时还必须单独核对 CUDA、cuDNN 和运行环境许可。
