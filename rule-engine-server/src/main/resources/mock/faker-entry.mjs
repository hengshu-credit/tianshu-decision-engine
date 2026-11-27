import { faker } from './locale/zh_CN.js'
import { faker as english } from './locale/en.js'

// 有必填参数或回调的方法使用可直接生成的业务样例。方法名由 Java 目录校验。
// JDK 17 对应版本的 GraalJS 缺少 ES2023 的非原地排序，Faker 的日期列表会使用它。
if (!Array.prototype.toSorted) {
  Object.defineProperty(Array.prototype, 'toSorted', { value(compare) { return Array.from(this).sort(compare) } })
}
const levels = ['low', 'medium', 'high']
const fields = { channel: 'web', status: 'active' }
const withArguments = {
  'date.between': () => faker.date.between(dateRange()),
  'date.betweens': () => faker.date.betweens({ ...dateRange(), count: 3 }),
  'string.fromCharacters': () => faker.string.fromCharacters('ABCDEFGHJKLMNPQRSTUVWXYZ23456789', 8),
  'helpers.arrayElement': () => faker.helpers.arrayElement(levels),
  'helpers.arrayElements': () => faker.helpers.arrayElements(levels),
  'helpers.enumValue': () => faker.helpers.enumValue({ ACTIVE: 'active', CLOSED: 'closed' }),
  'helpers.fake': () => faker.helpers.fake('{{person.lastName}}{{person.firstName}}'),
  'helpers.fromRegExp': () => faker.helpers.fromRegExp('[A-Z]{2}[0-9]{6}'),
  'helpers.maybe': () => faker.helpers.maybe(() => faker.person.firstName()) ?? '',
  'helpers.multiple': () => faker.helpers.multiple(() => faker.string.alphanumeric(6), { count: 3 }),
  'helpers.mustache': () => faker.helpers.mustache('REQ-{{code}}', { code: () => faker.string.numeric(8) }),
  'helpers.objectEntry': () => faker.helpers.objectEntry(fields),
  'helpers.objectKey': () => faker.helpers.objectKey(fields),
  'helpers.objectValue': () => faker.helpers.objectValue(fields),
  'helpers.rangeToNumber': () => faker.helpers.rangeToNumber({ min: 1, max: 100 }),
  'helpers.replaceSymbols': () => faker.helpers.replaceSymbols('??-######'),
  'helpers.shuffle': () => faker.helpers.shuffle(levels),
  'helpers.slugify': () => faker.helpers.slugify(faker.lorem.words(3)),
  'helpers.uniqueArray': () => faker.helpers.uniqueArray(levels, 2),
  'helpers.weightedArrayElement': () => faker.helpers.weightedArrayElement([{ weight: 8, value: 'low' }, { weight: 2, value: 'high' }]),
  // 中文语料未定义人名称谓，使用发行包的英文数据。
  'person.prefix': () => english.person.prefix(),
}

function dateRange() {
  const to = Date.now()
  return { from: to - 365 * 86400000, to }
}

let signatures
globalThis.configureFakerSignatures = json => { signatures = JSON.parse(json) }

function checkSize(value) {
  if (value && typeof value === 'object' && !Array.isArray(value)) {
    if (value.min === undefined || value.max === undefined || value.min > value.max) throw Error('数量范围需要 min 和 max')
    checkSize(value.min)
    checkSize(value.max)
  } else if (!Number.isInteger(value) || value < 0 || value > 4096) {
    throw Error('生成长度和数量须为 0 至 4096 的整数')
  }
}

function checkData(value, depth = 0) {
  if (depth > 16) throw Error('参数嵌套过深')
  if (typeof value === 'number' && (!Number.isFinite(value) || (Number.isInteger(value) && !Number.isSafeInteger(value)))) {
    throw Error('超出安全整数范围的参数请使用十进制字符串')
  }
  if (value && typeof value === 'object') {
    for (const [key, child] of Object.entries(value)) {
      if (['__proto__', 'constructor', 'prototype'].includes(key)) throw Error('无效参数属性：' + key)
      checkData(child, depth + 1)
    }
  }
}

function checkPattern(text) {
  if (typeof text !== 'string' || text.length > 512) throw Error('格式模板须为不超过 512 字符的字符串')
  for (const match of text.matchAll(/\{(\d+)(?:,(\d*))?\}/g)) {
    checkSize(Number(match[1]))
    if (match[2]) checkSize(Number(match[2]))
  }
}

