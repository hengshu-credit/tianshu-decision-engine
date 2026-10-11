import { hasPermission } from '@/security/permissionState'
import { shallowRef, watchEffect } from 'vue'

const states = new WeakMap()
const displays = new WeakMap()

export function applyPermissionVisibility(element, requirement) {
  const codes = Array.isArray(requirement) ? requirement : [requirement]
  const visible = codes.filter(Boolean).every(code => hasPermission(code))
  element.hidden = !visible
  if (visible) {
    element.removeAttribute('aria-hidden')
    const original = displays.get(element)
    if (original) {
      element.style.setProperty('display', original.value, original.priority)
      displays.delete(element)
    }
  } else {
    element.setAttribute('aria-hidden', 'true')
    if (!displays.has(element)) displays.set(element, { value: element.style.getPropertyValue('display'), priority: element.style.getPropertyPriority('display') })
    element.style.setProperty('display', 'none', 'important')
  }
}

export default {
  mounted(element, binding) {
    const requirement = shallowRef(binding.value)
    const stop = watchEffect(() => applyPermissionVisibility(element, requirement.value))
    states.set(element, { requirement, stop })
  },
  updated(element, binding) {
    const state = states.get(element)
    if (state) state.requirement.value = binding.value
    applyPermissionVisibility(element, binding.value)
  },
  unmounted(element) {
    states.get(element)?.stop()
    states.delete(element)
    displays.delete(element)
  }
}
