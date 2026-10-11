import { shallowMount } from '@test-utils'
import ExpectedOutputEditor from '@/components/test/ExpectedOutputEditor.vue'
import ConditionRangeInput from '@/components/common/ConditionRangeInput.vue'
import ConditionCollectionInput from '@/components/common/ConditionCollectionInput.vue'
import { parseExactJson, stringifyExactJson } from '@/utils/exactJson'
import { ElAlert } from 'element-plus/es/components/alert/index.mjs'

const outputs = [
  { refType: 'VARIABLE', refId: '9007199254740993', scriptName: 'score', label: '风险分', valueType: 'NUMBER' },
  { refType: 'VARIABLE', refId: '2', scriptName: 'decision', label: '决策', valueType: 'STRING' },
  { refType: 'MODEL', refId: '3', scriptName: 'accepted', label: '通过', valueType: 'BOOLEAN' },
]
const scalar = (value, valueType = 'NUMBER') => ({ kind: 'LITERAL', valueType, value })
const check = (extra = {}) => ({ refType: 'VARIABLE', refId: '9007199254740993', valueType: 'NUMBER', operator: '==', expected: scalar('18'), ...extra })
const config = (...checks) => stringifyExactJson({ version: 1, mode: 'FIELDS', checks })
function mountEditor(props = {}) {
  let wrapper
  wrapper = shallowMount(ExpectedOutputEditor, {
    props: { outputs, ...props, 'onUpdate:modelValue': value => wrapper.setProps({ modelValue: value }), 'onUpdate:snapshotJson': value => wrapper.setProps({ snapshotJson: value }) },
    stubs: { 'el-alert': ElAlert, ConditionRangeInput, ConditionCollectionInput },
  })
  return wrapper
}
const current = wrapper => parseExactJson(wrapper.emitted('update:modelValue').at(-1)[0])

