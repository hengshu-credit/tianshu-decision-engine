import { responseCodes } from './responseCodes'
import { fieldsSchema } from './fieldSchema'

const runtimePath = '/api/rule/runtime'
export const recoveryParameter = {
  name: 'externalRecoveryMode', in: 'query', required: false,
  schema: { type: 'string', enum: ['ORIGINAL_ONLY', 'CACHE_THEN_CALL'] },
  description: '省略时沿用生效的恢复策略。默认 ORIGINAL_ONLY 优先复用原调用和异步回执，原调用失败或确认无结果时才可能重新取数。CACHE_THEN_CALL 先查可用缓存，未命中可重新查询并计费。'
}
export const executionStates = {
  QUEUED: '已受理并排队，等待服务端执行。',
  RUNNING: '执行中，稍后查询同一追踪号。',
  WAITING_EXTERNAL: '正在等待外数。可轮询原任务的接口继续使用原回执。',
  PAUSED_ROUTING: '分流条件异常已暂停。修复后用原实验追踪号恢复。尚未绑定策略的最新版本实验可以采用修复后的路由配置，固定版本仍受原版本约束。',
  FAILED_RETRYABLE: '本次执行失败。核对 errorMessage 和 intermediate，修复依赖后恢复。',
  UNKNOWN_RETRYABLE: '外部操作的结果待核实。先核对原调用的状态和回执，再决定是否恢复。',
  SUCCEEDED: '执行成功，可读取最终结果。业务通过或拒绝由规则输出决定。'
}
export const runtimeOperations = [
  { id: 'getExecutionStatus', title: '查询执行状态', method: 'get', path: runtimePath + '/executions/{traceId}', idName: 'traceId',
    description: '传入原规则或实验的根追踪号。HTTP 200 表示查询成功，执行进度见 data.status，尝试次数见 data.attemptNo。未完成时可查看 data.intermediate。', schema: 'ExecutionStatus', codes: ['000000','300001','300002','300003','300004','300005','501001','301002','400002'] },
  { id: 'getExecutionResult', title: '读取最终结果', method: 'get', path: runtimePath + '/executions/{traceId}/result', idName: 'traceId',
    description: 'HTTP 200 返回原规则结果或完整实验结果。开放接口订单按规则结果结构读取。HTTP 202 表示结果尚未就绪，请继续查状态。', schema: 'ExecutionResult', codes: ['000000','300001','300002','300003','300004','300005','501001','301002','400008','400002'] },
  { id: 'resumeExecution', title: '从断点继续执行', method: 'post', path: runtimePath + '/executions/{traceId}/resume', idName: 'traceId',
    description: '修复异常后用原追踪号恢复。通常留空请求体，由服务端读取原参数。需要补交时，直接传原 params 对象，开放接口订单使用映射后的规则参数。已完成的订单返回已保存的结果。', schema: 'ExecutionResult' },
  { id: 'getExternalCall', title: '读取一次外数调用的报文', method: 'get', path: runtimePath + '/external-calls/{callId}', idName: 'callId',
    description: '从执行明细取得 callId，读取本项目已记录的报文。originalResponseBody 和 responseBody 的可用性取决于接口的报文保留配置，可检查 originalResponseAvailable、rawResponseAvailable。查询日志不会触发新的供应商调用。', schema: 'ExternalCall', codes: ['000000','300001','300002','300003','300004','300005','501001','301002','400002'] }
]

const str = { type: 'string' }
const integer = { type: 'integer' }
const nullableInteger = { type: ['integer','null'] }
const bool = { type: 'boolean' }
const nullableText = { type: ['string','null'] }
const ref = name => ({ $ref: '#/components/schemas/' + name })
const object = properties => ({ type: 'object', properties })
const list = items => ({ type: 'array', items })
const json = schema => ({ 'application/json': { schema } })

export function apiResponseHeaders(mapping, status) {
  const headers = { 'X-Trace-Id': { description: '响应包含追踪号时返回', schema: str } }
  if (mapping?.enabled) {
    headers['X-Rule-Standard-Code'] = { description: '映射前的标准结果码', schema: str }
    headers['X-Rule-Code-Mapping-Version'] = { description: '本次使用的结果码映射版本', schema: str }
  }
  if (Number(status) === 429) headers['Retry-After'] = { description: '若返回，等待的秒数', schema: integer }
  return headers
}

