import { shallowMount } from '@vue/test-utils'
import * as expressionApi from '@/api/expression'
import ExpressionEditorPage from '@/views/expression/ExpressionEditorPage.vue'

const draft = { kind: 'LITERAL', value: '8', valueType: 'NUMBER' }

const EditorStub = {
  name: 'ExpressionEditorDialog',
  props: ['value', 'embedded', 'visible'],
  template: '<div class="editor-stub" />',
  methods: {
    getDraft() { return JSON.parse(JSON.stringify(this.value)) },
    validateDraft() { return [] }
  }
}

function session(overrides = {}) {
  return {
    sessionId: 'session-1',
    ruleId: 9,
    status: 'ACTIVE',
    draft,
    vars: [],
    functions: [],
    listOptions: [],
    allowedKinds: [],
    context: 'READ_EXPRESSION',
    expectedType: 'NUMBER',
    title: '配置授信额度',
    ...overrides
  }
}

function mountPage(current = session()) {
  const dispatch = jest.fn().mockResolvedValue()
  const back = jest.fn()
  const wrapper = shallowMount(ExpressionEditorPage, {
    mocks: {
      $route: { params: { ruleId: '9', sessionId: 'session-1' } },
      $router: { back },
      $store: {
        getters: { 'expressionSessions/sessionById': () => current },
        dispatch
      },
      $message: { success: jest.fn(), error: jest.fn() },
      $confirm: jest.fn().mockResolvedValue('confirm')
    },
    stubs: {
      ExpressionEditorDialog: EditorStub,
      'el-alert': true,
      'el-button': true,
      'el-dialog': true,
      'el-radio-group': true,
      'el-radio-button': true,
      'el-form': true,
      'el-form-item': true,
      'el-input': true,
      'el-input-number': true,
      'el-switch': true,
      'el-tag': true,
      'el-empty': true
    },
    directives: { loading: () => {} }
  })
  return { wrapper, dispatch, back }
}

afterEach(() => jest.clearAllMocks())

describe('ExpressionEditorPage', () => {
  test('嵌入 layout-main 并可临时保存当前草稿', async() => {
    const { wrapper, dispatch } = mountPage()

    expect(wrapper.findComponent(EditorStub).props('embedded')).toBe(true)
    await wrapper.vm.saveDraft()

    expect(dispatch).toHaveBeenCalledWith('expressionSessions/saveDraft', {
      sessionId: 'session-1',
      draft
    })
  })

  test('保存并编译只编译表达式，成功后回填会话并返回', async() => {
    expressionApi.compileExpression.mockResolvedValue({
      data: { success: true, compiledScript: '8' }
    })
    const { wrapper, dispatch, back } = mountPage()

    await wrapper.vm.saveAndCompile()

    expect(expressionApi.compileExpression).toHaveBeenCalledWith({
      ruleId: 9,
      resolutionMode: 'CURRENT',
      operand: draft,
      params: {}
    })
    expect(dispatch).toHaveBeenCalledWith('expressionSessions/saveCompiled', {
      sessionId: 'session-1',
      operand: draft,
      compiledScript: '8'
    })
    expect(back).toHaveBeenCalled()
  })

  test('切换朔源模式先确认费用风险并按后端字段生成测试输入', async() => {
    expressionApi.getExpressionTestSchema.mockResolvedValue({
      data: {
        inputs: [{ scriptName: 'request.customerId', label: '客户编号', valueType: 'STRING' }],
        runtimeNodes: [{ scriptName: 'riskScore', label: '风险分', sourceType: 'API' }],
        sampleParams: { request: { customerId: 'C001' } },
        diagnostics: []
      }
    })
    const { wrapper } = mountPage()

    await wrapper.vm.changeResolutionMode('DEEP')

    expect(wrapper.vm.$confirm).toHaveBeenCalled()
    expect(expressionApi.getExpressionTestSchema).toHaveBeenCalledWith(expect.objectContaining({ resolutionMode: 'DEEP' }))
    expect(wrapper.vm.testValues['request.customerId']).toBe('C001')
    expect(wrapper.vm.runtimeNodes[0].sourceType).toBe('API')
  })

  test('测试提交只执行当前表达式并展示返回结果', async() => {
    expressionApi.executeExpression.mockResolvedValue({
      data: { success: true, result: 13, executeTimeMs: 4 }
    })
    const { wrapper } = mountPage()
    wrapper.setData({
      testFields: [{ scriptName: 'amount', valueType: 'NUMBER' }],
      testValues: { amount: 8 },
      testVisible: true
    })

    await wrapper.vm.runTest()

    expect(expressionApi.executeExpression).toHaveBeenCalledWith(expect.objectContaining({
      ruleId: 9,
      operand: draft,
      params: { amount: 8 }
    }))
    expect(wrapper.vm.testResult.result).toBe(13)
  })
})
