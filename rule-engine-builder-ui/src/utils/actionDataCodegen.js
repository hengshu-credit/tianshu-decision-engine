import { compileConditionExpression } from '@/constants/conditionOperators'
import { compileOperand, OPERAND_KINDS } from '@/utils/operand'

function quoteString(value) {
  return '"' + String(value == null ? '' : value).replace(/\\/g, '\\\\').replace(/"/g, '\\"') + '"'
}

function compileCondition(leftOperand, operator, rightOperand) {
  const left = compileOperand(leftOperand)
  if (!left) return 'true'
  const rightValue = rightOperand && rightOperand.kind === OPERAND_KINDS.LITERAL
    ? rightOperand.value
    : compileOperand(rightOperand)
  return compileConditionExpression(
    left,
    leftOperand.valueType,
    operator || '==',
    rightValue,
    rightOperand && rightOperand.kind === OPERAND_KINDS.LITERAL ? 'CONST' : 'VAR'
  )
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
      const call = block.outputField
        ? 'executeRuleField(' + quoteString(block.ruleCode) + ', ' + quoteString(block.outputField) + ')'
        : 'executeRule(' + quoteString(block.ruleCode) + ')'
      const target = compileOperand(block.targetOperand)
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
  return JSON.parse(JSON.stringify(actionData))
}

export function blocksToActionData(blocks) {
  if (!Array.isArray(blocks) || !blocks.length) return []
  return JSON.parse(JSON.stringify(blocks))
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
      return { type: 'rule-call', targetOperand: null, ruleId: null, ruleCode: '', ruleName: '', modelType: '', outputField: '' }
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