export const runtimeSchemas = {
  RuleResult: object({ traceId: str, revisionId: nullableInteger, artifactDigest: nullableText, result: {}, traces: list({}),
    success: bool, executeTimeMs: integer, errorMessage: nullableText, attemptNo: integer, resumed: bool, reusedCount: integer, reexecutedCount: integer, executionStatus: nullableText, retryAfterMs: nullableInteger }),
  ExperimentGroup: object({ traceId: nullableText, groupCode: str, groupName: str, groupType: str, ruleId: nullableInteger, ruleCode: nullableText, ruleVersion: nullableInteger, revisionId: nullableInteger, matched: bool, skipped: bool, success: bool, result: {}, traces: list({}), routeReason: nullableText, errorMessage: nullableText, executeTimeMs: integer }),
  ExperimentResult: object({ experimentTraceId: str, experimentCode: str, experimentName: str, requestKey: str, versionId: nullableInteger, version: nullableInteger, success: bool, errorMessage: nullableText, productionGroup: { anyOf: [ref('ExperimentGroup'), { type: 'null' }] }, testGroups: list(ref('ExperimentGroup')), tags: list(str), executeTimeMs: integer }),
  ExecutionResult: { anyOf: [ref('RuleResult'), ref('ExperimentResult')] },
  ExecutionStatus: object({ traceId: str, status: { type: 'string', description: Object.keys(executionStates).join(' / ') }, attemptNo: integer, revisionId: nullableInteger, artifactDigest: nullableText, intermediate: { type: ['object','null'] }, errorMessage: nullableText, expireTime: nullableText, result: { anyOf: [ref('RuleResult'), { type: 'null' }] } }),
  ExternalCall: object({ callId: str, traceId: str, rootTraceId: str, requestBody: {}, responseBody: {}, originalRequestBody: {}, originalResponseBody: {}, rawRequestAvailable: bool, rawResponseAvailable: bool, originalRequestAvailable: bool, originalResponseAvailable: bool }),
  ProjectToken: object({ accessToken: str, tokenType: str, tokenCode: str, projectCode: str, authCode: str, authType: str, issuedAt: str, expiresAt: str, graceExpiresAt: str, expiresInSeconds: integer, graceExpiresInSeconds: integer })
}

export function runtimeResponses(mapping, schema, codes) {
  const responses = {}
  for (const item of responseCodes(mapping)) {
    if (codes && !codes.includes(item.systemCode)) continue
    const response = responses[item.httpStatus] ||= {
      description: '', headers: apiResponseHeaders(mapping, item.httpStatus), content: json(object({ code: { type: 'string', enum: [] }, message: str, detail: nullableText,
        traceId: nullableText, success: { type: 'boolean', example: item.httpStatus === 200 }, data: item.httpStatus === 200 ? ref(schema) : {} }))
    }
    const values = response.content['application/json'].schema.properties.code.enum
    if (!values.includes(item.code)) values.push(item.code)
    response.description += (response.description ? '。' : '') + item.code + ' ' + item.message
  }
  return responses
}

export function experimentPath(code) { return runtimePath + '/experiment/execute/' + encodeURIComponent(code) }
export const experimentParameters = {
  requestKey: { type: 'string', description: '每笔进件唯一。相同请求重试沿用原值及原请求内容。省略时再次提交会创建独立执行。' },
  clientAppName: { type: 'string', description: '调用方应用名' },
  requestTime: { type: 'string', description: '可选的本地日期时间，例如 2026-01-15T09:30:00。重试沿用原值。' },
  versionMode: { type: 'string', enum: ['LATEST','FIXED'], description: '省略时使用当前版本' },
  versionId: { type: 'integer', format: 'int64', description: 'FIXED 时必须提供本实验的版本记录 ID' }
}
export function addRuntimePaths(paths, doc, security) {
  for (const experiment of doc.experiments) {
    const params = fieldsSchema(experiment.requestFields, 'params')
    const body = json({ ...object({ ...experimentParameters, params }), allOf: [{
      if: { required: ['versionMode'], properties: { versionMode: { const: 'FIXED' } } }, then: { required: ['versionId'] }
    }] })
    body['application/json'].example = experiment.exampleBody
    paths[experimentPath(experiment.experimentCode)] = { post: {
      operationId: 'experiment_' + experiment.experimentCode, summary: experiment.experimentName || experiment.experimentCode,
      description: [experiment.description, '生产组决策位于 data.productionGroup.result。', experiment.routingMode === 'CONDITION' && '分流条件异常策略 ' + experiment.routingErrorPolicy + '。', !experiment.inputFieldsAvailable && '当前输入字段快照不可用。'].filter(Boolean).join('\n'),
      tags: ['分流实验'], security: security.alternatives, parameters: [recoveryParameter], requestBody: { required: false, content: body },
      responses: runtimeResponses(doc.codeMapping, 'ExperimentResult')
    } }
  }
  for (const item of runtimeOperations) {
    const operation = { operationId: item.id, summary: item.title, description: item.description, tags: ['执行与恢复'], security: security.alternatives,
      parameters: [{ in: 'path', name: item.idName, required: true, schema: str }], responses: runtimeResponses(doc.codeMapping, item.schema, item.codes) }
    if (item.id === 'resumeExecution') {
      operation.parameters.push(recoveryParameter)
      operation.requestBody = { required: false, description: '可省略。提供时为原业务参数对象。', content: json({ type: 'object' }) }
    }
    paths[item.path] = { [item.method]: operation }
  }
  if (security.base.length) paths['/api/rule/auth/token'] = { post: {
    operationId: 'issueProjectToken', summary: '项目基础凭据换取临时令牌', tags: ['认证鉴权'],
    description: '使用启用的项目基础凭据，无需 Body。后续以 Authorization: Bearer <accessToken> 调用项目接口。有效期取决于凭据配置。',
    security: security.base, responses: runtimeResponses(doc.codeMapping, 'ProjectToken', ['000000','300001','300002','400002'])
  } }
}
