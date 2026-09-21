import { shallowMount } from '@test-utils'
import OperandPicker from '@/components/common/OperandPicker.vue'

const focusManualInput = vi.fn()

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
  test.each([
    { kind: 'LITERAL', value: '', valueType: 'STRING' },
    { kind: 'LITERAL', value: 0, valueType: 'NUMBER' },
    { kind: 'LITERAL', value: false, valueType: 'BOOLEAN' },
    { kind: 'PATH', value: 'request.age', code: 'request.age', refId: 8, refType: 'DATA_OBJECT', resolved: true },
  ])('重新编辑 $kind 保留原值、类型和引用，不因打开而写回', async value => {
    const wrapper = mountPicker({ value })
    wrapper.vm.openManualInput(value.kind)
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.manualOperand).toEqual(value)
    expect(wrapper.vm.manualOperand).not.toBe(value)
    expect(focusManualInput).toHaveBeenCalled()
    expect(wrapper.emitted().input).toBeUndefined()
    wrapper.unmount()
  })

  test('选择文本阈值立即记录合法空字符串，不能保存为缺少操作数', () => {
    const wrapper = mountPicker({ expectedType: 'STRING' })
    wrapper.vm.openManualInput('LITERAL')
    expect(wrapper.emitted().input?.at(-1)?.[0]).toEqual({ kind: 'LITERAL', value: '', valueType: 'STRING' })
    wrapper.unmount()
  })
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
    const dispatch = vi.fn().mockResolvedValue()
    const push = vi.fn()
    const source = { kind: 'LITERAL', value: '100', valueType: 'NUMBER' }
    const wrapper = mountPicker({ value: source }, {
      mocks: {
        $route: {
          name: 'DecisionTable',
          path: '/designer/table/7',
          fullPath: '/designer/table/7?sourceType=REVISION&sourceId=19',
          params: { id: '7' },
          query: { sourceType: 'REVISION', sourceId: '19' },
          meta: { title: '决策表设计器' }
        },
        $router: { push },
        $store: { dispatch, getters: {} }
      }
    })

    await wrapper.vm.openEditor()
    const payload = dispatch.mock.calls[0][1]

    expect(dispatch).toHaveBeenCalledWith('expressionSessions/openSession', expect.objectContaining({
      ruleId: 7,
      sourceKey: wrapper.vm.expressionSourceKey,
      title: '决策表 · 表达式',
      draft: source,
      returnRoute: {
        name: 'DecisionTable',
        params: { id: '7' },
        query: { sourceType: 'REVISION', sourceId: '19' }
      }
    }))
    expect(payload.sourceKey).not.toContain('undefined')
    expect(payload.draft).not.toBe(source)
    expect(push).toHaveBeenCalledWith({
      name: 'ExpressionEditor',
      params: { ruleId: '7', sessionId: payload.sessionId }
    })
    expect(wrapper.vm.editorVisible).toBe(false)
  })

  test('同一规则中的多个选择器使用独立会话', async() => {
    const dispatch = vi.fn().mockResolvedValue()
    const mocks = {
      $route: { path: '/designer/table/7', params: { id: '7' }, meta: { title: '决策表设计器' } },
      $router: { push: vi.fn() },
      $store: { dispatch, getters: {} }
    }
    const first = mountPicker({}, { mocks })
    const second = mountPicker({}, { mocks })

    await first.vm.openEditor()
    await second.vm.openEditor()

    const sourceKeys = dispatch.mock.calls.map(call => call[1].sourceKey)
    expect(new Set(sourceKeys).size).toBe(2)
    expect(sourceKeys.every(sourceKey => !sourceKey.includes('undefined'))).toBe(true)
  })

  test('多个规则设计器会话分别记录自己的精确返回位置', async() => {
    const dispatch = vi.fn().mockResolvedValue()
    const first = mountPicker({}, {
      mocks: {
        $route: {
          name: 'DecisionTable',
          path: '/designer/table/7',
          params: { id: '7' },
          query: { sourceType: 'VERSION', sourceId: '17' },
          meta: { title: '决策表设计器' }
        },
        $router: { push: vi.fn() },
        $store: { dispatch, getters: {} }
      }
    })
    const second = mountPicker({}, {
      mocks: {
        $route: {
          name: 'RuleSet',
          path: '/designer/ruleset/8',
          params: { id: '8' },
          query: { sourceType: 'REVISION', sourceId: '28' },
          meta: { title: '规则集设计器' }
        },
        $router: { push: vi.fn() },
        $store: { dispatch, getters: {} }
      }
    })

    await first.vm.openEditor()
    await second.vm.openEditor()

    expect(dispatch.mock.calls.map(call => call[1].returnRoute)).toEqual([
      {
        name: 'DecisionTable',
        params: { id: '7' },
        query: { sourceType: 'VERSION', sourceId: '17' }
      },
      {
        name: 'RuleSet',
        params: { id: '8' },
        query: { sourceType: 'REVISION', sourceId: '28' }
      }
    ])
  })

  test('缓存设计器恢复后只回填一次最新编译修订', async() => {
    const dispatch = vi.fn().mockResolvedValue()
    const pending = {
      operand: { kind: 'PATH', value: 'request.score' },
      compiledScript: 'request.score',
      revision: 2
    }
    const wrapper = mountPicker({}, {
      mocks: {
        $route: { path: '/designer/table/7', params: { id: '7' } },
        $router: { push: vi.fn() },
        $store: {
          dispatch,
          getters: {
            'expressionSessions/pendingCompiledResult': vi.fn(() => pending)
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
        $router: { push: vi.fn() },
        $store: { dispatch: vi.fn(), getters: {} }
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

  test('外部清空值时退出手输状态并回到字段选择器', async() => {
    const wrapper = mountPicker({ expectedType: 'NUMBER' })

    wrapper.vm.openManualInput('LITERAL')
    wrapper.vm.updateManualValue('600')
    await wrapper.setProps({
      value: { kind: 'LITERAL', value: '600', valueType: 'NUMBER' }
    })
    await wrapper.setProps({ value: null })

    expect(wrapper.vm.manualKind).toBe('')
    expect(wrapper.vm.manualOperand).toBeNull()
    expect(wrapper.find('.operand-manual-editor').exists()).toBe(false)
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
