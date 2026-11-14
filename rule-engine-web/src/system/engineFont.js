export const DEFAULT_ENGINE_FONT = Object.freeze({ source: 'DEFAULT', family: '', assetId: '', face: 0, instance: 0 })
export const DEFAULT_ENGINE_FONT_NAME = 'AlimamaFangYuanTiVF'
export const ENGINE_FONT_FAMILY = `"${DEFAULT_ENGINE_FONT_NAME}", "PingFang SC", "Microsoft YaHei", sans-serif`
export const DEFAULT_ENGINE_FONT_VARIATION = '"wght" 600, "BEVL" 80'
const quote = value => JSON.stringify(String(value)).replace(/</g, '\\3c ')
let saved = DEFAULT_ENGINE_FONT
let preview = null
let revision = 0
let activeFace = null
let activeKey = ''

export function publishedFontUrl({ assetId, face }) {
  return `/api/auth/console/brand/fonts/${encodeURIComponent(assetId)}/${face}`
}
export function localFontSource(family) { return `local(${quote(family)})` }
export function normalizeEngineFont(value) { return { ...DEFAULT_ENGINE_FONT, ...value } }
export function fontCoordinates(value) {
  return Object.entries(value || {}).filter(([tag, number]) => /^[A-Za-z0-9 ]{4}$/.test(tag) && Number.isFinite(number)).map(([tag, number]) => `${quote(tag)} ${number}`).join(', ') || 'normal'
}
function styles(family, coordinates) {
  const root = document.documentElement.style
  root.setProperty('--tianshu-font-family', family)
  root.setProperty('--tianshu-font-variation', coordinates)
}
async function render() {
  const current = ++revision
  const font = normalizeEngineFont(preview || saved)
  if (typeof document === 'undefined') return
  if (font.source === 'DEFAULT' || font.source === 'LOCAL' && !font.family) {
    styles(ENGINE_FONT_FAMILY, DEFAULT_ENGINE_FONT_VARIATION)
    if (activeFace) document.fonts?.delete(activeFace)
    activeFace = null; activeKey = ''
    return true
  }
  const key = font.source === 'LOCAL' ? `local:${font.family}` : `${font.assetId}:${font.face}:${font.previewUrl || ''}`
  const coordinates = font.source === 'LOCAL' ? 'normal' : fontCoordinates(font.coordinates)
  if (key === activeKey) { styles(`"${activeFace.family}", ${ENGINE_FONT_FAMILY}`, coordinates); return true }
  const family = `TianshuFont${current}`
  const source = font.source === 'LOCAL' ? localFontSource(font.family) : `url(${quote(font.previewUrl || publishedFontUrl(font))})`
  try {
    const face = new FontFace(family, source, { weight: '100 900' })
    await face.load()
    if (current !== revision) return
    document.fonts.add(face)
    styles(`"${family}", ${ENGINE_FONT_FAMILY}`, coordinates)
    if (activeFace) document.fonts.delete(activeFace)
    activeFace = face; activeKey = key
    return true
  } catch {
    if (current !== revision) return
    // A local font may be absent on another computer; uploaded fonts can also fail to load offline.
    styles(ENGINE_FONT_FAMILY, DEFAULT_ENGINE_FONT_VARIATION)
    if (activeFace) document.fonts.delete(activeFace)
    activeFace = null; activeKey = ''
    return false
  }
}
export function applyEngineFont(value) { saved = normalizeEngineFont(value); return render() }
// Preview is kept only in this tab's memory, never in the shared brand or browser storage.
export function previewEngineFont(value) { preview = value; return render() }
