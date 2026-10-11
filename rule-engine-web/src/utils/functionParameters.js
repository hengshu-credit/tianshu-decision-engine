/** 函数参数不能因解析失败或缺少名称而静默变成零参。 */
export function parseFunctionParameters(json) {
  if (json == null || typeof json === 'string' && !json.trim()) return []
  let params
  try { params = typeof json === 'string' ? JSON.parse(json) : json } catch { throw new Error('原参数配置不是有效 JSON，请重新配置参数后保存') }
  if (!Array.isArray(params) || params.some(item => !item || typeof item !== 'object' || Array.isArray(item))) {
    throw new Error('函数参数配置必须是对象数组，请重新配置参数后保存')
  }
  return params
}

export function validatedFunctionParameters(rows) {
  const params = parseFunctionParameters(rows)
  if (params.length === 1 && Object.keys(params[0]).every(key => ['name', 'type', 'label'].includes(key))
    && !params[0].name && !params[0].label && (!params[0].type || params[0].type === 'STRING')) return []
  const names = new Set()
  let explicitSignature
  for (let index = 0; index < params.length; index++) {
    const param = params[index]
    if (typeof param.name !== 'string' || !/^[A-Za-z_][A-Za-z0-9_]*$/.test(param.name.trim())) {
      throw new Error(`请填写第 ${index + 1} 个参数的有效名称，不会自动丢弃未填写的参数`)
    }
    const name = param.name.trim()
    if (names.has(name)) throw new Error(`参数名称重复：${name}`)
    names.add(name)
    if (param.type != null && typeof param.type !== 'string') throw new Error(`参数 ${name} 的业务类型无效`)
    const explicit = Object.prototype.hasOwnProperty.call(param, 'javaType')
    if (explicit && (typeof param.javaType !== 'string' || !param.javaType.trim() || param.javaType !== param.javaType.trim())) {
      throw new Error(`请明确参数 ${name} 的实际方法类型`)
    }
    if (explicitSignature !== undefined && explicitSignature !== explicit) throw new Error('请完整指定每个参数的方法类型，不能混用旧类型推断')
    explicitSignature = explicit
  }
  return params.map(param => ({ ...param }))
}

export function explicitFunctionSignature(form, params) {
  const method = form.implMethod?.trim() || form.funcCode
  if (!method || params.some(param => typeof param.javaType !== 'string' || !param.javaType)) return ''
  return `${method}(${params.map(param => param.javaType).join(',')})`
}

export const FUNCTION_JAVA_TYPES = [
  'double', 'int', 'long', 'float', 'short', 'byte', 'boolean', 'char',
  'java.lang.String', 'java.lang.Object', 'java.lang.Double', 'java.lang.Integer',
  'java.lang.Long', 'java.lang.Boolean', 'java.math.BigDecimal',
  'java.util.Date', 'java.time.LocalDate', 'java.time.LocalDateTime', 'java.util.List', 'java.util.Map',
  '[Ljava.lang.String;', '[I', '[D',
]
