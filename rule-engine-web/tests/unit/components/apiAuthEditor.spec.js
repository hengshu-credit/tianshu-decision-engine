import { shallowMount } from '@test-utils'
import ApiAuthEditor from '@/components/common/ApiAuthEditor.vue'
import { emptyApiAuthConfig } from '@/utils/apiAuthConfig'

test('XML 鉴权请求体通过内容选择器编辑完整内容，禁止误发对象配置', async () => {
  const value = { ...emptyApiAuthConfig(), contentType: 'application/xml', body: {} }
  const wrapper = shallowMount(ApiAuthEditor, { props: { type: 'TOKEN_API', value }, global: { stubs: { ApiObjectValueEditor: false } } })
  expect(wrapper.vm.rawBody).toBe(true)
  expect(wrapper.vm.validate()).toBe(false)
  await wrapper.setProps({ value: { ...value, body: { kind: 'LITERAL', valueType: 'STRING', value: '<token>原文</token>' } } })
  expect(wrapper.vm.validate()).toBe(true)
  expect(wrapper.findComponent({ ref: 'body' }).exists()).toBe(false)
  wrapper.unmount()
})
