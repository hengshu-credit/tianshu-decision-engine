const guards = new Map()

function routePath(value) {
  return String(value || '').split('?')[0]
}

export function registerDesignerLeaveGuard(path, confirmLeave, onAborted) {
  const key = routePath(path)
  if (!key || typeof confirmLeave !== 'function') return () => {}
  const registration = { confirmLeave, onAborted }
  guards.set(key, registration)
  return () => {
    if (guards.get(key) === registration) guards.delete(key)
  }
}

export async function confirmDesignerPathsCanClose(paths) {
  const checked = new Set()
  const approved = []
  for (const path of Array.isArray(paths) ? paths : []) {
    const key = routePath(path)
    if (checked.has(key)) continue
    checked.add(key)
    const guard = guards.get(key)
    if (!guard) continue
    if (!(await guard.confirmLeave())) {
      approved.forEach(item => item.onAborted?.())
      return false
    }
    approved.push(guard)
  }
  return true
}

export function clearDesignerLeaveGuards() {
  guards.clear()
}
