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
})
