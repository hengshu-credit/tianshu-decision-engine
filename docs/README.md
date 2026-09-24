# 天枢决策引擎前端操作手册

`index.html` 是面向业务配置人员的前端控制台操作手册，复用 `docs/project-usage/` 下的真实页面截图，不依赖外部 CDN。

## 本地预览

在仓库根目录执行：

```powershell
python -m http.server 4173 --directory docs
```

然后打开 <http://localhost:4173/>。直接双击 HTML 也能阅读，但本地 HTTP 服务更接近 GitHub Pages 的资源路径。

## GitHub Pages

`.github/workflows/pages.yml` 会在 `master` 分支的 `docs/` 内容变化时，将整个 `docs/` 目录发布到 GitHub Pages。部署完成后访问：

<https://hengshu-credit.github.io/tianshu-decision-engine/>

工作流使用 GitHub Actions 作为 Pages 构建来源，不需要额外安装 Node.js 或文档生成器。
