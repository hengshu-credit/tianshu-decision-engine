import { mount } from '@test-utils'
import RuleConditionDiff from '@/components/rule/versionDiff/RuleConditionDiff.vue'

function conditionLane() {
  return {
    key: 'group-1',
    kind: 'condition-group',
    status: 'modified',
    left: { title: '条件组 1' },
    right: { title: '条件组 1' },
    fields: [{ key: 'op', label: '条件关系', status: 'unchanged', leftText: 'AND', rightText: 'AND' }],
    children: [{
      key: 'leaf-1',
      kind: 'condition',
      status: 'modified',
      left: { title: '条件 1' },
      right: { title: '条件 1' },
      fields: [{ key: 'rightOperand', label: '右侧表达式', status: 'modified', leftText: '18', rightText: '21' }],
      children: []
    }]
  }
}

describe('RuleConditionDiff', () => {
  test('递归条件仍按共享行渲染', () => {
    const wrapper = mount(RuleConditionDiff, { props: { lane: conditionLane() } })

    expect(wrapper.findAll('.rule-diff-lane')).toHaveLength(2)
    expect(wrapper.text()).toContain('18')
    expect(wrapper.text()).toContain('21')
  })

  test('一个展开按钮同时隐藏左右两侧的子条件', async() => {
    const wrapper = mount(RuleConditionDiff, { props: { lane: conditionLane() } })

    expect(wrapper.findAll('.rule-diff-lane')).toHaveLength(2)
    await wrapper.find('.rule-diff-expand').trigger('click')
    expect(wrapper.findAll('.rule-diff-lane')).toHaveLength(1)
  })
})
