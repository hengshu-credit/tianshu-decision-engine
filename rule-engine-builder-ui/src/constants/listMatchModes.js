export const LIST_COMBINATION_MODES = Object.freeze([
  Object.freeze({ value: 'ANY_FIELD_ANY_LIST', label: '任一字段命中任一名单', description: '只要有一个查询表达式命中任意一个所选名单，即返回命中。' }),
  Object.freeze({ value: 'ALL_FIELDS_ANY_LIST', label: '全部字段各自命中任一名单', description: '每个查询表达式都必须至少命中一个所选名单。' }),
  Object.freeze({ value: 'ANY_FIELD_ALL_LISTS', label: '任一字段同时命中全部名单', description: '至少有一个查询表达式必须命中全部所选名单。' }),
  Object.freeze({ value: 'ALL_FIELDS_ALL_LISTS', label: '全部字段同时命中全部名单', description: '每个查询表达式都必须命中全部所选名单，限制最严格。' })
])

export const LIST_MATCH_MODES = Object.freeze([
  Object.freeze({ label: '在名单内（精确匹配）', value: 'IN_LIST' }),
  Object.freeze({ label: '不在名单内（精确排除）', value: 'NOT_IN_LIST' }),
  Object.freeze({ label: '被名单内容包含', value: 'CONTAINED_IN_LIST' }),
  Object.freeze({ label: '未被名单内容包含', value: 'NOT_CONTAINED_IN_LIST' })
])

export const POSITIVE_LIST_MATCH_MODES = Object.freeze(
  LIST_MATCH_MODES.filter(item => item.value === 'IN_LIST' || item.value === 'CONTAINED_IN_LIST')
)

export const LIST_ITEM_TYPES = Object.freeze([
  Object.freeze({ label: '手机号', value: 'MOBILE' }),
  Object.freeze({ label: '身份证', value: 'ID_CARD' }),
  Object.freeze({ label: '地址', value: 'ADDRESS' }),
  Object.freeze({ label: 'IP', value: 'IP' }),
  Object.freeze({ label: '设备号', value: 'DEVICE' }),
  Object.freeze({ label: '姓名', value: 'NAME' }),
  Object.freeze({ label: 'GPS', value: 'GPS' }),
  Object.freeze({ label: '邮箱', value: 'EMAIL' }),
  Object.freeze({ label: '银行卡', value: 'BANK_CARD' }),
  Object.freeze({ label: '其他', value: 'OTHER' })
])

export function listCombinationMode(value) {
  return LIST_COMBINATION_MODES.find(item => item.value === value) || LIST_COMBINATION_MODES[0]
}
