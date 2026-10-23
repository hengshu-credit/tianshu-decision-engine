import { shallowMount } from '@test-utils'
import ApiRequestSampleEditor from '@/components/common/ApiRequestSampleEditor.vue'

test('只有显式导入合法 JSON 才更新样例，特殊键和嵌套数组保持原样', async () => {
  const value = { original: 1 }
  const wrapper = shallowMount(ApiRequestSampleEditor, { props: { value } })
  try {
    wrapper.vm.text = '{invalid'
    wrapper.vm.apply()
    expect(wrapper.emitted('update:value')).toBeUndefined()
    expect(wrapper.vm.error).toContain('合法 JSON')
    await wrapper.setProps({ value: { original: 1 } })
    expect(wrapper.vm.text).toBe('{invalid')
    const next = { 'Mobile.Number': '', Customer: { Items: [{ Score: 0 }] } }
    wrapper.vm.text = JSON.stringify(next)
    wrapper.vm.apply()
    expect(wrapper.emitted('update:value')[0][0]).toEqual(next)
    expect(value).toEqual({ original: 1 })
    expect(wrapper.vm.error).toBe('')
  } finally { wrapper.unmount() }
})
