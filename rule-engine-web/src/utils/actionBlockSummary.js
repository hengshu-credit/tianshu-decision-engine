import { operandKindMeta } from './operand'
import { previewText } from './resourceVersionPreview'

// 摘要也用于未填完的动作，直接读表单字段即可，无需编译表达式。
function operandName(operand, fallback) {
  if (!operand) return fallback
  if (operand.kind === 'LITERAL') return JSON.stringify(operand.value) ?? fallback
  return operand.label || operand.code || operand.functionCode || operandKindMeta(operand).label || fallback
}

function resourceName(resources, id, fields, fallback) {
  if (id == null || id === '') return fallback
  const resource = resources.find(item => String(item.id) === String(id))
  return fields.map(key => resource?.[key]).find(Boolean) || fallback
}

export function actionBlockSummary(block, { listOptions, apiOptions, functions, rules }) {
  const target = operandName(block.targetOperand, '待选字段')
  switch (block.type) {
    case 'assign':
      return target + ' = ' + operandName(block.valueOperand, '待配置值')
    case 'list-write': {
      const lists = (block.listIds || []).map(id => resourceName(listOptions, id, ['listName', 'listCode'], '名单 #' + id))
      const parts = [lists.join('、') || '待选名单', previewText('operation', block.operation)]
      if (block.operation !== 'REMOVE') {
        const expiry = block.expiryMode === 'DURATION'
          ? (block.duration ?? '待填') + ' ' + previewText('durationUnit', block.durationUnit)
          : block.expireTime && block.expiryMode === 'UNTIL' ? block.expireTime : previewText('expiryMode', block.expiryMode)
        parts.push(expiry)
      }
      return parts.concat((block.entries?.length || 0) + ' 个内容字段').join(' · ')
    }
    case 'api-call':
      return resourceName(apiOptions, block.apiId, ['apiName', 'name', 'apiCode', 'code'], block.apiId ? 'API #' + block.apiId : '待选 API')
    case 'func-call':
      return resourceName(functions, block.functionId, ['functionLabel', 'funcName', 'functionName', 'funcCode'], block.functionCode || '待选函数')
        + ' · ' + (block.args?.length || 0) + ' 个参数'
    case 'rule-call':
      return resourceName(rules, block.ruleId, ['ruleName', 'ruleCode'], block.ruleName || block.ruleCode || '待选规则')
    case 'if-block': {
      const first = block.branches?.[0]
      return [first?.leftOperand && operandName(first.leftOperand), (block.branches?.length || 0) + ' 个分支'].filter(Boolean).join(' · ')
    }
    case 'switch-block':
      return operandName(block.matchOperand, '待选匹配值') + ' · ' + (block.cases?.length || 0) + ' 个 Case'
    case 'foreach':
      return (block.itemVar || '待填循环变量') + ' 遍历 ' + operandName(block.listOperand, '待选列表')
    case 'ternary':
      return target + ' = ' + operandName(block.trueOperand, '待配真值') + ' / ' + operandName(block.falseOperand, '待配假值')
    case 'in-check':
      return operandName(block.checkOperand, '待选检测值') + ' · ' + (block.inOperands?.length || 0) + ' 个候选值'
    case 'template-str':
      return target + ' · ' + (block.parts?.length || 0) + ' 个片段'
    default:
      return ''
  }
}
