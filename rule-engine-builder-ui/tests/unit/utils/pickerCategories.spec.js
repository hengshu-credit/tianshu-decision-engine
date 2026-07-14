import {
  REFERENCE_PICKER_CATEGORIES,
  pickerCategoryLabel,
  pickerReferenceCategory
} from '@/utils/pickerCategories'

describe('pickerCategories', () => {
  test('字段选择器和表达式资源区共享四类引用', () => {
    expect(REFERENCE_PICKER_CATEGORIES).toEqual([
      { key: 'standalone', label: '普通变量' },
      { key: 'constant', label: '常量' },
      { key: 'object', label: '数据对象' },
      { key: 'model', label: '模型' }
    ])
    expect(pickerCategoryLabel('constant')).toBe('常量')
  })

  test('没有显式分类的字段归入普通变量', () => {
    expect(pickerReferenceCategory({ varCode: 'age' })).toBe('standalone')
    expect(pickerReferenceCategory({ _ref: { category: 'model' } })).toBe('model')
  })
})
