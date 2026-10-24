import { hasPermission } from './permissionState'
import { operationPagePath, recordConsoleActivity } from './operationActivity'

const modules = { SYSTEM_SETTING: 'system', VARIABLE: 'field', CONSTANT: 'field', DATA_OBJECT_ROOT: 'field', DATA_OBJECT: 'field', DATA_FIELD: 'field', DATA_OBJECT_FIELD: 'field', FIELD_VALIDATION: 'field', MODEL: 'model', MODEL_OUTPUT: 'model', FUNCTION: 'function', EXTERNAL_API: 'datasource', EXTERNAL_DATASOURCE: 'datasource', DATABASE: 'database', SFTP: 'database', RULE: 'rule', RULE_PROJECT_BINDING: 'rule', LIST: 'list', LIST_LIBRARY: 'list', LIST_RECORD_BATCH: 'list', PROJECT: 'project', BILLING_CONFIG: 'billing', EXPERIMENT: 'experiment' }
const types = { standalone: 'VARIABLE', constant: 'CONSTANT', object: 'DATA_OBJECT', model: 'MODEL', external: 'EXTERNAL_API', rule: 'RULE', list: 'LIST_LIBRARY', database: 'DATABASE', sftp: 'SFTP', function: 'FUNCTION' }

export function resourceViewPermission(type) { return modules[type] ? `${modules[type]}:view` : '' }
export function resourceActionPermission(type, action) { return modules[type] ? `${modules[type]}:${action}` : '' }

function identity(item) {
  const rawType = item._refType || item.refType || item._ref?.refType || (item._function ? 'FUNCTION' : types[item._ref?.category])
  const type = ({ EXTERNAL: 'EXTERNAL_API', OBJECT: 'DATA_OBJECT' })[rawType] || rawType
  const id = item._varId ?? item.refId ?? item.id ?? item.varObj?.id ?? item.function?.functionId ?? item.function?.id
  return { type, id }
}

export function canViewPickerItem(item) {
  const { type, id } = identity(item)
  // 无持久化 ID 的内置运算和当前编辑对象的局部协议字段不属于资源目录。
  if (type === 'API_CONTEXT' || type === 'FUNCTION' && id == null) return true
  return hasPermission(resourceViewPermission(type))
}

export function recordPickerActivity(action, item, pagePath) {
  const { type, id } = identity(item)
  if (id == null || !modules[type] || !canViewPickerItem(item)) return
  const number = item.version ?? item.resource?.currentVersion ?? item.function?.version
  const version = number != null ? `v${number}` : item.versionId != null ? `ID ${item.versionId}` : undefined
  recordConsoleActivity({ action, pagePath: typeof pagePath === 'string' ? pagePath : operationPagePath(pagePath), resourceType: type, resourceId: String(id), name: item.varLabelText || item.varLabel || item.varCode || item.function?.funcName, ...(version ? { version } : {}) })
}
