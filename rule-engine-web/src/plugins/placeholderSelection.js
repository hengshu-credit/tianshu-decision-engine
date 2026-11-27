// 原生占位文字不属于输入值。只在用户显式选中提示时处理复制，输入和提交仍由原控件负责。
export function enablePlaceholderSelection(doc = document) {
  const controller = new AbortController()
  const listen = (name, handler) => doc.addEventListener(name, handler, { capture: true, signal: controller.signal })
  let selected = null
  let drag = null

  function hintAt(target) {
    if (!(target instanceof Element)) return null
    if (target.matches('input[placeholder]:placeholder-shown, textarea[placeholder]:placeholder-shown') &&
        !target.value && target.placeholder && !target.disabled) {
      return { field: target, label: target, text: target.placeholder }
    }
    const container = target.closest('.el-select__wrapper')
    const field = container?.querySelector('input[role="combobox"]')
    const label = container?.querySelector('.el-select__placeholder.is-transparent')
    if (field && !field.disabled && !field.value && label?.textContent) {
      return { field, label, text: label.textContent }
    }
    return null
  }

  function clear() {
    selected?.label.removeAttribute('data-selected-hint')
    selected = null
    drag = null
  }
  function select(hint) {
    clear()
    if (!hint) return
    selected = hint
    hint.label.setAttribute('data-selected-hint', '')
  }

  listen('pointerdown', event => {
    if (event.button !== 0) return
    clear()
    const hint = hintAt(event.target)
    if (hint && event.isPrimary && event.pointerType !== 'touch') {
      drag = { hint, x: event.clientX, id: event.pointerId }
    }
  })
  listen('pointermove', event => {
    if (event.pointerId === drag?.id && event.buttons === 1 && Math.abs(event.clientX - drag.x) >= 4) {
      select(hintAt(drag.hint.field))
    }
  })
  listen('pointerup', () => { drag = null })
  listen('pointercancel', () => { drag = null })
  listen('dblclick', event => {
    const hint = hintAt(event.target)
    if (hint) select(hint)
  })
  listen('keydown', event => {
    if (event.isComposing) return
    const shortcut = (event.ctrlKey || event.metaKey) && !event.altKey
    if (shortcut && event.key.toLowerCase() === 'a') {
      const hint = hintAt(event.target)
      if (hint) { event.preventDefault(); select(hint); return }
    }
    if (!(shortcut && event.key.toLowerCase() === 'c') && !['Control','Meta'].includes(event.key)) clear()
  })
  listen('copy', event => {
    if (!selected || !event.clipboardData) return
    const current = hintAt(doc.activeElement)
    if (current?.field !== selected.field || current.text !== selected.text) { clear(); return }
    event.clipboardData.setData('text/plain', current.text)
    event.preventDefault()
  })
  listen('input', clear)
  listen('compositionstart', clear)
  listen('focusout', event => {
    if (selected?.field === event.target || drag?.hint.field === event.target) clear()
  })
  return () => { clear(); controller.abort() }
}
