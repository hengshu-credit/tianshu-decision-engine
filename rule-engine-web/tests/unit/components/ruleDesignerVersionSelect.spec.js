import { mount } from '@test-utils'
import RuleDesignerVersionSelect from '@/components/rule/RuleDesignerVersionSelect.vue'

describe('RuleDesignerVersionSelect', () => {
  test('按生命周期修订和已发布版本分组展示稳定来源', () => {
    const wrapper = mount(RuleDesignerVersionSelect, {
      props: {
        modelValue: 'VERSION:9',
        options: [
          { value: 'REVISION:6', label: '待修改修订 v8', group: 'REVISION' },
          { value: 'VERSION:9', label: '发布版本 v7', group: 'VERSION' },
        ],
      },
    })

    expect(wrapper.vm.revisionOptions).toEqual([
      { value: 'REVISION:6', label: '待修改修订 v8', group: 'REVISION' },
    ])
    expect(wrapper.vm.versionOptions).toEqual([
      { value: 'VERSION:9', label: '发布版本 v7', group: 'VERSION' },
    ])
    expect(wrapper.get('[data-testid="designer-version-select"]').exists()).toBe(true)
    wrapper.unmount()
  })

  test('生产与草稿选项采用相同布局，保留完整版本格式且删除不会触发版本切换', async () => {
    const draft = { value: 'REVISION:6', label: '草稿 · 8 / 2026-10-10T10:30:00', group: 'REVISION', state: 'DRAFT' }
    const wrapper = mount(RuleDesignerVersionSelect, {
      props: {
        modelValue: 'VERSION:9',
        options: [{ value: 'VERSION:9', label: '生产 · 7 / 2026-10-09T10:30:00', group: 'VERSION' }, draft],
      },
    })
    const rows = wrapper.findAll('.version-option')
    expect(rows).toHaveLength(2)
    expect(rows.map(row => row.get('.version-option__label').text())).toEqual(['生产 · 7 / 2026-10-09T10:30:00', draft.label])
    expect(rows[0].find('.delete-draft').exists()).toBe(false)
    await rows[1].get('.delete-draft').trigger('click')
    expect(wrapper.emitted('delete')).toEqual([[draft]])
    expect(wrapper.emitted('change')).toBeUndefined()
    await wrapper.setProps({ disabled: true })
    expect(rows[1].get('.delete-draft').element.disabled).toBe(true)
    wrapper.unmount()
  })
})
