const RECOVERY_PREFIX = 'tianshu:rule-designer-recovery'

function normalizeIdentity(identity = {}) {
  return {
    definitionId: String(identity.definitionId || ''),
    revisionId: String(identity.revisionId || ''),
  }
}

function recoveryKey(identity) {
  const normalized = normalizeIdentity(identity)
  if (!normalized.definitionId || !normalized.revisionId) return ''
  return `${RECOVERY_PREFIX}:${normalized.definitionId}:${normalized.revisionId}`
}

function stableValue(value) {
  if (Array.isArray(value)) return value.map(stableValue)
  if (!value || typeof value !== 'object') return value
  return Object.keys(value)
    .sort()
    .reduce((result, key) => {
      result[key] = stableValue(value[key])
      return result
    }, {})
}

function canonicalDraft(modelJson) {
  const source = String(modelJson ?? '')
  try {
    return JSON.stringify(stableValue(JSON.parse(source)))
  } catch {
    return source
  }
}

export function createDraftFingerprint(modelJson) {
  const source = canonicalDraft(modelJson)
  let hash = 0x811c9dc5
  for (let index = 0; index < source.length; index += 1) {
    hash ^= source.charCodeAt(index)
    hash = Math.imul(hash, 0x01000193)
  }
  return `${source.length}:${(hash >>> 0).toString(16).padStart(8, '0')}`
}

export function saveDraftRecovery(storage, recovery) {
  if (!storage || typeof storage.setItem !== 'function') return
  const key = recoveryKey(recovery)
  if (!key) return
  const identity = normalizeIdentity(recovery)
  const modelJson = String(recovery.modelJson ?? '')
  const payload = {
    ...identity,
    lockVersion: Number(recovery.lockVersion || 0),
    modelJson,
    fingerprint: createDraftFingerprint(modelJson),
    savedAt: recovery.savedAt || new Date().toISOString(),
  }
  try {
    storage.setItem(key, JSON.stringify(payload))
  } catch {
    // sessionStorage 不可用或容量不足时，不阻断用户继续编辑。
  }
}

export function readDraftRecovery(storage, identity) {
  if (!storage || typeof storage.getItem !== 'function') return null
  const key = recoveryKey(identity)
  if (!key) return null
  try {
    const payload = JSON.parse(storage.getItem(key) || 'null')
    const normalized = normalizeIdentity(identity)
    if (
      !payload ||
      payload.definitionId !== normalized.definitionId ||
      payload.revisionId !== normalized.revisionId ||
      Number(payload.lockVersion || 0) !== Number(identity.lockVersion || 0) ||
      typeof payload.modelJson !== 'string' ||
      payload.fingerprint !== createDraftFingerprint(payload.modelJson)
    ) {
      storage.removeItem(key)
      return null
    }
    return payload
  } catch {
    storage.removeItem(key)
    return null
  }
}

export function clearDraftRecovery(storage, identity) {
  const key = recoveryKey(identity)
  if (!key || !storage || typeof storage.removeItem !== 'function') return
  try {
    storage.removeItem(key)
  } catch {
    // 清理失败不影响服务端草稿已经保存的事实。
  }
}
