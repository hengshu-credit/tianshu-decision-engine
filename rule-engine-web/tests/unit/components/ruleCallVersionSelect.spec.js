import { mount, flushPromises } from '@test-utils'
import * as api from '@/api/definition'
import RuleCallVersionSelect from '@/components/flow/RuleCallVersionSelect.vue'

test('冻结目录只显示制品保存的调用字段，不查询本地同号规则且保留LATEST策略', async () => {
  api.listPublishedVersions.mockClear()
  const frozen = [{ bindingId: '901', version: 3, available: true, outputFields: [{ scriptName: 'FrozenScore' }] }]
  const wrapper = mount(RuleCallVersionSelect, { props: { ruleId: '88', catalogMode: 'FROZEN', catalogKey: 'VERSION:81', frozenVersions: frozen } })
  await flushPromises()
  expect(api.listPublishedVersions).not.toHaveBeenCalled()
  expect(wrapper.vm.versions).toEqual(frozen)
  expect(wrapper.text()).toContain('原策略：随发布更新')
  expect(wrapper.text()).toContain('制品保存的目录')
  wrapper.vm.select('901')
  expect(wrapper.emitted('change')).toBeUndefined()
  wrapper.unmount()
})

test('同号规则从LIVE切到另一来源FROZEN后迟到本地字段不能覆盖', async () => {
  let release
  api.listPublishedVersions.mockReturnValueOnce(new Promise(resolve => { release = resolve }))
  const wrapper = mount(RuleCallVersionSelect, { props: { ruleId: '88', catalogKey: 'LIVE:A' } })
  const frozen = [{ bindingId: '902', version: 2, outputFields: [{ scriptName: 'ImportedScore' }] }]
  await wrapper.setProps({ catalogMode: 'FROZEN', catalogKey: 'VERSION:B', frozenVersions: frozen })
  release({ data: [{ bindingId: '11', version: 9, outputFields: [{ scriptName: 'WrongLocalScore' }] }] })
  await flushPromises()
  expect(wrapper.vm.versions).toEqual(frozen)
  expect(wrapper.emitted('fields').at(-1)[0]).toEqual({ ruleId: '88', versions: frozen, catalogKey: 'VERSION:B' })
  wrapper.unmount()
})

test('发布字段回调包含所属规则和完整版本，并在切规则加载时清除旧信息', async () => {
  const versions = [{ bindingId: '81', version: 1, available: true, outputFields: [{ scriptName: 'published_score' }] }]
  api.listPublishedVersions.mockResolvedValueOnce({ data: versions })
  const wrapper = mount(RuleCallVersionSelect, { props: { ruleId: '8' } })
  await flushPromises()
  expect(wrapper.emitted('fields').at(-1)).toEqual([{ ruleId: '8', versions }])
  api.listPublishedVersions.mockRejectedValueOnce(new Error('unavailable'))
  await wrapper.setProps({ ruleId: '9' }); await flushPromises()
  expect(wrapper.emitted('fields').at(-1)).toEqual([{ ruleId: '9', versions: null }])
  wrapper.unmount()
})

test('版本选项持久化稳定绑定 ID，未指定时是最新版，迟到结果不覆盖其他规则', async () => {
  let resolveOld
  api.listPublishedVersions.mockReturnValueOnce(new Promise(resolve => { resolveOld = resolve }))
    .mockResolvedValueOnce({ data: [{ id: '91', bindingId: '9007199254740993', version: 2, outputFields: [{ scriptName: 'score' }] }] })
  const wrapper = mount(RuleCallVersionSelect, { props: { ruleId: '1' } })
  await wrapper.setProps({ ruleId: '2' }); await flushPromises()
  resolveOld({ data: [{ id: '80', bindingId: '8', version: 1 }] }); await flushPromises()
  expect(wrapper.vm.versions.map(item => item.bindingId)).toEqual(['9007199254740993'])
  expect(wrapper.vm.latestLabel).toBe('最新 · v2')
  wrapper.vm.select('9007199254740993')
  expect(wrapper.emitted('change')[0]).toEqual([{ versionMode: 'FIXED', versionBindingId: '9007199254740993' }])
  wrapper.vm.select('LATEST')
  expect(wrapper.emitted('change')[1]).toEqual([{ versionMode: 'LATEST', versionBindingId: null }])
  wrapper.unmount()
})
