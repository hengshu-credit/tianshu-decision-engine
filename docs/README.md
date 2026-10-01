# 天枢决策引擎前端操作手册

`index.html` 是面向业务配置人员的前端控制台操作手册，复用 `docs/project-usage/` 下的真实页面截图，不依赖外部 CDN。当前页面按 17 个一级入口整理，重点展示九类规则的设计器与表达式追踪树、外数供应商/API/调用追踪、分流实验/分流追踪树，以及审批、离线迁移和账户权限。功能演示图统一按 2560 × 1440（2K）视口采集。

## 本地预览

在仓库根目录执行：

```powershell
python -m http.server 4173 --directory docs
```

然后打开 <http://localhost:4173/>。直接双击 HTML 也能阅读，但本地 HTTP 服务更接近 GitHub Pages 的资源路径。

## GitHub Pages

`.github/workflows/pages.yml` 会在 `master` 分支的 `docs/` 内容变化时，将整个 `docs/` 目录发布到 GitHub Pages。部署完成后访问：

<https://hengshu-credit.github.io/tianshu-decision-engine/>

部署参数与教程见 [部署教程](deployment.html)；Java SDK 和 HTTP-only SDK 接入见 [Java 服务接入](java-service-integration.html)。

工作流使用 GitHub Actions 作为 Pages 构建来源，不需要额外安装 Node.js 或文档生成器。

截图可重复生成：`npm --prefix rule-engine-ui run build` 后执行 `npm --prefix rule-engine-ui run docs:screenshots`，会更新 `docs/project-usage/`；README 图库按 [README 图库维护说明](../scripts/docs/README.md) 执行，会更新 `docs/project-usage/` 与 `capture-manifest.json`。两套脚本都使用固定文档 API 样例并在结束时检查未匹配请求和浏览器错误。

