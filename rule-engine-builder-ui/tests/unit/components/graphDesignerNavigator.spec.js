import { shallowMount } from '@test-utils'
import GraphDesignerNavigator from '@/components/flow/GraphDesignerNavigator.vue'

const fs = require('fs')
const path = require('path')

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

  test('工具栏按钮具有独立的默认和交互状态', () => {
    const source = fs.readFileSync(path.resolve(__dirname, '../../../src/components/flow/GraphDesignerNavigator.vue'), 'utf8')

    expect(source.match(/class="toolbar-action"/g)).toHaveLength(2)
    expect(source).toContain('.toolbar-action:hover,')
    expect(source).toContain('.toolbar-action:focus {')
    expect(source).toContain('.toolbar-action:active {')
    expect(source).toContain('background: #ffffff;')
    expect(source).toContain('color: #1d39c4;')
  })
})
