import { parseTree } from 'jsonc-parser'
import { parseMetadataResponse } from './metadataResponse'

const LEGACY_REFERENCE_IDS = new Set([
  '_varId', '_targetVarId', '_condVarId', '_matchVarId', '_checkVarId', '_rightVarId',
  'leftVarId', 'rightVarId',
])
const LITERAL_VALUES = new Set(['value', 'values', 'constantValue', 'defaultValue', 'condValue', 'testParams', 'sampleParams'])

/** 只保护设计器协议中的引用ID；固定值、脚本文字、图节点id不属于资源身份。 */
export function parseDesignerModelJson(json) {
  const errors = []
  const root = parseTree(json, errors, { disallowComments: true, allowTrailingComma: false, allowEmptyContent: false })
  if (!root || errors.length) throw new SyntaxError('规则设计内容不是有效 JSON')
  const replacements = []
  const pending = [{ node: root, identity: false, scriptReferences: false }]
  while (pending.length) {
    const { node, identity, scriptReferences } = pending.pop()
    if (identity && node.type === 'number') {
      const token = json.slice(node.offset, node.offset + node.length)
      // 复用服务端Long标识边界，仅传一个已确认的引用token，不遍历业务对象。
      const id = parseMetadataResponse(`{"id":${token}}`).id
      if (typeof id === 'string') replacements.push({ start: node.offset, end: node.offset + node.length, value: JSON.stringify(id) })
    } else if (node.type === 'array') {
      for (const child of node.children || []) pending.push({ node: child, identity, scriptReferences })
    } else if (node.type === 'object') {
      const properties = node.children || []
      const field = key => properties.find(property => property.children[0].value === key)?.children[1]?.value
      const kind = field('kind')
      const type = field('type')
      if (kind === 'LITERAL') continue
      for (const property of properties) {
        const [key, value] = property.children
        if (LITERAL_VALUES.has(key.value)) continue
        const reference = LEGACY_REFERENCE_IDS.has(key.value)
          || key.value === 'refId' && ['REFERENCE', 'PATH'].includes(kind)
          || key.value === 'functionId' && (kind === 'FUNCTION' || type === 'func-call')
          || ['ruleId', 'versionBindingId'].includes(key.value) && type === 'rule-call'
          || key.value === 'listIds' && kind === 'LIST_QUERY'
          || key.value === 'varId' && (scriptReferences || !!field('refType'))
        pending.push({ node: value, identity: reference, scriptReferences: key.value === 'scriptVarRefs' })
      }
    }
  }
  replacements.sort((a, b) => a.start - b.start)
  const parts = []
  let cursor = 0
  for (const replacement of replacements) {
    parts.push(json.slice(cursor, replacement.start), replacement.value)
    cursor = replacement.end
  }
  parts.push(json.slice(cursor))
  return JSON.parse(parts.join(''))
}