describe('ExpectedOutputEditor', () => {
  test('旧执行快照的深调试追踪不占业务预算，原JSON不改写或迁移', async () => {
    let deep = { debug: true }
    for (let index = 0; index < 160; index++) deep = { next: deep }
    const snapshot = JSON.stringify({ data: { success: true, result: { score: 88, traces: ['business'] }, traces: [deep] } })
    const wrapper = mountEditor({ modelValue: null, snapshotJson: snapshot })
    try {
      expect(wrapper.vm.validate().valid).toBe(true)
      expect(wrapper.props('snapshotJson')).toBe(snapshot)
      expect(wrapper.emitted('update:snapshotJson')).toBeUndefined()
      expect(wrapper.emitted('update:modelValue')).toBeUndefined()
      await wrapper.setProps({ snapshotJson: JSON.stringify({ data: { success: true, result: { traces: deep }, traces: [] } }) })
      expect(wrapper.vm.validate().valid).toBe(false)
      await wrapper.setProps({ snapshotJson: '{"data":{"success":true,"result":88,"traces":[{"a":1,"a":2}]}}' })
      expect(wrapper.vm.validate().valid).toBe(false)
    } finally { wrapper.unmount() }
  })

  test('旧null配置沿用JSON快照，打开或收到实际结果不自动迁移或改写预期', async () => {
    const snapshot = '{"data":{"result":{"score":9007199254740993}}}'
    const wrapper = mountEditor({ snapshotJson: snapshot })
    try {
      expect(wrapper.find('[data-testid="expectation-snapshot"]').exists()).toBe(true)
      expect(wrapper.emitted('update:modelValue')).toBeUndefined()
      await wrapper.setProps({ actualResponseJson: '{"data":{"result":{"score":9007199254740995}}}' })
      expect(wrapper.emitted('update:snapshotJson')).toBeUndefined()
      await wrapper.find('[data-testid="expectation-use-actual"]').trigger('click')
      expect(wrapper.emitted('update:snapshotJson').at(-1)[0]).toBe('{"data":{"result":{"score":9007199254740995}}}')
      expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    } finally { wrapper.unmount() }
  })

  test('新增字段校验按引用类型与精确ID绑定，数字始终保存原文本', async () => {
    const wrapper = mountEditor({ modelValue: '{"version":1,"mode":"NONE"}' })
    try {
      wrapper.findComponent('[data-testid="expectation-mode"]').vm.$emit('update:modelValue', 'FIELDS')
      await wrapper.vm.$nextTick()
      await wrapper.find('[data-testid="expectation-add-check"]').trigger('click')
      wrapper.findComponent('[data-testid="expectation-field-0"]').vm.$emit('update:modelValue', 'VARIABLE:9007199254740993')
      await wrapper.vm.$nextTick()
      wrapper.findComponent('[data-testid="expectation-value-0"]').vm.$emit('update:modelValue', '9007199254740993.123456789012345678901')
      await wrapper.vm.$nextTick()
      const saved = current(wrapper).checks[0]
      expect(saved.refId).toBe('9007199254740993')
      expect(saved.refType).toBe('VARIABLE')
      expect(saved.expected).toEqual(scalar('9007199254740993.123456789012345678901'))
      expect(wrapper.vm.validate().valid).toBe(true)
    } finally { wrapper.unmount() }
  })

  test('ID失效或输出类型变化时保留原配置并明确报错，不按同名字段重绑', async () => {
    const value = config(check())
    const wrapper = mountEditor({ modelValue: value, outputs: [{ ...outputs[0], refId: '99' }] })
    try {
      expect(wrapper.text()).toContain('引用已失效')
      expect(wrapper.vm.validate().valid).toBe(false)
      expect(wrapper.emitted('update:modelValue')).toBeUndefined()
      await wrapper.setProps({ outputs: [{ ...outputs[0], valueType: 'STRING' }] })
      expect(wrapper.text()).toContain('类型已变化')
      expect(wrapper.props('modelValue')).toBe(value)
      expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    } finally { wrapper.unmount() }
  })

  test('用户切换字段仍保留原预期，类型不匹配必须明确改值而非静默清空', async () => {
    const wrapper = mountEditor({ modelValue: config(check()) })
    try {
      wrapper.findComponent('[data-testid="expectation-field-0"]').vm.$emit('update:modelValue', 'VARIABLE:2')
      await wrapper.vm.$nextTick()
      expect(current(wrapper).checks[0].expected).toEqual(scalar('18'))
      expect(wrapper.vm.validate().valid).toBe(false)
      expect(wrapper.text()).toContain('预期值类型')
    } finally { wrapper.unmount() }
  })

  test('区间复用两个边界输入，缺边界阻断校验，完整高精度边界不经JS数字', async () => {
    const wrapper = mountEditor({ modelValue: config(check({ operator: 'between', expected: scalar('18,') })) })
    try {
      expect(wrapper.findComponent(ConditionRangeInput).exists()).toBe(true)
      expect(wrapper.vm.validate().valid).toBe(false)
      wrapper.findComponent('[data-testid="condition-range-upper"]').vm.$emit('update:modelValue', '9007199254740993.123456789')
      await wrapper.vm.$nextTick()
      expect(current(wrapper).checks[0].expected.value).toBe('18,9007199254740993.123456789')
      expect(wrapper.vm.validate().valid).toBe(true)
    } finally { wrapper.unmount() }
  })

  test('集合复用逐项编辑并准确说明预期语义，空串与逗号成员原样保存', async () => {
    const expected = { kind: 'ARRAY', valueType: 'LIST', items: [scalar('A,B', 'STRING'), scalar('', 'STRING'), { kind: 'CAST', targetType: 'NUMBER', operand: scalar('9007199254740993.123456789', 'STRING') }] }
    const wrapper = mountEditor({ modelValue: config(check({ refId: '2', valueType: 'STRING', operator: 'in', expected })) })
    try {
      expect(wrapper.findComponent(ConditionCollectionInput).exists()).toBe(true)
      expect(wrapper.text()).toContain('空字符串也按原值比较')
      expect(wrapper.text()).not.toContain('不会因此命中集合')
      wrapper.findComponent('[data-testid="collection-value-0"]').vm.$emit('update:modelValue', ' comma, value ')
      await wrapper.vm.$nextTick()
      expect(current(wrapper).checks[0].expected.items).toEqual([scalar(' comma, value ', 'STRING'), ...expected.items.slice(1)])
      expect(wrapper.vm.validate().valid).toBe(true)
    } finally { wrapper.unmount() }
  })

  test('缺失与null比较不需要预期值，不把全部未配置的校验显示为通过', async () => {
    const wrapper = mountEditor({ modelValue: config(check({ operator: 'is_missing', expected: null })) })
    try {
      expect(wrapper.find('[data-testid="expectation-value-0"]').exists()).toBe(false)
      expect(wrapper.vm.validate().valid).toBe(true)
      expect(wrapper.text()).not.toContain('用例通过')
      wrapper.findComponent('[data-testid="expectation-mode"]').vm.$emit('update:modelValue', 'NONE')
      await wrapper.vm.$nextTick()
      expect(wrapper.text()).toContain('不判断用例是否通过')
      expect(current(wrapper).mode).toBe('NONE')
    } finally { wrapper.unmount() }
  })

  test('无稳定ID的临时输出不允许建立字段断言，重复ID也不可选', () => {
    const wrapper = mountEditor({ modelValue: config(), outputs: [{ scriptName: 'temp', valueType: 'NUMBER' }, outputs[0], { ...outputs[0], scriptName: 'duplicate' }] })
    try {
      expect(wrapper.text()).toContain('绑定输出字段')
      expect(wrapper.vm.selectableOutputs).toHaveLength(0)
      expect(wrapper.find('[data-testid="expectation-add-check"]').attributes('disabled')).toBeDefined()
    } finally { wrapper.unmount() }
  })

  test('非法原配置保留并显示错误，不能在打开时自动清除', () => {
    const wrapper = mountEditor({ modelValue: '{' })
    try {
      expect(wrapper.vm.validate().valid).toBe(false)
      expect(wrapper.findAll('.el-alert').some(alert => alert.text().includes('配置格式无效'))).toBe(true)
      expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    } finally { wrapper.unmount() }
  })

  test('复杂对象预期引导快照，固定值编辑没有表达式或OperandPicker入口', () => {
    const wrapper = mountEditor({ modelValue: config(check({ refId: '8', valueType: 'OBJECT', expected: null })), outputs: [{ refType: 'VARIABLE', refId: '8', scriptName: 'detail', valueType: 'OBJECT' }] })
    try {
      expect(wrapper.text()).toContain('JSON 快照')
      expect(wrapper.findComponent({ name: 'OperandPicker' }).exists()).toBe(false)
      expect(wrapper.find('[data-testid="expectation-value-0"]').exists()).toBe(false)
    } finally { wrapper.unmount() }
  })

  test.each(['{"version":"1","mode":"FIELDS","checks":[]}', '{"version":1,"mode":"FIELDS","checks":[null]}'])('非法配置结构不会崩溃或按有效新配置采用：%s', modelValue => {
    let wrapper
    try {
      expect(() => { wrapper = mountEditor({ modelValue }) }).not.toThrow()
      expect(wrapper.vm.validate().valid).toBe(false)
      expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    } finally { wrapper?.unmount() }
  })

  test('固定文本值属性缺失不是合法空字符串，必须明确填写', () => {
    const wrapper = mountEditor({ modelValue: config(check({ refId: '2', valueType: 'STRING', expected: { kind: 'LITERAL', valueType: 'STRING' } })) })
    try { expect(wrapper.vm.validate().valid).toBe(false) } finally { wrapper.unmount() }
  })

  test('已有固定null明确展示原意，不能在布尔编辑器中假显示为false', () => {
    const wrapper = mountEditor({ modelValue: config(check({ refId: '3', refType: 'MODEL', valueType: 'BOOLEAN', expected: scalar(null, 'BOOLEAN') })) })
    try {
      expect(wrapper.text()).toContain('原预期值为 null')
      expect(wrapper.find('[data-testid="expectation-value-0"]').exists()).toBe(false)
      expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    } finally { wrapper.unmount() }
  })

  test('切换比较方式保留原值，只有明确替换动作才将原标量改为集合', async () => {
    const wrapper = mountEditor({ modelValue: config(check()) })
    try {
      wrapper.findComponent('[data-testid="expectation-operator-0"]').vm.$emit('update:modelValue', 'in')
      await wrapper.vm.$nextTick()
      expect(current(wrapper).checks[0].expected).toEqual(scalar('18'))
      expect(wrapper.vm.validate().valid).toBe(false)
      const button = wrapper.findAllComponents({ name: 'ElButton' }).find(item => item.text().includes('替换为逐项集合'))
      await button.trigger('click')
      expect(current(wrapper).checks[0].expected).toEqual({ kind: 'ARRAY', valueType: 'LIST', items: [] })
    } finally { wrapper.unmount() }
  })

  test('同ID输出改名只更新展示，不修改已存关联或预期', async () => {
    const value = config(check())
    const wrapper = mountEditor({ modelValue: value })
    try {
      await wrapper.setProps({ outputs: [{ ...outputs[0], label: '最新风险分', scriptName: 'renamed_score' }] })
      expect(wrapper.vm.validate().valid).toBe(true)
      expect(wrapper.findAllComponents({ name: 'ElOption' }).some(option => option.attributes('label') === '最新风险分 (renamed_score)')).toBe(true)
      expect(wrapper.props('modelValue')).toBe(value)
      expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    } finally { wrapper.unmount() }
  })

  test('超安全整数的字符串ID选择保存后重载，不能混淆同名相邻ID', async () => {
    const adjacent = { ...outputs[0], refId: '9007199254740992', scriptName: 'adjacent_score' }
    const choices = [adjacent, outputs[0]]
    let wrapper = mountEditor({ modelValue: config(), outputs: choices })
    try {
      await wrapper.find('[data-testid="expectation-add-check"]').trigger('click')
      wrapper.findComponent('[data-testid="expectation-field-0"]').vm.$emit('update:modelValue', 'VARIABLE:9007199254740993')
      await wrapper.vm.$nextTick()
      wrapper.findComponent('[data-testid="expectation-value-0"]').vm.$emit('update:modelValue', '25')
      await wrapper.vm.$nextTick()
      const saved = wrapper.emitted('update:modelValue').at(-1)[0]
      expect(saved).toContain('"refId":"9007199254740993"')
      expect(saved).not.toContain('9007199254740992')
      wrapper.unmount()
      wrapper = mountEditor({ modelValue: saved, outputs: choices })
      expect(wrapper.vm.validate().valid).toBe(true)
      expect(wrapper.vm.currentField(wrapper.vm.checks[0]).scriptName).toBe('score')
      expect(wrapper.findComponent('[data-testid="expectation-field-0"]').attributes()).toMatchObject({ 'model-value': 'VARIABLE:9007199254740993' })
      expect(wrapper.emitted('update:modelValue')).toBeUndefined()
      await wrapper.setProps({ outputs: [adjacent] })
      expect(wrapper.vm.validate().valid).toBe(false)
      expect(wrapper.props('modelValue')).toBe(saved)
    } finally { wrapper.unmount() }
  })
})
