import { mount, flushPromises } from '@test-utils'
import * as api from '@/api/definition'
import RuleCallVersionSelect from '@/components/flow/RuleCallVersionSelect.vue'

test('版本选项持久化稳定绑定 ID，未指定时是最新版，迟到结果不覆盖其他规则', async () => {
  let resolveOld
  api.listPublishedVersions.mockReturnValueOnce(new Promise(resolve => { resolveOld = resolve }))
    .mockResolvedValueOnce({ data: [{ id: '91', bindingId: '9007199254740993', version: 2, outputFields: [{ scriptName: 'score' }] }] })
  const wrapper = mount(RuleCallVersionSelect, { props: { ruleId: '1' } })
  await wrapper.setProps({ ruleId: '2' }); await flushPromises()
  resolveOld({ data: [{ id: '80', bindingId: '8', version: 1 }] }); await flushPromises()
  expect(wrapper.vm.versions.map(item => item.bindingId)).toEqual(['9007199254740993'])
  wrapper.vm.select('9007199254740993')
  expect(wrapper.emitted('change')[0]).toEqual([{ versionMode: 'FIXED', versionBindingId: '9007199254740993' }])
  wrapper.vm.select('LATEST')
  expect(wrapper.emitted('change')[1]).toEqual([{ versionMode: 'LATEST', versionBindingId: null }])
  wrapper.unmount()
})
