export function formatConstantValue(value, varType) {
  const type = String(varType || 'STRING').toUpperCase()
  if (value === null || value === undefined) return '—'
  const text = String(value)
  if (type === 'STRING' && text === '') return "''"
  return text
}

export function hasConstantValue(value, varType) {
  if (value === null || value === undefined) return false
  const type = String(varType || 'STRING').toUpperCase()
  if (type === 'STRING') return true
  return String(value).trim() !== ''
}
