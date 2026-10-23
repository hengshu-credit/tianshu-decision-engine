import { findNodeAtLocation, parseTree } from 'jsonc-parser'
import { parseExactJson, stringifyExactJson } from './exactJson'

// 保留执行结果的原始数字文本；元数据仍按普通类型供页面交互使用。
export function parseExperimentResponse(text) {
  if (typeof text !== 'string') return text
  let response
  try { response = JSON.parse(text) } catch { throw new SyntaxError('实验执行响应不是有效 JSON') }
  const root = parseTree(text)
  const raw = parts => { const node = findNodeAtLocation(root, parts); return node ? text.slice(node.offset, node.offset + node.length) : undefined }
  const data = response?.data
  if (data && typeof data === 'object') {
    Object.defineProperty(data, '__rawExperimentJson', { value: raw(['data']), enumerable: false })
    const keepResult = (group, parts) => {
      const json = raw([...parts, 'result'])
      if (group && json !== undefined) Object.defineProperty(group, 'resultJson', { value: json, enumerable: false })
    }
    keepResult(data.productionGroup, ['data', 'productionGroup'])
    if (Array.isArray(data.testGroups)) data.testGroups.forEach((group, index) => keepResult(group, ['data', 'testGroups', index]))
  }
  return response
}

export function formatExperimentJson(value) {
  return value?.__rawExperimentJson ? stringifyExactJson(parseExactJson(value.__rawExperimentJson), 2) : JSON.stringify(value, null, 2)
}
