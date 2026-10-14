import { shallowMount } from '@test-utils'
import { nextTick } from 'vue'
import ResourcePicker from '@/components/common/ResourcePicker.vue'
import ProtocolPathPicker from '@/components/common/ProtocolPathPicker.vue'
import { preferredFieldPageSize, readFieldPageSize, setPreferredFieldPageSize } from '@/utils/fieldPickerPreferences'
import { buildFieldTree, fieldAncestors, visibleFieldRows } from '@/utils/fieldPicker'
import { API_MODULE_FIELDS } from '@/utils/apiExecution'

const VarPicker = (await vi.importActual('@/components/common/VarPicker.vue')).default

afterEach(() => { setPreferredFieldPageSize(50, null) })

test('每页条数跨入口复用、可恢复；显式配置的入口保留自己的条数', async () => {
  const saved = new Map()
  const storage = { getItem: key => saved.get(key), setItem: (key, value) => saved.set(key, value) }
  const first = shallowMount(VarPicker)
  const second = shallowMount(VarPicker)
  const explicit = shallowMount(VarPicker, { props: { pageSize: 75 } })
  first.vm.onPageSizeChange(200)
  await nextTick()
  expect(second.vm.resolvedPageSize).toBe(200)
  expect(explicit.vm.resolvedPageSize).toBe(75)
  expect(first.emitted('update:pageSize').at(-1)).toEqual([200])
  setPreferredFieldPageSize(125, storage)
  expect(readFieldPageSize(storage)).toBe(125)
  expect(readFieldPageSize({ getItem: () => 'Infinity' })).toBe(50)
  expect(readFieldPageSize({ getItem: () => '-1' })).toBe(50)
  expect(preferredFieldPageSize.value).toBe(125)
  first.unmount(); second.unmount(); explicit.unmount()
})

test('模型转换方法按 ID 选择；同名函数、长整数 ID 和清除都不串引用', () => {
  const firstId = '9007199254740993', secondId = '9007199254740994'
  const wrapper = shallowMount(ResourcePicker, { props: { category: 'function', resources: [
    { id: firstId, funcCode: 'same', funcName: '转换方法 A' },
    { id: secondId, funcCode: 'same', funcName: '转换方法 B' },
  ] } })
  expect(wrapper.vm.options.map(item => item.varCode)).toEqual(['same', 'same'])
  wrapper.vm.change(secondId)
  expect(wrapper.emitted('update:value')[0]).toEqual([secondId])
  expect(wrapper.emitted('select')[0][0].funcName).toBe('转换方法 B')
  wrapper.vm.change('')
  expect(wrapper.emitted('update:value').at(-1)).toEqual([null])
  wrapper.unmount()
})

test('外数上下文的请求、响应、状态有父级；路径保持协议原文', () => {
  const wrapper = shallowMount(ProtocolPathPicker, { props: { paths: API_MODULE_FIELDS, contextLabel: '当前 API' } })
  const fields = wrapper.vm.fields
  const tree = buildFieldTree(fields)
  const status = tree.nodes.get('API_CONTEXT:response.httpStatus:')
  expect(status.type).toBe('INTEGER')
  expect(fieldAncestors(status).map(node => node.name)).toEqual(['响应'])
  expect(fields.find(item => item.varCode === 'request.headers')._varId).toBe('request.headers')
  const rows = visibleFieldRows(tree, new Set(['API_CONTEXT:response:']), 'api-response', '')
  expect(rows.map(row => row.node.name)).toEqual(expect.arrayContaining(['响应', '响应 · HTTP 状态码', '响应 · 原始响应体']))
  wrapper.unmount()
})
