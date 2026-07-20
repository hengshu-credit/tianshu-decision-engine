const MODEL_TYPES = [
  'TABLE', 'TREE', 'FLOW', 'RULE_SET', 'CROSS',
  'SCORE', 'CROSS_ADV', 'SCORE_ADV', 'SCRIPT'
]

const FIELD_LABELS = {
  hitPolicy: '命中策略',
  executionMode: '执行模式',
  resultVar: '结果字段',
  initialScore: '初始分',
  defaultEdgeLineType: '连线样式',
  ruleCode: '规则编码',
  ruleName: '规则名称',
  priority: '优先级',
  enabled: '启用状态',
  status: '状态',
  type: '类型',
  name: '名称',
  label: '名称',
  operator: '判断关系',
  op: '条件关系',
  leftOperand: '左侧表达式',
  rightOperand: '右侧表达式',
  targetOperand: '赋值目标',
  valueOperand: '赋值内容',
  actionDataType: '动作类型',
  actionType: '动作类型',
  value: '取值',
  score: '分值',
  weight: '权重',
  min: '最小值',
  max: '最大值',
  result: '结果',
  conditionExpression: '条件表达式',
  conditionConfig: '条件配置',
  source: '来源节点',
  target: '目标节点',
  gatewayDirection: '网关方向',
  terminationScope: '终止范围',
  varCode: '字段编码',
  varLabel: '字段名称',
  varType: '字段类型',
  _varId: '字段引用',
  _refType: '引用类型',
  rangeBoundary: '区间边界',
  expression: '表达式',
  script: '脚本'
}

const IGNORED_COMPARE_KEYS = new Set([
  'x', 'y', 'logicflow', '_collapsed', '_editing', '_origin', '_saving'
])

export function parseRuleVersionModel(value) {
  if (!value) return { model: {}, raw: value || '', error: '' }
  if (typeof value === 'object') {
    return { model: value, raw: JSON.stringify(value), error: '' }
  }
  try {
    return { model: JSON.parse(value), raw: value, error: '' }
  } catch (e) {
    return { model: {}, raw: String(value), error: '版本内容无法解析' }
  }
}

function comparableValue(value) {
  if (Array.isArray(value)) return value.map(comparableValue)
  if (!value || typeof value !== 'object') return value
  const result = {}
  Object.keys(value).sort().forEach(key => {
    if (!IGNORED_COMPARE_KEYS.has(key)) result[key] = comparableValue(value[key])
  })
  return result
}

function stableStringify(value) {
  return JSON.stringify(comparableValue(value))
}

function sameValue(left, right) {
  return stableStringify(left) === stableStringify(right)
}

function valueText(value) {
  if (value === undefined) return '未配置'
  if (value === null || value === '') return '空值'
  if (value === true) return '是'
  if (value === false) return '否'
  if (typeof value === 'object') {
    const operand = operandText(value)
    return operand || JSON.stringify(value)
  }
  return String(value)
}

function referenceKey(value) {
  if (!value || typeof value !== 'object') return ''
  const refId = value.refId != null ? value.refId : value._varId
  const refType = value.refType || value._refType || ''
  return refId != null ? String(refType) + ':' + String(refId) : ''
}

function operandText(value) {
  if (!value || typeof value !== 'object') return valueText(value)
  if (value.kind === 'LITERAL') return valueText(value.value)
  if (value.kind === 'FUNCTION') {
    const name = value.functionLabel || value.functionCode || value.funcCode || value.code || '函数'
    const args = (value.args || []).map(operandText).join(', ')
    return name + '(' + args + ')'
  }
  if (value.kind === 'BINARY') {
    return operandText(value.left) + ' ' + (value.operator || '') + ' ' + operandText(value.right)
  }
  const label = value.label || value.varLabel || value.code || value.varCode || value.refCode || value.value || ''
  const refId = value.refId != null ? value.refId : value._varId
  const refType = value.refType || value._refType || 'REF'
  return refId != null ? (label || '引用') + '（' + refType + ' #' + refId + '）' : label
}

function rawIdentity(item, kind) {
  if (!item || typeof item !== 'object') return ''
  if (item.id != null) return kind + ':id:' + item.id
  if (item.uid != null) return kind + ':uid:' + item.uid
  if (item.ruleCode) return kind + ':rule:' + item.ruleCode
  const ref = referenceKey(item)
  if (ref) return kind + ':ref:' + ref
  return ''
}

function rawSignature(item, kind) {
  if (!item || typeof item !== 'object') return kind + ':' + valueText(item)
  const left = referenceKey(item.leftOperand || item.operand || item)
  const target = referenceKey(item.targetOperand || {})
  if (left || target) return kind + ':shape:' + left + ':' + target
  return ''
}

