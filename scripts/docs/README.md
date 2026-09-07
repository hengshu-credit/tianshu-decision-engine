# README 图库维护

README 引用 `docs/readme/` 中的当前界面截图。截图使用真实前端生产构建和仓库的固定文档 API 样例，不访问业务数据库。规则示例由当前核心模块编译并执行，原始表达式追踪再交给规则测试页面展示。

在仓库根目录执行（PowerShell、JDK 17、已安装前端依赖）：

```powershell
mvn -q -pl rule-engine-core -am compile
mvn -q -pl rule-engine-core dependency:build-classpath "-Dmdep.outputFile=target/docs-classpath.txt"
npm --prefix rule-engine-builder-ui run build
node scripts/docs/prepare-examples.cjs
$docsCp = 'rule-engine-core/target/classes;rule-engine-model/target/classes;' + (Get-Content rule-engine-core/target/docs-classpath.txt -Raw).Trim()
java --class-path $docsCp scripts/docs/CompileExamples.java rule-engine-core/target/docs/examples.json rule-engine-core/target/docs/executed-examples.json
node scripts/docs/capture-readme.cjs
```

首次运行需在前端目录执行 `npx playwright install chromium`。编译中间文件、执行结果和失败截图保存在被忽略的 `rule-engine-core/target/docs/`；最终 PNG 与采集清单位于 `docs/readme/`。所有浏览器页面和进程均在完成或失败后主动关闭。

脚本核对九份输出与明确预期，检查页面运行错误和未匹配 API，并通过 UI 选择规则、加载样例输入、执行测试、打开追踪页签。图片中的耗时是本机单次执行样例，不作为性能基准。设计器配置和测试均使用同一份模型；截图不表示已完成真实后端、数据库或外部资源联调。
