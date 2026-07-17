import { shallowMount } from '@vue/test-utils'
import DesignerTestDialog from '@/components/common/DesignerTestDialog.vue'
import * as definitionApi from '@/api/definition'

describe('DesignerTestDialog unified schema', () => {
  beforeEach(() => {
    jest.clearAllMocks()
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
      propsData: {
        definitionId: 7,
        projectId: 1,
        modelType: 'FLOW',
        modelJson,
        paramsTemplate: { legacy: true }
      },
      stubs: { MonacoEditor: true }
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
