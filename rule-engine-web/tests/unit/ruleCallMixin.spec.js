import * as definitionApi from '@/api/definition'
import ruleCallMixin from '@/mixins/ruleCallMixin'

function createContext() {
  const ctx = Object.assign({}, ruleCallMixin.data(), {
    definitionId: 7,
    projectIdForRefs: null
  })
  Object.keys(ruleCallMixin.methods).forEach(name => {
    ctx[name] = ruleCallMixin.methods[name].bind(ctx)
  })
  return ctx
}

describe('ruleCallMixin', () => {
  test('旧发布字段校验请求晚到不能覆盖较新的字段快照', async () => {
    const ctx = createContext()
    ctx.projectRules = [{ id: 8 }]
    let resolveOld
    definitionApi.listPublishedVersions.mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve }))
    const model = { actions: [{ type: 'rule-call', ruleId: 8 }] }
    const old = ctx.loadRuleCallPublishedVersions(model)
    const stale = expect(old).rejects.toThrow('已变化')
    const current = [{ bindingId: '81', version: 2, outputFields: [{ scriptName: 'current_score' }] }]
    definitionApi.listPublishedVersions.mockResolvedValueOnce({ data: current })
    await ctx.loadRuleCallPublishedVersions(model)
    resolveOld({ data: [{ bindingId: '81', version: 1, outputFields: [{ scriptName: 'old_score' }] }] })
    await stale
    expect(ctx.projectRules[0].executionVersions).toEqual(current)
  })
  test('只读取模型实际调用的发布版本字段并去重，不改草稿字段投影', async () => {
    const ctx = createContext()
    ctx.projectRules = [{ id: 8, outputFields: [{ scriptName: 'draft_score' }] }, { id: 9 }, { id: 10 }]
    const published = [{ bindingId: '81', version: 1, available: true, outputFields: [{ scriptName: 'published_score' }] }]
    definitionApi.listPublishedVersions.mockResolvedValue({ data: published })
    await ctx.loadRuleCallPublishedVersions({ nodes: [{ actions: [{ type: 'rule-call', ruleId: 8 }, { type: 'rule-call', ruleId: '8' }] }] })
    expect(definitionApi.listPublishedVersions).toHaveBeenCalledTimes(1)
    expect(definitionApi.listPublishedVersions).toHaveBeenCalledWith('8')
    expect(ctx.projectRules[0].executionVersions).toEqual(published)
    expect(ctx.projectRules[0].outputFields).toEqual([{ scriptName: 'draft_score' }])
    expect(ctx.projectRules[1].executionVersions).toBeUndefined()
  })
  test('返回缓存设计器时刷新已发布规则状态，保留当前模型', async () => {
    const ctx = createContext()
    ctx.$route = { path: '/designer/flow/7', params: { id: '7' } }
    ctx.model = { nodes: [{ id: 'unsaved' }] }
    definitionApi.getDefinition.mockResolvedValue({ id: 7, projectId: 2, ruleCode: 'Main' })
    definitionApi.listProjectDefinitions.mockResolvedValue({ records: [{ id: 8, ruleCode: 'Child', modelType: 'RULE_SET', status: 1 }] })
    expect(typeof ruleCallMixin.activated).toBe('function')
    await ruleCallMixin.activated.call(ctx)
    expect(ctx.projectRules[0]).toMatchObject({ id: 8, status: 1 })
    expect(ctx.model.nodes).toEqual([{ id: 'unsaved' }])
  })
  beforeEach(() => vi.clearAllMocks())

  test('旧规则目录请求晚到时不能覆盖刷新后的发布状态', async () => {
    const ctx = createContext()
    definitionApi.getDefinition.mockResolvedValue({ id: 7, projectId: 2 })
    let resolveOld
    definitionApi.listProjectDefinitions.mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve }))
    const old = ctx.loadRuleCallOptions(7)
    await Promise.resolve()
    definitionApi.listProjectDefinitions.mockResolvedValueOnce({ records: [{ id: 8, modelType: 'RULE_SET', status: 1 }] })
    await ctx.loadRuleCallOptions(7)
    resolveOld({ records: [{ id: 8, modelType: 'RULE_SET', status: 0 }] })
    await old
    expect(ctx.projectRules[0].status).toBe(1)
  })

  test('加载项目规则和已关联全局规则且保留原始编码', async () => {
    definitionApi.getDefinition.mockResolvedValue({ id: 7, projectId: 2, ruleCode: 'Main_Flow' })
    definitionApi.listProjectDefinitions.mockResolvedValue({ records: [
      { id: 7, projectId: 2, scope: 'PROJECT', ruleCode: 'Main_Flow', modelType: 'FLOW', status: 1 },
      { id: 8, projectId: 0, scope: 'GLOBAL', ruleCode: 'Score_Card', modelType: 'SCORE', status: 1,
        inputFieldsJson: [{ scriptName: 'CREDIT_AMOUNT' }], outputFieldsJson: [{ scriptName: 'score' }] }
    ] })
    const ctx = createContext()

    await ctx.loadRuleCallOptions(7)

    expect(definitionApi.listProjectDefinitions).toHaveBeenCalledWith(2, { pageNum: 1, pageSize: 1000 })
    expect(ctx.currentRuleId).toBe(7)
    expect(ctx.currentRuleCode).toBe('Main_Flow')
    expect(ctx.projectRules[1]).toMatchObject({
      id: 8,
      scope: 'GLOBAL',
      ruleCode: 'Score_Card',
      inputFields: [{ scriptName: 'CREDIT_AMOUNT' }],
      outputFields: [{ scriptName: 'score' }]
    })
  })

  test('加载失败时清空选项并暴露错误状态', async () => {
    definitionApi.getDefinition.mockRejectedValue(new Error('network'))
    const ctx = createContext()

    await ctx.loadRuleCallOptions(7)

    expect(ctx.projectRules).toEqual([])
    expect(ctx.ruleOptionsLoadError).toBe(true)
  })
})
