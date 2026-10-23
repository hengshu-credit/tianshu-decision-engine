import { listVariablesByProject } from '@/api/variable'
import { getVariableTree } from '@/api/dataObject'
import { listAllFunctionsByProject } from '@/api/function'
import { listAllModelsByProject } from '@/api/model'
import { hasPermission } from '@/security/permissionState'
import { buildPickerOptions, buildReferenceCatalog } from './referenceCatalog'

/** 各鉴权和请求编辑入口读取相同的当前范围目录；单项读取失败不会清空其他可用内容。 */
export async function loadApiReferenceCatalog(projectId) {
  const sources = [
    ['字段', 'field:view', () => listVariablesByProject(projectId)],
    ['数据对象', 'field:view', () => getVariableTree(projectId)],
    ['函数', 'function:view', () => listAllFunctionsByProject(projectId)],
    ['模型', 'model:view', () => listAllModelsByProject(projectId)],
  ]
  const results = await Promise.allSettled(sources.map(([, permission, read]) => hasPermission(permission) ? read() : Promise.resolve([])))
  const data = results.map(result => result.status === 'fulfilled' ? result.value?.data ?? result.value : [])
  const rows = value => Array.isArray(value) ? value : value?.records || []
  return {
    vars: buildPickerOptions(buildReferenceCatalog(rows(data[0]), rows(data[1]?.tree || data[1]), rows(data[3]))),
    functions: rows(data[2]),
    errors: results.flatMap((result, index) => result.status === 'rejected' ? [`${sources[index][0]}目录加载失败`] : []),
  }
}
