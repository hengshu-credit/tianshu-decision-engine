export class ConditionRangeError extends Error {
  constructor(message) {
    super(message)
    this.name = 'ConditionRangeError'
  }
}

export function isRangeOperator(operator) {
  return operator === 'between' || operator === 'not_between'
}

function decimal(value) {
  const match = /^([+-]?)(\d+(?:\.\d*)?|\.\d+)(?:[eE]([+-]?\d+))?$/.exec(value)
  if (!match) return null
  const [integer, fraction = ''] = match[2].split('.')
  const digits = (integer + fraction).replace(/^0+/, '')
  const exponent = BigInt(match[3] || 0)
  if (exponent < -2147483647n || exponent > 2147483647n) return null
  const scale = BigInt(fraction.length) - exponent
  if (scale < -2147483648n || scale > 2147483647n) return null
  return {
    sign: digits ? (match[1] === '-' ? -1 : 1) : 0,
    digits,
    order: BigInt(digits.length) - scale,
  }
}

export function isValidDecimalText(value) {
  return value != null && decimal(String(value).trim()) !== null
}

function compareDecimals(left, right) {
  if (left.sign !== right.sign) return left.sign < right.sign ? -1 : 1
  if (!left.sign) return 0
  if (left.order !== right.order) return (left.order < right.order ? -1 : 1) * left.sign
  const size = Math.max(left.digits.length, right.digits.length)
  const l = left.digits.padEnd(size, '0')
  const r = right.digits.padEnd(size, '0')
  return (l === r ? 0 : l < r ? -1 : 1) * left.sign
}

function dateValue(value) {
  // 与运行时一致：10 位及以上整数文本优先按 Long 毫秒解释，不猜成紧凑日期。
  if (/^-?\d{10,}$/.test(value)) {
    const millis = BigInt(value)
    return millis >= -9223372036854775808n && millis <= 9223372036854775807n
      ? { kind: 'epoch', value: millis } : null
  }
  const compact = /^(\d{4})(\d{2})(\d{2})$/.exec(value)
  const separated = /^(\d{1,4})([-/])(\d{1,2})\2(\d{1,2})(?:([ T])(\d{1,2}):(\d{1,2}):(\d{1,2})(?:\.(\d{1,3}))?)?$/.exec(value)
  if (!compact && !separated) return null
  if (separated && separated[2] === '/' && (separated[5] === 'T' || separated[9])) return null
  const [year, month, day, hour, minute, second, millis] = compact
    ? [Number(compact[1]), Number(compact[2]), Number(compact[3]), 0, 0, 0, 0]
    : [Number(separated[1]), Number(separated[3]), Number(separated[4]), Number(separated[6] || 0), Number(separated[7] || 0), Number(separated[8] || 0), Number(separated[9] || 0)]
  if (year < 1 || month < 1 || month > 12 || hour > 23 || minute > 59 || second > 59) return null
  const leap = year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0)
  const days = [31, leap ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]
  if (day < 1 || day > days[month - 1]) return null
  const date = new Date(0)
  date.setUTCFullYear(year, month - 1, day)
  date.setUTCHours(hour, minute, second, millis)
  return { kind: 'calendar', value: BigInt(date.getTime()) }
}

export function requireRangeBounds(value, valueType, valueKind = 'CONST') {
  if (valueKind !== 'CONST') throw new ConditionRangeError('区间条件必须分别填写上下界常量')
  const parts = value == null ? [] : String(value).split(',')
  if (parts.length > 2) throw new ConditionRangeError('区间只能包含下界和上界两个边界')
  if (!parts[0]?.trim()) throw new ConditionRangeError('区间下界不能为空')
  if (!parts[1]?.trim()) throw new ConditionRangeError('区间上界不能为空')
  const bounds = parts.map(part => part.trim())
  const dateType = ['DATE', 'DATETIME', 'TIMESTAMP', 'LOCALDATE', 'LOCALDATETIME'].includes(String(valueType || '').toUpperCase())
  const parsed = bounds.map(dateType ? dateValue : decimal)
  if (parsed.some(value => !value)) throw new ConditionRangeError(dateType ? '区间上下界必须是有效日期或时间' : '区间上下界必须是有效数字')
  // 混合 epoch / 本地日期的先后受服务端时区影响，由后端用实际运行时规则作最终校验。
  const reversed = dateType
    ? parsed[0].kind === parsed[1].kind && parsed[0].value > parsed[1].value
    : compareDecimals(parsed[0], parsed[1]) > 0
  if (reversed) throw new ConditionRangeError('区间下界不能大于上界')
  return bounds
}
