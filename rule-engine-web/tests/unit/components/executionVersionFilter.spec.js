import { shallowMount } from '@test-utils'
import ExecutionVersionFilter from '@/components/common/ExecutionVersionFilter.vue'
import * as definitions from '@/api/definition'

afterEach(() => vi.restoreAllMocks())

test('日志筛选按业务版本去重，覆盖修订不冒充新版本', async () => {
  vi.spyOn(definitions, 'listVersions').mockResolvedValue({ data: [
    { id: 13, businessVersion: 1, version: 3 }, { id: 12, businessVersion: 2, version: 2 }, { id: 11, businessVersion: 1, version: 1 },
  ] })
  const view = shallowMount(ExecutionVersionFilter, { props: { modelValue: { ruleId: '7', versionMode: 'LATEST' } } })
  await view.vm.load(true)
  expect(view.vm.versions.map(row => row.version)).toEqual([2, 1])
  expect(view.vm.selected).toBe('LATEST')
  view.vm.selectVersion('1')
  expect(view.emitted('update:modelValue').at(-1)[0]).toEqual({ ruleId: '7', versionMode: 'FIXED', ruleVersion: 1 })
  view.unmount()
})

test('切换规则清除原版本选择，避免同号版本归属串用', () => {
  const view = shallowMount(ExecutionVersionFilter, { props: { modelValue: { ruleId: '7', versionMode: 'FIXED', ruleVersion: 1 } } })
  view.vm.selectRule('8')
  expect(view.emitted('update:modelValue').at(-1)[0]).toEqual({ ruleId: '8', versionMode: 'ALL', ruleVersion: null })
  view.unmount()
})
