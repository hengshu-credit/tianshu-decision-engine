const PREFIX = 'qlexpress.pageState.'

function storage() {
  try {
    if (typeof window !== 'undefined' && window.sessionStorage) {
      return window.sessionStorage
    }
  } catch (e) {
    // ignore
  }
  return null
}

export function restorePageState(key, fallback = {}) {
  const store = storage()
  if (!store || !key) return { ...fallback }
  try {
    const raw = store.getItem(PREFIX + key)
    if (!raw) return { ...fallback }
    const parsed = JSON.parse(raw)
    return { ...fallback, ...(parsed || {}) }
  } catch (e) {
    return { ...fallback }
  }
}

export function savePageState(key, state) {
  const store = storage()
  if (!store || !key) return
  try {
    store.setItem(PREFIX + key, JSON.stringify(state || {}))
  } catch (e) {
    // ignore
  }
}

export function clearPageState(key) {
  const store = storage()
  if (!store || !key) return
  try {
    store.removeItem(PREFIX + key)
  } catch (e) {
    // ignore
  }
}
