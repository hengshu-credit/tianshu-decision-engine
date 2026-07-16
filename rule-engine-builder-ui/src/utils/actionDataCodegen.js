import { compileOperand, createLiteralOperand, createPathOperand, OPERAND_KINDS } from '@/utils/operand'
import { compileConditionOperands } from '@/utils/conditionOperand'
import { isRuleOutputMappingEnabled } from '@/utils/ruleCallConfig'

function quoteString(value) {
  return '"' + String(value == null ? '' : value).replace(/\\/g, '\\\\').replace(/"/g, '\\"') + '"'
}

function compileCondition(leftOperand, operator, rightOperand) {
  return compileConditionOperands(leftOperand, operator, rightOperand)
}

function generateAssignment(action, indent) {
  const target = compileOperand(action && action.targetOperand)
  const value = compileOperand(action && action.valueOperand)
  if (!target || !value) return ''
  const pad = '    '.repeat(indent)
  const lines = [pad + target + ' = ' + value]
  if (action.enableRounding && action.decimalPlaces != null && Number(action.decimalPlaces) >= 0) {
    lines.push(pad + target + ' = roundScale(' + target + ', ' + Number(action.decimalPlaces) + ', ' + quoteString(action.roundingMode || 'HALF_UP') + ')')
  }
  return lines.join('\n')
}

function generateBlock(block, indent) {
  const pad = '    '.repeat(indent)
  if (!block || !block.type) return ''

  switch (block.type) {
    case 'assign':
      return generateAssignment(block, indent)

    case 'if-block': {
      const branches = block.branches || []
      if (!branches.length) return ''
      const lines = []
      branches.forEach(branch => {
        const actions = (branch.actions || []).map(action => generateBlock(action, indent + 1)).filter(Boolean)
        if (branch.type === 'if') lines.push(pad + 'if (' + compileCondition(branch.leftOperand, branch.operator, branch.rightOperand) + ') {')
        else if (branch.type === 'elseif') lines.push(pad + '} else if (' + compileCondition(branch.leftOperand, branch.operator, branch.rightOperand) + ') {')
        else lines.push(pad + '} else {')
        lines.push(...actions)
      })
      lines.push(pad + '}')
      return lines.join('\n')
    }

    case 'switch-block': {
      const match = compileOperand(block.matchOperand)
      if (!match) return ''
      const lines = []
      let hasCase = false
      ;(block.cases || []).forEach(item => {
        const value = compileOperand(item.valueOperand)
        if (!value) return
        lines.push((hasCase ? pad + '} else ' : pad) + 'if (' + match + ' == ' + value + ') {')
        ;(item.actions || []).forEach(action => {
          const code = generateBlock(action, indent + 1)
          if (code) lines.push(code)
        })
        hasCase = true
      })
      if ((block.defaultActions || []).length) {
        lines.push(hasCase ? pad + '} else {' : pad + 'if (true) {')
        block.defaultActions.forEach(action => {
          const code = generateBlock(action, indent + 1)
          if (code) lines.push(code)
        })
        hasCase = true
      }
      if (!hasCase) return ''
      lines.push(pad + '}')
      return lines.join('\n')
    }

    case 'func-call': {
      const functionCode = block.functionCode || ''
      if (!functionCode) return ''
      const call = functionCode + '(' + (block.args || []).map(compileOperand).join(', ') + ')'
      const target = compileOperand(block.targetOperand)
      return target ? pad + target + ' = ' + call : pad + call
    }

    case 'foreach': {
      const list = compileOperand(block.listOperand)
      if (!block.itemVar || !list) return ''
      const lines = [pad + 'for (' + block.itemVar + ' : ' + list + ') {']
      ;(block.actions || []).forEach(action => {
        const code = generateBlock(action, indent + 1)
        if (code) lines.push(code)
      })
      lines.push(pad + '}')
      return lines.join('\n')
    }

    case 'ternary': {
      const target = compileOperand(block.targetOperand)
      const truthy = compileOperand(block.trueOperand)
      const falsy = compileOperand(block.falseOperand)
      if (!target || !compileOperand(block.leftOperand) || !truthy || !falsy) return ''
      return pad + target + ' = ' + compileCondition(block.leftOperand, block.operator, block.rightOperand) + ' ? ' + truthy + ' : ' + falsy
    }

    case 'in-check': {
      const target = compileOperand(block.targetOperand)
      const check = compileOperand(block.checkOperand)
      if (!target || !check) return ''
      const values = (block.inOperands || []).map(compileOperand).filter(Boolean).join(', ')
      const truthy = compileOperand(block.trueOperand)
      const falsy = compileOperand(block.falseOperand)
      if (!truthy || !falsy) return ''
      return pad + target + ' = ' + check + ' in [' + values + '] ? ' + truthy + ' : ' + falsy
    }

    case 'template-str': {
      const target = compileOperand(block.targetOperand)
      if (!target || !(block.parts || []).length) return ''
      const content = block.parts.map(part => part.type === 'expr'
        ? '${' + compileOperand(part.operand) + '}'
        : String(part.operand && part.operand.value != null ? part.operand.value : '')
      ).join('')
      return pad + target + ' = ' + quoteString(content)
    }

    case 'rule-call': {
      if (!block.ruleCode) return ''
      const outputMappingEnabled = isRuleOutputMappingEnabled(block)
      const call = outputMappingEnabled && block.outputField
        ? 'executeRuleField(' + quoteString(block.ruleCode) + ', ' + quoteString(block.outputField) + ')'
        : 'executeRule(' + quoteString(block.ruleCode) + ')'
      const target = outputMappingEnabled ? compileOperand(block.targetOperand) : ''
      return target ? pad + target + ' = ' + call : pad + call
    }

    default:
      return ''
  }
}

export function generateScript(actionData) {
  if (!Array.isArray(actionData) || !actionData.length) return ''
  return actionData.map(block => generateBlock(block, 0)).filter(Boolean).join('\n')
}

export function actionDataToBlocks(actionData) {
  if (!Array.isArray(actionData) || !actionData.length) return []
  return actionData.map(normalizeActionBlock)
}

export function normalizeGraphActionData(modelData) {
  if (!modelData || typeof modelData !== 'object') return modelData
  ;(modelData.nodes || []).forEach(node => {
    node.actionData = actionDataToBlocks(node.actionData)
  })
  const logicflow = modelData.logicflow
  ;(logicflow && logicflow.nodes || []).forEach(node => {
    if (!node.properties) node.properties = {}
    node.properties.actionData = actionDataToBlocks(node.properties.actionData)
  })
  return modelData
}

export function blocksToActionData(blocks) {
  if (!Array.isArray(blocks) || !blocks.length) return []
  return JSON.parse(JSON.stringify(blocks))
}

function normalizeActionBlock(source) {
  const block = JSON.parse(JSON.stringify(source || {}))
  const targetOperand = block.targetOperand || legacyReferenceOperand(
    block.target,
    block._targetVarId != null ? block._targetVarId : block._varId,
    block._targetRefType || block._refType,
    block.targetVarType
  )
  if (block.target !== undefined || block.targetOperand !== undefined) block.targetOperand = targetOperand

  if (block.type === 'assign') {
    block.valueOperand = block.valueOperand || legacyValueOperand(block.value, block.valueType)
  } else if (block.type === 'if-block') {
    block.branches = (block.branches || []).map(branch => ({
      ...branch,
      leftOperand: branch.leftOperand || legacyReferenceOperand(branch.condVar, branch._varId, branch._refType, branch.condVarType),
      operator: branch.operator || branch.condOp || '==',
      rightOperand: branch.rightOperand || legacyLiteralOperand(branch.condValue, branch.condVarType),
      actions: (branch.actions || []).map(normalizeActionBlock)
    }))
    block.branches.forEach(branch => removeKeys(branch, ['condVar', 'condOp', 'condValue', 'condVarType', '_varId', '_refType']))
  } else if (block.type === 'switch-block') {
    block.matchOperand = block.matchOperand || legacyReferenceOperand(block.matchVar)
    block.cases = (block.cases || []).map(item => ({
      ...item,
      valueOperand: item.valueOperand || legacyLiteralOperand(item.value),
      actions: (item.actions || []).map(normalizeActionBlock)
    }))
    block.cases.forEach(item => removeKeys(item, ['value']))
    block.defaultActions = (block.defaultActions || []).map(normalizeActionBlock)
  } else if (block.type === 'func-call') {
    block.functionCode = block.functionCode || block.funcName || ''
    const refs = block._argRefs || []
    block.args = (block.args || []).map((arg, index) => {
      if (arg && arg.kind) return arg
      const ref = refs[index] || {}
      return ref._varId != null || ref.varId != null || ref._refType || ref.refType
        ? legacyReferenceOperand(arg, ref._varId != null ? ref._varId : ref.varId, ref._refType || ref.refType, ref.varType)
        : legacyLiteralOperand(arg)
    })
  } else if (block.type === 'foreach') {
    block.listOperand = block.listOperand || legacyReferenceOperand(block.listExpr)
    block.actions = (block.actions || []).map(normalizeActionBlock)
  } else if (block.type === 'ternary') {
    block.leftOperand = block.leftOperand || legacyReferenceOperand(block.condVar, block._condVarId, block._condRefType, block.condVarType)
    block.operator = block.operator || block.condOp || '=='
    block.rightOperand = block.rightOperand || legacyLiteralOperand(block.condValue, block.condVarType)
    block.trueOperand = block.trueOperand || legacyValueOperand(block.trueValue)
    block.falseOperand = block.falseOperand || legacyValueOperand(block.falseValue)
  } else if (block.type === 'in-check') {
    block.checkOperand = block.checkOperand || legacyReferenceOperand(block.checkVar)
    block.inOperands = block.inOperands || (block.inValues || []).map(value => legacyLiteralOperand(value))
    block.trueOperand = block.trueOperand || legacyValueOperand(block.trueValue)
    block.falseOperand = block.falseOperand || legacyValueOperand(block.falseValue)
  } else if (block.type === 'template-str') {
    block.parts = (block.parts || []).map(part => part.operand
      ? part
      : { type: part.type, operand: part.type === 'expr' ? legacyReferenceOperand(part.content) : createLiteralOperand(part.content || '') })
  } else if (block.type === 'rule-call' && typeof block.enableOutputMapping !== 'boolean') {
    block.enableOutputMapping = !!(block.outputField || block.targetOperand)
  }

  removeKeys(block, [
    'target', 'value', 'valueType', 'funcName', '_argRefs', 'matchVar', 'listExpr',
    'condVar', 'condOp', 'condValue', 'condVarType', 'trueValue', 'falseValue',
    'checkVar', 'inValues', '_targetVarId', '_targetRefType', '_varId', '_refType',
    '_condVarId', '_condRefType', 'targetVarType'
  ])
  return block
}

function legacyReferenceOperand(value, refId, refType, valueType) {
  if (value == null || String(value).trim() === '') return null
  const code = String(value).trim()
  if (refId != null && refType) {
    return {
      kind: OPERAND_KINDS.REFERENCE,
      value: code,
      code,
      label: code,
      valueType: valueType || '',
      refId,
      refType,
      resolved: true
    }
  }
  return createPathOperand(code)
}

function legacyLiteralOperand(value, valueType) {
  if (value == null) return null
  const text = String(value).trim()
  const quoted = text.length >= 2 && ((text[0] === '"' && text[text.length - 1] === '"') || (text[0] === "'" && text[text.length - 1] === "'"))
  const normalizedValue = quoted ? text.slice(1, -1) : text
  const type = valueType || (text !== '' && !isNaN(text) ? 'NUMBER' : text === 'true' || text === 'false' ? 'BOOLEAN' : 'STRING')
  return createLiteralOperand(normalizedValue, type)
}

function legacyValueOperand(value, valueType) {
  if (value == null || String(value).trim() === '') return null
  const text = String(value).trim()
  const isQuoted = text.length >= 2 && ((text[0] === '"' && text[text.length - 1] === '"') || (text[0] === "'" && text[text.length - 1] === "'"))
  if (valueType || isQuoted || !isNaN(text) || text === 'true' || text === 'false' || text === 'null') {
    return legacyLiteralOperand(text, valueType || (text === 'null' ? 'NULL' : undefined))
  }
  return createPathOperand(text)
}

function removeKeys(target, keys) {
  keys.forEach(key => { if (target[key] !== undefined) delete target[key] })
}

function newAssignment() {
  return { type: 'assign', targetOperand: null, valueOperand: null }
}

export function newBlock(type) {
  switch (type) {
    case 'assign':
      return newAssignment()
    case 'if-block':
      return { type: 'if-block', branches: [{ type: 'if', leftOperand: null, operator: '==', rightOperand: null, actions: [newAssignment()] }] }
    case 'switch-block':
      return { type: 'switch-block', matchOperand: null, cases: [{ valueOperand: null, actions: [newAssignment()] }], defaultActions: [newAssignment()] }
    case 'func-call':
      return { type: 'func-call', targetOperand: null, functionId: null, functionCode: '', args: [null] }
    case 'foreach':
      return { type: 'foreach', itemVar: 'item', listOperand: null, actions: [newAssignment()] }
    case 'ternary':
      return { type: 'ternary', targetOperand: null, leftOperand: null, operator: '==', rightOperand: null, trueOperand: null, falseOperand: null }
    case 'in-check':
      return { type: 'in-check', targetOperand: null, checkOperand: null, inOperands: [], trueOperand: null, falseOperand: null }
    case 'template-str':
      return { type: 'template-str', targetOperand: null, parts: [{ type: 'text', operand: null }] }
    case 'rule-call':
      return { type: 'rule-call', targetOperand: null, ruleId: null, ruleCode: '', ruleName: '', modelType: '', enableOutputMapping: false, outputField: '' }
    default:
      return newAssignment()
  }
}

export const BLOCK_TYPES = [
  { type: 'assign', label: '赋值', icon: 'el-icon-edit', color: '#1890ff' },
  { type: 'if-block', label: '条件分支', icon: 'el-icon-s-operation', color: '#fa8c16' },
  { type: 'switch-block', label: 'Switch 匹配', icon: 'el-icon-menu', color: '#722ed1' },
  { type: 'func-call', label: '函数调用', icon: 'el-icon-phone-outline', color: '#13c2c2' },
  { type: 'foreach', label: 'ForEach 循环', icon: 'el-icon-refresh', color: '#52c41a' },
  { type: 'ternary', label: '三元表达式', icon: 'el-icon-question', color: '#eb2f96' },
  { type: 'in-check', label: 'IN 判断', icon: 'el-icon-finished', color: '#2f54eb' },
  { type: 'template-str', label: '动态字符串', icon: 'el-icon-document', color: '#8c8c8c' },
  { type: 'rule-call', label: '执行规则', icon: 'el-icon-position', color: '#096dd9' }
]