function alignItems(left, right, options) {
  const identity = options.identity
  const signature = options.signature
  const equals = options.equals || sameValue
  const rows = left.length + 1
  const cols = right.length + 1
  const costs = Array.from({ length: rows }, () => Array(cols).fill(0))
  const operations = Array.from({ length: rows }, () => Array(cols).fill(''))
  for (let i = 1; i < rows; i++) {
    costs[i][0] = i
    operations[i][0] = 'remove'
  }
  for (let j = 1; j < cols; j++) {
    costs[0][j] = j
    operations[0][j] = 'add'
  }
  for (let i = 1; i < rows; i++) {
    for (let j = 1; j < cols; j++) {
      const leftItem = left[i - 1]
      const rightItem = right[j - 1]
      const leftId = identity(leftItem)
      const rightId = identity(rightItem)
      const leftSignature = signature(leftItem)
      const rightSignature = signature(rightItem)
      let pairCost = 1.25
      if (leftId && rightId) pairCost = leftId === rightId ? (equals(leftItem, rightItem) ? 0 : 0.25) : 3
      else if (equals(leftItem, rightItem)) pairCost = 0
      else if (leftSignature && leftSignature === rightSignature) pairCost = 0.5
      const pair = costs[i - 1][j - 1] + pairCost
      const remove = costs[i - 1][j] + 1
      const add = costs[i][j - 1] + 1
      if (pair <= remove && pair <= add) {
        costs[i][j] = pair
        operations[i][j] = 'pair'
      } else if (remove <= add) {
        costs[i][j] = remove
        operations[i][j] = 'remove'
      } else {
        costs[i][j] = add
        operations[i][j] = 'add'
      }
    }
  }
  const pairs = []
  let i = left.length
  let j = right.length
  while (i > 0 || j > 0) {
    const operation = operations[i][j]
    if (operation === 'pair') {
      pairs.push({ left: left[i - 1], right: right[j - 1] })
      i--
      j--
    } else if (operation === 'remove') {
      pairs.push({ left: left[i - 1], right: null })
      i--
    } else {
      pairs.push({ left: null, right: right[j - 1] })
      j--
    }
  }
  return pairs.reverse()
}

export function pairBusinessItems(left, right, options = {}) {
  const kind = options.kind || 'item'
  const leftItems = Array.isArray(left) ? left : []
  const rightItems = Array.isArray(right) ? right : []
  return alignItems(leftItems, rightItems, {
    identity: item => options.identity ? options.identity(item) : rawIdentity(item, kind),
    signature: item => options.signature ? options.signature(item) : rawSignature(item, kind)
  }).map((pair, index) => ({
    key: rawIdentity(pair.left || pair.right, kind) || kind + ':' + index,
    kind,
    status: !pair.left ? 'added' : (!pair.right ? 'removed' : (sameValue(pair.left, pair.right) ? 'unchanged' : 'modified')),
    left: pair.left,
    right: pair.right,
    fields: [],
    children: []
  }))
}

function field(key, label, value, text) {
  return {
    key,
    label: label || FIELD_LABELS[key] || key,
    value: comparableValue(value),
    text: text === undefined ? valueText(value) : text
  }
}

function referenceField(key, label, value) {
  if (!value) return field(key, label, null, '未配置')
  const comparable = comparableValue(value)
  return field(key, label, comparable, operandText(value) || '未配置')
}

function semanticNode(kind, title, options = {}) {
  return {
    kind,
    title,
    subtitle: options.subtitle || '',
    identity: options.identity || '',
    matchKey: options.matchKey || '',
    fields: options.fields || [],
    children: options.children || []
  }
}

function legacyReference(holder) {
  if (!holder) return null
  if (holder.operand) return holder.operand
  if (holder.leftOperand) return holder.leftOperand
  if (holder._varId != null || holder.varCode) {
    return {
      kind: 'REFERENCE',
      refId: holder._varId,
      refType: holder._refType,
      code: holder.varCode,
      label: holder.varLabel,
      valueType: holder.varType
    }
  }
  return null
}

function literalOperand(value, type) {
  return { kind: 'LITERAL', value, valueType: type || '' }
}

function conditionNode(condition, index, path) {
  const item = condition || {}
  const children = Array.isArray(item.children) ? item.children : []
  const isGroup = item.type === 'group' || children.length > 0
  const identity = item.id != null ? 'condition:' + item.id : (item.uid ? 'condition:' + item.uid : '')
  if (isGroup) {
    return semanticNode('condition-group', '条件组 ' + (index + 1), {
      identity,
      matchKey: 'condition-group:' + path,
      fields: [field('op', '条件关系', item.op || item.logic || 'AND')],
      children: children.map((child, childIndex) => conditionNode(child, childIndex, path + '.' + childIndex))
    })
  }
  const left = item.leftOperand || legacyReference(item)
  const right = item.rightOperand || literalOperand(item.value, item.varType)
  const leftKey = referenceKey(left)
  return semanticNode('condition', '条件 ' + (index + 1), {
    identity,
    matchKey: 'condition:' + (leftKey || path),
    fields: [
      referenceField('leftOperand', '左侧表达式', left),
      field('operator', '判断关系', item.operator || item.op || ''),
      referenceField('rightOperand', '右侧表达式', right)
    ]
  })
}

