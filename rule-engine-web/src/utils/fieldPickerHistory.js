import { shallowRef } from 'vue'
import { fieldIdentity } from './fieldPicker'

// Only identities are retained for this console session. Labels, values and credentials are never cached.
export const recentFieldKeys = shallowRef([])
export function rememberField(item) {
  const key = fieldIdentity(item)
  recentFieldKeys.value = [key, ...recentFieldKeys.value.filter(value => value !== key)].slice(0, 30)
}

export function selectedFieldKeys(value, options, selectedOptions = []) {
  const keys = new Set(selectedOptions.map(fieldIdentity))
  const available = new Map(options.map(item => [fieldIdentity(item), item]))
  const visit = node => {
    if (!node || typeof node !== 'object') return
    if (node.kind === 'FUNCTION' && node.functionId != null) keys.add(`FUNCTION:${node.functionId}`)
    if (node.refId != null && node.refType) {
      const key = fieldIdentity({ _varId: node.refId, _refType: node.refType, relativePath: node.relativePath })
      if (available.has(key)) keys.add(key)
    }
    for (const child of Object.values(node)) {
      if (Array.isArray(child)) child.forEach(visit)
      else if (child && typeof child === 'object') visit(child)
    }
  }
  visit(value)
  return [...keys].filter(key => available.has(key))
}
