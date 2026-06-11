// tests/unit/constants/varTypes.spec.js
import {
  VAR_TYPE_FORM_OPTIONS,
  VAR_TYPE_FILTER_OPTIONS,
  varTypeLabel,
  varTypeTagColor
} from '@/constants/varTypes'

describe('VAR_TYPE_FORM_OPTIONS', () => {
  test('包含所有 8 种类型', () => {
    expect(VAR_TYPE_FORM_OPTIONS.length).toBe(8)
  })

  test('每项有 label 和 value', () => {
    VAR_TYPE_FORM_OPTIONS.forEach(opt => {
      expect(opt).toHaveProperty('label')
      expect(opt).toHaveProperty('value')
      expect(typeof opt.label).toBe('string')
      expect(typeof opt.value).toBe('string')
    })
  })

  test('value 唯一不重复', () => {
    const values = VAR_TYPE_FORM_OPTIONS.map(o => o.value)
    expect(new Set(values).size).toBe(values.length)
  })

  test('包含 OBJECT 类型', () => {
    expect(VAR_TYPE_FORM_OPTIONS.find(o => o.value === 'OBJECT')).toBeDefined()
  })
})

describe('VAR_TYPE_FILTER_OPTIONS', () => {
  test('包含所有 8 种类型', () => {
    expect(VAR_TYPE_FILTER_OPTIONS.length).toBe(8)
  })

  test('每项有 label 和 value', () => {
    VAR_TYPE_FILTER_OPTIONS.forEach(opt => {
      expect(opt).toHaveProperty('label')
      expect(opt).toHaveProperty('value')
    })
  })

  test('value 与 FORM_OPTIONS 一致', () => {
    const formValues = VAR_TYPE_FORM_OPTIONS.map(o => o.value)
    const filterValues = VAR_TYPE_FILTER_OPTIONS.map(o => o.value)
    expect(filterValues).toEqual(formValues)
  })
})

describe('varTypeLabel', () => {
  test('STRING → 字符串', () => {
    expect(varTypeLabel('STRING')).toBe('字符串')
  })

  test('NUMBER → 数值', () => {
    expect(varTypeLabel('NUMBER')).toBe('数值')
  })

  test('BOOLEAN → 布尔', () => {
    expect(varTypeLabel('BOOLEAN')).toBe('布尔')
  })

  test('DATE → 日期', () => {
    expect(varTypeLabel('DATE')).toBe('日期')
  })

  test('ENUM → 枚举', () => {
    expect(varTypeLabel('ENUM')).toBe('枚举')
  })

  test('OBJECT → 对象', () => {
    expect(varTypeLabel('OBJECT')).toBe('对象')
  })

  test('LIST → 列表', () => {
    expect(varTypeLabel('LIST')).toBe('列表')
  })

  test('MAP → 映射', () => {
    expect(varTypeLabel('MAP')).toBe('映射')
  })

  test('未知类型原样返回', () => {
    expect(varTypeLabel('UNKNOWN')).toBe('UNKNOWN')
    expect(varTypeLabel('CUSTOM')).toBe('CUSTOM')
  })

  test('null/undefined 返回 null', () => {
    expect(varTypeLabel(null)).toBe(null)
    expect(varTypeLabel(undefined)).toBe(undefined)
  })
})

describe('varTypeTagColor', () => {
  test('NUMBER → warning', () => {
    expect(varTypeTagColor('NUMBER')).toBe('warning')
  })

  test('BOOLEAN → success', () => {
    expect(varTypeTagColor('BOOLEAN')).toBe('success')
  })

  test('DATE → info', () => {
    expect(varTypeTagColor('DATE')).toBe('info')
  })

  test('ENUM → danger', () => {
    expect(varTypeTagColor('ENUM')).toBe('danger')
  })

  test('LIST → warning', () => {
    expect(varTypeTagColor('LIST')).toBe('warning')
  })

  test('MAP → info', () => {
    expect(varTypeTagColor('MAP')).toBe('info')
  })

  test('STRING → 空字符串（无颜色）', () => {
    expect(varTypeTagColor('STRING')).toBe('')
  })

  test('OBJECT → 空字符串（无颜色）', () => {
    expect(varTypeTagColor('OBJECT')).toBe('')
  })

  test('未知类型返回空字符串', () => {
    expect(varTypeTagColor('UNKNOWN')).toBe('')
  })

  test('null/undefined 返回空字符串', () => {
    expect(varTypeTagColor(null)).toBe('')
    expect(varTypeTagColor(undefined)).toBe('')
  })
})