function genericBusinessFields(item, excluded = []) {
  const skip = new Set(['id', 'uid', 'children', 'conditions', 'conditionRoot', 'actions', 'actionData'].concat(excluded))
  return Object.keys(item || {}).filter(key => !skip.has(key) && !IGNORED_COMPARE_KEYS.has(key)).map(key => {
    const value = item[key]
    if (value && typeof value === 'object' && !Array.isArray(value)) return referenceField(key, FIELD_LABELS[key], value)
    if (!Array.isArray(value)) return field(key, FIELD_LABELS[key], value)
    return null
  }).filter(Boolean)
}

const ACTION_TYPE_LABELS = {
  assign: '赋值',
  'if-block': '条件分支',
  'switch-block': 'Switch 匹配',
  'func-call': '函数调用',
  foreach: 'ForEach 循环',
  ternary: '三元表达式',
  'in-check': 'IN 判断',
  'template-str': '动态字符串',
  'rule-call': '执行规则'
}

function operandNode(value, index, kind, title) {
  return semanticNode(kind, title + ' ' + (index + 1), {
    matchKey: kind + ':' + operandText(value),
    fields: [referenceField('operand', '表达式', value)]
  })
}

function actionBranchNode(branch, index, path) {
  const item = branch || {}
  const left = item.leftOperand || legacyReference(item)
  const right = item.rightOperand || literalOperand(item.value, item.varType)
  return semanticNode('action-branch', item.type === 'else' ? '否则分支' : '条件分支 ' + (index + 1), {
    identity: item.id != null ? 'action-branch:' + item.id : '',
    matchKey: 'action-branch:' + (item.type || 'if') + ':' + referenceKey(left),
    fields: [
      field('type', '分支类型', item.type || 'if'),
      referenceField('leftOperand', '左侧表达式', left),
      field('operator', '判断关系', item.operator || item.condOp || ''),
      referenceField('rightOperand', '右侧表达式', right)
    ],
    children: (item.actions || []).map((action, actionIndex) => actionNode(action, actionIndex, path + '.action.' + actionIndex))
  })
}

function actionCaseNode(item, index, path) {
  const branch = item || {}
  const value = branch.valueOperand || literalOperand(branch.value)
  return semanticNode('action-case', 'Case ' + (index + 1), {
    identity: branch.id != null ? 'action-case:' + branch.id : '',
    matchKey: 'action-case:' + operandText(value),
    fields: [referenceField('valueOperand', '匹配值', value)],
    children: (branch.actions || []).map((action, actionIndex) => actionNode(action, actionIndex, path + '.action.' + actionIndex))
  })
}

function actionNode(action, index, path) {
  const item = action || {}
  const target = item.targetOperand || legacyReference(item)
  const value = item.valueOperand || (item.operand && !target ? item.operand : literalOperand(item.value, item.varType))
  const actionType = item.actionDataType || item.actionType || item.type || item.kind || 'assign'
  const identity = item.id != null ? 'action:' + item.id : (item.uid ? 'action:' + item.uid : '')
  const targetKey = referenceKey(target)
  const fields = [
    field('actionType', '动作类型', actionType, ACTION_TYPE_LABELS[actionType] || actionType),
    referenceField('targetOperand', '赋值目标', target),
    referenceField('valueOperand', '赋值内容', value)
  ]
  genericBusinessFields(item, ['targetOperand', 'valueOperand', 'operand', 'value', 'varCode', 'varLabel', 'varType', '_varId', '_refType', 'type', 'kind', 'actionDataType', 'actionType']).forEach(itemField => fields.push(itemField))
  const children = []
  ;(item.branches || []).forEach((branch, branchIndex) => children.push(actionBranchNode(branch, branchIndex, path + '.branch.' + branchIndex)))
  ;(item.cases || []).forEach((branch, branchIndex) => children.push(actionCaseNode(branch, branchIndex, path + '.case.' + branchIndex)))
  if ((item.defaultActions || []).length) {
    children.push(semanticNode('action-default', '默认分支', {
      matchKey: 'action-default',
      children: item.defaultActions.map((child, childIndex) => actionNode(child, childIndex, path + '.default.' + childIndex))
    }))
  }
  (item.actions || []).forEach((child, childIndex) => children.push(actionNode(child, childIndex, path + '.action.' + childIndex)))
  ;(item.args || []).forEach((operand, operandIndex) => children.push(operandNode(operand, operandIndex, 'action-argument', '函数参数')))
  ;(item.inOperands || []).forEach((operand, operandIndex) => children.push(operandNode(operand, operandIndex, 'action-in-value', 'IN 选项')))
  ;(item.parts || []).forEach((part, partIndex) => {
    children.push(semanticNode('action-template-part', '字符串片段 ' + (partIndex + 1), {
      matchKey: 'action-template-part:' + (part.type || '') + ':' + operandText(part.operand),
      fields: [field('type', '片段类型', part.type || 'text'), referenceField('operand', '片段内容', part.operand)]
    }))
  })
  return semanticNode('action', (ACTION_TYPE_LABELS[actionType] || '执行动作') + ' ' + (index + 1), {
    identity,
    matchKey: 'action:' + actionType + ':' + targetKey,
    fields,
    children
  })
}

