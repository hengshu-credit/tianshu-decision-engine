import { parseMetadataResponse } from './metadataResponse'
import { buildRulePreviewSections } from './ruleVersionDiff'
import { varTypeLabel } from '@/constants/varTypes'

const labels = {
  title: '标题', articleType: '文章分类', tags: '标签', visibility: '可见范围', summary: '摘要',
  category: '文章分类', occurredAt: '发生时间', eventIds: '关联事件 ID', summaryAuto: '自动摘要', changeSummary: '变更说明',
  apiConfigId: '接口 ID', dbDatasourceId: '数据库 ID', sftpDatasourceId: 'SFTP 数据源 ID', params: '查询参数', resultPath: '结果提取路径',
  requestOverrides: '请求参数覆盖', forceRefresh: '强制刷新', exceptionStrategy: '异常处理', fallbackValue: '异常默认值',
  maxRows: '最多返回行数', maxContentBytes: '内容上限（字节）', queryTimeoutSeconds: '查询超时（秒）',
  outputFormat: '输出格式', charset: '字符编码', fileAddress: '业务文件地址', uploadRemotePath: '上传目标路径',
  uploadTimeoutMs: '上传超时（毫秒）', cacheSeconds: '缓存时长（秒）', retryBackoffMs: '重试间隔（毫秒）', retryBackoffMultiplier: '重试间隔倍数',
  listIds: '名单库 ID', queryOperands: '待匹配内容', combinationMode: '组合匹配方式', matchMode: '名单匹配方式', itemTypes: '名单项类型', returnMode: '返回方式',
  implScript: '函数执行脚本', bindingMode: '绑定方式',
  projectCode: '项目编码', projectName: '项目名称', scope: '作用范围', status: '启用状态', description: '业务说明',
  varCode: '变量编码', varLabel: '变量名称', varType: '数据类型', varSource: '取值来源', sourceType: '数据来源',
  defaultValue: '默认值', valueRange: '取值范围', exampleValue: '示例值', recordResult: '记录取值结果', systemConstant: '系统常量',
  ruleCode: '规则编码', ruleName: '规则名称', modelType: '模型类型', modelCode: '模型编码', modelName: '模型名称',
  modelFormat: '模型格式', modelFileName: '模型文件', modelFileSize: '文件大小（字节）', modelDigest: '文件摘要', modelVersion: '模型版本',
  inputFieldCount: '输入字段数', outputFieldCount: '输出字段数', preloadOnStartup: '启动时预加载', executionTimeoutMs: '执行超时（毫秒）',
  apiCode: '接口编码', apiName: '接口名称', requestMethod: '请求方式', endpointUrl: '接口地址', contentType: '内容格式',
  requestMode: '请求模式', authMode: '鉴权模式', timeoutMs: '超时（毫秒）', retryCount: '重试次数', cacheEnabled: '启用缓存', cacheTtlSeconds: '缓存时长（秒）',
  datasourceCode: '数据源编码', datasourceName: '数据源名称', protocol: '协议', host: '主机地址', port: '端口', databaseName: '数据库名称',
  dbType: '数据库类型', jdbcUrl: '数据库地址', username: '连接账号', connectionMode: '连接方式', remotePath: '远程路径',
  objectCode: '对象编码', objectLabel: '对象名称', objectType: '对象类型', fieldCode: '字段编码', fieldName: '字段名称', fieldType: '字段类型',
  fieldLabel: '字段名称', required: '必填', nullable: '允许空值', refType: '引用类型', versionMode: '版本选择',
  experimentCode: '实验编码', experimentName: '实验名称', groupCode: '分组编码', groupName: '分组名称', groupType: '分组角色',
  trafficRatio: '流量比例', splitType: '分流方式', conditionValue: '命中值', conditionExpression: '命中条件', invokeExternalSource: '调用外部数据',
  funcCode: '函数编码', funcName: '函数名称', implType: '实现方式', implMethod: '调用方法', implClass: 'Java 类', implBeanName: '服务组件', returnType: '返回类型',
  libraryCode: '名单编码', libraryName: '名单名称', listType: '名单类型', matchType: '匹配方式', expireTime: '到期时间',
  billingCode: '计费编码', billingName: '计费名称', billingItemCode: '计费项', billingCondition: '计费条件', unitPrice: '单价', currency: '币种',
  validationName: '校验名称', validationType: '校验方式', validationValue: '校验值', errorMessage: '不通过提示',
  name: '名称', label: '显示名称', type: '类型', value: '取值', code: '编码', enabled: '启用', sortOrder: '排序',
  sourceConfig: '取值配置', sourceContent: '取值配置', options: '枚举选项', fields: '对象字段', groups: '实验分组',
  inputFields: '输入字段', inputFieldsJson: '输入字段', outputFields: '输出字段', outputFieldsJson: '输出字段',
  paramsJson: '函数参数', modelConfig: '模型配置', inputSchemaJson: '输入结构', outputSchemaJson: '输出结构',
  runtimeConstraintsJson: '运行要求', validationReportJson: '校验报告', trainingInfo: '训练信息', targetCategories: '目标类别',
  requestMapping: '请求字段映射', responseMapping: '响应字段映射', queryConfig: '查询参数', headerConfig: '请求头', executionConfig: '执行策略',
  conditionConfig: '分组条件', inputValidationConfig: '输入校验', bodyTemplate: '请求内容模板', requestScript: '请求处理脚本', responseScript: '响应处理脚本',
  script: '执行脚本', scriptContent: '函数脚本', scriptVarRefs: '脚本引用', sql: '查询 SQL', sqlTemplate: '查询 SQL', querySql: '查询 SQL',
  projectId: '所属项目 ID', varId: '变量 ID', modelId: '模型 ID', ruleId: '规则 ID', apiId: '接口 ID', datasourceId: '数据源 ID',
  refObjectId: '引用对象 ID', requestObjectId: '请求对象 ID', responseObjectId: '响应对象 ID', versionBindingId: '固定版本 ID',
}
const enums = {
  modelType: { TABLE: '决策表', TREE: '决策树', FLOW: '决策流', RULE_SET: '规则集', CROSS: '交叉表', SCORE: '评分卡', CROSS_ADV: '复杂交叉表', SCORE_ADV: '复杂评分卡', SCRIPT: 'QL 脚本' },
  varSource: { INPUT: '请求输入', CONSTANT: '常量', API: '外部接口', DB: '数据库查询', LIST: '名单匹配', SFTP: 'SFTP 文件读取', DERIVED: '衍生变量', COMPUTED: '计算得出', EXPRESSION: '表达式', SCRIPT: '脚本计算' },
  sourceType: { INPUT: '请求输入', API: '外部接口', DB: '数据库查询', LIST: '名单匹配', MANUAL: '手工配置' },
  scope: { GLOBAL: '全局共享', PROJECT: '项目内使用' },
  status: { 1: '启用', 0: '停用', ACTIVE: '启用', INACTIVE: '停用' },
  versionMode: { LATEST: '跟随最新版', FIXED: '固定版本' },
  groupType: { CHAMPION: '冠军组', CHALLENGER: '挑战组', TEST: '测试组' },
  implType: { QLEXPRESS: 'QL 表达式', JAVA: 'Java 方法', BEAN: '服务组件方法' },
  hitPolicy: { FIRST: '首条命中即停止', ALL: '执行所有命中规则' },
  executionMode: { SERIAL: '顺序执行', PARALLEL: '并行执行' },
  op: { AND: '同时满足全部条件', OR: '满足任一条件' },
  operator: { '>=': '大于或等于', '<=': '小于或等于', '>': '大于', '<': '小于', '==': '等于', '!=': '不等于' },
}
const technical = new Set(['id', 'createTime', 'updateTime', 'createBy', 'updateBy', 'lockVersion', 'compiledScript', 'publishedVersion', 'currentVersion', 'businessVersion', 'definitionId'])
const structured = new Set(Object.keys(labels).filter(key => /Json$|Config$|Mapping$|Schema$/.test(key)).concat(['sourceContent', 'modelJson']))

