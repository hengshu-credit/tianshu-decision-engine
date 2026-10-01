import { mount } from '@test-utils'
import RuleDiffLane from '@/components/rule/versionDiff/RuleDiffLane.vue'

describe('RuleDiffLane', () => {
  test('新增内容在左侧渲染占位并在右侧显示新增标记', () => {
    const wrapper = mount(RuleDiffLane, {
      props: {
        lane: {
          key: 'rule-new',
          kind: 'rule',
          status: 'added',
          left: null,
          right: { title: '新增规则', subtitle: '' },
          fields: [{ key: 'value', label: '取值', status: 'added', leftText: '未配置', rightText: '100' }],
          children: []
        }
      }
    })

    expect(wrapper.find('.rule-diff-side--left .rule-diff-placeholder').exists()).toBe(true)
    expect(wrapper.find('.rule-diff-side--right').text()).toContain('+ 新增')
    expect(wrapper.find('.rule-diff-side--right').text()).toContain('新增规则')
  })

  test('修改字段只生成一个共享字段行并同时展示左右值', () => {
    const wrapper = mount(RuleDiffLane, {
      props: {
        lane: {
          key: 'condition-1',
          kind: 'condition',
          status: 'modified',
          left: { title: '年龄条件' },
          right: { title: '年龄条件' },
          fields: [{ key: 'value', label: '比较值', status: 'modified', leftText: '18', rightText: '21' }],
          children: []
        }
      }
    })

    const fieldLane = wrapper.find('.rule-diff-field-lane')
    expect(fieldLane.exists()).toBe(true)
    expect(fieldLane.findAll('.rule-diff-field')).toHaveLength(2)
    expect(fieldLane.text()).toContain('18')
    expect(fieldLane.text()).toContain('21')
    expect(fieldLane.findAll('.is-modified')).toHaveLength(2)
  })

  test('删除内容在右侧保留等位占位', () => {
    const wrapper = mount(RuleDiffLane, {
      props: {
        lane: {
          key: 'rule-old',
          kind: 'rule',
          status: 'removed',
          left: { title: '旧规则' },
          right: null,
          fields: [],
          children: []
        }
      }
    })

    expect(wrapper.find('.rule-diff-side--right .rule-diff-placeholder').exists()).toBe(true)
    expect(wrapper.find('.rule-diff-side--left').text()).toContain('- 删除')
  })
})
