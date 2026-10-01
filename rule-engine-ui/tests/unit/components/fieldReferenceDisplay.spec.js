import { mount } from '@test-utils'
import FieldReferenceDisplay from '@/components/common/FieldReferenceDisplay.vue'

describe('FieldReferenceDisplay', () => {
  test('按规则设计器样式分层展示类型、字段编码和业务名称', () => {
    const wrapper = mount(FieldReferenceDisplay, {
      props: {
        field: {
          fieldType: 'INTEGER',
          fieldCode: 'age',
          fieldName: '年龄',
        },
      },
    })

    const type = wrapper.find('.field-reference-display__type')
    expect(type.text()).toBe('i')
    expect(type.attributes('title')).toBe('整数')
    expect(type.classes()).toContain('field-reference-display__type--i')
    expect(wrapper.find('.field-reference-display__code').text()).toBe('age')
    expect(wrapper.find('.field-reference-display__name').text()).toBe('年龄')
  })

  test('未知类型使用问号标识且缺少名称时回退到字段编码', () => {
    const wrapper = mount(FieldReferenceDisplay, {
      props: {
        field: {
          fieldType: 'CUSTOM',
          fieldCode: 'custom_field',
        },
        compact: true,
      },
    })

    expect(wrapper.classes()).toContain('field-reference-display--compact')
    expect(wrapper.find('.field-reference-display__type').text()).toBe('?')
    expect(wrapper.find('.field-reference-display__name').text()).toBe(
      'custom_field'
    )
  })
})
