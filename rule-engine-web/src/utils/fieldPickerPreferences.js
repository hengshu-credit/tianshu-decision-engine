import { shallowRef } from 'vue'

const PAGE_SIZE_KEY = 'tianshu-field-picker-page-size'
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

export function setPreferredFieldPageSize(value, storage = browserStorage()) {
  const size = normalizeFieldPageSize(value)
  preferredFieldPageSize.value = size
  try { storage?.setItem(PAGE_SIZE_KEY, String(size)) } catch { /* 禁用本地存储时仍在本次会话中复用设置。 */ }
  return size
}
