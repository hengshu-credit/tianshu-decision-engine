import { shallowMount } from '@test-utils'
import JsonVersionDiff from '@/components/common/JsonVersionDiff.vue'

describe('JsonVersionDiff', () => {
  test('原始快照对比保留相邻长整型标识的每一位', () => {
    const wrapper = shallowMount(JsonVersionDiff, { props: {
      original: '{"id":9007199254740992}', modified: '{"id":9007199254740993}',
    } })
    expect(wrapper.vm.originalText).toContain('9007199254740992')
    expect(wrapper.vm.modifiedText).toContain('9007199254740993')
    expect(wrapper.vm.changed).toBe(true)
  })
  test('格式化左右版本并把具体内容交给差异编辑器', () => {
    const wrapper = shallowMount(JsonVersionDiff, {
      props: {
        original: '{"threshold":60,"level":"B"}',
        modified: { threshold: 80, level: 'A' },
        originalLabel: 'V1',
        modifiedLabel: '当前审批版本',
      },
      stubs: { MonacoDiffEditor: true },
    })

    expect(wrapper.vm.originalText).toBe(
      '{\n  "threshold": 60,\n  "level": "B"\n}'
    )
    expect(wrapper.vm.modifiedText).toBe(
      '{\n  "threshold": 80,\n  "level": "A"\n}'
    )
    expect(wrapper.vm.changed).toBe(true)
    expect(wrapper.text()).toContain('V1')
    expect(wrapper.text()).toContain('当前审批版本')
  })

  test('可隐藏重复的版本标题但保留差异编辑器', () => {
    const wrapper = shallowMount(JsonVersionDiff, {
      props: {
        original: '{"a":1}',
        modified: '{"a":2}',
        showHeader: false,
      },
      stubs: { MonacoDiffEditor: true },
    })

    expect(wrapper.find('.json-version-diff__head').exists()).toBe(false)
    expect(wrapper.findComponent({ name: 'MonacoDiffEditor' }).exists()).toBe(true)
  })
})
