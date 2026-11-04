import Vditor from 'vditor'
import DOMPurify from 'dompurify'
import 'vditor/dist/index.css'

export const ledgerEditorCdn = new URL(`${import.meta.env.BASE_URL}vendor/vditor`, window.location.href).href.replace(/\/$/, '')
let loading
function script(path, id) {
  if (document.getElementById(id)?.dataset.loaded === 'true') return Promise.resolve()
  return new Promise((resolve, reject) => {
    document.getElementById(id)?.remove()
    const element = document.createElement('script')
    element.id = id
    element.src = `${ledgerEditorCdn}/dist/${path}`
    element.onload = () => { clearTimeout(timeout); element.dataset.loaded = 'true'; resolve() }
    element.onerror = () => { clearTimeout(timeout); element.remove(); reject(new Error('Markdown 编辑资源加载失败，请重试')) }
    const timeout = setTimeout(element.onerror, 20000)
    document.head.appendChild(element)
  })
}
export function loadLedgerMarkdown() {
  if (!loading) loading = Promise.all([
    script('js/icons/ant.js', 'vditorIconScript'),
    script('js/i18n/zh_CN.js', 'vditorI18nScriptzh_CN'),
    script('js/lute/lute.min.js', 'vditorLuteScript'),
  ]).catch(error => { loading = null; throw error })
  return loading
}
export function ledgerAssetId(source) {
  try {
    const url = new URL(source, window.location.origin)
    if (url.origin !== window.location.origin || url.username || url.password || url.search || url.hash) return null
    return url.pathname.match(/^\/api\/rule\/ledger\/assets\/([1-9]\d*)\/content$/)?.[1] || null
  } catch { return null }
}
export function sanitizeLedgerHtml(html) {
  const element = document.createElement('div')
  element.innerHTML = DOMPurify.sanitize(html, { USE_PROFILES: { html: true }, FORBID_TAGS: ['style', 'form', 'iframe', 'object', 'embed', 'button', 'textarea', 'select'], FORBID_ATTR: ['style', 'srcset'] })
  element.querySelectorAll('img').forEach(image => {
    const id = ledgerAssetId(image.getAttribute('src'))
    if (!id) { image.replaceWith(document.createTextNode('［图片需先上传至台账素材库］')); return }
    image.src = `/api/rule/ledger/assets/${id}/content`
    image.loading = 'lazy'
  })
  element.querySelectorAll('input').forEach(input => {
    if (input.closest('.vditor-task')) { input.type = 'checkbox'; input.disabled = true } else input.remove()
  })
  element.querySelectorAll('a').forEach(link => {
    if (!/^(https?:|mailto:|\/|#)/i.test(link.getAttribute('href') || '')) link.removeAttribute('href')
    link.setAttribute('rel', 'noopener noreferrer')
  })
  return element.innerHTML
}
export async function renderLedgerMarkdown(markdown) {
  await loadLedgerMarkdown()
  return sanitizeLedgerHtml(await Vditor.md2html(markdown || '', { cdn: ledgerEditorCdn, markdown: { sanitize: true } }))
}
export { Vditor }
