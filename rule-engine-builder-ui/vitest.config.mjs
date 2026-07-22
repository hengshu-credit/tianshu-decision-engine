import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

const resolveFromRoot = path => fileURLToPath(new URL(path, import.meta.url))

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: [
      { find: '@test-utils', replacement: resolveFromRoot('./tests/vueTestUtils.js') },
      { find: /^monaco-editor$/, replacement: resolveFromRoot('./tests/__mocks__/monaco-editor.js') },
      { find: /^@logicflow\/core$/, replacement: resolveFromRoot('./tests/__mocks__/empty.js') },
      { find: /^@logicflow\/extension$/, replacement: resolveFromRoot('./tests/__mocks__/empty.js') },
      { find: /^@logicflow\/layout$/, replacement: resolveFromRoot('./tests/__mocks__/empty.js') },
      { find: '@', replacement: resolveFromRoot('./src') }
    ],
    extensions: ['.mjs', '.js', '.mts', '.ts', '.jsx', '.tsx', '.json', '.vue']
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: [
      resolveFromRoot('./tests/setup.js'),
      resolveFromRoot('./tests/setupAfterEnv.js')
    ],
    include: ['tests/unit/**/*.spec.js'],
    css: false,
    coverage: {
      provider: 'v8',
      include: [
        'src/utils/**/*.js',
        'src/constants/**/*.js'
      ],
      exclude: ['src/**/*.spec.js']
    }
  }
})
