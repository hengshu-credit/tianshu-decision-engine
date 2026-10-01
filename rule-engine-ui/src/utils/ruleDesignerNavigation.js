const DESIGNER_ROUTES = Object.freeze({
  TABLE: 'DecisionTable',
  TREE: 'DecisionTree',
  FLOW: 'DecisionFlow',
  RULE_SET: 'RuleSet',
  CROSS: 'CrossTable',
  SCORE: 'Scorecard',
  CROSS_ADV: 'AdvancedCrossTable',
  SCORE_ADV: 'AdvancedScorecard',
  SCRIPT: 'ScriptEditor',
})

const MODEL_TYPE_ALIASES = Object.freeze({
  DECISION_TABLE: 'TABLE',
  DECISION_TREE: 'TREE',
  DECISION_FLOW: 'FLOW',
  RULESET: 'RULE_SET',
  ADVANCED_CROSS_TABLE: 'CROSS_ADV',
  ADVANCED_SCORECARD: 'SCORE_ADV',
  QL_SCRIPT: 'SCRIPT',
})

export function normalizeRuleModelType(modelType) {
  const value = String(modelType || '').trim().toUpperCase()
  return MODEL_TYPE_ALIASES[value] || value
}

export function resolveDesignerRouteName(modelType) {
  return DESIGNER_ROUTES[normalizeRuleModelType(modelType)] || null
}

export function ruleDesignerLocation(rule) {
  const routeName = resolveDesignerRouteName(rule && rule.modelType)
  const definitionId = rule && (rule.definitionId || rule.id)
  if (!routeName || !definitionId) return null
  return {
    name: routeName,
    params: { id: String(definitionId) },
  }
}
