import { applyEdits, format } from 'jsonc-parser'
import { parseMetadataResponse } from './metadataResponse'

export function formatSnapshotJson(value) {
  if (typeof value !== 'string') return JSON.stringify(value ?? {}, null, 2)
  try {
    JSON.parse(value)
    // Reindent the source tokens so large IDs and decimal literals remain exact.
    return applyEdits(value, format(value, undefined, { tabSize: 2, insertSpaces: true, eol: '\n' }))
  } catch {
    return value
  }
}

// Legacy history APIs store the business configuration inside version-specific envelopes.
export function historicalSnapshot(type, version, modelType) {
  if (!version) return ''
  if (type === 'FUNCTION') return version.functionJson ?? ''
  if (type === 'RULE') {
    const rule = { modelType: version.modelType || modelType, modelJson: version.modelJson }
    if (version.openApiConfigJson) rule.openApiConfigJson = version.openApiConfigJson
    return rule
  }
  if (type === 'EXPERIMENT') {
    try {
      const experiment = parseMetadataResponse(version.experimentJson)
      const groups = parseMetadataResponse(version.groupsJson || '[]')
      if (!experiment || typeof experiment !== 'object' || Array.isArray(experiment) || !Array.isArray(groups)) return ''
      return { ...experiment, groups }
    } catch { return '' }
  }
  if (type === 'MODEL') {
    const { modelFormat, modelFileName, modelFileSize, modelDigest, modelConfig, inputSchemaJson,
      outputSchemaJson, validationReportJson, runtimeConstraintsJson, sampleStatus, status } = version
    return Object.fromEntries(Object.entries({ modelFormat, modelFileName, modelFileSize, modelDigest,
      modelConfig, inputSchemaJson, outputSchemaJson, validationReportJson, runtimeConstraintsJson, sampleStatus, status })
      .filter(([, value]) => value != null))
  }
  return version
}
