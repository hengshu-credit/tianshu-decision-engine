# README 图库维护

README 引用 `docs/project-usage/` 中的当前界面截图。截图使用真实前端生产构建和仓库的固定文档 API 样例，不访问业务数据库，统一使用默认主题和 2560 × 1440（2K）视口。规则示例由当前核心模块编译并执行，原始表达式追踪再交给规则测试页面展示。

在仓库根目录执行（PowerShell、JDK 17、已安装前端依赖）：

```powershell
mvn -q -pl rule-engine-core -am compile
mvn -q -pl rule-engine-core dependency:build-classpath "-Dmdep.outputFile=target/docs-classpath.txt"
npm --prefix rule-engine-web run build
node scripts/docs/prepare-examples.cjs
$docsCp = 'rule-engine-core/target/classes;rule-engine-model/target/classes;' + (Get-Content rule-engine-core/target/docs-classpath.txt -Raw).Trim()
java --class-path $docsCp scripts/docs/CompileExamples.java rule-engine-core/target/docs/examples.json rule-engine-core/target/docs/executed-examples.json
node scripts/docs/capture-readme.cjs
```

首次运行可在前端目录执行 `npx playwright install chromium`，也可设置 `PLAYWRIGHT_CHANNEL=chrome` 复用本机 Chrome。中间文件、待核对的新图和失败截图放在 `rule-engine-core/target/docs/`。脚本逐一核对本次采集结果与首页、操作手册引用的原图，再将 2K PNG 和清单写入 `docs/project-usage/`。只有首页引用的图片才生成 WebP 预览，位于 `thumbs/`，README 用它们预览，点击打开对应的 PNG。

脚本核对九份执行输出与预期，检查页面错误和未匹配的请求，并通过 UI 选择规则、执行测试、打开“表达式追踪树”，确认相应规则类型的追踪内容可见。决策树和决策流先在各自的设计器中点击“一键美化”并保存到演示草稿，追踪视图沿用保存后的节点位置。

分流实验以同一实验的配置、分组判断和实际子规则为一组。外数 API 以同一接口的“请求字段”和“接口测试 → 完整外数调用链”为一组。文档夹具校验接口标识、字段绑定与测试入参后，返回该演示调用的阶段记录。追踪小图取自界面中的追踪面板，原图保留整页上下文。

