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
  beforeEach(() => jest.clearAllMocks())

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
