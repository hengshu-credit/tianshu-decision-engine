import { varTypeLabel } from '@/constants/varTypes'
import { pickerCategoryLabel } from './pickerCategories'

function displayValue(value) {
  return typeof value === 'object' && value !== null ? JSON.stringify(value) : String(value)
}

export function fieldPickerDetails(node, versionLabel = '') {
  if (!node) return ''
  const item = node.item || {}
  const ref = item._ref || {}
  const raw = item.function || item.resource || item.varObj || ref.varObj || {}
  const parts = [node.name, node.code, `${varTypeLabel(node.type)} (${node.type})`]
  const category = ({ function: '函数 / 方法', operator: '操作符', rule: '规则', list: '名单', database: '数据库' })[node.category] || pickerCategoryLabel(node.category)
  if (category) parts.push(`分类：${category}`)
  const owner = ref.objectLabel || ref.modelLabel || ref.apiLabel
  if (owner && owner !== node.name) parts.push(`归属：${owner}`)
  if (raw.scope) parts.push(raw.scope === 'GLOBAL' ? '全局' : '项目级')
  if (versionLabel && versionLabel !== '—') parts.push(`版本：${versionLabel}`)
  const value = item.constantValue ?? raw.defaultValue
  if (value != null && value !== '') parts.push(`值：${displayValue(value)}`)
  if (item._function || node.category === 'function') {
    let params = raw.params || raw.parameters
    if (!params && raw.paramsJson) {
      try { params = JSON.parse(raw.paramsJson) } catch { params = null }
    }
    if (Array.isArray(params)) parts.push(`参数：${params.map(param => `${param.name || param.label || ''}:${param.type || param.valueType || 'ANY'}`).join('，') || '无'}`)
    if (raw.returnType) parts.push(`返回：${raw.returnType}`)
  }
  const description = item.description || raw.description || raw.remark || ref.description
  if (description) parts.push(`说明：${description}`)
  if (node.children.length) parts.push(`子项：${node.children.length}`)
  return parts.filter(Boolean).join(' · ')
}
