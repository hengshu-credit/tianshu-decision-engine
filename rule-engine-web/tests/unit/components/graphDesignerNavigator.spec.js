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

  test('工具栏按钮使用公共主题的默认和交互状态', () => {
    const source = fs.readFileSync(path.resolve(__dirname, '../../../src/components/flow/GraphDesignerNavigator.vue'), 'utf8')
    const theme = fs.readFileSync(path.resolve(__dirname, '../../../src/styles/theme-buttons.scss'), 'utf8')

    expect(source.match(/class="toolbar-action"/g)).toHaveLength(2)
    expect(source).not.toContain('.toolbar-action:hover')
    expect(theme).toContain('background: var(--tianshu-bg-surface)')
    expect(theme).toContain('background: var(--tianshu-button-soft-hover-bg)')
    expect(theme).toContain('background: var(--tianshu-button-soft-active-bg)')
  })
})
