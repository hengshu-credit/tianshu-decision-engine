/**
 * 变量引用统一格式化工具
 *
 * 格式约定：
 *   变量/常量: 中文标签 + monospace编码
 *   对象字段: 中文标签 + monospace编码（包含对象路径）
 *
 * 示例：
 *   普通变量 → "税收金额 amount"
 *   常量     → "最大重试次数 MAX_RETRY_COUNT"
 *   对象字段 → "申请金额 TaxRequest.amount"
 */

/**
 * 格式化变量引用展示文本（返回字符串，供直接显示）。
 * @param {Object} item
 *   必需字段: varLabel, varCode
 *   可选字段: objectLabel（dataObject 时使用）
 * @returns {string}
 */
export function formatVarDisplay(item) {
  if (!item) return ''
  const label = item.varLabel || ''
  const code = item.varCode || ''
  if (item.objectLabel) {
    return code ? `${label} ${item.objectLabel}.${code}` : label
  }
  return code ? `${label} ${code}` : label
}

/**
 * 构建变量引用元数据（返回 { label, code } 对象，供组件模板分开渲染）。
 * @param {Object} v 变量对象
 * @returns {{ label: string, code: string }}
 */
export function makeRefLabel(v) {
  const label = v.varLabel || ''
  const code = v.scriptName || v.varCode || ''
  return { label, code }
}