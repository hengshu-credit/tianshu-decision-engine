import dayjs from 'dayjs'

export const DEFAULT_WATERMARK_SETTINGS = Object.freeze({
  watermarkEnabled: false,
  watermarkContent: '{系统名称} {用户名} {登录时间}',
  watermarkTransparency: 88,
  watermarkLayout: 'DIAGONAL',
  watermarkDensity: 'NORMAL',
})

export const WATERMARK_LAYOUTS = Object.freeze([
  { value: 'DIAGONAL', label: '斜向平铺', rotate: -22 },
  { value: 'HORIZONTAL', label: '水平平铺', rotate: 0 },
  { value: 'VERTICAL', label: '竖向平铺', rotate: -90 },
])
export const WATERMARK_DENSITIES = Object.freeze([
  { value: 'SPARSE', label: '稀疏', gap: 180 },
  { value: 'NORMAL', label: '标准', gap: 100 },
  { value: 'DENSE', label: '密集', gap: 40 },
])

export function resolveWatermarkSettings(value = {}) {
  return Object.fromEntries(Object.entries(DEFAULT_WATERMARK_SETTINGS).map(([key, fallback]) => [key, value[key] ?? fallback]))
}

// 预览和全局覆盖层共用同一份文本替换与绘制参数，动态值只替换一次，始终按纯文本绘制。
export function createWatermarkOptions(config, user, dark = false) {
  const settings = resolveWatermarkSettings(config)
  const time = user?.loginTime ? dayjs(user.loginTime) : null
  const values = { 系统名称: config.systemName || '', 用户名: user?.username || '未登录', 登录时间: time?.isValid() ? time.format('YYYY:MM:DD HH:mm:ss.SSS') : '未知' }
  const content = settings.watermarkContent.split(/\r?\n/).map(line => line.replace(/\{(系统名称|用户名|登录时间)\}/g, (_, key) => values[key]))
  const layout = WATERMARK_LAYOUTS.find(item => item.value === settings.watermarkLayout) || WATERMARK_LAYOUTS[0]
  const density = WATERMARK_DENSITIES.find(item => item.value === settings.watermarkDensity) || WATERMARK_DENSITIES[1]
  const alpha = (100 - settings.watermarkTransparency) / 100
  return { content, rotate: layout.rotate, gap: [density.gap, density.gap], font: { color: `rgba(${dark ? '255, 255, 255' : '0, 0, 0'}, ${alpha})`, fontSize: 13 } }
}
