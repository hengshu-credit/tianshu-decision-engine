import { shallowMount, flushPromises } from '@test-utils'
import ResourceVersionSelect from '@/components/common/ResourceVersionSelect.vue'
import { getResourceVersions } from '@/api/resourceVersions'

vi.mock('@/api/resourceVersions', () => ({ getResourceVersions: vi.fn() }))
const value = { versionMode: 'FIXED', versionId: '81' }
const workspace = { versions: [{ versionId: '93', version: 2, status: 'ACTIVE' }], history: [{ id: '81', versionNo: 2 }, { id: '93', versionNo: 2 }] }
beforeEach(() => { vi.clearAllMocks(); getResourceVersions.mockResolvedValue({ data: workspace }) })

test('覆盖后旧锚点继续显示同一业务版本，不显示裸快照 ID 或改写绑定', async () => {
  const wrapper = shallowMount(ResourceVersionSelect, { props: { resourceType: 'EXPERIMENT', resourceId: '7', modelValue: value } })
  await wrapper.vm.load(true); await flushPromises()
  expect(wrapper.vm.selected).toBe('93')
  expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  expect(wrapper.findAllComponents({ name: 'ElOption' }).map(option => option.attributes('label'))).toContain('指定 v2')
  wrapper.unmount()
})

test('被删除的绑定保持明确不可用提示，不能退回最新版', async () => {
  getResourceVersions.mockResolvedValue({ data: { versions: [{ versionId: '93', version: 2, status: 'ACTIVE', deleted: true }], history: workspace.history } })
  const wrapper = shallowMount(ResourceVersionSelect, { props: { resourceType: 'EXPERIMENT', resourceId: '7', modelValue: value } })
  await wrapper.vm.load(true); await flushPromises()
  expect(wrapper.findAllComponents({ name: 'ElOption' }).map(option => option.attributes('label'))).toContain('所选版本已删除或不可用')
  expect(wrapper.vm.selected).toBe('81')
  expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  wrapper.unmount()
})
