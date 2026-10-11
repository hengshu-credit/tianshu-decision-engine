import { shallowMount } from '@test-utils'
import { flushPromises } from '@vue/test-utils'
import TransferVersionBindingRow from '@/components/transfer/TransferVersionBindingRow.vue'
import * as api from '@/api/transfer'

vi.mock('@/api/transfer', () => ({ listTransferResources: vi.fn(), listTransferVersionCandidates: vi.fn() }))
const source = { referenceKey: 'RULE:1|/field', sourceResourceKey: 'RULE:1', refType: 'MODEL_OUTPUT', targetResourceType: 'MODEL', versionSource: 'BUSINESS_VERSION' }
const mount = props => shallowMount(TransferVersionBindingRow, { props: { source, ...props } })
beforeEach(() => { vi.clearAllMocks(); api.listTransferVersionCandidates.mockResolvedValue({ data: [{ versionId: '80', snapshotId: '91', version: 2, fields: [{ id: '5', label: '评分', valueType: 'INTEGER' }] }] }) })

test('选择目标版本携带快照并清除旧字段，切换资源清除版本', async () => {
  const view = mount({ modelValue: { resourceId: '50', fieldId: '6' } }); await flushPromises()
  view.vm.selectVersion('80')
  expect(view.emitted('update:modelValue')[0][0]).toEqual({ resourceId: '50', versionId: '80', expectedSnapshotId: '91', fieldId: null })
  view.vm.selectResource('51')
  expect(view.emitted('update:modelValue')[1][0]).toEqual({ resourceId: '51', versionId: null, expectedSnapshotId: null, fieldId: null }); view.unmount()
})

test('资源切换后忽略原资源迟到的版本列表', async () => {
  let resolve
  api.listTransferVersionCandidates.mockImplementationOnce(() => new Promise(done => { resolve = done }))
  const view = mount({ modelValue: { resourceId: '50' } })
  await view.setProps({ modelValue: { resourceId: '51' } }); await flushPromises()
  resolve({ data: [{ versionId: 'old' }] }); await flushPromises()
  expect(view.vm.versions.map(item => item.versionId)).toEqual(['80']); view.unmount()
})

test('最新规则仅映射资源，保持跟随最新策略且不查询固定版', async () => {
  const view = mount({ source: { ...source, refType: 'RULE', targetResourceType: 'RULE', versionSource: 'LATEST' }, modelValue: { resourceId: '50' } }); await flushPromises()
  expect(api.listTransferVersionCandidates).not.toHaveBeenCalled()
  expect(view.text()).toContain('保持最新版策略'); view.unmount()
})

test('变更任一映射身份后不再显示旧预检通过，并保留目标名称', async () => {
  const binding = { resourceId: '50', versionId: '80', fieldId: '5', expectedSnapshotId: '91' }
  const view = mount({ source: { ...source, valid: true, binding, target: { resourceId: '50', ownerLabel: '目标模型', code: 'risk' } }, modelValue: binding }); await flushPromises()
  expect(view.text()).toContain('校验通过')
  expect(view.findComponent({ name: 'RemoteFilterSelect' }).props('selectedOption')).toMatchObject({ id: '50', label: '目标模型' })
  await view.setProps({ modelValue: { ...binding, fieldId: '6' } })
  expect(view.text()).not.toContain('校验通过'); expect(view.text()).toContain('重新预检')
  await view.setProps({ modelValue: { ...binding, expectedSnapshotId: '92' } })
  expect(view.text()).not.toContain('校验通过'); view.unmount()
})