export function previewText(key, value) {
  if (value === undefined) return '未配置'
  if (value === null) return '空值'
  if (value === '') return '空文本'
  if (typeof value === 'boolean') return value ? '是' : '否'
  if (['varType', 'fieldType', 'returnType'].includes(key)) return varTypeLabel(value)
  return enums[key]?.[value] || String(value)
}

function decode(key, value) {
  if (structured.has(key) && typeof value === 'string' && /^\s*[[{]/.test(value)) {
    try { return parseMetadataResponse(value) } catch { return value }
  }
  return value
}

function node(key, value, title = labels[key] || key) {
  const decoded = decode(key, value)
  if (key === 'modelContent') return { title: '模型制品', text: value ? '已保存模型文件内容，可结合文件摘要与输入输出定义核对' : '未配置' }
  if (Array.isArray(decoded)) return { title, children: decoded.map((item, index) => node('', item, `第 ${index + 1} 项`)), text: decoded.length ? '' : '暂无配置' }
  if (decoded && typeof decoded === 'object') {
    const children = Object.entries(decoded).filter(([name]) => !technical.has(name)).map(([name, item]) => node(name, item))
    return { title, children, text: children.length ? '' : '暂无业务配置' }
  }
  return { title, text: previewText(key, decoded) }
}

function ruleNode(item) {
  return { title: item.title, text: item.subtitle, children: [
    ...(item.fields || []).map(field => ({ title: field.label, text: enums[field.key]?.[field.value] || field.text })),
    ...(item.children || []).map(ruleNode),
  ] }
}

function canonical(value) {
  if (Array.isArray(value)) return value.map(canonical)
  if (value && typeof value === 'object') return Object.fromEntries(Object.keys(value).sort().map(key => [key, canonical(value[key])]))
  return value
}

export function buildResourcePreview(snapshot, resourceType) {
  try {
    const value = parseMetadataResponse(snapshot)
    if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error()
    const sections = []
    const basic = []
    let warning = ''
    for (const [key, raw] of Object.entries(value)) {
      if (technical.has(key)) continue
      if (resourceType === 'RULE' && (key === 'content' || key === 'modelJson')) continue
      const decoded = decode(key, raw)
      const item = node(key, raw)
      const entry = { key, title: item.title, nodes: [item], signature: JSON.stringify(canonical(decoded)) }
      if (decoded && typeof decoded === 'object' || structured.has(key) || key === 'modelContent') sections.push(entry)
      else basic.push(entry)
    }
    if (resourceType === 'RULE') {
      const content = decode('modelJson', value.content) || {}
      const model = decode('modelJson', content.modelJson ?? value.modelJson)
      try {
        if (!model || typeof model !== 'object' || Array.isArray(model)) throw new Error()
        const ruleSections = value.modelType === 'SCRIPT'
          ? [{ key: 'script', title: '脚本与引用', nodes: [node('script', model.script), node('scriptVarRefs', model.scriptVarRefs || [])] }]
          : buildRulePreviewSections(value.modelType, model).map(section => ({ ...section, nodes: section.nodes.map(ruleNode) }))
        if (!ruleSections.length) throw new Error()
        for (const section of ruleSections) sections.push({ ...section, key: 'rule:' + section.key, signature: JSON.stringify(canonical(section.nodes)) })
      } catch { warning = '此规则的业务结构暂时无法预览，请展开下方原始 JSON 核对。' }
    }
    return { sections: [...basic, ...sections], warning }
  } catch {
    return { sections: [], warning: '版本快照格式异常，无法生成业务预览。原始内容仍可在下方查看。' }
  }
}

export function pairPreviewSections(left, right) {
  const keys = [...new Set([...left.map(section => section.key), ...right.map(section => section.key)])]
  const leftMap = new Map(left.map(section => [section.key, section]))
  const rightMap = new Map(right.map(section => [section.key, section]))
  return keys.map(key => {
    const original = leftMap.get(key)
    const modified = rightMap.get(key)
    return { key, left: original, right: modified, status: !original ? 'added' : !modified ? 'removed' : original.signature === modified.signature ? 'same' : 'changed' }
  })
}
