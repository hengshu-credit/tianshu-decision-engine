import { shallowMount } from '@test-utils'
import ApiSampleStructure from '@/components/common/ApiSampleStructure.vue'
import ApiParameterFields from '@/components/common/ApiParameterFields.vue'

describe('large API structures', () => {
  test('万字段结构只显示当前页，搜索能到达最后一个字段', async () => {
    const sample = Object.fromEntries(Array.from({ length: 9937 }, (_, index) => [`QY_${index}`, index]))
    const wrapper = shallowMount(ApiSampleStructure, { props: { sample } })
    expect(wrapper.vm.visibleRows).toHaveLength(50)
    await wrapper.setData({ query: 'QY_9936', page: 1 })
    expect(wrapper.vm.visibleRows.map(item => item.value)).toEqual(['response.body.QY_9936'])
    wrapper.unmount()
  })
  test('跨页删除通过字段 ID 定位，不误删第一页同一行', async () => {
    const fields = Array.from({ length: 120 }, (_, index) => ({ id: `field_${index}`, path: `name_${index}` }))
    const wrapper = shallowMount(ApiParameterFields, { props: { fields, output: true } })
    await wrapper.setData({ page: 2 })
    expect(wrapper.vm.visibleFields[0].id).toBe('field_50')
    wrapper.vm.remove('field_62')
    const saved = wrapper.emitted('update:fields')[0][0]
    expect(saved.find(item => item.id === 'field_62')).toBeUndefined()
    expect(saved.find(item => item.id === 'field_12')).toBeDefined()
    wrapper.unmount()
  })
})
