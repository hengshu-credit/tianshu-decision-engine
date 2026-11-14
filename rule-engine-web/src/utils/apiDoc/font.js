import {
  DEFAULT_ENGINE_FONT_NAME, DEFAULT_ENGINE_FONT_VARIATION, ENGINE_FONT_FAMILY,
  fontCoordinates, localFontSource, normalizeEngineFont, publishedFontUrl,
} from '../../system/engineFont'

function blobDataUrl(blob) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result)
    reader.onerror = () => reject(reader.error)
    reader.readAsDataURL(blob)
  })
}

async function readFont(url, type) {
  const response = await fetch(url)
  if (!response.ok || response.headers.get('Content-Type')?.startsWith('text/')) throw new Error('无法读取引擎字体文件')
  const blob = await response.blob()
  if (!blob.size) throw new Error('引擎字体文件为空')
  return blobDataUrl(blob.slice(0, blob.size, type))
}

async function readDefaultFont() {
  const base = import.meta.env.BASE_URL || './'
  try {
    return await readFont(`${base}fonts/font.woff2`, 'font/woff2')
  } catch {
    return readFont(`${base}fonts/font.ttf`, 'font/ttf')
  }
}

export async function loadApiDocFont(value) {
  const font = normalizeEngineFont(value)
  const [defaultData, uploadedData] = await Promise.all([
    readDefaultFont(),
    font.source === 'UPLOAD' ? readFont(publishedFontUrl(font), 'font/sfnt').catch(() => null) : null,
  ])
  return { font, defaultData, uploadedData }
}

function fontFace(family, source, variation) {
  return `@font-face{font-family:"${family}";src:${source};font-style:normal;font-weight:100 900;font-display:swap;font-variation-settings:${variation}}`
}

export function renderApiDocFont({ font, defaultData, uploadedData } = {}) {
  const rules = []
  if (defaultData) {
    rules.push('/* 阿里妈妈方圆体 VF 版权所有 © 淘宝（中国）软件有限公司（阿里妈妈） */')
    rules.push(fontFace(DEFAULT_ENGINE_FONT_NAME, `url("${defaultData}")`, DEFAULT_ENGINE_FONT_VARIATION))
  }
  const source = uploadedData ? `url("${uploadedData}")` : font?.source === 'LOCAL' && font.family ? localFontSource(font.family) : ''
  const variation = source ? fontCoordinates(uploadedData ? font.coordinates : null) : DEFAULT_ENGINE_FONT_VARIATION
  if (source) rules.push(fontFace('TianshuDocumentFont', source, variation))
  rules.push(`:root{--tianshu-font-family:${source ? '"TianshuDocumentFont", ' : ''}${ENGINE_FONT_FAMILY};--tianshu-font-variation:${variation}}`)
  return `<style id="engine-document-font">${rules.join('\n')}</style>`
}
