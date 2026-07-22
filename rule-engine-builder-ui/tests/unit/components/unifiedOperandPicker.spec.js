import { shallowMount } from '@test-utils'
import OperandPicker from '@/components/common/OperandPicker.vue'

const focusManualInput = jest.fn()

function mountPicker(propsData = {}, options = {}) {
  return shallowMount(OperandPicker, {
    props: { value: null, vars: [], functions: [], allowedKinds: ['LITERAL', 'REFERENCE', 'FUNCTION', 'OPERATION'], ...propsData },
    mocks: options.mocks,
    stubs: {
      VarPicker: { name: 'VarPicker', template: '<div />' },
      ExpressionEditorDialog: { name: 'ExpressionEditorDialog', template: '<div />' },
      'el-input': {
        name: 'ElInput',
        template: '<input />',
        methods: { focus: focusManualInput }
      },
      'el-select': { name: 'ElSelect', template: '<select><slot /></select>' },
      'el-option': { name: 'ElOption', template: '<option />' },
      'el-tooltip': { template: '<span><slot /></span>' }
    }
  })
}

describe('统一 OperandPicker', () => {
  beforeEach(() => {
    focusManualInput.mockClear()
  })

  test('选择带参数函数时先进入表达式编辑器，应用后才更新外部值', () => {
    const fn = { id: 9, funcCode: 'max', paramsJson: '[{"name":"a","type":"NUMBER"},{"name":"b","type":"NUMBER"}]' }
    const wrapper = mountPicker({ functions: [fn] })

    wrapper.vm.onQuickInput({ kind: 'FUNCTION', functionId: 9, functionCode: 'max', args: [] })
    expect(wrapper.emitted().input).toBeUndefined()
    expect(wrapper.vm.editorVisible).toBe(true)
    expect(wrapper.vm.editorValue.args).toHaveLength(2)

    wrapper.vm.onEditorApply(wrapper.vm.editorValue)
    expect(wrapper.emitted().input[0][0].functionCode).toBe('max')
  })

  test('无参数函数和普通字段保持一次点击完成', () => {
    const wrapper = mountPicker({ functions: [{ id: 10, funcCode: 'currentDate', paramsJson: '[]' }] })
    wrapper.vm.onQuickInput({ kind: 'FUNCTION', functionId: 10, functionCode: 'currentDate', args: [] })
    wrapper.vm.onQuickInput({ kind: 'REFERENCE', refId: 2, refType: 'VARIABLE', code: 'amount' })

    expect(wrapper.emitted().input).toHaveLength(2)
    expect(wrapper.vm.editorVisible).toBe(false)
  })

  test('已有值点击公式按钮时以深拷贝打开，不直接改父值', () => {
    const source = { kind: 'LITERAL', value: '100', valueType: 'NUMBER' }
    const wrapper = mountPicker({ value: source })
    wrapper.vm.openEditor()
    wrapper.vm.editorValue.value = '200'

    expect(source.value).toBe('100')
    expect(wrapper.vm.editorVisible).toBe(true)
  })

  test('设计器内点击公式按钮创建会话并进入 layout-main 独立路由', async() => {
    const dispatch = jest.fn().mockResolvedValue()
    const push = jest.fn()
    const source = { kind: 'LITERAL', value: '100', valueType: 'NUMBER' }
    const wrapper = mountPicker({ value: source }, {
      mocks: {
        $route: { path: '/designer/table/7', params: { id: '7' }, meta: { title: '决策表设计器' } },
        $router: { push },
        $store: { dispatch, getters: {} }
      }
    })

    await wrapper.vm.openEditor()
    const payload = dispatch.mock.calls[0][1]

    expect(dispatch).toHaveBeenCalledWith('expressionSessions/openSession', expect.objectContaining({
      ruleId: 7,
      sourceKey: `operand-picker-${wrapper.vm._uid}`,
      title: '决策表 · 表达式',
      draft: source
    }))
    expect(payload.draft).not.toBe(source)
    expect(push).toHaveBeenCalledWith({
      name: 'ExpressionEditor',
      params: { ruleId: '7', sessionId: payload.sessionId }
    })
    expect(wrapper.vm.editorVisible).toBe(false)
  })

  test('缓存设计器恢复后只回填一次最新编译修订', async() => {
    const dispatch = jest.fn().mockResolvedValue()
    const pending = {
      operand: { kind: 'PATH', value: 'request.score' },
      compiledScript: 'request.score',
      revision: 2
    }
    const wrapper = mountPicker({}, {
      mocks: {
        $route: { path: '/designer/table/7', params: { id: '7' } },
        $router: { push: jest.fn() },
        $store: {
          dispatch,
          getters: {
            'expressionSessions/pendingCompiledResult': jest.fn(() => pending)
          }
        }
      }
    })
    wrapper.setData({ expressionSessionId: 'session-7' })

    await wrapper.vm.consumePendingExpression()
    await wrapper.vm.consumePendingExpression()

    expect(wrapper.emitted().input).toHaveLength(1)
    expect(wrapper.emitted().select).toHaveLength(1)
    expect(dispatch).toHaveBeenCalledWith('expressionSessions/markApplied', {
      sessionId: 'session-7',
      revision: 2
    })
  })

  test('非设计器调用继续使用原弹层', async() => {
    const wrapper = mountPicker({}, {
      mocks: {
        $route: { path: '/variable', params: {} },
        $router: { push: jest.fn() },
        $store: { dispatch: jest.fn(), getters: {} }
      }
    })

    await wrapper.vm.openEditor()

    expect(wrapper.vm.editorVisible).toBe(true)
    expect(wrapper.vm.$store.dispatch).not.toHaveBeenCalled()
  })

  test('手输阈值在当前选择框切换为输入并支持修改类型', async() => {
    const wrapper = mountPicker({ expectedType: 'NUMBER' })

    wrapper.vm.openManualInput('LITERAL')
    await wrapper.vm.$nextTick()

    expect(wrapper.find('.operand-manual-editor').exists()).toBe(true)
    expect(wrapper.vm.editorVisible).toBe(false)
    expect(wrapper.vm.manualOperand).toEqual({ kind: 'LITERAL', value: '', valueType: 'NUMBER' })
    expect(focusManualInput).toHaveBeenCalled()

    wrapper.vm.patchManualOperand({ valueType: 'BOOLEAN' })
    wrapper.vm.patchManualOperand({ value: 'true' })

    const emitted = wrapper.emitted().input
    expect(emitted[emitted.length - 1][0]).toEqual({ kind: 'LITERAL', value: 'true', valueType: 'BOOLEAN' })
    expect(wrapper.vm.editorVisible).toBe(false)
  })

  test('手输路径在当前选择框录入并按稳定 ID 解析', () => {
    const wrapper = mountPicker({
      allowedKinds: ['PATH', 'REFERENCE'],
      vars: [{
        varCode: 'request.age',
        varLabel: '客户年龄',
        varType: 'INTEGER',
        _varId: 8,
        _refType: 'DATA_OBJECT',
        _ref: { category: 'object' }
      }]
    })

    wrapper.vm.openManualInput('PATH')
    wrapper.vm.updateManualValue('request.age')
    wrapper.vm.resolveManualPath()

    const emitted = wrapper.emitted().input
    expect(emitted[emitted.length - 1][0]).toMatchObject({
      kind: 'PATH',
      value: 'request.age',
      refId: 8,
      refType: 'DATA_OBJECT',
      resolved: true
    })
    expect(wrapper.vm.editorVisible).toBe(false)
  })

  test('手输路径反查完成后报告解析结果和候选项', () => {
    const wrapper = mountPicker({
      allowedKinds: ['PATH', 'REFERENCE'],
      vars: [{
        varCode: 'request.hits',
        varLabel: '命中列表',
        varType: 'LIST',
        _varId: 18,
        _refType: 'DATA_OBJECT',
        _ref: { category: 'object' }
      }]
    })

    wrapper.vm.openManualInput('PATH')
    wrapper.vm.updateManualValue('request.hits')
    wrapper.vm.resolveManualPath()

    const events = wrapper.emitted()['path-resolve']
    expect(events).toHaveLength(1)
    expect(events[0][0]).toMatchObject({
      operand: { value: 'request.hits', valueType: 'LIST', refId: 18, refType: 'DATA_OBJECT', resolved: true },
      candidates: []
    })
  })
})
