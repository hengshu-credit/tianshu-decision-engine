vi.unmock('axios')
vi.unmock('@/api/definition')
vi.unmock('@/api/project')
vi.unmock('@/api/artifact')
vi.unmock('@/api/transfer')

import axios from 'axios'
import request from '@/api/request'
import * as definition from '@/api/definition'
import * as project from '@/api/project'
import * as artifact from '@/api/artifact'
import * as transfer from '@/api/transfer'

let wire
const response = '{"code":200,"data":{"id":9007199254740993,"revisionId":9007199254740994,"lockVersion":9007199254740995}}'
const metadataCalls = [
  ['definition detail', () => definition.getDefinitionDetail('7')],
  ['detail alias', () => definition.getDetail('7')],
  ['definition list', () => definition.listDefinitions({})],
  ['project definitions', () => definition.listProjectDefinitions('7', {})],
  ['definition', () => definition.getDefinition('7')],
  ['definition content', () => definition.getContent('7')],
  ['create definition', () => definition.createDefinition({ ruleCode: 'Exact_Code' })],
  ['revisions', () => definition.listRuleRevisions('7')],
  ['revision', () => definition.getRuleRevision('7', '9')],
  ['revision fields', () => definition.getRuleRevisionFieldView('7', '9')],
  ['current draft', () => definition.getCurrentDraftRevision('7')],
  ['draft create', () => definition.createDraftRevision('7', '9')],
  ['draft ensure', () => definition.ensureDraftRevision('7')],
  ['draft save', () => definition.saveContent({ definitionId: '7', revisionId: '9', lockVersion: '9007199254740995', modelJson: '{}' })],
  ['temporary draft', () => definition.saveTemporaryDraft('7', {})],
  ['designer draft', () => definition.saveDesignerDraft('7', {})],
  ['draft from source', () => definition.createDraftFromSource('7', { sourceType: 'REVISION', sourceId: '9' })],
  ['repair preview', () => definition.getRuleRevisionRepairPreview('7')],
  ['repair revision', () => definition.repairRuleRevision('7', {})],
  ['preflight', () => definition.preflightRuleRevision('7', '9')],
  ['lifecycle action', () => definition.approveRuleRevision('7', '9', {})],
  ['published versions', () => definition.listPublishedVersions('7')],
  ['versions', () => definition.listVersions('7')],
  ['version', () => definition.getVersion('7', 1)],
  ['version id', () => definition.getVersionById('7', '9')],
  ['timeline', () => definition.getRuleLifecycleTimeline('7')],
  ['project list', () => project.listProjects({})],
  ['artifact import', () => artifact.importArtifact(new Blob(['fixture']))],
  ['artifact options', () => artifact.getArtifactDeploymentOptions('7', 0)],
  ['artifact deploy', () => artifact.deployArtifact({ artifactId: '7' })],
  ['transfer candidates', () => transfer.listTransferResources({ nodeType: 'RULE' })],
  ['transfer fields', () => transfer.listTransferResourceFields('DATA_OBJECT', '7')],
]

beforeEach(() => {
  vi.clearAllMocks()
  wire = response
  // 使用真实Axios transform链；adapter提供原始HTTP文本，禁止先JSON.parse再伪造string ID。
  request.mockImplementation(config => axios.request({
    ...config,
    adapter: async actual => ({ data: wire, status: 200, statusText: 'OK', headers: { 'content-type': 'application/json' }, config: actual }),
  }).then(result => result.data))
})

