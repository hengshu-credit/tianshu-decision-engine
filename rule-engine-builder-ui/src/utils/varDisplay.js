/**
 * 变量引用统一格式化工具
 *
 * 格式约定（类 Linux 命令说明风格）：
 *   普通变量/常量: 变量类型 / 字段名称(字段编码)
 *   对象字段:     变量类型 / 对象标签 / 字段名称(字段编码)
 *
 * 示例：
 *   普通变量 → "数值 / 税率(amount)"
 *   常量     → "数值 / 最大重试次数(MAX_RETRY_COUNT)"
 *   对象字段 → "数值 / TaxRequest / 申请金额(amount)"
 */

import { varTypeLabel as varTypeLabelFn } from '@/constants/varTypes'

/**
 * 将 varMap 中的变量项格式化为展示文本。
 * @param {Object} item - 由 buildVarOptions / buildVarOptions2 构建的变量选项项
 *   必需字段: varLabel, varCode, sourceType
 *   可选字段: varType, sourceLabel, objectLabel, varObj
 * @returns {string} 格式化后的展示文本
 */
export function formatVarDisplay(item) {
  if (!item) return ''

  const typePart = varTypeLabelFn(item.varType) || item.varType || ''
  const labelPart = item.varLabel || ''
  const codePart = item.varCode || ''

  if (item.sourceType === 'dataObject') {
    // 对象字段：类型 / 对象标签 / 字段名(编码)
    const objPart = item.objectLabel || item.sourceLabel || ''
    return `${typePart} / ${objPart} / ${labelPart}(${codePart})`
  } else {
    // 普通变量 / 常量：类型 / 字段名(编码)
    return `${typePart} / ${labelPart}(${codePart})`
  }
}

/**
 * 快速构建变量展示文本（直接从 API 返回的原始对象构建）。
 * @param {Object} obj - 原始变量对象（来自 listVariables / getVariableTree 等）
 * @param {string} sourceType - 'variable' | 'constant' | 'dataObject'
 * @param {string} objectLabel - 仅 dataObject 时需要，对象标签
 */
export function buildVarDisplayText(obj, sourceType, objectLabel) {
  if (!obj) return ''
  const varLabel = obj.varLabel || obj.varCode || ''
  const varCode = obj.scriptName || obj.varCode || ''
  const varType = obj.varType || ''
  const typePart = varTypeLabelFn(varType) || varType || ''
  if (sourceType === 'dataObject') {
    return `${typePart} / ${objectLabel || ''} / ${varLabel}(${varCode})`
  }
  return `${typePart} / ${varLabel}(${varCode})`
}