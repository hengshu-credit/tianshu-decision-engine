export const REFERENCE_PICKER_CATEGORIES = Object.freeze([
  Object.freeze({ key: 'standalone', label: '普通变量' }),
  Object.freeze({ key: 'constant', label: '常量' }),
  Object.freeze({ key: 'object', label: '数据对象' }),
  Object.freeze({ key: 'model', label: '模型' })
])

export function pickerReferenceCategory(item) {
  return (item && item._ref && item._ref.category) || 'standalone'
}

export function pickerCategoryLabel(key) {
  const found = REFERENCE_PICKER_CATEGORIES.find(item => item.key === key)
  return found ? found.label : key
}