describe('限定元数据响应的原始数字ID保全', () => {
  test.each(metadataCalls)('%s在真实Axios解析前保留相邻大ID及锁版本', async (_name, invoke) => {
    const result = await invoke()
    expect(result.data.id).toBe('9007199254740993')
    expect(result.data.revisionId).toBe('9007199254740994')
    expect(result.data.lockVersion).toBe('9007199254740995')
  })

  test('安全数字和普通业务数字不改，JSON字符串内部及名称空格原样保留', async () => {
    wire = '{"code":200,"data":{"id":7,"revisionId":9007199254740991,"lockVersion":0,"revisionNo":2,"amount":9007199254740993,"grid":9007199254740993,"ruleCode":" Mixed_Code ","modelJson":"{ \\"id\\": 9007199254740993 }","validationRuleIds":"[9007199254740993]","sourceIds":[7,9007199254740993,9007199254740994]}}'
    const result = await definition.getDefinitionDetail(7)
    expect(result.data.id).toBe(7)
    expect(result.data.revisionId).toBe(Number.MAX_SAFE_INTEGER)
    expect(result.data.lockVersion).toBe(0)
    expect(result.data.revisionNo).toBe(2)
    expect(result.data.amount).toBe(JSON.parse('9007199254740993'))
    expect(result.data.grid).toBe(JSON.parse('9007199254740993'))
    expect(result.data.ruleCode).toBe(' Mixed_Code ')
    expect(result.data.modelJson).toBe('{ "id": 9007199254740993 }')
    expect(result.data.validationRuleIds).toBe('[9007199254740993]')
    expect(result.data.sourceIds).toEqual([7, '9007199254740993', '9007199254740994'])
  })

  test('元数据特殊键仍是普通数据，不能污染原型或误处理非标识业务字段', async () => {
    wire = '{"code":200,"data":{"__proto__":{"id":9007199254740993},"constructor":{"parentId":9007199254740994},"valid":9007199254740993}}'
    const result = await definition.getDefinitionDetail(7)
    expect(Object.hasOwn(result.data, '__proto__')).toBe(true)
    expect(result.data.__proto__.id).toBe('9007199254740993')
    expect(result.data.constructor.parentId).toBe('9007199254740994')
    expect(result.data.valid).toBe(JSON.parse('9007199254740993'))
    expect(Object.prototype.id).toBeUndefined()
  })

  test.each(['9007199254740993.0', '9.007199254740993e15', '9007199254740991.1', '9.0071992547409911e15', '-9007199254740993', '9223372036854775808', '1e1000'])('不规范或越界unsafe ID %s明确拒绝', async token => {
    wire = '{"code":200,"data":{"id":' + token + '}}'
    await expect(definition.getDefinitionDetail(7)).rejects.toThrow(/标识/)
  })

  test('边界以内的安全数字即使使用合法小数或指数形式仍按原Axios语义读取', async () => {
    wire = '{"code":200,"data":{"id":9007199254740991.0,"revisionId":1e3}}'
    const result = await definition.getDefinitionDetail(7)
    expect(result.data.id).toBe(Number.MAX_SAFE_INTEGER)
    expect(result.data.revisionId).toBe(1000)
  })

  test('Long最大值按原始token保留，已被上游解析失真的数字ID必须拒绝', async () => {
    wire = '{"code":200,"data":{"id":9223372036854775807}}'
    expect((await definition.getDefinitionDetail(7)).data.id).toBe('9223372036854775807')
    wire = { code: 200, data: { id: Number('9007199254740993') } }
    await expect(definition.getDefinitionDetail(7)).rejects.toThrow(/原始.*JSON/)
  })

  test('执行响应不安装元数据转换，保留原Axios行为', async () => {
    const result = await definition.executeRule({ definitionId: 7, params: {} })
    expect(typeof result.data.id).toBe('number')
    expect(request.mock.calls[0][0].transformResponse).toBeUndefined()
    await definition.executeApiScenarioValidated(7, { requestJson: '{"params":{"id":9007199254740993}}' })
    expect(request.mock.calls[1][0].transformResponse).toBeUndefined()
    expect(request.mock.calls[1][0].data.requestJson).toBe('{"params":{"id":9007199254740993}}')
  })

  test('制品下载保持blob，部署候选的全局项目0必须显式传递', async () => {
    await artifact.downloadArtifact('9007199254740993')
    expect(request.mock.calls[0][0]).toMatchObject({ responseType: 'blob' })
    expect(request.mock.calls[0][0].transformResponse).toBeUndefined()
    await artifact.getArtifactDeploymentOptions('9007199254740993', 0)
    expect(request.mock.calls[1][0].params).toEqual({ targetProjectId: 0 })
  })
})
