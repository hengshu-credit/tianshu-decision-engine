import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { defineConfig, loadEnv, normalizePath } from 'vite'
import vue from '@vitejs/plugin-vue'
import { viteStaticCopy } from 'vite-plugin-static-copy'

const projectRoot = path.dirname(fileURLToPath(import.meta.url))
const variablesFile = normalizePath(path.resolve(projectRoot, 'src/styles/variables.scss'))
const elementOverrideFile = normalizePath(path.resolve(projectRoot, 'src/styles/element-override.scss'))

export default defineConfig(({ mode }) => {
  // 仅用于开发服务器配置，不将根目录中的凭据注入浏览器。
  const env = loadEnv(mode, path.resolve(projectRoot, '..'), ['SERVER_PORT', 'VITE_PORT', 'VITE_DEV_PROXY'])

  return {
    base: './',
    // Monaco 的 AMD loader 会暴露全局 define；LogicFlow 的部分依赖仍包含 UMD
    // 分支，懒加载设计器时不能让这些已打包依赖再次向 Monaco 注册匿名模块。
    define: {
      'define.amd': 'false'
    },
    optimizeDeps: {
      rolldownOptions: {
        transform: {
          define: { 'define.amd': 'false' }
        }
      }
    },
    plugins: [
      vue(),
      viteStaticCopy({
        targets: [
          {
            src: normalizePath(path.resolve(projectRoot, 'node_modules/monaco-editor/min/vs')),
            dest: '.'
          }
        ]
      })
    ],
    resolve: {
      alias: {
        '@': path.resolve(projectRoot, 'src')
      },
      extensions: ['.mjs', '.js', '.mts', '.ts', '.jsx', '.tsx', '.json', '.vue']
    },
    server: {
      port: Number(env.VITE_PORT || 9090),
      strictPort: true,
      proxy: {
        '/api': {
          target: env.VITE_DEV_PROXY || `http://localhost:${env.SERVER_PORT || 8080}`,
          changeOrigin: true
        }
      }
    },
    css: {
      preprocessorOptions: {
        scss: {
          additionalData(source, filename) {
            const normalized = normalizePath(filename || '')
            if (normalized === variablesFile || normalized === elementOverrideFile) return source
            // 变量名沿用历史 `$--*` 形式，在 Sass module 中会被视为私有成员，
            // 因此迁移期间继续使用 @import 注入以保持现有组件行为。
            return `@import "@/styles/variables.scss";\n${source}`
          },
          silenceDeprecations: ['legacy-js-api', 'import']
        }
      }
    },
    build: {
      sourcemap: false
    }
  }
})
