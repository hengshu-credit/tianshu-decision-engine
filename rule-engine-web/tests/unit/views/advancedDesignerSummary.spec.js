import { shallowMount, flushPromises } from '@test-utils'
import { ElInputNumber } from 'element-plus/es/components/input-number/index.mjs'
import { ElInput } from 'element-plus/es/components/input/index.mjs'
import AdvancedCrossTable from '@/views/designer/AdvancedCrossTable.vue'
import AdvancedScorecard from '@/views/designer/AdvancedScorecard.vue'

const literal = (value, valueType = 'NUMBER') => ({ kind: 'LITERAL', valueType, value })
const ref = (id = 19, code = 'age', label = '年龄', valueType = 'NUMBER') => ({ kind: 'REFERENCE', refId: id, refType: 'VARIABLE', code, value: code, label, valueType })
const dim = segments => ({ operand: ref(), varCode: 'age', varLabel: '客户自定义维度', varType: 'NUMBER', _varId: 19, segments })

async function mountDesigner(component, model, options = {}) {
  const wrapper = shallowMount({ ...component, methods: { ...component.methods, loadContent() {} } }, {
    mocks: { $route: { params: {} }, $router: { back() {} } },
    ...options,
  })
  await wrapper.setData({ model, contentLoaded: true })
  return wrapper
}

test('交叉表行列标题区分同阈值的不同条件，并保留客户分段标签和引用', async () => {
  const segments = [{ label: '客户甲', operator: '<', valueOperand: literal('18') }, { label: '', operator: '>=', valueOperand: literal('18') }]
  const model = { rowDimensions: [dim(segments)], colDimensions: [dim(segments)], resultVar: { operand: ref(20, 'result', '结果') }, cells: [] }
  const original = JSON.stringify(model)
  const wrapper = await mountDesigner(AdvancedCrossTable, model)
  try {
    expect(wrapper.vm.colHeaderRows[0].map(cell => cell.label)).toEqual(['客户甲（小于 18）', '大于等于 18'])
    expect(wrapper.vm.rowHeaderCells.map(cells => cells[0].label)).toEqual(['客户甲（小于 18）', '大于等于 18'])
    expect(wrapper.find('.act-matrix').text()).toContain('客户甲（小于 18）')
    expect(JSON.stringify(model)).toBe(original)
  } finally { wrapper.unmount() }
})

test.each(['group', 'dimension'])('实际%s权重控件加载和输入不把后端支持的小权重截断为零', async kind => {
  const model = { initialScore: 0, resultVar: { varCode: 'score' }, thresholds: [], dimensionGroups: [{ groupLabel: '精细权重', weight: 0.123456789,
    dimensions: [{ varLabel: '维度', weight: 0.123456789, rules: [] }] }] }
  const wrapper = await mountDesigner(AdvancedScorecard, model, { stubs: { 'el-input-number': ElInputNumber, 'el-input': ElInput } })
  try {
    await flushPromises()
    const target = kind === 'group' ? wrapper.vm.model.dimensionGroups[0] : wrapper.vm.model.dimensionGroups[0].dimensions[0]
    const input = wrapper.find((kind === 'group' ? '.group-weight-summary' : '.dim-weight-area') + ' input[role="spinbutton"]')
    expect(target.weight).toBe(0.123456789)
    expect.soft(input.element.value).toBe('0.123456789')
    await input.setValue('0.0049')
    expect(target.weight).toBe(0.0049)
    expect(input.element.value).toBe('0.0049')
    const saved = JSON.parse(wrapper.vm.serializeDesignerDraft()).dimensionGroups[0]
    expect((kind === 'group' ? saved : saved.dimensions[0]).weight).toBe(0.0049)
    await input.setValue('0')
    expect(target.weight).toBe(0)
  } finally { wrapper.unmount() }
})

test('交叉表区间标题按实际开闭边界显示精确上下界，残缺不称任意', async () => {
  const segments = ['[)', '()', '[]', '(]'].map(rangeBoundary => ({ operator: 'range', rangeBoundary,
    minOperand: literal('0'), maxOperand: literal('9007199254740993.123456789') }))
  segments.push({ operator: 'range', rangeBoundary: '[]', minOperand: literal('18'), maxOperand: literal(null) })
  const model = { rowDimensions: [dim(segments)], colDimensions: [dim([{ operator: '*', label: '全部' }])], resultVar: { operand: ref(20, 'result', '结果') }, cells: [] }
  const original = JSON.stringify(model)
  const wrapper = await mountDesigner(AdvancedCrossTable, model)
  try {
    expect(wrapper.vm.rowHeaderCells.slice(0, 4).map(cells => cells[0].label)).toEqual([
      '大于等于 0 且 小于 9007199254740993.123456789',
      '大于 0 且 小于 9007199254740993.123456789',
      '大于等于 0 且 小于等于 9007199254740993.123456789',
      '大于 0 且 小于等于 9007199254740993.123456789',
    ])
    expect(wrapper.vm.rowHeaderCells[4][0].label).toContain('条件未完成')
    expect(wrapper.vm.rowHeaderCells[4][0].label).not.toContain('任意')
    expect(wrapper.vm.colHeaderRows[0][0].label).toBe('全部（任意（无条件））')
    expect(JSON.stringify(model)).toBe(original)
  } finally { wrapper.unmount() }
})

