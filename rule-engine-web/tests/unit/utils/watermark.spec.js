import { createWatermarkOptions, DEFAULT_WATERMARK_SETTINGS, resolveWatermarkSettings } from '@/system/watermark'

describe('水印绘制参数', () => {
  const user = { username: 'admin', loginTime: '2026-10-10T12:00:00.121' }
  test('旧配置使用原有默认内容和间距，登录时间保留毫秒', () => {
    expect(resolveWatermarkSettings()).toEqual(DEFAULT_WATERMARK_SETTINGS)
    expect(createWatermarkOptions({ systemName: '天枢' }, user)).toEqual({ content: ['天枢 admin 2026:10:10 12:00:00.121'], rotate: -22, gap: [100, 100], font: { color: 'rgba(0, 0, 0, 0.12)', fontSize: 13 } })
  })
  test('保留自定义文字、字段顺序与换行，不递归替换动态值或解释 HTML', () => {
    const options = createWatermarkOptions({ systemName: '引擎', watermarkContent: '仅供内部\r\n{用户名} / {系统名称}\n{登录时间}\n<img onerror=alert(1)>' }, { ...user, username: '$&{系统名称}' })
    expect(options.content).toEqual(['仅供内部', '$&{系统名称} / 引擎', '2026:10:10 12:00:00.121', '<img onerror=alert(1)>'])
  })
  test.each([[0, '1'], [65, '0.35'], [100, '0']])('透明度 %s%% 正确转换为绘制 alpha', (transparency, alpha) => {
    const config = { watermarkTransparency: transparency }
    expect(createWatermarkOptions(config, user).font.color).toBe(`rgba(0, 0, 0, ${alpha})`)
    expect(createWatermarkOptions(config, user, true).font.color).toBe(`rgba(255, 255, 255, ${alpha})`)
  })
  test.each([['DIAGONAL', -22], ['HORIZONTAL', 0], ['VERTICAL', -90]])('排列 %s 对应正确角度', (layout, rotate) => {
    expect(createWatermarkOptions({ watermarkLayout: layout }, user).rotate).toBe(rotate)
  })
  test.each([['SPARSE', 180], ['NORMAL', 100], ['DENSE', 40]])('密度 %s 对应正确平铺间距', (density, gap) => {
    expect(createWatermarkOptions({ watermarkDensity: density }, user).gap).toEqual([gap, gap])
  })
})
