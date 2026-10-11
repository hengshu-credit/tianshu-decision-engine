export function revisionTestSource(revision) {
  if (!revision) return null
  if (revision.state === 'LEGACY' || revision.sourceType === 'LEGACY_CONTENT') return { sourceType: 'WORKSPACE' }
  const sourceId = String((revision.state === 'VERSION' ? revision.sourceId || revision.id : revision.id) || '')
  if (!/^[1-9]\d*$/.test(sourceId)) return null
  if (revision.state === 'VERSION') return { sourceType: 'VERSION', sourceId }
  return {
    sourceType: 'REVISION', sourceId,
    ...(revision.state === 'DRAFT' ? { lockVersion: revision.lockVersion } : {}),
  }
}

export function executionSourceKey(source) {
  return JSON.stringify([source?.sourceType || '', source?.sourceId == null ? '' : String(source.sourceId), source?.lockVersion ?? null,
    source?.baseSourceType || '', source?.baseSourceId == null ? '' : String(source.baseSourceId)])
}

export function previewTestSource(revision, definitionId) {
  if (!revision) return null
  const base = revisionTestSource(revision)
  if (!base) return null
  const baseSourceId = base.sourceType === 'WORKSPACE' ? String(definitionId || '') : base.sourceId
  if (!/^[1-9]\d*$/.test(baseSourceId)) return null
  return { sourceType: 'PREVIEW', baseSourceType: base.sourceType, baseSourceId }
}

export function requireExecutionIdentity(identity, definitionId, source) {
  if (!source) return
  if (!identity || String(identity.definitionId) !== String(definitionId)
    || identity.sourceType !== source.sourceType
    || (source.sourceId != null && String(identity.sourceId) !== String(source.sourceId))
    || (source.lockVersion != null && String(identity.lockVersion) !== String(source.lockVersion))
    || (source.baseSourceType != null && identity.baseSourceType !== source.baseSourceType)
    || (source.baseSourceId != null && String(identity.baseSourceId) !== String(source.baseSourceId))) {
    throw new Error('测试响应与本次选择的规则版本不一致，请重新加载后测试')
  }
}

export function executionSourceLabel(source, identity) {
  if (!source) return '当前设计内容预览'
  if (source.sourceType === 'LATEST') return '最新版（执行时选择当前生产版本）'
  if (source.sourceType === 'BUSINESS_VERSION') return `指定业务版本${identity?.versionNo != null ? ' v' + identity.versionNo : ''}（跟随该版本的覆盖发布）`
  if (source.sourceType === 'PREVIEW') return '当前画布预览（包含未保存修改，按当前依赖执行）'
  if (source.sourceType === 'WORKSPACE') return '旧版工作区测试（当前已保存内容）'
  const selectedLabel = typeof identity === 'string' ? identity : ''
  if (source.sourceType === 'VERSION') return `${selectedLabel || (identity?.versionNo != null ? `发布版本 v${identity.versionNo}` : '所选已发布版本快照')}（按冻结制品执行）`
  const revision = selectedLabel || (identity?.revisionNo != null ? `修订 ${identity.revisionNo}` : '所选修订')
  return source.lockVersion == null
    ? `${revision}（按冻结制品执行）`
    : `${selectedLabel ? '' : '草稿'}${revision}（按已保存配置和当前依赖执行）`
}