test('交叉表区间引用和日期边界复用中文描述，不从编码反推ID', async () => {
  const row = dim([{ operator: 'range', rangeBoundary: '(]', minOperand: ref(21, 'lower', '下限'), maxOperand: ref(22, 'upper', '上限') }])
  const column = { ...dim([{ operator: 'range', rangeBoundary: '[)', minOperand: literal('2026-01-01', 'DATE'), maxOperand: literal('2026-12-31', 'DATE') }]), operand: ref(23, 'applyDate', '申请日期', 'DATE'), varType: 'DATE' }
  const wrapper = await mountDesigner(AdvancedCrossTable, { rowDimensions: [row], colDimensions: [column], resultVar: { operand: ref(20, 'result', '结果') }, cells: [] })
  try {
    expect(wrapper.vm.rowHeaderCells[0][0].label).toBe('大于 下限 lower 且 小于等于 上限 upper')
    expect(wrapper.vm.colHeaderRows[0][0].label).toBe('大于等于 "2026-01-01" 且 小于 "2026-12-31"')
    expect(row.segments[0].minOperand.refId).toBe(21)
    expect(row.segments[0].maxOperand.refId).toBe(22)
  } finally { wrapper.unmount() }
})

test('复杂评分卡公式与汇总显示实际权重精度，零权重不伪装成默认一', async () => {
  const model = { initialScore: 0, resultVar: { varCode: 'score', operand: ref(20, 'score', '评分') }, thresholds: [], dimensionGroups: [
    { groupLabel: '客户精细权重', weight: 0.123456789, dimensions: [{ operand: ref(), varLabel: '年龄', weight: 0.234567891, rules: [] }] },
    { groupLabel: '停用组', weight: 0, dimensions: [{ operand: ref(), varLabel: '零权重', weight: 0, rules: [] }] },
  ] }
  const original = JSON.stringify(model)
  const wrapper = await mountDesigner(AdvancedScorecard, model)
  try {
    expect(wrapper.find('.formula-text').text()).toContain('0.123456789')
    expect(wrapper.find('.formula-text').text()).toContain('0.234567891')
    expect(wrapper.findAll('.weight-detail-item')[0].text()).toContain('组权重 0.123456789')
    expect(wrapper.findAll('.weight-detail-item')[1].text()).toContain('组权重 0')
    expect(wrapper.findAll('.weight-detail-item')[1].text()).toContain('零权重:0')
    expect(wrapper.find('.weight-value').text()).not.toBe('0.03')
    expect(JSON.parse(wrapper.vm.serializeDesignerDraft())).toMatchObject(JSON.parse(original))
    expect(JSON.stringify(model)).toBe(original)
  } finally { wrapper.unmount() }
})

test.each([
  [[0.1, 0.2], '0.3'],
  [[0.0049], '0.0049'],
  [[0], '0'],
  [[1e-15], '1e-15'],
])('派生总权重避免浮点噪声并标注约值，实际权重%s保持不变', async (weights, expected) => {
  const model = { initialScore: 0, resultVar: { varCode: 'score' }, thresholds: [], dimensionGroups: [{ groupLabel: '汇总', weight: 1,
    dimensions: weights.map((weight, index) => ({ varLabel: '维度' + index, weight, rules: [] })) }] }
  const original = JSON.stringify(model)
  const wrapper = await mountDesigner(AdvancedScorecard, model)
  try {
    expect(wrapper.find('.weight-value').text()).toBe(expected)
    expect(wrapper.find('.weight-label-sm').text()).toContain('约')
    for (const weight of weights) expect(wrapper.find('.weight-detail-dims').text()).toContain(String(weight))
    expect(JSON.stringify(model)).toBe(original)
    expect(JSON.parse(wrapper.vm.serializeDesignerDraft()).dimensionGroups).toEqual(model.dimensionGroups)
  } finally { wrapper.unmount() }
})