function callback(spec, depth) {
  if (!spec || typeof spec.$faker !== 'string') throw Error('回调用 {"$faker":"方法编码","args":[]} 指定')
  return () => invoke(spec.$faker, spec.args ?? [], depth + 1)
}

// 模板中的方法与直接调用共用目录及参数校验，插入值按文本处理。
function template(pattern, depth) {
  const text = Array.isArray(pattern) ? faker.helpers.arrayElement(pattern) : pattern
  if (typeof text !== 'string') throw Error('请输入模板字符串')
  const result = []
  let offset = 0
  for (const token of text.matchAll(/\{\{(\w+\.\w+)(?:\(([\s\S]*?)\))?\}\}/g)) {
    const literal = text.slice(offset, token.index)
    if (literal.includes('{{')) throw Error('无效的模板表达式')
    result.push(literal, String(invoke(token[1], JSON.parse('[' + (token[2] || '') + ']'), depth + 1)))
    offset = token.index + token[0].length
  }
  if (text.slice(offset).includes('{{')) throw Error('无效的模板表达式')
  return result.join('') + text.slice(offset)
}

function invoke(key, args, depth = 0) {
  if (depth > 4 || !Object.hasOwn(signatures, key)) throw Error('未知方法或嵌套调用过深：' + key)
  const signature = signatures[key]
  if (!Array.isArray(args) || args.length > signature.length) throw Error('请按方法签名传入 JSON 参数数组')
  if (args.length && args.length < signature.filter(name => !name.endsWith('?')).length) throw Error('缺少必填参数：' + signature.join(', '))
  if (!args.length && Object.hasOwn(withArguments, key)) return withArguments[key]()
  const [module, method] = key.split('.')
  const options = args[0]
  for (const [index, value] of args.entries()) {
    const name = signature[index].replace(/\?$/, '')
    if (/^(?:length|count|wordCount|lineCount|sentenceCount|paragraphCount)$/.test(name)) checkSize(value)
  }
  if (key === 'helpers.arrayElements' || key === 'helpers.uniqueArray') {
    if (args[1] !== undefined) checkSize(args[1])
  }
  if (key === 'helpers.multiple' && args[1]?.count !== undefined) checkSize(args[1].count)
  if (options && typeof options === 'object' && !Array.isArray(options) && module !== 'helpers') {
    for (const field of ['length', 'count', 'extensionCount', 'symbolCount']) {
      if (Object.hasOwn(options, field)) checkSize(options[field])
    }
  } else if (typeof options === 'number' && (module === 'string' || module === 'word' || module === 'lorem' ||
      ['finance.accountNumber', 'finance.pin'].includes(key))) checkSize(options)

  if (key === 'helpers.fake' && args.length) return template(options, depth)
  if (options !== undefined && ['helpers.fromRegExp', 'helpers.replaceCreditCardSymbols'].includes(key)) checkPattern(options)
  if (key === 'finance.creditCardNumber') {
    const issuer = typeof options === 'string' ? options : options?.issuer
    if (issuer !== undefined) checkPattern(issuer)
  }
  if (key === 'helpers.multiple' || key === 'helpers.maybe' || (key === 'helpers.uniqueArray' && !Array.isArray(options))) {
    args[0] = callback(options, depth)
  }
  if (key === 'helpers.mustache' && args[1]) {
    for (const [name, value] of Object.entries(args[1])) {
      if (!/^[\w-]+$/.test(name)) throw Error('模板变量名只支持字母、数字、下划线或短横线')
      if (value?.$faker) args[1][name] = callback(value, depth)
    }
  }
  if (key === 'internet.password' && options?.pattern !== undefined) {
    checkPattern(options.pattern)
    const pattern = new RegExp(options.pattern)
    if (!Array.from({length: 94}, (_, i) => String.fromCharCode(33 + i)).some(c => pattern.test(c))) {
      throw Error('密码字符模式必须能匹配可打印字符')
    }
    options.pattern = pattern
  }
  if (['number.int', 'number.float'].includes(key) && options?.distributor) {
    options.distributor = callback(options.distributor, depth)
  }
  const instance = key === 'person.prefix' ? english : faker
  const result = instance[module][method](...args)
  return key === 'helpers.maybe' && result === undefined ? '' : result
}

globalThis.sampleFakerMethod = (key, rawArgs) => {
  const args = JSON.parse(rawArgs)
  checkData(args)
  const value = invoke(key, args)
  const json = typeof value === 'bigint' ? value.toString() : JSON.stringify(value)
  if (typeof json !== 'string' || json.length > 1048576) throw Error('生成结果为空或超过 1 MiB')
  return json
}
