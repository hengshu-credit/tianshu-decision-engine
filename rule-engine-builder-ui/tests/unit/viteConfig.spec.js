import { describe, expect, it } from 'vitest'
import viteConfig from '../../vite.config.mjs'

describe('Vite AMD 兼容配置', () => {
  it('生产构建和开发依赖预构建都禁用 UMD 的 AMD 分支', () => {
    expect(viteConfig.define).toEqual({ 'define.amd': 'false' })
    expect(
      viteConfig.optimizeDeps?.rolldownOptions?.transform?.define
    ).toEqual({ 'define.amd': 'false' })
  })
})