function legacyConditionNode(item, definition) {
  const condition = item || {}
  const column = definition || {}
  let right = condition.rightOperand
  if (!right && condition.valueKind === 'VAR') {
    right = legacyReference({
      varCode: condition.value,
      varLabel: condition.rightVarLabel,
      varType: condition.rightVarType,
      _varId: condition._rightVarId,
      _refType: condition._rightRefType
    })
  }
  return {
    ...condition,
    type: condition.type || 'leaf',
    leftOperand: condition.leftOperand || legacyReference(condition) || legacyReference(column),
    rightOperand: right || literalOperand(condition.value, column.varType || condition.varType)
  }
}

function legacyTableAction(item, definition) {
  const action = item || {}
  if (action.targetOperand !== undefined || action.valueOperand !== undefined) return action
  const column = action.varCode ? action : (definition || {})
  return {
    ...action,
    targetOperand: legacyReference(column),
    valueOperand: literalOperand(action.value == null ? '' : action.value, column.varType)
  }
}

function ruleNode(rule, index, modelType, options = {}) {
  const item = rule || {}
  const code = item.ruleCode || ''
  const identity = item.id != null ? 'rule:' + item.id : (item.uid ? 'rule:' + item.uid : (code ? 'rule-code:' + code : ''))
  const condition = item.conditionRoot || (Array.isArray(item.conditions)
    ? {
        type: 'group',
        op: 'AND',
        children: item.conditions.map((conditionItem, conditionIndex) => legacyConditionNode(conditionItem, (options.legacyConditions || [])[conditionIndex]))
      }
    : null)
  const rawActionList = modelType === 'RULE_SET' ? (item.actionData || []) : (item.actions || item.actionData || [])
  const actionList = modelType === 'TABLE'
    ? rawActionList.map((action, actionIndex) => legacyTableAction(action, (options.legacyActions || [])[actionIndex]))
    : rawActionList
  const children = []
  if (condition) children.push(conditionNode(condition, 0, 'rule.' + index + '.condition'))
  actionList.forEach((action, actionIndex) => children.push(actionNode(action, actionIndex, 'rule.' + index + '.action.' + actionIndex)))
  const fields = []
  if (modelType === 'RULE_SET') {
    fields.push(field('ruleCode', '规则编码', code))
    fields.push(field('ruleName', '规则名称', item.ruleName || ''))
    fields.push(field('priority', '优先级', item.priority))
    fields.push(field('enabled', '启用状态', item.enabled !== undefined ? item.enabled : item.status !== 0))
  }
  const matchCondition = condition && (condition.leftOperand || (condition.children && condition.children[0] && condition.children[0].leftOperand))
  return semanticNode('rule', item.ruleName || code || '规则 ' + (index + 1), {
    identity,
    matchKey: 'rule-shape:' + referenceKey(matchCondition),
    fields,
    children
  })
}

function graphNode(item, index, modelType) {
  const node = item || {}
  const fields = [
    field('type', '节点类型', node.type || ''),
    field('name', '节点名称', node.name || node.label || ''),
    field('conditionExpression', '条件表达式', node.conditionExpression || ''),
    field('gatewayDirection', '网关方向', node.gatewayDirection || ''),
    field('terminationScope', '终止范围', node.terminationScope || '')
  ]
  if (node.leftVarId != null) fields.push(field('leftVarId', '左侧字段引用', { refId: node.leftVarId, refType: node.leftRefType }))
  if (node.rightVarId != null) fields.push(field('rightVarId', '右侧字段引用', { refId: node.rightVarId, refType: node.rightRefType }))
  const children = (node.actionData || []).map((action, actionIndex) => actionNode(action, actionIndex, 'node.' + index + '.action.' + actionIndex))
  return semanticNode(modelType === 'TREE' ? 'tree-node' : 'flow-node', node.name || node.label || node.type || '节点 ' + (index + 1), {
    identity: node.id != null ? 'node:' + node.id : '',
    matchKey: 'node:' + (node.type || index),
    fields,
    children
  })
}

