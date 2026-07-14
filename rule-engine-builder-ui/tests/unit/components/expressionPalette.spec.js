import { shallowMount } from '@vue/test-utils'
import ExpressionPalette from '@/components/expression/ExpressionPalette.vue'

describe('ExpressionPalette', () => {
  test('名单上下文仅展示名单查询并插入完整默认配置', async () => {
    const wrapper = shallowMount(ExpressionPalette, {
      propsData: { allowedKinds: ['LIST_QUERY'] },
      stubs: { 'el-input': true }
    })

    const button = wrapper.find('.palette-list-query')
    expect(button.exists()).toBe(true)
    await button.trigger('click')

    expect(wrapper.emitted().insert[0][0]).toEqual({
      kind: 'LIST_QUERY',
      listIds: [],
      itemTypes: [],
      combinationMode: 'ANY_FIELD_ANY_LIST',
      matchMode: 'IN_LIST',
      valueType: 'BOOLEAN'
    })
    expect(wrapper.find('.palette-grid').exists()).toBe(false)
  })
})
