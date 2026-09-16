import { shallowMount } from '@test-utils'
import DesignerTestDialog from '@/components/common/DesignerTestDialog.vue'
import * as definitionApi from '@/api/definition'

const TraceTree = (await vi.importActual('../../../src/components/common/TraceTree.vue')).default

describe('DesignerTestDialog unified schema', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  test('未保存的决策流使用后端统一样例参数', async () => {
    definitionApi.getRuleTestSchema.mockResolvedValue({
      data: {
        sampleParams: {
          score_f1_fields: { HYBASE_X115: 0 },
          idcard_no: '',
          credit_time: ''
        },
        diagnostics: []
      }
    })
    const modelJson = {
      nodes: [{
        actionData: [{
          type: 'func-call',
          target: 'age',
          args: ['idcard_no', 'credit_time', 'DAY'],
          _argRefs: [{ _varId: 6 }, { _varId: 8 }, null]
        }, { type: 'rule-call', ruleId: 2, ruleCode: 'JCZR' }]
      }]
    }
    const wrapper = shallowMount(DesignerTestDialog, {
      props: {
        definitionId: 7,
        projectId: 1,
        modelType: 'FLOW',
        modelJson,
        paramsTemplate: { legacy: true }
      },
      stubs: {
        MonacoEditor: true,
        'el-alert': true,
        'el-button': true,
        'el-dialog': true,
        'el-input-number': true,
        'el-tab-pane': true,
        'el-tabs': true
      }
    })

    await wrapper.vm.open()

    expect(definitionApi.getRuleTestSchema).toHaveBeenCalledWith({
      targetType: 'RULE',
      targetId: 7,
      projectId: 1,
      modelType: 'FLOW',
      modelJson: JSON.stringify(modelJson)
    })
    expect(JSON.parse(wrapper.vm.paramsJson)).toEqual({
      score_f1_fields: { HYBASE_X115: 0 },
      idcard_no: '',
      credit_time: ''
    })
    expect(wrapper.vm.paramsJson).not.toContain('DAY')

    definitionApi.executeRule.mockResolvedValue({ data: { success: true, result: 101 } })
    await wrapper.vm.execute()
    expect(definitionApi.executeRule).toHaveBeenCalledWith({
      definitionId: 7,
      projectId: 1,
      modelType: 'FLOW',
      modelJson: JSON.stringify(modelJson),
      params: {
        score_f1_fields: { HYBASE_X115: 0 },
        idcard_no: '',
        credit_time: ''
      }
    }, 180000)
  })
})

describe('DesignerTestDialog expression trace', () => {
  function mountDialog() {
    return shallowMount(DesignerTestDialog, {
      props: { definitionId: 7, modelType: 'SCRIPT' },
      stubs: { TraceTree }
    })
  }

  function traceFrame(status = 'SUCCESS') {
    return {
      schemaVersion: 2,
      traceKind: 'RULE',
      traceId: 'designer-test-trace',
      ruleName: '设计器测试规则',
      ruleCode: 'designer_test',
      modelType: 'SCRIPT',
      modelJson: JSON.stringify({ script: 'return age >= 21;' }),
      status,
      durationMs: 12,
      expressionTrace: [{
        type: 'OPERATOR', token: '>=', evaluated: true, value: false,
        children: [
          { type: 'VARIABLE', token: 'age', evaluated: true, value: 20 },
          { type: 'VALUE', token: '21', evaluated: true, value: 21 }
        ]
      }],
      children: []
    }
  }

  beforeEach(() => {
    vi.clearAllMocks()
  })

  test('测试后展示追踪树，并保持本次执行的输入和模型快照', async () => {
    const frame = traceFrame()
    definitionApi.executeRule.mockResolvedValue({ data: {
      success: true, result: false, executeTimeMs: 12, traces: [frame]
    } })
    const wrapper = mountDialog()
    expect(wrapper.find('[name="tree"]').exists()).toBe(false)
    await wrapper.setData({ paramsJson: '{"age":20}' })
    await wrapper.vm.execute()

    const pane = wrapper.find('[name="tree"]')
    expect(pane.attributes('label')).toBe('表达式追踪树')
    expect(pane.find('.rule-trace-name').text()).toBe('设计器测试规则')
    expect(pane.text()).toContain('规则执行过程')
    const tree = wrapper.findComponent(TraceTree)
    expect(JSON.parse(tree.props('traceInfo'))).toEqual([frame])
    expect(tree.props('inputParams')).toBe('{"age":20}')
    expect(tree.props('outputResult')).toBe('false')
    expect(wrapper.vm.activeTab).toBe('output')

    await wrapper.setData({ paramsJson: '{"age":16}' })
    expect(tree.props('inputParams')).toBe('{"age":20}')
    expect(tree.vm.ruleDefinitionModel).toEqual({ script: 'return age >= 21;' })
    wrapper.unmount()
  })

  test('执行失败时仍能查看已返回的追踪树', async () => {
    definitionApi.executeRule.mockResolvedValue({ data: {
      success: false, errorMessage: '执行中断', traces: [traceFrame('FAILED')]
    } })
    const wrapper = mountDialog()
    await wrapper.vm.execute()

    expect(wrapper.vm.activeTab).toBe('error')
    expect(wrapper.find('[name="error"]').text()).toContain('执行中断')
    expect(wrapper.find('[name="tree"] .rule-trace-name').text()).toBe('设计器测试规则')
    expect(wrapper.findComponent(TraceTree).props('outputResult')).toBe('')
    wrapper.unmount()
  })

  test.each([{ traces: undefined }, { traces: [] }])('无追踪数据 $traces 时显示空态，重新执行不残留上次追踪', async ({ traces }) => {
    definitionApi.executeRule
      .mockResolvedValueOnce({ data: { success: true, traces: [traceFrame()] } })
      .mockResolvedValueOnce({ data: { success: false, errorMessage: '编译失败', traces } })
    const wrapper = mountDialog()
    await wrapper.vm.execute()
    expect(wrapper.find('.rule-trace-name').exists()).toBe(true)

    await wrapper.vm.execute()
    expect(wrapper.find('.rule-trace-name').exists()).toBe(false)
    expect(wrapper.find('[name="tree"]').text()).toContain('暂无追踪信息')
    wrapper.unmount()
  })
})
