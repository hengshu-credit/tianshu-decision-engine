/**
 * Vue CLI 构建配置：生产构建为常规压缩打包，不再使用 javascript-obfuscator 混淆。
 * 构建产物默认输出到本目录下的 dist/，不写入 rule-engine-server。
 */
const CopyWebpackPlugin = require('copy-webpack-plugin')
const path = require('path')

// Webpack 插件：dev 模式下将 monaco-editor 的 vs/ 目录通过 devServer 中间件暴露，
// 解决 copy-webpack-plugin 在 dev server 内存文件系统下不生成实际文件的问题。
// 插件在 devServer.app 上注册中间件，早于 webpack-dev-middleware 处理请求，
// 确保 /vs/ 路径直接返回 node_modules 源文件而非 webpack HTML
class MonacoDevStaticPlugin {
  apply(compiler) {
    compiler.options.devServer = compiler.options.devServer || {}
    const vsDir = path.resolve(__dirname, 'node_modules/monaco-editor/min/vs')
    const serveStatic = require('serve-static')
    // onBeforeSetupMiddleware 在 webpack-dev-server v3 中注册于 webpack-dev-middleware 之前
    // Vue CLI schema 会过滤掉此选项，但通过 config.plugin 注入可绕过 schema 验证
    compiler.options.devServer.onBeforeSetupMiddleware = function (devServer) {
      devServer.app.use('/vs', serveStatic(vsDir, {
        dotfiles: 'ignore',
        etag: true,
        extensions: ['js']
      }))
    }
  }
}

module.exports = {
  /** 生产构建不跑 eslint-loader（避免历史代码阻断打包）；开发可依赖 IDE，或执行 npm run lint */
  lintOnSave: process.env.NODE_ENV !== 'production',
  productionSourceMap: false,
  devServer: {
    port: 9090,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  css: {
    loaderOptions: {
      scss: {
        additionalData: (content, loaderContext) => {
          const resourcePath = loaderContext.resourcePath || ''
          if (
            resourcePath.includes('variables.scss') ||
            resourcePath.includes('element-override.scss')
          ) {
            return content
          }
          return `@import "~@/styles/variables.scss";\n${content}`
        },
        sassOptions: {
          silenceDeprecations: ['legacy-js-api', 'import']
        }
      }
    }
  },
  chainWebpack: (config) => {
    if (process.env.NODE_ENV === 'production') {
      config.module.rules.delete('eslint')
    }

    // Monaco Editor：AMD loader 方式，将 monaco-editor/min/vs 目录复制到输出目录的 vs/
    // 生产构建时 copy-webpack-plugin 正常工作；dev 模式下通过 MonacoDevStaticPlugin 提供静态服务
    config.plugin('copy-monaco')
      .use(CopyWebpackPlugin, [{
        patterns: [
          { from: path.resolve(__dirname, 'node_modules/monaco-editor/min/vs'), to: 'vs', noErrorOnMissing: true },
          // 复制 Element UI 字体文件，避免 dev server 加载慢
          { from: path.resolve(__dirname, 'node_modules/element-ui/lib/theme-chalk/fonts'), to: 'fonts', noErrorOnMissing: true }
        ]
      }])

    // dev 模式注册 monaco 静态文件插件（绕过 Vue CLI schema 验证）
    if (process.env.NODE_ENV !== 'production') {
      config.plugin('monaco-dev-static').use(MonacoDevStaticPlugin)
    }
  }
}