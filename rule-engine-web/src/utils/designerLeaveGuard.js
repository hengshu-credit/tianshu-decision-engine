const guards = new Map()

function routePath(value) {
  return String(value || '').split('?')[0]
}

export function registerDesignerLeaveGuard(path, confirmLeave) {
  const key = routePath(path)
  if (!key || typeof confirmLeave !== 'function') return () => {}
  guards.set(key, confirmLeave)
  return () => {
    if (guards.get(key) === confirmLeave) guards.delete(key)
  }
}

export async function confirmDesignerPathsCanClose(paths) {
  const checked = new Set()
  for (const path of Array.isArray(paths) ? paths : []) {
    const key = routePath(path)
    if (checked.has(key)) continue
    checked.add(key)
    const confirmLeave = guards.get(key)
    if (confirmLeave && !(await confirmLeave())) return false
  }
  return true
}

export function clearDesignerLeaveGuards() {
  guards.clear()
}
