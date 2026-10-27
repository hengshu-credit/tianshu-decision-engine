import { shallowRef } from 'vue'

const PAGE_SIZE_KEY = 'tianshu-field-picker-page-size'
const CATEGORY_WIDTH_KEY = 'tianshu-field-picker-category-width'
const COLUMN_WIDTHS_KEY = 'tianshu-field-picker-column-widths'
export const FIELD_PICKER_COLUMNS = Object.freeze([
  { key: 'type', label: '数据类型', width: 64, min: 64 },
  { key: 'name', label: '名称', width: 104, min: 80 },
  { key: 'code', label: '编码', width: 136, min: 96 },
  { key: 'version', label: '版本', width: 88, min: 80 },
])

export function normalizeFieldColumnWidths(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return null
  return Object.fromEntries(FIELD_PICKER_COLUMNS.map(column => {
    const width = Number(value[column.key])
    return [column.key, Number.isFinite(width) ? Math.max(column.min, Math.round(width * 1000) / 1000) : column.width]
  }))
}

// 偏好保留各列比例；容器变化后重新分配可用宽度，达到最小宽度的列不再收缩。
export function fitFieldColumnWidths(value, availableWidth) {
  const widths = normalizeFieldColumnWidths(value) || Object.fromEntries(FIELD_PICKER_COLUMNS.map(column => [column.key, column.width]))
  const minimum = FIELD_PICKER_COLUMNS.reduce((sum, column) => sum + column.min, 0)
  let remaining = Math.max(minimum, availableWidth || Object.values(widths).reduce((sum, width) => sum + width, 0))
  let active = [...FIELD_PICKER_COLUMNS]
  const result = {}
  while (active.length) {
    const weight = active.reduce((sum, column) => sum + widths[column.key], 0)
    const constrained = active.filter(column => remaining * widths[column.key] / weight < column.min)
    if (!constrained.length) {
      active.forEach(column => { result[column.key] = remaining * widths[column.key] / weight })
      break
    }
    constrained.forEach(column => { result[column.key] = column.min; remaining -= column.min })
    active = active.filter(column => !constrained.includes(column))
  }
  return result
}

export function resizeFieldColumnWidths(widths, key, delta) {
  const column = FIELD_PICKER_COLUMNS.find(item => item.key === key)
  if (!column) return widths
  const others = FIELD_PICKER_COLUMNS.filter(item => item.key !== key)
  const capacity = others.reduce((sum, item) => sum + widths[item.key] - item.min, 0)
  const change = Math.max(column.min - widths[key], Math.min(delta, capacity))
  if (!change) return widths
  const weight = change > 0 ? capacity : others.reduce((sum, item) => sum + widths[item.key], 0)
  const result = { ...widths, [key]: widths[key] + change }
  others.forEach(item => { result[item.key] -= change * (change > 0 ? widths[item.key] - item.min : widths[item.key]) / weight })
  return result
}

function readColumnWidths() {
  try { return normalizeFieldColumnWidths(JSON.parse(browserStorage()?.getItem(COLUMN_WIDTHS_KEY) || 'null')) } catch { return null }
}

export const preferredFieldColumnWidths = shallowRef(readColumnWidths())

export function setPreferredFieldColumnWidths(value, storage = browserStorage()) {
  const widths = normalizeFieldColumnWidths(value)
  preferredFieldColumnWidths.value = widths
  try { if (widths) storage?.setItem(COLUMN_WIDTHS_KEY, JSON.stringify(widths)); else storage?.removeItem(COLUMN_WIDTHS_KEY) } catch { /* 禁用存储时仍保留会话内列宽。 */ }
  return widths
}
export function normalizeFieldPageSize(value) {
  const size = Number(value)
  return Number.isFinite(size) && size >= 1 ? Math.trunc(size) : 50
}

function browserStorage() {
  try { return typeof window === 'undefined' ? null : window.localStorage } catch { return null }
}

export function readFieldPageSize(storage = browserStorage()) {
  try { return normalizeFieldPageSize(storage?.getItem(PAGE_SIZE_KEY)) } catch { return 50 }
}

export const preferredFieldPageSize = shallowRef(readFieldPageSize())

export function normalizeFieldCategoryWidth(value) {
  const width = Number(value)
  return Number.isFinite(width) && width > 0 ? Math.max(96, Math.min(320, Math.round(width))) : 144
}

function readCategoryWidth() {
  try { return normalizeFieldCategoryWidth(browserStorage()?.getItem(CATEGORY_WIDTH_KEY)) } catch { return 144 }
}

export const preferredFieldCategoryWidth = shallowRef(readCategoryWidth())

export function setPreferredFieldCategoryWidth(value, storage = browserStorage()) {
  const width = normalizeFieldCategoryWidth(value)
  preferredFieldCategoryWidth.value = width
  try { storage?.setItem(CATEGORY_WIDTH_KEY, String(width)) } catch { /* 存储不可用时仍可调整当前会话。 */ }
  return width
}

export function setPreferredFieldPageSize(value, storage = browserStorage()) {
  const size = normalizeFieldPageSize(value)
  preferredFieldPageSize.value = size
  try { storage?.setItem(PAGE_SIZE_KEY, String(size)) } catch { /* 禁用本地存储时仍在本次会话中复用设置。 */ }
  return size
}