function edgeNode(item, index) {
  const edge = item || {}
  const children = []
  if (edge.conditionConfig) children.push(conditionNode(edge.conditionConfig, 0, 'edge.' + index + '.condition'))
  return semanticNode('edge', edge.name || '连线 ' + (index + 1), {
    identity: edge.id != null ? 'edge:' + edge.id : (edge.source && edge.target ? 'edge:' + edge.source + '->' + edge.target : ''),
    matchKey: 'edge:' + (edge.source || '') + '->' + (edge.target || ''),
    fields: [
      field('source', '来源节点', edge.source || edge.sourceNodeId || ''),
      field('target', '目标节点', edge.target || edge.targetNodeId || ''),
      field('name', '分支名称', edge.name || ''),
      field('conditionExpression', '条件表达式', edge.conditionExpression || '')
    ],
    children
  })
}

function dimensionNode(item, index, axis) {
  const dim = item || {}
  const operand = dim.operand || legacyReference(dim)
  const identity = referenceKey(operand)
  const children = (dim.segments || []).map((segment, segmentIndex) => {
    const value = segment.valueOperand || literalOperand(segment.value, dim.varType)
    const segmentText = segment.label || operandText(value) || [segment.operator, segment.min, segment.max].join(':')
    return semanticNode('segment', '分段 ' + (segmentIndex + 1), {
      identity: segment.id != null ? 'segment:' + segment.id : '',
      matchKey: 'segment:' + segmentText,
      fields: [
        field('label', '分段名称', segment.label || ''),
        field('operator', '判断关系', segment.operator || ''),
        referenceField('valueOperand', '比较值', value),
        referenceField('minOperand', '最小值', segment.minOperand || literalOperand(segment.min)),
        referenceField('maxOperand', '最大值', segment.maxOperand || literalOperand(segment.max)),
        field('rangeBoundary', '区间边界', segment.rangeBoundary || '')
      ]
    })
  })
  return semanticNode('dimension', dim.varLabel || dim.varCode || (axis === 'row' ? '行维度 ' : '列维度 ') + (index + 1), {
    identity: identity ? 'dimension:' + identity : '',
    matchKey: 'dimension:' + axis + ':' + (identity || index),
    fields: [referenceField('operand', '维度字段', operand), field('weight', '权重', dim.weight)],
    children
  })
}

function scoreItemNode(item, index) {
  const score = item || {}
  const left = score.leftOperand || (score.condVar
    ? {
        kind: 'REFERENCE',
        refId: score._varId,
        refType: score._refType,
        code: score.condVar,
        label: score.conditionLabel || score.condVar,
        valueType: score.condVarType
      }
    : legacyReference(score))
  const conditionValue = score.condValue !== undefined ? score.condValue : score.value
  const right = score.rightOperand || literalOperand(conditionValue, score.condVarType || score.varType)
  return semanticNode('score-item', score.label || score.varLabel || '评分项 ' + (index + 1), {
    identity: score.id != null ? 'score:' + score.id : '',
    matchKey: 'score:' + (referenceKey(left) || index),
    fields: [
      referenceField('leftOperand', '评分字段', left),
      field('operator', '判断关系', score.operator || score.condOperator || ''),
      referenceField('rightOperand', '比较值', right),
      field('score', '分值', score.score),
      field('weight', '权重', score.weight)
    ]
  })
}

function thresholdNode(item, index) {
  const threshold = item || {}
  return semanticNode('threshold', '结果区间 ' + (index + 1), {
    identity: threshold.id != null ? 'threshold:' + threshold.id : '',
    matchKey: 'threshold:' + index,
    fields: [
      field('min', '最小值', threshold.min),
      field('max', '最大值', threshold.max),
      referenceField('result', '结果', threshold.resultOperand || literalOperand(threshold.result))
    ]
  })
}

function matrixCellNodes(model) {
  const rows = model.rowHeaders || []
  const cols = model.colHeaders || []
  const cells = model.cells || model.cellData || []
  const rowOperands = model.rowHeaderOperands || []
  const colOperands = model.colHeaderOperands || []
  const cellOperands = model.cellOperands || []
  const result = []
  cells.forEach((row, rowIndex) => {
    (row || []).forEach((cellValue, colIndex) => {
      const rowText = operandText(rowOperands[rowIndex]) || valueText(rows[rowIndex])
      const colText = operandText(colOperands[colIndex]) || valueText(cols[colIndex])
      const cell = cellOperands[rowIndex] && cellOperands[rowIndex][colIndex]
        ? cellOperands[rowIndex][colIndex]
        : literalOperand(cellValue)
      result.push(semanticNode('matrix-cell', rowText + ' × ' + colText, {
        matchKey: 'cell:' + rowText + ':' + colText,
        fields: [
          field('row', '行条件', rowOperands[rowIndex] || rows[rowIndex], rowText),
          field('column', '列条件', colOperands[colIndex] || cols[colIndex], colText),
          referenceField('result', '结果', cell)
        ]
      }))
    })
  })
  return result
}

