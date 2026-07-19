import { shallowMount } from '@vue/test-utils'
import GraphDesignerNavigator from '@/components/flow/GraphDesignerNavigator.vue'

describe('GraphDesignerNavigator', () => {
  test('下拉展开时保留当前搜索关键字，不用空查询覆盖筛选结果', () => {
    const wrapper = shallowMount(GraphDesignerNavigator, {
      stubs: {
        'el-select': true,
        'el-option': true,
        'el-button': true,
        'el-popover': true
      }
    })

    wrapper.vm.onRemoteSearch('ArcFace')
    wrapper.vm.onVisibleChange(true)

    expect(wrapper.emitted('search')).toEqual([['ArcFace'], ['ArcFace']])
  })
})
