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

globalThis.sampleFakerMethod = key => {
  const [module, method] = key.split('.')
  const value = withArguments[key] ? withArguments[key]() : faker[module][method]()
  return typeof value === 'bigint' ? value.toString() : JSON.stringify(value)
}