function segmentCoordinate(segment) {
  if (!segment) return '未配置'
  if (segment.label) return segment.label
  if (segment.valueOperand) return operandText(segment.valueOperand)
  if (segment.value !== undefined) return valueText(segment.value)
  return [segment.operator || '', valueText(segment.min), valueText(segment.max), segment.rangeBoundary || ''].join(' ')
}

function dimensionCombinationLabels(dimensions) {
  return (dimensions || []).reduce((combinations, dimension) => {
    const segments = (dimension.segments || []).map(segmentCoordinate)
    if (!segments.length) return combinations
    return combinations.reduce((result, combination) => {
      segments.forEach(segment => result.push(combination ? combination + ' × ' + segment : segment))
      return result
    }, [])
  }, [''])
}

function advancedCellNodes(model) {
  const cells = model.cells || model.cellData || []
  const rowCoordinates = dimensionCombinationLabels(model.rowDimensions)
  const colCoordinates = dimensionCombinationLabels(model.colDimensions)
  const result = []
  cells.forEach((row, rowIndex) => {
    (row || []).forEach((cellValue, colIndex) => {
      const rowCoordinate = rowCoordinates[rowIndex] || '第 ' + (rowIndex + 1) + ' 行'
      const colCoordinate = colCoordinates[colIndex] || '第 ' + (colIndex + 1) + ' 列'
      result.push(semanticNode('matrix-cell', rowCoordinate + ' × ' + colCoordinate, {
        matchKey: 'cell:' + rowCoordinate + ':' + colCoordinate,
        fields: [referenceField('result', '结果', cellValue && typeof cellValue === 'object' ? cellValue : literalOperand(cellValue))]
      }))
    })
  })
  return result
}

function advancedScoreGroupNode(item, index) {
  const group = item || {}
  const children = (group.dimensions || []).map((dimension, dimensionIndex) => {
    const dim = dimensionNode(dimension, dimensionIndex, 'score')
    dim.kind = 'score-dimension'
    dim.children = (dimension.rules || []).map((rule, ruleIndex) => {
      const conditions = (rule.conditions || []).map((condition, conditionIndex) => conditionNode(condition, conditionIndex, 'score.' + index + '.' + dimensionIndex + '.' + ruleIndex + '.' + conditionIndex))
      return semanticNode('score-rule', '评分规则 ' + (ruleIndex + 1), {
        identity: rule.id != null ? 'score-rule:' + rule.id : '',
        matchKey: 'score-rule:' + ruleIndex,
        fields: [field('score', '分值', rule.score)],
        children: conditions
      })
    })
    return dim
  })
  return semanticNode('dimension-group', group.groupLabel || '维度组 ' + (index + 1), {
    identity: group.id != null ? 'group:' + group.id : '',
    matchKey: 'group:' + (group.groupLabel || index),
    fields: [field('label', '维度组名称', group.groupLabel || ''), field('weight', '组权重', group.weight)],
    children
  })
}

function section(key, title, variant, nodes) {
  return { key, title, variant, nodes: nodes || [] }
}

