export const generatorNames = { DATETIME: '当前日期时间', TIMESTAMP: '当前时间戳', FIXED: '固定值', TEMPLATE: '文本模板', DECIMAL: '小数区间', CHOICE: '随机候选值', INTEGER: '整数区间', BOOLEAN: '随机布尔值', DATE: '近期日期', TEXT: '随机文本', PHONE: '手机号码', EMAIL: '电子邮箱', UUID: '唯一标识' }
export const mockTypes = { STRING: '字符串', NUMBER: '数值', BOOLEAN: '布尔', DATE: '日期', DATETIME: '日期时间', TIMESTAMP: '时间戳', OBJECT: '对象', LIST: '列表' }
export function mockType(type) {
  if (['INT','INTEGER','LONG','BYTE','SHORT','DECIMAL','BIGDECIMAL','FLOAT','DOUBLE','PROBABILITY'].includes(type)) return 'NUMBER'
  if (['ARRAY','VECTOR','COLLECTION','SET'].includes(type)) return 'LIST'
  return ({ BOOL: 'BOOLEAN', MAP: 'OBJECT', LOCALDATE: 'DATE', LOCALDATETIME: 'DATETIME' })[type] || (mockTypes[type] ? type : 'STRING')
}
