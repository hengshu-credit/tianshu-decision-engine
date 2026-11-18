export function identifyMonacoFields(editor) {
  const root = editor?.getDomNode?.()
  if (!root) return
  const identify = (field, suffix) => {
    if (field && !field.id && !field.name) field.id = `tianshu-${editor.getId()}-${suffix}`
  }
  identify(root.querySelector('textarea.inputarea'), 'source')

  const widgets = root.querySelector('.overflowingContentWidgets')
  if (!widgets) return
  const identifyRename = () => {
    const field = widgets.querySelector('input.rename-input')
    identify(field, 'rename')
    return Boolean(field)
  }
  if (identifyRename()) return
  const observer = new MutationObserver(() => {
    if (identifyRename()) observer.disconnect()
  })
  observer.observe(widgets, { childList: true })
  editor.onDidDispose(() => observer.disconnect())
}
