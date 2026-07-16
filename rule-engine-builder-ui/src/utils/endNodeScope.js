export const END_SCOPE_CURRENT_RULE = 'CURRENT_RULE'
export const END_SCOPE_ALL_RULES = 'ALL_RULES'

const APPEARANCES = {
  [END_SCOPE_CURRENT_RULE]: {
    text: '返回',
    name: '跳出当前规则',
    fill: '#FA8C16',
    stroke: '#D46B08',
    tagType: 'warning'
  },
  [END_SCOPE_ALL_RULES]: {
    text: '终止',
    name: '跳出整体规则',
    fill: '#FF4D4F',
    stroke: '#CF1322',
    tagType: 'danger'
  }
}

export function normalizeEndScope(scope) {
  return scope === END_SCOPE_ALL_RULES ? END_SCOPE_ALL_RULES : END_SCOPE_CURRENT_RULE
}

export function getEndNodeAppearance(scope) {
  return APPEARANCES[normalizeEndScope(scope)]
}
