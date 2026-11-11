import { describe, expect, it } from 'vitest'
import createViteConfig from '../../vite.config.mjs'

describe('Vite AMD 兼容配置', () => {
  it('生产构建和开发依赖预构建都禁用 UMD 的 AMD 分支', () => {
    const viteConfig = createViteConfig({ mode: 'production', command: 'build' })
    const { 'import.meta.env.TIANSHU_MAP_ASSETS': assets, ...defines } = viteConfig.define
    expect(defines).toEqual({ 'define.amd': 'false' })
    const urls = JSON.parse(assets)
    expect(urls['/maps/dashboard-world.geojson']).toMatch(/^\/assets\/dashboard-world-[a-f0-9]{16}\.json$/)
    expect(urls['/maps/dashboard-china-ADM1.geojson']).toMatch(/^\/assets\/dashboard-china-ADM1-[a-f0-9]{16}\.json$/)
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
      '@logicflow/layout/es/dagre/index.js',
    ])
  })
})
