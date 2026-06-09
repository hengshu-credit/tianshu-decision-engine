/**
 * Vue CLI 构建配置：生产构建为常规压缩打包，不再使用 javascript-obfuscator 混淆。
 * 构建产物默认输出到本目录下的 dist/，不写入 rule-engine-server。
 */
const MonacoWebpackPlugin = require('monaco-editor-webpack-plugin')

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

    // Monaco Editor：插件自动处理 worker 文件注入
    config.plugin('monaco-editor').use(MonacoWebpackPlugin, [{
      languages: ['json']
    }])

    // 强制 babel 处理 monaco-editor（include-loader 加载的 ESM 文件需要转译）
    config.module.rule('js')
      .test(/\.js$/)
      .include
      .add('/node_modules/monaco-editor/')
      .end()
      .use('babel-loader')
      .loader('babel-loader')
  }
}