function buildSideSections(modelType, model) {
  const source = model || {}
  if (modelType === 'TABLE') {
    return [
      section('settings', '规则配置', 'settings', [semanticNode('settings', '决策表配置', { fields: [field('hitPolicy', '命中策略', source.hitPolicy || 'FIRST')] })]),
      section('rules', '规则', 'list', (source.rules || []).map((item, index) => ruleNode(item, index, modelType, {
        legacyConditions: source.conditions,
        legacyActions: source.actions
      })))
    ]
  }
  if (modelType === 'RULE_SET') {
    return [
      section('settings', '规则集配置', 'settings', [semanticNode('settings', '规则集配置', { fields: [
        field('executionMode', '执行模式', source.executionMode || 'SERIAL'),
        referenceField('resultVar', '结果字段', source.resultVar && (source.resultVar.operand || legacyReference(source.resultVar)))
      ] })]),
      section('rules', '业务规则', 'list', (source.rules || []).map((item, index) => ruleNode(item, index, modelType)))
    ]
  }
  if (modelType === 'TREE' || modelType === 'FLOW') {
    return [
      section('settings', modelType === 'TREE' ? '决策树配置' : '决策流配置', 'settings', [semanticNode('settings', '图配置', { fields: [field('defaultEdgeLineType', '连线样式', source.defaultEdgeLineType || '')] })]),
      section('nodes', modelType === 'TREE' ? '节点' : '流程节点', 'graph', (source.nodes || []).map((item, index) => graphNode(item, index, modelType))),
      section('edges', '分支连线', 'edges', (source.edges || []).map(edgeNode))
    ]
  }
  if (modelType === 'CROSS') {
    const rowNodes = (source.rowHeaders || []).map((value, index) => semanticNode('axis-value', '行条件 ' + (index + 1), {
      matchKey: 'row:' + (operandText((source.rowHeaderOperands || [])[index]) || valueText(value)),
      fields: [referenceField('value', '条件值', (source.rowHeaderOperands || [])[index] || literalOperand(value))]
    }))
    const colNodes = (source.colHeaders || []).map((value, index) => semanticNode('axis-value', '列条件 ' + (index + 1), {
      matchKey: 'column:' + (operandText((source.colHeaderOperands || [])[index]) || valueText(value)),
      fields: [referenceField('value', '条件值', (source.colHeaderOperands || [])[index] || literalOperand(value))]
    }))
    return [
      section('dimensions', '维度', 'settings', [semanticNode('settings', '交叉表维度', { fields: [
        referenceField('rowVar', '行字段', source.rowVar && (source.rowVar.operand || legacyReference(source.rowVar))),
        referenceField('colVar', '列字段', source.colVar && (source.colVar.operand || legacyReference(source.colVar))),
        referenceField('resultVar', '结果字段', source.resultVar && (source.resultVar.operand || legacyReference(source.resultVar)))
      ] })]),
      section('rowHeaders', '行条件', 'axis', rowNodes),
      section('colHeaders', '列条件', 'axis', colNodes),
      section('cells', '结果矩阵', 'matrix', matrixCellNodes(source))
    ]
  }
  if (modelType === 'CROSS_ADV') {
    return [
      section('settings', '复杂交叉表配置', 'settings', [semanticNode('settings', '结果配置', { fields: [referenceField('resultVar', '结果字段', source.resultVar && (source.resultVar.operand || legacyReference(source.resultVar)))] })]),
      section('rowDimensions', '行维度', 'dimensions', (source.rowDimensions || []).map((item, index) => dimensionNode(item, index, 'row'))),
      section('colDimensions', '列维度', 'dimensions', (source.colDimensions || []).map((item, index) => dimensionNode(item, index, 'column'))),
      section('cells', '结果矩阵', 'matrix', advancedCellNodes(source))
    ]
  }
  if (modelType === 'SCORE') {
    return [
      section('settings', '评分卡配置', 'settings', [semanticNode('settings', '评分卡配置', { fields: [
        field('initialScore', '初始分', source.initialScore == null ? 0 : source.initialScore),
        referenceField('resultVar', '结果字段', source.resultVar && (source.resultVar.operand || legacyReference(source.resultVar)))
      ] })]),
      section('scoreItems', '评分项', 'score', (source.scoreItems || []).map(scoreItemNode)),
      section('thresholds', '结果阈值', 'thresholds', (source.thresholds || []).map(thresholdNode))
    ]
  }
  if (modelType === 'SCORE_ADV') {
    return [
      section('settings', '复杂评分卡配置', 'settings', [semanticNode('settings', '评分卡配置', { fields: [
        field('initialScore', '初始分', source.initialScore == null ? 100 : source.initialScore),
        referenceField('resultVar', '结果字段', source.resultVar && (source.resultVar.operand || legacyReference(source.resultVar)))
      ] })]),
      section('dimensionGroups', '维度组', 'score-groups', (source.dimensionGroups || []).map(advancedScoreGroupNode)),
      section('thresholds', '结果阈值', 'thresholds', (source.thresholds || []).map(thresholdNode))
    ]
  }
  return []
}

function pairFields(left, right) {
  const leftMap = {}
  const rightMap = {}
  ;(left || []).forEach(item => { leftMap[item.key] = item })
  ;(right || []).forEach(item => { rightMap[item.key] = item })
  return Array.from(new Set(Object.keys(leftMap).concat(Object.keys(rightMap)))).map(key => {
    const leftField = leftMap[key]
    const rightField = rightMap[key]
    return {
      key,
      label: (leftField || rightField).label,
      status: !leftField ? 'added' : (!rightField ? 'removed' : (sameValue(leftField.value, rightField.value) ? 'unchanged' : 'modified')),
      leftValue: leftField && leftField.value,
      rightValue: rightField && rightField.value,
      leftText: leftField ? leftField.text : '未配置',
      rightText: rightField ? rightField.text : '未配置'
    }
  })
}

function semanticIdentity(item) {
  return item && item.identity || ''
}

function semanticSignature(item) {
  return item && item.matchKey || ''
}

function semanticComparable(item) {
  if (!item) return item
  return {
    kind: item.kind,
    fields: (item.fields || []).map(itemField => ({ key: itemField.key, value: itemField.value })),
    children: (item.children || []).map(semanticComparable)
  }
}

function sameSemanticValue(left, right) {
  return stableStringify(semanticComparable(left)) === stableStringify(semanticComparable(right))
}

