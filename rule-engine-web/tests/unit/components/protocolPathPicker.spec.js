import { shallowMount } from '@vue/test-utils'
import ProtocolPathPicker from '@/components/common/ProtocolPathPicker.vue'

test('路径不在目录中时明确提示，结构移除原路径后标记失效', async () => {
  const wrapper = shallowMount(ProtocolPathPicker, { props: { value: 'body.Score', paths: [{ value: 'body.Score', label: '评分', type: 'NUMBER' }] } })
  expect(wrapper.vm.isKnownPath).toBe(true)
  await wrapper.setProps({ paths: [{ value: 'body', type: 'OBJECT' }] })
  expect(wrapper.text()).toContain('已从当前结构移除')
  await wrapper.setProps({ value: 'body.Dynamic' })
  expect(wrapper.text()).toContain('自定义路径')
  wrapper.unmount()
})

test('已知路径类型不符时提示，不能用有响应根节点掩盖不存在的子路径', () => {
  const wrapper = shallowMount(ProtocolPathPicker, { props: { value: 'body', paths: [{ value: 'body', type: 'OBJECT' }], expectedType: 'NUMBER' } })
  expect(wrapper.text()).toContain('类型不一致')
  wrapper.unmount()
})
