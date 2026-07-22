const CopyWebpackPlugin = require('copy-webpack-plugin')
const path = require('path')
const serveStatic = require('serve-static')

const minVsDir = path.resolve(__dirname, 'node_modules/monaco-editor/min/vs')

module.exports = {
  lintOnSave: process.env.NODE_ENV !== 'production',
  productionSourceMap: false,
  devServer: {
    port: 9090,
    watchFiles: {
      paths: ['src/**/*', 'public/**/*'],
      options: {
        ignored: [
          path.resolve(__dirname, 'node_modules'),
          path.resolve(__dirname, 'dist')
        ]
      }
    },
    setupMiddlewares: (middlewares, devServer) => {
      devServer.app.use('/vs', serveStatic(minVsDir, {
        dotfiles: 'ignore',
        etag: true,
        extensions: ['js']
      }))
      return middlewares
    },
    proxy: {
      '/api': {
        target: process.env.VUE_APP_DEV_PROXY || 'http://localhost:8080',
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
          return `@import "@/styles/variables.scss";\n${content}`
        },
        sassOptions: {
          silenceDeprecations: ['legacy-js-api', 'import']
        }
      }
    }
  },
  chainWebpack: config => {
    if (process.env.NODE_ENV === 'production') {
      config.module.rules.delete('eslint')
    }

    config.plugin('copy-monaco')
      .use(CopyWebpackPlugin, [{
        patterns: [
          {
            from: minVsDir,
            to: 'vs',
            noErrorOnMissing: true
          }
        ]
      }])
  }
}
