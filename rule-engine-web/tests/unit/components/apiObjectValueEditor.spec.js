import { mount } from '@vue/test-utils'
import ApiObjectValueEditor from '@/components/common/ApiObjectValueEditor.vue'

const literal = value => ({ kind: 'LITERAL', valueType: 'STRING', value })
function editor(value) {
  return mount(ApiObjectValueEditor, { props: { value }, global: { stubs: { ApiValueEditor: true, ElInput: true, ElButton: true } } })
}

test('等值外部对象重新解析不丢失未完成字段；实际替换配置仍会刷新', async () => {
  const value = { known: literal('kept') }
  const wrapper = editor(value)
  try {
    wrapper.vm.add()
    const row = wrapper.vm.rows[1]
    row.path = 'client_secret'
    expect(wrapper.vm.validate()).toBe(false)
    await wrapper.setProps({ value: JSON.parse(JSON.stringify(value)) })
    expect(wrapper.vm.rows[1]).toMatchObject({ id: row.id, path: 'client_secret' })
    expect(wrapper.vm.validate()).toBe(false)
    await wrapper.setProps({ value: { replacement: literal('new') } })
    expect(wrapper.vm.rows.map(item => item.path)).toEqual(['replacement'])
    expect(wrapper.vm.validate()).toBe(true)
  } finally { wrapper.unmount() }
})

test('鉴权键名中的点号、空格与大小写按原文保存，引用 ID 不变', () => {
  const reference = { kind: 'REFERENCE', refType: 'VARIABLE', refId: '9007199254740993', code: 'oldName' }
  const wrapper = editor({ 'X.Client Id': reference })
  wrapper.vm.emitValue()
  expect(wrapper.emitted('update:value').at(-1)[0]).toEqual({ 'X.Client Id': reference })
  wrapper.unmount()
})

test('父组件回填本次更新不重建行，空草稿保留，重复键不能悄悄覆盖', async () => {
  const wrapper = editor({ clientId: literal('a') })
  const row = wrapper.vm.rows[0]
  wrapper.vm.add()
  row.path = 'clientKey'
  wrapper.vm.emitValue()
  await wrapper.setProps({ value: wrapper.emitted('update:value').at(-1)[0] })
  expect(wrapper.vm.rows[0].id).toBe(row.id)
  expect(wrapper.vm.rows).toHaveLength(2)
  wrapper.vm.rows[1].path = 'clientKey'
  expect(wrapper.vm.validate()).toBe(false)
  expect(wrapper.vm.error).toContain('重复')
  wrapper.unmount()
})

test('嵌套对象、空对象和数组内容不被转为字符串或丢弃', () => {
  const value = { client: { id: literal('a') }, empty: {}, scopes: { kind: 'LITERAL', valueType: 'LIST', value: '["a","b"]' } }
  const wrapper = editor(value)
  wrapper.vm.emitValue()
  expect(wrapper.emitted('update:value').at(-1)[0]).toEqual(value)
  wrapper.unmount()
})

test('业务对象可以包含名为 kind 的字段', () => {
  const wrapper = editor({ payload: { kind: literal('consumer') } })
  expect(wrapper.vm.isObject(wrapper.vm.rows[0].value)).toBe(true)
  wrapper.unmount()
})
