// 列表和看板不需要编辑器；首次真正挂载编辑器时才下载 AMD 资源。
function ensureMonacoLoading() {
  if (window.monaco || document.querySelector('script[data-tianshu-monaco-loader]')) return
  const base = new URL(import.meta.env.BASE_URL || './', document.baseURI)
  const vs = new URL('vs/', base)
  const worker = new URL('base/worker/workerMain.js', vs).href
  window.MonacoEnvironment = {
    getWorkerUrl: () => worker,
    getWorker: (_moduleId, label) => new Worker(worker, { name: label || 'monaco' }),
  }
  const script = document.createElement('script')
  script.dataset.tianshuMonacoLoader = ''
  script.src = new URL('loader.js', vs).href
  script.async = true
  script.onerror = () => script.remove()
  script.onload = () => {
    if (typeof window.require !== 'function') { script.remove(); return }
    window.require.config({ paths: { vs: vs.href.replace(/\/$/, '') } })
    window.require(['vs/editor/editor.main'], monaco => { window.monaco = monaco }, () => script.remove())
  }
  document.head.appendChild(script)
}

/** Wait for the shared loader while allowing an editor to leave before it finishes. */
export function waitForMonaco(signal) {
  if (!signal.aborted && typeof window !== 'undefined') ensureMonacoLoading()
  return new Promise(resolve => {
    let timer = null
    let attempts = 0
    const finish = monaco => {
      clearTimeout(timer)
      signal.removeEventListener('abort', cancel)
      resolve(monaco)
    }
    const cancel = () => finish(null)
    const check = () => {
      if (signal.aborted || typeof window === 'undefined') return finish(null)
      if (window.monaco || attempts++ >= 100) return finish(window.monaco || null)
      timer = setTimeout(check, 100)
    }
    signal.addEventListener('abort', cancel, { once: true })
    check()
  })
}
