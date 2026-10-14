import { shallowMount } from '@test-utils'
import OperandPicker from '@/components/common/OperandPicker.vue'

const focusManualInput = vi.fn()

function mountPicker(propsData = {}, options = {}) {
  return shallowMount(OperandPicker, {
    props: { value: null, vars: [], functions: [], allowedKinds: ['LITERAL', 'PATH', 'REFERENCE', 'FUNCTION', 'OPERATION'], ...propsData },
    mocks: options.mocks,
    stubs: {
      VarPicker: { name: 'VarPicker', props: ['editorMode', 'editorKind', 'value'], template: '<div><slot name="editor" :visible="true" /></div>', methods: { openPopover: vi.fn(), closePopover: vi.fn() } },
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
  test('历史路径中误存的纯 JSON 数组可打开阈值草稿，应用前不改写', () => {
    const text = '["0000", "B0013", 9007199254740993]'
    const wrapper = mountPicker({ value: { kind: 'PATH', value: text } })
    wrapper.vm.openManualInput('LITERAL')
    expect(wrapper.vm.manualOperand).toEqual({ kind: 'LITERAL', valueType: 'LIST', value: text })
    expect(wrapper.emitted('input')).toBeUndefined()
    wrapper.unmount()
  })
  test('纯数组节点在阈值面板编辑时保留字符串编码、数字精度及嵌套内容', () => {
    const value = { kind: 'ARRAY', items: [
      { kind: 'LITERAL', valueType: 'STRING', value: '0000' },
      { kind: 'LITERAL', valueType: 'NUMBER', value: '9007199254740993' },
      { kind: 'ARRAY', items: [{ kind: 'LITERAL', valueType: 'BOOLEAN', value: false }] },
    ] }
    const wrapper = mountPicker({ value })
    wrapper.vm.openManualInput('LITERAL')
    expect(wrapper.vm.manualKind).toBe('LITERAL')
    expect(wrapper.vm.manualOperand.valueType).toBe('LIST')
    expect(wrapper.vm.manualInputText).toContain('"0000"')
    expect(wrapper.vm.manualInputText).toContain('9007199254740993')
    expect(wrapper.vm.manualInputText).toContain('false')
    expect(wrapper.emitted('input')).toBeUndefined()
    wrapper.unmount()
  })
  test('函数通用 Object 参数可接收任意类型，LIST 参数仍保持限制', () => {
    const wrapper = mountPicker({ value: { kind: 'FUNCTION', parameterTypes: ['OBJECT', 'LIST'], args: [null, null] } })
    expect(wrapper.vm.argumentType(0)).toBe('')
    expect(wrapper.vm.argumentType(1)).toBe('LIST')
    wrapper.unmount()
  })
  test('所有阈值使用同一个 Monaco，类型切换不重建编辑器或改写内容', async () => {
    const wrapper = mountPicker()
    try {
      wrapper.vm.openManualInput('LITERAL')
      await wrapper.vm.$nextTick()
      const editor = wrapper.getComponent({ name: 'MonacoEditor' })
      for (const [text, type, language] of [
        ['00123', 'STRING', 'plaintext'],
        ['9007199254740993', 'NUMBER', 'plaintext'],
        ['false', 'BOOLEAN', 'plaintext'],
        ['[9007199254740993,2]', 'LIST', 'json'],
        ['{"amount":9007199254740993}', 'OBJECT', 'json'],
        ['第一行\n第二行', 'STRING', 'plaintext'],
      ]) {
        editor.vm.$emit('input', text)
        await wrapper.vm.$nextTick()
        expect(wrapper.vm.manualOperand).toMatchObject({ value: text, valueType: type })
        expect(editor.props('value')).toBe(text)
        expect(editor.props('language')).toBe(language)
        expect(wrapper.getComponent({ name: 'MonacoEditor' }).vm).toBe(editor.vm)
      }
      wrapper.vm.selectManualType('DATE')
      editor.vm.$emit('input', '2026-10-10')
      await wrapper.vm.$nextTick()
      expect(wrapper.getComponent({ name: 'MonacoEditor' }).vm).toBe(editor.vm)
      expect(editor.props('value')).toBe('2026-10-10')
      wrapper.vm.openManualInput('PATH')
      await wrapper.vm.$nextTick()
      expect(wrapper.findComponent({ name: 'MonacoEditor' }).exists()).toBe(false)
      expect(wrapper.findComponent({ name: 'ElInput' }).exists()).toBe(true)
      wrapper.vm.openManualInput('LITERAL')
      await wrapper.vm.$nextTick()
      expect(wrapper.getComponent({ name: 'MonacoEditor' }).props('value')).toBe('2026-10-10')
      expect(wrapper.vm.manualTypeMode).toBe('DATE')
      expect(wrapper.emitted().input).toBeUndefined()
    } finally { wrapper.unmount() }
  })
  test('写入路径遵循当前位置的分类限制，不能引用只读模型', () => {
    const wrapper = mountPicker({ writableOnly: true, vars: [{ _varId: 1, _refType: 'MODEL', varCode: 'model', varType: 'OBJECT', _ref: { category: 'model' } }] })
    wrapper.vm.openManualInput('PATH')
    wrapper.vm.updateManualValue('model.score')
    wrapper.vm.finishManualInput()
    expect(wrapper.vm.manualError).toContain('未找到对应字段')
    expect(wrapper.emitted().input).toBeUndefined()
    wrapper.unmount()
  })
  test('同名路径需明确选择，切换面板保留该选择，只有应用才回填', () => {
    const vars = [1, 2].map(id => ({ _varId: id, _refType: 'VARIABLE', varCode: 'same', varLabel: '字段' + id }))
    const wrapper = mountPicker({ vars })
    wrapper.vm.openManualInput('PATH')
    wrapper.vm.updateManualValue('same')
    wrapper.vm.finishManualInput()
    expect(wrapper.vm.manualPathCandidates).toHaveLength(2)
    expect(wrapper.emitted().input).toBeUndefined()
    wrapper.vm.selectManualPathCandidate(vars[1])
    wrapper.vm.openManualInput('LITERAL')
    wrapper.vm.updateManualValue('3.5')
    wrapper.vm.openManualInput('PATH')
    expect(wrapper.vm.manualOperand.refId).toBe(2)
    wrapper.vm.finishManualInput()
    expect(wrapper.emitted().input.at(-1)[0].refId).toBe(2)
    wrapper.unmount()
  })
  test('写入路径保留选择器及输入焦点，鼠标移动不提交或销毁草稿', async () => {
    const wrapper = mountPicker({ vars: [{ _varId: 1, _refType: 'VARIABLE', varCode: 'age', varLabel: '年龄', varType: 'NUMBER' }] })
    wrapper.vm.openManualInput('PATH')
    await wrapper.vm.$nextTick()
    const picker = wrapper.findComponent({ name: 'VarPicker' })
    expect(picker.exists()).toBe(true)
    expect(picker.props('editorMode')).toBe(true)
    expect(picker.props('editorKind')).toBe('PATH')
    wrapper.vm.updateManualValue('age')
    document.dispatchEvent(new Event('pointermove'))
    expect(wrapper.vm.manualKind).toBe('PATH')
    expect(wrapper.emitted().input).toBeUndefined()
    wrapper.vm.finishManualInput()
    expect(wrapper.emitted().input.at(-1)[0]).toMatchObject({ refId: 1, code: 'age', resolved: true })
    wrapper.unmount()
  })
  test('阈值在面板中配置，输入法尚未结束时不能应用', async () => {
    const wrapper = mountPicker({ value: { kind: 'LITERAL', value: '低风险', valueType: 'STRING' } })
    try {
      wrapper.vm.openManualInput('LITERAL')
      await wrapper.vm.$nextTick()
      await wrapper.get('.operand-manual-panel').trigger('compositionstart')
      wrapper.vm.finishManualInput()
      expect(wrapper.vm.manualKind).toBe('LITERAL')
      expect(wrapper.emitted().input).toBeUndefined()
      await wrapper.get('.operand-manual-panel').trigger('compositionend')
      wrapper.vm.finishManualInput()
      expect(wrapper.vm.manualKind).toBe('')
      expect(wrapper.emitted().input.at(-1)[0].value).toBe('低风险')
    } finally { wrapper.unmount() }
  })
  test('阈值编辑完成后收起为摘要，保留 false 和数字零', async () => {
    for (const value of [false, 0]) {
      const wrapper = mountPicker({ value: { kind: 'LITERAL', value, valueType: typeof value === 'boolean' ? 'BOOLEAN' : 'NUMBER' } })
      wrapper.vm.openManualInput('LITERAL')
      await wrapper.vm.$nextTick()
      wrapper.vm.finishManualInput()
      expect(wrapper.vm.manualKind).toBe('')
      expect(wrapper.emitted().input.at(-1)[0].value).toBe(value)
      wrapper.unmount()
    }
  })
  test('路径输入先做最长匹配，应用时向页面提交带稳定 ID 的引用', () => {
    const wrapper = mountPicker({ vars: [
      { varCode: 'model', varLabel: '模型', _varId: 10, _refType: 'MODEL', varType: 'OBJECT' },
      { varCode: 'model.scores', varLabel: '评分列表', _varId: 11, _refType: 'DATA_OBJECT', varType: 'LIST' },
    ] })
    wrapper.vm.openManualInput('PATH')
    wrapper.vm.updateManualValue('model.scores[0].score')
    expect(wrapper.emitted().input).toBeUndefined()
    wrapper.vm.finishManualInput()
    expect(wrapper.emitted().input.at(-1)[0]).toMatchObject({ refId: 11, relativePath: '[0].score', resolved: true })
    wrapper.unmount()
  })
  test.each([
    { kind: 'LITERAL', value: '', valueType: 'STRING' },
    { kind: 'LITERAL', value: 0, valueType: 'NUMBER' },
    { kind: 'LITERAL', value: false, valueType: 'BOOLEAN' },
    { kind: 'PATH', value: 'request.age', code: 'request.age', refId: 8, refType: 'DATA_OBJECT', resolved: true },
  ])('重新编辑 $kind 保留原值、类型和引用，不因打开而写回', async value => {
    const wrapper = mountPicker({ value })
    wrapper.vm.openManualInput(value.kind)
    await wrapper.vm.$nextTick()
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.manualOperand).toEqual(value)
    expect(wrapper.vm.manualOperand).not.toBe(value)
    expect(wrapper.findComponent({ name: 'VarPicker' }).props('editorMode')).toBe(true)
    expect(wrapper.emitted().input).toBeUndefined()
    wrapper.unmount()
  })

  test('手输编辑器随外部切换节点同步新的阈值，不残留上一个节点内容', async () => {
    const wrapper = mountPicker({ value: { kind: 'LITERAL', value: 'old', valueType: 'NUMBER' } })
    wrapper.vm.openManualInput('LITERAL')
    await wrapper.setProps({ value: { kind: 'LITERAL', value: 'new', valueType: 'NUMBER' } })
    expect(wrapper.vm.manualOperand).toEqual({ kind: 'LITERAL', value: 'new', valueType: 'NUMBER' })
    expect(wrapper.vm.manualInputText).toBe('new')
    wrapper.unmount()
  })

  test('同编码方法按稳定 ID 选择，不能回退到列表中的另一个同名方法', () => {
    const wrapper = mountPicker({ functions: [
      { id: 1, funcCode: 'same', paramsJson: '[]' },
      { id: 2, funcCode: 'same', paramsJson: '[{"name":"a","type":"NUMBER"}]' },
    ] })
    expect(wrapper.vm.findFunction({ functionId: 2, functionCode: 'same' }).id).toBe(2)
    expect(wrapper.vm.findFunction({ functionId: 999, functionCode: 'same' })).toBeNull()
    wrapper.unmount()
  })

  test('文本阈值允许明确应用空字符串，打开面板本身不修改绑定', () => {
    const wrapper = mountPicker({ expectedType: 'STRING' })
    wrapper.vm.openManualInput('LITERAL')
    expect(wrapper.emitted().input).toBeUndefined()
    wrapper.vm.finishManualInput()
    expect(wrapper.emitted().input?.at(-1)?.[0]).toEqual({ kind: 'LITERAL', value: '', valueType: 'STRING' })
    wrapper.unmount()
  })
  beforeEach(() => {
    focusManualInput.mockClear()
  })

  test('函数先选入输入框，点击具体参数后配置并保留函数 ID', async () => {
    const fn = { id: 9, funcCode: 'max', paramsJson: '[{"name":"a","type":"NUMBER"},{"name":"b","type":"NUMBER"}]' }
    const wrapper = mountPicker({ functions: [fn] })
    wrapper.vm.onQuickInput({ kind: 'FUNCTION', functionId: 9, functionCode: 'max', args: [] })
    const value = wrapper.emitted().input[0][0]
    expect(value.args).toHaveLength(2)
    expect(wrapper.vm.editorVisible).toBe(false)
    await wrapper.setProps({ value })
    wrapper.vm.openArgument(1)
    expect(wrapper.vm.argumentType(1)).toBe('NUMBER')
    wrapper.vm.updateArgument({ kind: 'REFERENCE', refType: 'VARIABLE', refId: '9007199254740993', valueType: 'INTEGER' })
    expect(wrapper.emitted().input.at(-1)[0]).toMatchObject({ functionId: 9, args: [value.args[0], { refId: '9007199254740993' }] })
    wrapper.unmount()
  })

  test('无参数函数和普通字段保持一次点击完成', () => {
    const wrapper = mountPicker({ functions: [{ id: 10, funcCode: 'currentDate', paramsJson: '[]' }] })
    wrapper.vm.onQuickInput({ kind: 'FUNCTION', functionId: 10, functionCode: 'currentDate', args: [] })
    wrapper.vm.onQuickInput({ kind: 'REFERENCE', refId: 2, refType: 'VARIABLE', code: 'amount' })

    expect(wrapper.emitted().input).toHaveLength(2)
    expect(wrapper.vm.editorVisible).toBe(false)
  })

  test('LIST 和 JSON 使用 Monaco，非法 JSON 不覆盖已保存值', async () => {
    const wrapper = mountPicker({ expectedType: 'LIST' })
    wrapper.vm.openManualInput('LITERAL')
    await wrapper.vm.$nextTick()
    expect(wrapper.findComponent({ name: 'MonacoEditor' }).exists()).toBe(true)
    wrapper.vm.updateManualValue('[1,')
    wrapper.vm.finishManualInput()
    expect(wrapper.vm.manualError).toBeTruthy()
    expect(wrapper.emitted().input).toBeUndefined()
    wrapper.vm.updateManualValue('[1, 2]')
    wrapper.vm.finishManualInput()
    expect(wrapper.emitted().input.at(-1)[0]).toMatchObject({ value: '[1, 2]', valueType: 'LIST' })
    await wrapper.setProps({ value: wrapper.emitted().input.at(-1)[0] })
    wrapper.vm.openManualInput('LITERAL')
    wrapper.vm.updateManualValue('{}')
    wrapper.vm.finishManualInput()
    expect(wrapper.vm.manualError).toBe('请输入合法的 JSON 数组')
    expect(wrapper.emitted().input).toHaveLength(1)
    wrapper.unmount()
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

    await wrapper.vm.openFullPageEditor()
    const payload = dispatch.mock.calls[0][1]

    expect(dispatch).toHaveBeenCalledWith('expressionSessions/openSession', expect.objectContaining({
      ruleId: '7',
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

    await first.vm.openFullPageEditor()
    await second.vm.openFullPageEditor()

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

    await first.vm.openFullPageEditor()
    await second.vm.openFullPageEditor()

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

  test('管理配置也进入独立表达式页并记录返回位置', async() => {
    const wrapper = mountPicker({}, {
      mocks: {
        $route: { path: '/variable', params: {} },
        $router: { push: vi.fn() },
        $store: { dispatch: vi.fn(), getters: {} }
      }
    })

    await wrapper.vm.openFullPageEditor()

    expect(wrapper.vm.editorVisible).toBe(false)
    expect(wrapper.vm.$store.dispatch).toHaveBeenCalledWith('expressionSessions/openSession', expect.objectContaining({ ruleId: null }))
    expect(wrapper.vm.$router.push).toHaveBeenCalledWith({ name: 'ConfigurationExpressionEditor', params: { sessionId: wrapper.vm.expressionSessionId } })
  })

  test('阈值面板自动推荐且尊重用户手选类型，应用前不改输入框', async() => {
    const wrapper = mountPicker()

    wrapper.vm.openManualInput('LITERAL')
    await wrapper.vm.$nextTick()

    expect(wrapper.find('.operand-manual-panel').exists()).toBe(true)
    expect(wrapper.vm.editorVisible).toBe(false)
    wrapper.vm.updateManualValue('123.5')
    expect(wrapper.vm.manualOperand.valueType).toBe('NUMBER')
    wrapper.vm.selectManualType('STRING')
    wrapper.vm.updateManualValue('456')
    expect(wrapper.vm.manualOperand.valueType).toBe('STRING')
    wrapper.vm.selectManualType('AUTO')
    expect(wrapper.vm.manualOperand.valueType).toBe('NUMBER')

    wrapper.vm.patchManualOperand({ valueType: 'BOOLEAN' })
    wrapper.vm.patchManualOperand({ value: 'true' })
    expect(wrapper.emitted().input).toBeUndefined()
    wrapper.vm.finishManualInput()

    const emitted = wrapper.emitted().input
    expect(emitted[emitted.length - 1][0]).toEqual({ kind: 'LITERAL', value: 'true', valueType: 'BOOLEAN' })
    expect(wrapper.vm.editorVisible).toBe(false)
  })

  test('节点切换为引用时退出旧阈值编辑，不能继续编辑上一个节点的值', async() => {
    const wrapper = mountPicker({ value: { kind: 'LITERAL', value: '5', valueType: 'NUMBER' } })
    wrapper.vm.openManualInput('LITERAL')
    await wrapper.setProps({ value: { kind: 'REFERENCE', refId: 6, refType: 'VARIABLE', code: 'idcard_no', valueType: 'STRING' } })
    expect(wrapper.vm.manualKind).toBe('')
    expect(wrapper.vm.manualOperand).toBeNull()
    expect(wrapper.find('.operand-manual-panel').exists()).toBe(false)
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
    expect(wrapper.find('.operand-manual-panel').exists()).toBe(false)
  })

  test('路径面板录入并按稳定 ID 解析后应用', () => {
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
    wrapper.vm.finishManualInput()

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
    wrapper.vm.finishManualInput()

    const events = wrapper.emitted()['path-resolve']
    expect(events).toHaveLength(1)
    expect(events[0][0]).toMatchObject({
      operand: { value: 'request.hits', valueType: 'LIST', refId: 18, refType: 'DATA_OBJECT', resolved: true },
      candidates: []
    })
  })
})
