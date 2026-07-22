import {
  FIELD_VALIDATION_REGEX_PRESETS,
  findFieldValidationRegexPreset
} from '@/constants/fieldValidationRegexPresets'

const cases = [
  ['DIGITS', '数字', '012345', '12a'],
  ['DIGITS_MIN_16', '至少16位的数字', '1234567890123456', '123456789012345'],
  ['DIGITS_15_TO_18', '15-18位的数字', '123456789012345', '12345678901234'],
  ['CHINESE', '汉字', '中文', '中文A'],
  ['ALPHANUMERIC', '英文和数字', 'abc123', 'abc-123'],
  ['EMAIL', 'Email地址', 'user.name+tag@example-domain.com', 'user@'],
  ['DOMAIN', '域名', 'sub.example.com.', 'example'],
  ['MOBILE', '手机号码', '13800138000', '23800138000'],
  ['ID_CARD', '身份证号', '11010519900101123X', '11010519901301123X'],
  ['IP_ADDRESS', 'IP地址', '192.168.1.1', '256.168.1.1']
]

test.each(cases)('%s 预置匹配有效值并拒绝无效值', (value, label, valid, invalid) => {
  const preset = FIELD_VALIDATION_REGEX_PRESETS.find(item => item.value === value)
  expect(preset.label).toBe(label)
  expect(new RegExp(preset.pattern).test(valid)).toBe(true)
  expect(new RegExp(preset.pattern).test(invalid)).toBe(false)
})

test('预置编码唯一，且可按正则精确反查', () => {
  const values = FIELD_VALIDATION_REGEX_PRESETS.map(item => item.value)
  expect(FIELD_VALIDATION_REGEX_PRESETS).toHaveLength(10)
  expect(new Set(values).size).toBe(values.length)
  const mobile = FIELD_VALIDATION_REGEX_PRESETS.find(item => item.value === 'MOBILE')
  expect(findFieldValidationRegexPreset(mobile.pattern)).toBe(mobile)
  expect(findFieldValidationRegexPreset('^custom$')).toBeUndefined()
})
