import { pickerReferenceCategory } from '@/utils/pickerCategories'

function sourceMeta(item, category) {
  const ref = (item && item._ref) || {}
  if (category === 'model') {
    return {
      code: ref.modelCode || item.modelCode || firstPathSegment(item),
      label: ref.modelLabel || ref.modelName || item.modelLabel || item.modelName || ''
    }
  }
  return {
    code: ref.objectCode || ref.objectScriptName || item.objectCode || firstPathSegment(item),
    label: ref.objectLabel || item.objectLabel || ''
  }
}

function firstPathSegment(item) {
  return String((item && (item.varCode || item.refCode || item.code)) || '').split('.')[0]
}

function removeExactPrefix(value, prefix, separator) {
  const text = String(value || '')
  const fullPrefix = prefix ? String(prefix) + separator : ''
  return fullPrefix && text.indexOf(fullPrefix) === 0 ? text.substring(fullPrefix.length) : text
}

export function referenceGroupKey(group) {
  if (!group) return ''
  if (group.groupKey) return group.groupKey
  const category = group.groupCategory || pickerReferenceCategory(group)
  const meta = sourceMeta(group, category)
  return `${category}:${meta.code}`
}

export function referenceGroupCode(item, category) {
  const groupCategory = category || (item && item.groupCategory) || pickerReferenceCategory(item)
  return (item && item.groupCode) || sourceMeta(item, groupCategory).code
}

export function referenceGroupLabel(item, category) {
  const groupCategory = category || (item && item.groupCategory) || pickerReferenceCategory(item)
  const meta = sourceMeta(item, groupCategory)
  return (item && item.groupLabel) || meta.label || meta.code
}

export function referenceChildRelativePath(item, category) {
  const groupCategory = category || pickerReferenceCategory(item)
  const code = (item && (item.varCode || item.refCode || item.code)) || ''
  return removeExactPrefix(code, sourceMeta(item, groupCategory).code, '.')
}

export function referenceChildDisplayName(item, category) {
  const groupCategory = category || pickerReferenceCategory(item)
  const label = (item && (item.varLabelText || item.varLabel || item.label)) || ''
  return removeExactPrefix(label, sourceMeta(item, groupCategory).label, '/') || referenceChildRelativePath(item, groupCategory)
}

export function groupReferenceOptions(options = [], category) {
  if (!['object', 'model'].includes(category)) return []
  const groups = {}
  ;(options || []).forEach(item => {
    if (pickerReferenceCategory(item) !== category) return
    const meta = sourceMeta(item, category)
    if (!groups[meta.code]) groups[meta.code] = { meta, children: [] }
    groups[meta.code].children.push(item)
  })

  return Object.keys(groups).sort((left, right) => left.localeCompare(right)).map(code => {
    const group = groups[code]
    const children = group.children.slice().sort((left, right) => String(left.varCode || '').localeCompare(String(right.varCode || '')))
    return {
      ...children[0],
      _referenceGroup: true,
      groupCategory: category,
      groupKey: `${category}:${code}`,
      groupCode: code,
      groupLabel: group.meta.label || code,
      children
    }
  })
}

function defaultSearchTexts(item, category) {
  return [
    item && item.varCode,
    item && item.varLabel,
    item && item.varLabelText,
    referenceChildRelativePath(item, category),
    referenceChildDisplayName(item, category)
  ].filter(Boolean).map(value => String(value).toLowerCase())
}

export function filterReferenceGroups(groups = [], keyword, searchTextProvider) {
  const key = String(keyword || '').trim().toLowerCase()
  if (!key) return groups
  return (groups || []).reduce((result, group) => {
    const category = group.groupCategory || pickerReferenceCategory(group)
    const groupTexts = [group.groupCode, group.groupLabel].filter(Boolean).map(value => String(value).toLowerCase())
    const groupMatches = groupTexts.some(value => value.includes(key))
    const children = groupMatches
      ? group.children
      : (group.children || []).filter(child => {
        const texts = searchTextProvider ? searchTextProvider(child) : defaultSearchTexts(child, category)
        return (texts || []).some(value => String(value).toLowerCase().includes(key))
      })
    if (children.length) result.push({ ...group, children })
    return result
  }, [])
}
