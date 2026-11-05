import dayjs from 'dayjs'
import { parseMetadataResponse } from './metadataResponse'

export const LEDGER_CATEGORIES = [
  ['PRODUCT', '产品改动'], ['STRATEGY', '策略改动'], ['RELEASE', '规则上线'],
  ['INCIDENT', '生产事故'], ['MEETING', '周会结论'], ['WORK', '日常工作'], ['OTHER', '其他'],
].map(([value, label]) => ({ value, label }))
// 仅用于旧事件的兼容回显；新增事件由用户配置自己的状态枚举。
export const LEDGER_EVENT_STATUSES = [{ value: 'FOLLOWING', label: '跟进中' }, { value: 'ACCEPTANCE', label: '待验收' }, { value: 'DONE', label: '已完成' }]
export const LEDGER_ASSET_STATUSES = [{ value: 'CLEAN', label: '可用' }, { value: 'PENDING', label: '待检测' }, { value: 'QUARANTINED', label: '已隔离' }]
export const ledgerCategoryLabel = value => LEDGER_CATEGORIES.find(item => item.value === value)?.label || value || '其他'
export const ledgerTime = value => value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '—'
export const ledgerSize = value => Number(value) >= 1048576 ? `${(Number(value) / 1048576).toFixed(1)} MB` : `${Math.ceil(Number(value || 0) / 1024)} KB`
export function ledgerDateShortcuts() {
  return [
    { text: '今天', value: () => [dayjs().startOf('day').toDate(), dayjs().endOf('day').toDate()] },
    { text: '昨天', value: () => [dayjs().subtract(1, 'day').startOf('day').toDate(), dayjs().subtract(1, 'day').endOf('day').toDate()] },
    { text: '最近24小时', value: () => [dayjs().subtract(24, 'hour').toDate(), new Date()] },
    { text: '近7天', value: () => [dayjs().subtract(6, 'day').startOf('day').toDate(), dayjs().endOf('day').toDate()] },
    { text: '本周', value: () => [dayjs().subtract((dayjs().day() + 6) % 7, 'day').startOf('day').toDate(), dayjs().endOf('day').toDate()] },
    { text: '本月', value: () => [dayjs().startOf('month').toDate(), dayjs().endOf('day').toDate()] },
  ]
}
export function newLedgerContent(projectId) {
  return { title: '', body: '', summary: '', summaryAuto: true, category: 'WORK', scope: projectId ? 'PROJECT' : '', projectId: projectId || null, occurredAt: dayjs().format('YYYY-MM-DDTHH:mm:ss'), eventIds: [], changeSummary: '' }
}
export function snapshotContent(snapshot) {
  try { return parseMetadataResponse(snapshot || {}) } catch { return {} }
}
