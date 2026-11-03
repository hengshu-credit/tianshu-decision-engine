import { applyEngineFont, previewEngineFont } from '@/system/engineFont'

describe('引擎字体预览与生效隔离', () => {
  let pending
  beforeEach(() => {
    pending = []
    vi.stubGlobal('FontFace', class {
      constructor(family, source) { this.family = family; this.source = source }
      load() { return new Promise((resolve, reject) => pending.push({ resolve: () => resolve(this), reject, face: this })) }
    })
    Object.defineProperty(document, 'fonts', { configurable: true, value: { add: vi.fn(), delete: vi.fn() } })
    previewEngineFont(null)
    applyEngineFont()
  })
  afterEach(() => { previewEngineFont(null); applyEngineFont(); vi.unstubAllGlobals() })
  const family = () => document.documentElement.style.getPropertyValue('--tianshu-font-family')
  const variation = () => document.documentElement.style.getPropertyValue('--tianshu-font-variation')
  test('默认使用真正的 SemiBold 轴值', () => {
    expect(family()).toContain('AlimamaFangYuanTiVF')
    expect(variation()).toBe('"wght" 600, "BEVL" 80')
  })
  test('远程刷新不会覆盖当前预览，取消后应用最新保存值', async () => {
    const preview = previewEngineFont({ source: 'LOCAL', family: 'Preview' })
    pending.at(-1).resolve(); await preview
    expect(pending.at(-1).face.source).toBe('local("Preview")')
    await applyEngineFont({ source: 'LOCAL', family: 'Saved' })
    const cancel = previewEngineFont(null)
    expect(pending.at(-1).face.source).toBe('local("Saved")')
    pending.at(-1).resolve(); await cancel
    expect(family()).toContain(pending.at(-1).face.family)
  })
  test('迟到加载不能复活已取消的字体，重复刷新不再次下载', async () => {
    const first = previewEngineFont({ source: 'UPLOAD', assetId: 'font-id', face: 1, coordinates: { wght: 600 } })
    const old = pending.at(-1)
    await previewEngineFont(null)
    old.resolve(); await first
    expect(family()).toContain('AlimamaFangYuanTiVF')
    expect(document.fonts.add).not.toHaveBeenCalled()
    const save = applyEngineFont({ source: 'UPLOAD', assetId: 'font-id', face: 1, coordinates: { wght: 600 } })
    pending.at(-1).resolve(); await save
    expect(variation()).toBe('"wght" 600')
    const count = pending.length
    await applyEngineFont({ source: 'UPLOAD', assetId: 'font-id', face: 1, coordinates: { wght: 700 } })
    expect(pending).toHaveLength(count)
    expect(variation()).toBe('"wght" 700')
  })
  test('字体加载失败回退，字体名称不能插入 CSS', async () => {
    const load = applyEngineFont({ source: 'LOCAL', family: 'bad");url(https://example.com)' })
    expect(pending.at(-1).face.source).toBe('local("bad\\");url(https://example.com)")')
    pending.at(-1).reject(new Error('missing')); await load
    expect(family()).toContain('AlimamaFangYuanTiVF')
  })
})
