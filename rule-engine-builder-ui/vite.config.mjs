import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { defineConfig, normalizePath } from 'vite'
import vue from '@vitejs/plugin-vue'
import { viteStaticCopy } from 'vite-plugin-static-copy'

const projectRoot = path.dirname(fileURLToPath(import.meta.url))
const variablesFile = normalizePath(path.resolve(projectRoot, 'src/styles/variables.scss'))
const elementOverrideFile = normalizePath(path.resolve(projectRoot, 'src/styles/element-override.scss'))

export default defineConfig({
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
    port: 9090,
    strictPort: true,
    proxy: {
      '/api': {
        target: process.env.VITE_DEV_PROXY || 'http://localhost:8080',
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
})
