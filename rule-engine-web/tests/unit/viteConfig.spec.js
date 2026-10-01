import { describe, expect, it } from 'vitest'
import createViteConfig from '../../vite.config.mjs'

describe('Vite AMD 兼容配置', () => {
  it('生产构建和开发依赖预构建都禁用 UMD 的 AMD 分支', () => {
    const viteConfig = createViteConfig({ mode: 'production', command: 'build' })
    expect(viteConfig.define).toEqual({ 'define.amd': 'false' })
    expect(
      viteConfig.optimizeDeps?.rolldownOptions?.transform?.define
    ).toEqual({ 'define.amd': 'false' })
  })

  it('开发模式会一起预构建 LogicFlow 依赖并刷新过期缓存', () => {
    const viteConfig = createViteConfig({ mode: 'development', command: 'serve' })

    expect(viteConfig.optimizeDeps?.force).toBe(true)
    expect(viteConfig.optimizeDeps?.include).toEqual([
      '@logicflow/core',
      '@logicflow/extension',
      '@logicflow/layout',
    ])
  })
})