function pairSemanticNodes(left, right) {
  return alignItems(left || [], right || [], {
    identity: semanticIdentity,
    signature: semanticSignature,
    equals: sameSemanticValue
  }).map((pair, index) => buildSemanticLane(pair.left, pair.right, index))
}

function buildSemanticLane(left, right, index) {
  if (!left) {
    return {
      key: right.identity || right.matchKey || right.kind + ':added:' + index,
      kind: right.kind,
      status: 'added',
      left: null,
      right,
      fields: pairFields([], right.fields),
      children: pairSemanticNodes([], right.children)
    }
  }
  if (!right) {
    return {
      key: left.identity || left.matchKey || left.kind + ':removed:' + index,
      kind: left.kind,
      status: 'removed',
      left,
      right: null,
      fields: pairFields(left.fields, []),
      children: pairSemanticNodes(left.children, [])
    }
  }
  const fields = pairFields(left.fields, right.fields)
  const children = pairSemanticNodes(left.children, right.children)
  const changed = fields.some(item => item.status !== 'unchanged') || children.some(item => item.status !== 'unchanged')
  return {
    key: left.identity || right.identity || left.matchKey || right.matchKey || left.kind + ':' + index,
    kind: left.kind || right.kind,
    status: changed ? 'modified' : 'unchanged',
    left,
    right,
    fields,
    children
  }
}

function pairSections(leftSections, rightSections) {
  const leftMap = {}
  const rightMap = {}
  leftSections.forEach(item => { leftMap[item.key] = item })
  rightSections.forEach(item => { rightMap[item.key] = item })
  return Array.from(new Set(Object.keys(leftMap).concat(Object.keys(rightMap)))).map(key => {
    const left = leftMap[key]
    const right = rightMap[key]
    const source = left || right
    return {
      key,
      title: source.title,
      variant: source.variant,
      lanes: pairSemanticNodes(left && left.nodes, right && right.nodes)
    }
  })
}

function countLane(summary, lane) {
  if (lane.status === 'added') {
    summary.added++
    return
  }
  if (lane.status === 'removed') {
    summary.removed++
    return
  }
  let fieldChanges = 0
  lane.fields.forEach(item => {
    if (item.status === 'modified') { summary.modified++; fieldChanges++ }
    if (item.status === 'added') { summary.added++; fieldChanges++ }
    if (item.status === 'removed') { summary.removed++; fieldChanges++ }
  })
  lane.children.forEach(child => countLane(summary, child))
  if (lane.status === 'modified' && fieldChanges === 0 && lane.children.length === 0) summary.modified++
}

function countSummary(sections) {
  const summary = { modified: 0, added: 0, removed: 0, total: 0 }
  sections.forEach(item => item.lanes.forEach(lane => countLane(summary, lane)))
  summary.total = summary.modified + summary.added + summary.removed
  return summary
}

function scriptRefNode(item, index) {
  const ref = item || {}
  return semanticNode('script-ref', ref.refCode || '变量引用 ' + (index + 1), {
    identity: ref.refCode ? 'script-token:' + ref.refCode : '',
    matchKey: 'script-ref:' + index,
    fields: [
      field('refCode', '脚本引用', ref.refCode || ''),
      field('varId', '变量 ID', ref.varId),
      field('refType', '引用类型', ref.refType || 'VARIABLE')
    ]
  })
}

function buildScript(left, right) {
  const leftRefs = (left.scriptVarRefs || []).map(scriptRefNode)
  const rightRefs = (right.scriptVarRefs || []).map(scriptRefNode)
  return {
    leftScript: left.script || '',
    rightScript: right.script || '',
    refLanes: pairSemanticNodes(leftRefs, rightRefs)
  }
}

export function buildRuleVersionDiff(input) {
  const modelType = MODEL_TYPES.includes(input.modelType) ? input.modelType : input.modelType || ''
  const left = parseRuleVersionModel(input.leftModelJson)
  const right = parseRuleVersionModel(input.rightModelJson)
  const leftModel = modelType === 'SCRIPT' && left.error ? { script: left.raw, scriptVarRefs: [] } : left.model
  const rightModel = modelType === 'SCRIPT' && right.error ? { script: right.raw, scriptVarRefs: [] } : right.model
  const sections = modelType === 'SCRIPT'
    ? []
    : pairSections(buildSideSections(modelType, leftModel), buildSideSections(modelType, rightModel))
  const script = modelType === 'SCRIPT' ? buildScript(leftModel, rightModel) : null
  const summarySections = script ? [{ lanes: script.refLanes }] : sections
  const summary = countSummary(summarySections)
  if (script && script.leftScript !== script.rightScript) {
    summary.modified++
    summary.total++
  }
  return {
    modelType,
    leftModel,
    rightModel,
    sections,
    summary,
    errors: {
      left: modelType === 'SCRIPT' ? '' : left.error,
      right: modelType === 'SCRIPT' ? '' : right.error
    },
    script
  }
}
