import { hasPermission } from '@/security/permissionState'

export function applyPermissionVisibility(element, requirement) {
  const codes = Array.isArray(requirement) ? requirement : [requirement]
  const visible = codes.filter(Boolean).every(code => hasPermission(code))
  element.hidden = !visible
  if (visible) {
    element.removeAttribute('aria-hidden')
  } else {
    element.setAttribute('aria-hidden', 'true')
  }
}

export default {
  mounted(element, binding) {
    applyPermissionVisibility(element, binding.value)
  },
  updated(element, binding) {
    applyPermissionVisibility(element, binding.value)
  }
}
