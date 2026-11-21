# 离线迁移包中的原生函数扩展

离线导出的 SCRIPT 函数携带脚本文本。格式 3 对独立部署的 JAVA 函数和可定位实现类的 Spring Bean 增加了扩展 JAR 附件。导出器根据实际加载实现的代码来源，在管理员配置的函数扩展目录内查找原件。附件按 SHA-256 寻址，共用一个 JAR 的函数只占一份。平台内置实现、应用本身的类及无法取得单独 JAR 的 Bean 继续使用目标环境提供的实现。只包含配置和模型的旧包继续支持格式 1、2。

在每个需要执行这些函数的节点，将经过部署方确认的 JAR 放入独立的只读扩展目录，包含扩展所需的其他依赖，并让运行时类加载器能访问它。原生方法继续使用 `@ScriptCallable` 和原生函数权限校验；Spring Bean 还需在目标应用中注册。服务器使用 Spring Boot 可执行 JAR 时，例如：

```bash
java -DRULE_FUNCTION_EXTENSION_DIRECTORY=/srv/tianshu/functions -Dloader.path=/srv/tianshu/functions -cp server.jar org.springframework.boot.loader.launch.PropertiesLauncher
```

服务端默认导出目录为工作目录下的 `extensions/functions`。可通过环境变量 `RULE_FUNCTION_EXTENSION_DIRECTORY` 或同名 JVM 属性指定。SDK 自执行时也要部署匹配的扩展及运行依赖。扩展更换后重启相应节点。

格式 3 的预检会核对所选函数绑定的 JAR 摘要；目标缺失或同名实现摘要不符时，面板显示需部署的包内路径，导入被拦截。迁移器不会把上传的 JAR 写入运行目录或加载为服务端代码。导入不会替换现有 JAR。需要人工核验时可直接从 ZIP 的 `function-jars/` 提取文件，并比对摘要。旧包仅有类名与方法名时仍须按原流程安装扩展。

Compose 部署可把 `FUNCTION_EXTENSIONS_MOUNT` 设为受信目录绝对路径，在原有 Compose 文件后追加 `-f docker/compose.function-extensions.yml`。该文件为 server 挂载只读目录，并保留各副本原有的执行日志和名单写入日志目录。导出比对的是源节点当前实际部署的 JAR；历史绑定只有类名时，应确认部署版本与所需历史实现一致。
