const SUPPORTED_MODEL_TYPES = ['TABLE', 'TREE', 'FLOW', 'CROSS', 'SCORE', 'CROSS_ADV', 'SCORE_ADV', 'SCRIPT', 'RULE_SET']

export const RULE_MODEL_LABELS = {
  TABLE: '决策表',
  TREE: '决策树',
  FLOW: '决策流',
  CROSS: '交叉表',
  SCORE: '评分卡',
  CROSS_ADV: '复杂交叉表',
  SCORE_ADV: '复杂评分卡',
  SCRIPT: 'QL 脚本',
  RULE_SET: '规则集'
}

function parseFieldList(value) {
  if (Array.isArray(value)) return value
  if (typeof value !== 'string' || !value.trim()) return []
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? parsed : []
  } catch (e) {
    return []
  }
}

export function normalizeRuleOption(rule) {
  if (!rule) return null
  const inputFields = parseFieldList(rule.inputFieldsJson || rule.inputFields)
  const outputFields = parseFieldList(rule.outputFieldsJson || rule.outputFields)
  return Object.assign({}, rule, {
    scope: rule.scope || (Number(rule.projectId) === 0 ? 'GLOBAL' : 'PROJECT'),
    inputFields,
    outputFields,
    inputFieldsJson: inputFields,
    outputFieldsJson: outputFields
  })
}

export function normalizeRuleOptions(rules) {
  const seen = new Set()
  return (rules || [])
    .filter(rule => rule && rule.id != null && SUPPORTED_MODEL_TYPES.includes(rule.modelType))
    .map(normalizeRuleOption)
    .filter(rule => {
      const key = String(rule.id)
      if (seen.has(key)) return false
      seen.add(key)
      return true
    })
}

export function collectRuleCallBlocks(actions, out = []) {
  (actions || []).forEach(action => {
    if (!action || typeof action !== 'object') return
    if (action.type === 'rule-call') out.push(action)
    collectRuleCallBlocks(action.actions, out)
    collectRuleCallBlocks(action.defaultActions, out)
    ;(action.branches || []).forEach(branch => collectRuleCallBlocks(branch && branch.actions, out))
    ;(action.cases || []).forEach(item => collectRuleCallBlocks(item && item.actions, out))
  })
  return out
}

function visitRuleCalls(value, callback, visited = new Set()) {
  if (!value || typeof value !== 'object' || visited.has(value)) return
  visited.add(value)
  if (value.type === 'rule-call') callback(value)
  if (Array.isArray(value)) {
    value.forEach(item => visitRuleCalls(item, callback, visited))
    return
  }
  Object.keys(value).forEach(key => visitRuleCalls(value[key], callback, visited))
}

export function repairLegacyRuleCallRefs(model, rules) {
  let repaired = 0
  visitRuleCalls(model, call => {
    if (call.ruleId != null || !call.ruleCode) return
    const matches = (rules || []).filter(rule => String(rule.ruleCode) === String(call.ruleCode))
    if (matches.length !== 1) return
    const rule = matches[0]
    call.ruleId = rule.id
    call.ruleName = rule.ruleName || ''
    call.modelType = rule.modelType || ''
    repaired += 1
  })
  return repaired
}

export function isRuleOutputMappingEnabled(call) {
  if (!call) return false
  if (typeof call.enableOutputMapping === 'boolean') return call.enableOutputMapping
  return !!(call.outputField || call.targetOperand)
}

export function validateRuleCallBlock(call, context = {}) {
  if (!call || (call.ruleId == null && !call.ruleCode)) return '执行规则动作未选择要调用的规则'
  if (call.ruleId == null) return '执行规则动作缺少稳定规则 ID，请重新选择规则'
  const sameId = context.currentRuleId != null && String(call.ruleId) === String(context.currentRuleId)
  const sameCode = context.currentRuleCode && call.ruleCode && String(call.ruleCode) === String(context.currentRuleCode)
  if (sameId || sameCode) return '不能调用当前规则自身，会形成规则调用环'
  const rule = (context.rules || []).find(item => String(item.id) === String(call.ruleId))
  if (!rule) return '所选规则已不存在或不在当前项目可用范围内'
  if (rule.status != null && Number(rule.status) !== 1) return '所选规则未启用，不能执行'
  if (isRuleOutputMappingEnabled(call)) {
    const hasOutput = !!call.outputField
    const hasTarget = !!call.targetOperand
    if (!hasOutput || !hasTarget) return '输出字段和目标字段必须同时配置'
    if (hasOutput && !(rule.outputFields || rule.outputFieldsJson || []).some(field =>
      String(field.scriptName || field.fieldName) === String(call.outputField))) {
      return '映射的输出字段已不存在，请重新选择'
    }
  }
  return ''
}

export function validateRuleCallsInModel(model, context = {}) {
  const errors = []
  visitRuleCalls(model, call => {
    const error = validateRuleCallBlock(call, context)
    if (error) errors.push(error)
  })
  return errors
}
