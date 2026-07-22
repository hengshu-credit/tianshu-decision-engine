export const FIELD_VALIDATION_REGEX_PRESETS = [
  { value: 'DIGITS', label: '数字', pattern: '^[0-9]*$' },
  {
    value: 'DIGITS_MIN_16',
    label: '至少16位的数字',
    pattern: '^\\d{16,}$'
  },
  {
    value: 'DIGITS_15_TO_18',
    label: '15-18位的数字',
    pattern: '^\\d{15,18}$'
  },
  {
    value: 'CHINESE',
    label: '汉字',
    pattern: '^[\\u4e00-\\u9fa5]{0,}$'
  },
  {
    value: 'ALPHANUMERIC',
    label: '英文和数字',
    pattern: '^[a-zA-Z0-9]+$'
  },
  {
    value: 'EMAIL',
    label: 'Email地址',
    pattern: '^\\w+([-+.]\\w+)*@\\w+([-.]\\w+)*\\.\\w+([-.]\\w+)*$'
  },
  {
    value: 'DOMAIN',
    label: '域名',
    pattern:
      '^[a-zA-Z0-9][-a-zA-Z0-9]{0,62}(\\.[a-zA-Z0-9][-a-zA-Z0-9]{0,62})+\\.?$'
  },
  { value: 'MOBILE', label: '手机号码', pattern: '^1[0-9]{10}$' },
  {
    value: 'ID_CARD',
    label: '身份证号',
    pattern:
      '^[1-9]\\d{5}(18|19|20)\\d{2}((0[1-9])|(1[0-2]))(([0-2][1-9])|10|20|30|31)\\d{3}[0-9Xx]$'
  },
  {
    value: 'IP_ADDRESS',
    label: 'IP地址',
    pattern:
      '^((?:(?:25[0-5]|2[0-4]\\d|[01]?\\d?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|[01]?\\d?\\d))$'
  }
]

export function findFieldValidationRegexPreset(pattern) {
  return FIELD_VALIDATION_REGEX_PRESETS.find(item => item.pattern === pattern)
}
