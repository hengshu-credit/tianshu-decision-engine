import { getRuleTestSchema, getContent, getRuleRevision, saveDesignerDraft } from '@/api/definition'
import { getApiConfig, updateApiConfig } from '@/api/datasource'
import { editableResource } from '@/api/governance'
import { getModel, getModelVersionTestContext, saveTestParams } from '@/api/model'
import { getSavedSample, saveDefaultInput } from '@/api/testData'
import { apiDefaultInput } from '@/utils/testParamTemplate'
import { loadApiReferenceCatalog } from '@/utils/apiReferenceCatalog'
import { parseExactJson, stringifyExactJson, isExactNumber } from '@/utils/exactJson'

function jsonText(value) {
  return stringifyExactJson(typeof value === 'string' ? parseExactJson(value) : value, 2)
}
async function apiConfig(row, editable) {
  const { data } = await getApiConfig(row.ownerId)
  return editable ? editableResource('EXTERNAL_API', row.ownerId, data) : data
}
function namedSample(config, id) {
  const spec = parseExactJson(config.executionConfig)
  const sample = spec.samples?.find(item => String(item.id) === String(id))
  if (!sample) throw new Error('该具名样例已被移除，请刷新')
  return { spec, sample }
}
export async function readCatalogSample(row, editable) {
  if (row.type === 'API') {
    const config = await apiConfig(row, editable)
    if (row.source === 'API_NAMED') return { text: jsonText(namedSample(config,row.id).sample.params) }
    const value = config.testSampleParams || apiDefaultInput(config,(await loadApiReferenceCatalog(row.projectId || 0)).vars)
    return { text: jsonText(value) }
  }
  if (row.type === 'MODEL' && row.source !== 'MODEL_VERSION') {
    const { data } = await getModel(row.ownerId)
    const model = editable ? await editableResource('MODEL',row.ownerId,data) : data
    const config = typeof model.modelConfig === 'string' ? parseExactJson(model.modelConfig) : model.modelConfig
    if (config?.testParams) return { text: jsonText(config.testParams), version: data.currentVersion }
    const { data: context } = data.currentVersion > 0
      ? await getModelVersionTestContext(row.ownerId,data.currentVersion)
      : { data: { schema: (await getRuleTestSchema({ targetType: 'MODEL', targetId: row.ownerId })).data } }
    return { text: jsonText(context.schema.sampleParamsJson || context.schema.sampleParams), version: data.currentVersion }
  }
  if (row.source === 'DEFAULT') {
    const { data } = await getRuleTestSchema({ targetType: row.type, targetId: row.ownerId })
    return { text: jsonText(data.sampleParamsJson || data.sampleParams) }
  }
  if (row.source === 'REVISION' || row.source === 'DESIGN') {
    const { data } = row.source === 'REVISION' ? await getRuleRevision(row.ownerId,row.id) : await getContent(row.ownerId)
    const model = parseExactJson(data.modelJson)
    return { text: jsonText(model.testParams), model, revision: row.source === 'REVISION' ? data : null }
  }
  return { text: jsonText((await getSavedSample(row)).data.paramsJson), readonly: true }
}

export async function writeCatalogSample(row, original, text) {
  const params = parseExactJson(text)
  if (!params || typeof params !== 'object' || Array.isArray(params) || isExactNumber(params)) throw new Error('输入参数必须是 JSON 对象')
  if (row.type === 'API') {
    const config = await apiConfig(row,true)
    if (row.source === 'API_NAMED') {
      const { spec, sample } = namedSample(config,row.id)
      if (jsonText(sample.params) !== original.text) throw new Error('样例已变更，请重新打开后修改')
      sample.params = params
      config.executionConfig = stringifyExactJson(spec)
    } else {
      if (config.testSampleParams && jsonText(config.testSampleParams) !== original.text) throw new Error('默认样例已变更，请重新打开后修改')
      config.testSampleParams = text
    }
    await updateApiConfig(config)
    return '接口草稿已保存，审批后生效'
  }
  if (row.type === 'MODEL') {
    await saveTestParams(row.ownerId, text, original.version || undefined)
    return '模型草稿已保存，审批后生效'
  }
  if (row.source === 'DESIGN' || row.source === 'REVISION') {
    const revision = original.revision
    const modelJson = stringifyExactJson({ ...original.model, testParams: text })
    await saveDesignerDraft(row.ownerId, { modelJson, requestId: Array.from(crypto.getRandomValues(new Uint8Array(16)), byte => byte.toString(16).padStart(2,'0')).join(''),
      ...(revision ? { sourceType: 'REVISION', sourceId: row.id, revisionId: row.id, lockVersion: revision.lockVersion, saveMode: 'OVERWRITE' } : { saveMode: 'NEW' }) })
    return '评分卡设计草稿已保存'
  }
  await saveDefaultInput(row.type,row.ownerId,text)
  return '默认测试输入已保存'
}
