import { compactVersion } from './resourceVersion'

const typeOf = item => item?._refType || item?.refType || item?._ref?.refType
const idOf = item => item?._varId ?? item?.refId ?? item?.id
const codeOf = item => item?.varCode || item?.refCode || item?.code || ''
const ownerOf = (item, kind) => item?.[`${kind}Id`] ?? item?._ref?.[`${kind}Id`] ?? item?.varObj?.[`${kind}Id`]
const sameId = (left, right) => left != null && right != null && String(left) === String(right)
const within = (path, root) => path === root || path.startsWith(root + '.') || path.startsWith(root + '[')

function tokens(path) {
  const result = []
  let offset = 0
  while (offset < path.length) {
    const part = /^(?:\.([\p{L}\p{N}_-]+)|\[(\d+)\]|\[("(?:[^"\\]|\\.)*")\])/u.exec(path.slice(offset))
    if (!part) return null
    let key
    try { key = part[1] ?? (part[2] == null ? JSON.parse(part[3]) : Number(part[2])) } catch { return null }
    offset += part[0].length
    result.push({ key, end: offset })
  }
  return result
}

function versionLabel(value, option) {
  if (value.protocolPath || value.refType === 'API_CONTEXT') return ''
  if (value.refType === 'CONSTANT' && !value.versionMode) return ''
  if (value.versionMode === 'FIXED') return compactVersion({ ...value, version: option?.version ?? value.version, displayVersion: option?.displayVersion ?? value.displayVersion })
  const number = option?.displayVersion ?? option?.version ?? option?._ref?.modelVersion ?? option?._ref?.objectVersion ?? option?.varObj?.version ?? value.displayVersion ?? value.version
  return value.versionMode ? compactVersion({ ...value, version: number, displayVersion: number }) : number != null ? `v${number}` : value.refId != null ? '最新' : ''
}

/** 目录仅用于展示；只在已选资源和已选版本内部寻找最长已知路径，绝不重新绑定 ID。 */
export function matchPathContent(value, options = []) {
  if (!value || value.kind !== 'PATH' || value.refId == null) return null
  const compatible = item => value.versionMode === 'FIXED'
    ? item.versionMode === 'FIXED' && sameId(item.versionId, value.versionId) && (item.versionSource || '') === (value.versionSource || '')
    : item.versionMode !== 'FIXED'
  const directory = options.filter(item => !item.disabled && compatible(item))
  const source = directory.find(item => sameId(idOf(item), value.refId) && typeOf(item) === value.refType
    && (!['EXTERNAL_API', 'API_CONTEXT'].includes(value.refType) || (item.relativePath || item._ref?.relativePath || '') === (value.anchorRelativePath || '')))
  const full = value.code || value.value || ''
  const anchor = value.anchorPath || (value.relativePath && full.endsWith(value.relativePath) ? full.slice(0, -value.relativePath.length) : full)
  const root = codeOf(source) || anchor
  const suffix = within(full, anchor) ? full.slice(anchor.length) : ''
  const path = root + suffix
  const parts = tokens(suffix)
  const base = { label: source?.varLabelText || source?.varLabel || value.label || root, code: root, suffix, version: versionLabel(value, source) }
  if (!parts) return base
  const sourceKey = `${value.refType}:${value.refId}:${value.versionId || ''}:${value.versionSource || ''}:${anchor}`
  // 固定版本的展示快照不依赖当前目录；目录加载后仍以同版本目录为准。
  const saved = value.pathDisplay
  if (value.versionMode === 'FIXED' && saved?.sourceKey === sourceKey && within(path, saved.matchedPath || saved.code)) {
    Object.assign(base, saved, { suffix: path.slice((saved.matchedPath || saved.code).length) })
  }
  const scoped = item => {
    const type = typeOf(item)
    if (value.refType === 'DATA_OBJECT_ROOT') return type === 'DATA_OBJECT' && sameId(ownerOf(item, 'object'), value.refId)
    if (value.refType === 'DATA_OBJECT') return type === 'DATA_OBJECT' && sameId(ownerOf(item, 'object'), ownerOf(source, 'object'))
    if (value.refType === 'MODEL') return type === 'MODEL_OUTPUT' && sameId(ownerOf(item, 'model'), value.refId)
    if (value.refType === 'MODEL_OUTPUT') return type === 'MODEL_OUTPUT' && sameId(ownerOf(item, 'model'), ownerOf(source, 'model'))
    if (value.refType === 'API_CONTEXT') return type === 'API_CONTEXT'
    return type === value.refType && sameId(idOf(item), value.refId)
  }
  const matches = directory.flatMap(item => {
    const code = codeOf(item)
    if (!scoped(item) || code === root || !within(code, root)) return []
    const keys = tokens(code.slice(root.length))
    if (!keys?.length || keys.length > parts.length || keys.some((part, index) => part.key !== parts[index].key)) return []
    return [{ item, code, size: keys.length, suffix: suffix.slice(parts[keys.length - 1].end) }]
  }).sort((a, b) => b.size - a.size)
  for (const match of matches) {
    const peers = matches.filter(item => item.size === match.size)
    if (new Set(peers.map(({ item, code }) => `${typeOf(item)}:${idOf(item)}:${code}`)).size !== 1) continue
    return { label: match.item.varLabelText || match.item.varLabel || match.item.refLabel?.label || match.code,
      code: match.code, suffix: match.suffix, matchedPath: path.slice(0, path.length - match.suffix.length), version: versionLabel(value, match.item), sourceKey }
  }
  return base
}
