import { ACCENT_PRESETS, DEFAULT_THEME_CONFIG } from '@/theme/themeConfig'
import { applyTheme } from '@/theme/themeRuntime'

describe('主题状态色的业务含义', () => {
  test.each(['LIGHT', 'DARK'])('%s 的校验、通知、提示及按钮共享语义状态，品牌色不能改变结果含义', colorScheme => {
    const root = document.documentElement
    for (const preset of ACCENT_PRESETS) {
      applyTheme({ ...DEFAULT_THEME_CONFIG, colorScheme, accentPreset: preset.id }, root)
      for (const type of ['success', 'warning', 'danger']) {
        const value = name => root.style.getPropertyValue(name)
        expect(value(`--el-color-${type}`), `${preset.id} / ${type}`)
          .toBe(value(`--tianshu-status-${type}-solid`))
        for (const field of ['bg', 'border', 'text']) {
          expect(value(`--tianshu-${type}-${field}`), `${preset.id} / ${type} / ${field}`)
            .toBe(value(`--tianshu-status-${type}-${field}`))
        }
      }
      expect(root.style.getPropertyValue('--el-color-error'))
        .toBe(root.style.getPropertyValue('--el-color-danger'))
    }
  })

  test.each(['LIGHT', 'DARK'])('%s 下切换任意主题仍保持成功绿、警告橙、危险红', colorScheme => {
    for (const preset of ACCENT_PRESETS) {
      const root = document.documentElement
      root.removeAttribute('style')
      applyTheme({ ...DEFAULT_THEME_CONFIG, colorScheme, accentPreset: preset.id }, root)
      const channels = type => root.style.getPropertyValue(`--tianshu-status-${type}-solid`)
        .replace('#', '').match(/.{2}/g)
        .map(value => Number.parseInt(value, 16))
      const [sr, sg, sb] = channels('success')
      const [wr, wg, wb] = channels('warning')
      const [dr, dg, db] = channels('danger')
      expect(sg, preset.id).toBeGreaterThan(sr * 1.5)
      expect(sg, preset.id).toBeGreaterThan(sb * 1.3)
      expect(wr, preset.id).toBeGreaterThan(wg)
      expect(wg, preset.id).toBeGreaterThan(wb * 2)
      expect(dr, preset.id).toBeGreaterThan(dg * 2)
      expect(dr, preset.id).toBeGreaterThan(db * 2)
    }
  })
})
