export function versionFields(source) {
  if (!source?.versionMode) return {}
  return { versionMode: source.versionMode, versionId: source.versionMode === 'FIXED' ? source.versionId : null,
    version: source.version, versionOwnerId: source.versionOwnerId,
    ...(source.displayVersion != null ? { displayVersion: source.displayVersion } : {}),
    ...(source.versionMode === 'FIXED' && source.versionSource ? { versionSource: source.versionSource } : {}) }
}

export function compactVersion(source) {
  if (!source?.versionMode) return ''
  const display = source.displayVersion ?? source.version
  const record = source.version != null && String(display) !== String(source.version) ? `·记录${source.version}` : ''
  const number = display != null ? `v${display}${record}` : ''
  return source.versionMode === 'FIXED' ? `${source.versionSource === 'FUNCTION_HISTORY' ? '旧' : ''}${number}` : `最新${number ? '·' + number : ''}`
}

export function resourceVersionIdentity(item) {
  if (!item || item._operation || item.catalogMode === 'FROZEN' || item.function?.catalogMode === 'FROZEN') return null
  const type = item._function ? 'FUNCTION' : item._refType || item.refType || item._ref?.refType
  const refId = item._function ? item.function?.id ?? item.function?.functionId : item._varId ?? item.refId ?? item.id
  const module = { VARIABLE: 'variable', CONSTANT: 'variable', DATA_OBJECT: 'dataobject', DATA_OBJECT_ROOT: 'dataobject', MODEL: 'model', MODEL_OUTPUT: 'model', FUNCTION: 'function', EXTERNAL_API: 'datasource' }[type]
  const ownerId = item.versionOwnerId ?? ({ DATA_OBJECT: item._ref?.objectId ?? item.objectId, MODEL_OUTPUT: item._ref?.modelId ?? item.modelId }[type]) ?? refId
  if (!module || !refId || !ownerId) return null
  return { module, refType: type, refId: String(refId), ownerId: String(ownerId) }
}
