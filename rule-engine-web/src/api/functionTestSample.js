import { getFunctionById, updateFunction } from './function'
import { editableResource } from './governance'
import { hasPermission } from '@/security/permissionState'
import { functionSampleText, withFunctionSample } from '@/utils/functionTestSample'

export async function readFunctionSample(id, editable) {
  const { data } = await getFunctionById(id)
  const writable = editable && hasPermission('function:edit', data) && (!['JAVA', 'BEAN'].includes(data.implType) || hasPermission('function:native'))
  const config = writable ? await editableResource('FUNCTION', id, data) : data
  return { config, text: functionSampleText(config), readonly: !writable }
}

export async function saveFunctionSample(id, original, text) {
  const latest = await readFunctionSample(id, true)
  if (latest.readonly) throw new Error('当前函数样例为只读')
  if (latest.text !== original) throw new Error('函数样例已变更，请重新打开后修改')
  const updated = withFunctionSample(latest.config, text)
  await updateFunction(updated)
  return functionSampleText(updated)
}
