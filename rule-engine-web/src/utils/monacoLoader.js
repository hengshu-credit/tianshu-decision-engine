/** Wait for the shared loader while allowing an editor to leave before it finishes. */
export function waitForMonaco(signal) {
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
