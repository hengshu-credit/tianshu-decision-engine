import { mount } from '@test-utils'
import SqlParameterEditor from '@/components/common/SqlParameterEditor.vue'

test('SQL 参数支持类型化值和 ID 引用，不自动改变旧表达式', async () => {
  const value = '["$.oldName", 0, false, null]'
  const wrapper = mount(SqlParameterEditor, { props: { value, sql: 'SELECT ?, ?, ?, ?', variables: [{ _refType: 'VARIABLE', _varId: 7, refCode: 'Age_New', refName: '年龄', varType: 'INTEGER' }] } })
  expect(wrapper.emitted('update:value')).toBeUndefined()
  wrapper.vm.selectReference(0, 'VARIABLE:7')
  const updated = JSON.parse(wrapper.emitted('update:value')[0][0])
  expect(updated[0]).toEqual(expect.objectContaining({ kind: 'REFERENCE', refId: 7, refType: 'VARIABLE', code: 'Age_New' }))
  expect(updated.slice(1)).toEqual([0, false, null])
  wrapper.vm.changeValue(1, 'NUMBER', '9007199254740993')
  const numeric = JSON.parse(wrapper.emitted('update:value')[1][0])[1]
  expect(numeric).toEqual({ kind: 'LITERAL', valueType: 'NUMBER', value: '9007199254740993' })
  await wrapper.setProps({ value: '{invalid' })
  expect(wrapper.find('el-alert-stub').attributes('title')).toContain('原内容已保留')
  wrapper.unmount()
})
