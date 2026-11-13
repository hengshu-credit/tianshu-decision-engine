import { computed, reactive } from 'vue'
import { getCurrentUser } from '@/security/permissionState'

const prefix = 'tianshu:page-guidance:'
const hidden = reactive(new Set())
const accountPrefix = computed(() => {
  const user = getCurrentUser()
  return prefix + (user?.userId != null ? 'id-' + user.userId : user?.username ? 'name-' + encodeURIComponent(user.username) : 'guest') + ':'
})

function loadPreferences() {
  try {
    const storage = window.localStorage
    const keys = Object.keys(storage).filter(key => key.startsWith(prefix))
    hidden.clear()
    keys.forEach(key => hidden.add(key))
  } catch { /* 无法使用本地存储时保留内存中的选择。 */ }
}

if (typeof window !== 'undefined') {
  loadPreferences()
  window.addEventListener('storage', event => {
    if (event.key === null || event.key.startsWith(prefix)) loadPreferences()
  })
}

export function isGuidanceHidden(id) {
  return hidden.has(accountPrefix.value + id)
}

export function hideGuidance(id) {
  const key = accountPrefix.value + id
  hidden.add(key)
  try { window.localStorage.setItem(key, '1') } catch { /* 当前会话仍然生效。 */ }
}

export const hasHiddenGuidance = computed(() => Array.from(hidden).some(key => key.startsWith(accountPrefix.value)))

export function restoreGuidance() {
  for (const key of hidden) {
    if (key.startsWith(accountPrefix.value)) {
      hidden.delete(key)
      try { window.localStorage.removeItem(key) } catch { /* 当前会话仍然生效。 */ }
    }
  }
}
