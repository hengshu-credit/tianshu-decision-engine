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
  })

})
