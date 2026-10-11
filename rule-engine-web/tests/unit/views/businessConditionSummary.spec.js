import { shallowMount } from '@test-utils'
import GatewayBranchEditor from '@/components/flow/GatewayBranchEditor.vue'
import Scorecard from '@/views/designer/Scorecard.vue'
const TraceTree = (await vi.importActual('../../../src/components/common/TraceTree.vue')).default

const ref = (code = 'age', label = '年龄', id = 19) => ({ kind: 'REFERENCE', refId: id, refType: 'VARIABLE', code, value: code, label, valueType: 'NUMBER' })
const literal = value => ({ kind: 'LITERAL', valueType: 'NUMBER', value })
const leaf = (code, operator, value) => ({ type: 'leaf', leftOperand: ref(code, code), operator, rightOperand: literal(value) })

test('残缺网关区间必须显示待完善，不能被摘要称为默认分支', async () => {
  const condition = { type: 'group', op: 'AND', children: [{ type: 'leaf', leftOperand: ref(), operator: 'between', rightOperand: literal('18,') }] }
  const wrapper = shallowMount(GatewayBranchEditor, { props: {
    edge: { id: 'branch', properties: { conditionExpr: '', conditionConfig: condition } },
    index: 0, count: 1, priority: 1, parseConditionConfig: config => JSON.parse(JSON.stringify(config)),
  } })
  try {
    const summary = wrapper.find('.branch-condition-summary')
    expect(summary.text()).not.toContain('默认分支')
    expect(summary.text()).toMatch(/未完成|上界/)
    await wrapper.setProps({ edge: { id: 'branch', properties: { conditionExpr: '', conditionConfig: { type: 'group', op: 'AND', children: [] } } } })
    expect(summary.text()).toContain('默认分支')
  } finally { wrapper.unmount() }
})

test('结构化与实际追踪条件保留括号，集合的精确成员不能丢失', () => {
  const wrapper = shallowMount(TraceTree, { props: { modelType: 'TABLE' } })
  try {
    const tree = { type: 'group', op: 'AND', children: [
      { type: 'group', op: 'OR', children: [leaf('a', '==', '1'), leaf('b', '==', '2')] }, leaf('c', '==', '3'),
    ] }
    expect(wrapper.vm._modelConditionText(tree)).toContain('(a 等于 1 或 b 等于 2) 且 c 等于 3')
    const traceLeaf = code => ({ type: 'OPERATOR', token: '==', children: [{ type: 'VARIABLE', token: code }, { type: 'VALUE', value: 1 }] })
    const trace = { type: 'OPERATOR', token: '&&', children: [{ type: 'OPERATOR', token: '||', children: [traceLeaf('a'), traceLeaf('b')] }, traceLeaf('c')] }
    expect(wrapper.vm._traceCondText(trace)).toContain('(a 等于 1 或 b 等于 1) 且 c 等于 1')
  } finally { wrapper.unmount() }
})

test('结构化追踪的集合成员与精确数值转换均能解释而非显示空白', () => {
  const wrapper = shallowMount(TraceTree, { props: { modelType: 'TABLE' } })
  try {
    const collection = { kind: 'ARRAY', items: [{ kind: 'LITERAL', valueType: 'STRING', value: 'A,B' }, { kind: 'LITERAL', valueType: 'STRING', value: '' },
      { kind: 'CAST', targetType: 'NUMBER', operand: { kind: 'LITERAL', valueType: 'STRING', value: '9007199254740993.123456789' } }] }
    const text = wrapper.vm._modelOperandText(collection)
    expect(text).toContain('"A,B"')
    expect(text).toContain('""')
    expect(text).toContain('9007199254740993.123456789')
    expect(text).not.toContain('toNumberValue')
  } finally { wrapper.unmount() }
})

test('相同字段的多项AND条件使用摘要列，避免分列只剩最后一个边界', () => {
  const wrapper = shallowMount(TraceTree, { props: { modelType: 'TABLE', definitionModel: { hitPolicy: 'FIRST', rules: [{
    conditionRoot: { type: 'group', op: 'AND', children: [leaf('age', '>=', '18'), leaf('age', '<=', '60')] }, actions: [],
  }] } } })
  try {
    expect(wrapper.vm.tableUseCondSummary).toBe(true)
    expect(wrapper.vm.tableCondSummaryText(wrapper.vm.tableRules[0])).toContain('age 大于等于 18 且 age 小于等于 60')
  } finally { wrapper.unmount() }
})

test('评分卡公式展示ID同步后的字段名称而不是旧condVar或评分项标题', async () => {
  const model = { initialScore: 0, thresholds: [], resultVar: { varCode: 'oldResult', operand: ref('oldResult', '旧结果', 20) },
    scoreItems: [{ conditionLabel: '客户自己的评分项名称', condVar: 'oldAge', condOperator: 'between', condValue: '18,60',
      leftOperand: ref('oldAge', '旧年龄'), rightOperand: literal('18,60'), score: 10, weight: 1 }] }
  Scorecard.methods._syncModelVarRefs.call({ model, $forceUpdate() {}, varPickerOptions: [
    { _varId: 19, _refType: 'VARIABLE', varCode: 'ageRenamed', varLabel: '最新年龄', varType: 'NUMBER' },
    { _varId: 20, _refType: 'VARIABLE', varCode: 'resultRenamed', varLabel: '最新评分', varType: 'NUMBER' },
  ] })
  const wrapper = shallowMount({ ...Scorecard, methods: { ...Scorecard.methods, loadContent() {} } }, {
    mocks: { $route: { params: {} }, $router: { back() {} } },
  })
  try {
    await wrapper.setData({ model, contentLoaded: true })
    const formula = wrapper.find('.formula-text').text()
    expect(formula).toContain('最新年龄 ageRenamed')
    expect(formula).toContain('最新评分 resultRenamed')
    expect(formula).toContain('介于')
    expect(formula).not.toContain('IF(')
    expect(model.scoreItems[0].conditionLabel).toBe('客户自己的评分项名称')
    expect(model.scoreItems[0].condVar).toBe('oldAge')
    expect(model.scoreItems[0].leftOperand.refId).toBe(19)
  } finally { wrapper.unmount() }
})

test('评分卡旧自定义QL被旧反解器拆分后仍不伪称为已绑定的字段条件', () => {
  const original = 'riskAllowed(age) > 0 && score > 20'
  const model = { initialScore: 0, resultVar: { varCode: 'score' }, thresholds: [], scoreItems: [{ condition: original, conditionLabel: '客户自定义判断' }] }
  Scorecard.methods.normalizeModel.call({ model, parseCondition: Scorecard.methods.parseCondition })
  expect(Scorecard.methods.scoreConditionSummary(model.scoreItems[0])).toContain('自定义表达式')
  expect(model.scoreItems[0].condition).toBe(original)
})
