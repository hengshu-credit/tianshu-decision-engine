import { reactive } from 'vue'
import { cloneExactJson, createExactNumber, exactNumberText, isExactNumber, parseExactJson, parseExecutionSnapshotJson, stringifyExactJson } from '@/utils/exactJson'

describe('测试配置精确JSON传输', () => {
  test('旧执行快照按业务视图读取，深debug和别名元数据不消耗业务预算', () => {
    let deep = { debug: true }
    for (let i = 0; i < 160; i++) deep = { next: deep }
    const raw = '{"code":200,"data":{"success":true,"result":{"n":9007199254740993,"traces":["business"],"__proto__":"kept"},"outputs":{"ignored":1},"errorMessage":null,"error":"ignored","traces":' + JSON.stringify(deep) + '}}'
    const view = parseExecutionSnapshotJson(raw)
    expect(stringifyExactJson(view)).toBe('{"success":true,"result":{"n":9007199254740993,"traces":["business"],"__proto__":"kept"},"errorMessage":null}')
    expect(() => parseExactJson(raw)).toThrow(/深度/)
    for (const alias of ['outputs', 'value', 'resolvedValue']) {
      expect(stringifyExactJson(parseExecutionSnapshotJson(`{"success":false,"${alias}":1e400,"error":"rejected"}`))).toBe('{"success":false,"result":1e400,"errorMessage":"rejected"}')
    }
  })

  test('快照排除分支仍检查重复键、语法和尾随值，不能隐去无效JSON', () => {
    for (const raw of [
      '{"data":{"success":true,"result":1,"traces":[{"a":1,"a":2}]}}',
      '{"data":{"success":true,"result":1,"traces":[{"a":}]}}',
      '{"data":{"success":true,"result":1,"traces":[1,]}}',
      '{"data":{"success":true,"result":1}} {}',
    ]) expect(() => parseExecutionSnapshotJson(raw)).toThrow()
  })

  test('业务128层边界一致，result内同名traces及普通手工JSON不享受排除', () => {
    const nested = count => {
      let value = 1
      for (let i = 0; i < count; i++) value = { next: value }
      return value
    }
    expect(() => parseExecutionSnapshotJson(JSON.stringify({ data: { success: true, result: nested(128) } }))).not.toThrow()
    expect(() => parseExecutionSnapshotJson(JSON.stringify({ data: { success: true, result: nested(129) } }))).toThrow(/深度/)
    expect(() => parseExecutionSnapshotJson(JSON.stringify({ data: { success: true, result: { traces: nested(160) } } }))).toThrow(/深度/)
    expect(() => parseExecutionSnapshotJson(JSON.stringify({ traces: nested(160) }))).toThrow(/深度/)
    expect(() => parseExecutionSnapshotJson(JSON.stringify({ success: 'true', result: 1, traces: nested(160) }))).toThrow(/深度/)
    const manual = '{"traces":["business"],"arbitrary":{"n":9007199254740993}}'
    expect(stringifyExactJson(parseExecutionSnapshotJson(manual))).toBe(manual)
    expect(() => parseExecutionSnapshotJson('[]')).toThrow(/对象/)
  })

  test('修改一个字段不损失未知大整数或长小数，不把数字改成字符串', () => {
    const original = '{"params":{"known":1,"unknown":9007199254740993,"precise":0.123456789012345678901,"explicitNull":null},"metadata":{"keep":true}}'
    const value = parseExactJson(original)
    value.params.known = createExactNumber('2.50')
    expect(stringifyExactJson(value)).toBe('{"params":{"known":2.50,"unknown":9007199254740993,"precise":0.123456789012345678901,"explicitNull":null},"metadata":{"keep":true}}')
    expect(Object.hasOwn(value.params, 'missing')).toBe(false)
    expect(value.params.explicitNull).toBeNull()
  })

  test('深层数组对象和指数保持原数值token，不按指数展开', () => {
    const text = '{"items":[{"n":1e400,"s":"9007199254740993"}],"value":-0.00100}'
    const value = parseExactJson(text)
    expect(isExactNumber(value.items[0].n)).toBe(true)
    expect(exactNumberText(value.items[0].n)).toBe('1e400')
    expect(stringifyExactJson(value)).toBe(text)
  })

  test('不能把已舍入的普通JS大整数宣称为精确原值', () => {
    expect(() => exactNumberText(JSON.parse('9007199254740993'))).toThrow(/原始 JSON/)
    expect(exactNumberText(25)).toBe('25')
  })

  test('无效JSON和重复键明确拒绝，避免编辑时静默丢掉某个成员', () => {
    expect(() => parseExactJson('{"n":}')).toThrow()
    expect(() => parseExactJson('{"n":1,"n":2}')).toThrow()
  })

  test('未知__proto__字段必须作为自有数据保留，不得改变解析对象原型', () => {
    const text = '{"params":{"__proto__":{"kept":true},"known":1}}'
    const parsed = parseExactJson(text)
    expect(Object.hasOwn(parsed.params, '__proto__')).toBe(true)
    expect(Object.hasOwn(parsed.params, 'kept')).toBe(false)
    expect(stringifyExactJson(parsed)).toBe(text)
  })

  test('普通JSON中的数字标记同名属性仍是普通数据，不能变成数字或丢字段', () => {
    const text = '{"metadata":{"isLosslessNumber":true,"value":"25","unknown":"keep","constructor":{"prototype":"data"}}}'
    const parsed = parseExactJson(text)
    expect(isExactNumber(parsed.metadata)).toBe(false)
    expect(stringifyExactJson(parsed)).toBe(text)
  })

  test('Vue响应式数据通过精确克隆仍保留数值身份，不能退化成内部value对象', () => {
    const text = '{"n":9007199254740993,"items":[0.123456789012345678901]}'
    const copied = cloneExactJson(reactive(parseExactJson(text)))
    expect(isExactNumber(copied.n)).toBe(true)
    expect(stringifyExactJson(copied)).toBe(text)
  })

  test('严格JSON模式拒绝注释和尾逗号，writer拒绝非JSON对象', () => {
    expect(() => parseExactJson('{/*comment*/"n":1}')).toThrow()
    expect(() => parseExactJson('{"n":1,}')).toThrow()
    expect(() => stringifyExactJson({ date: new Date() })).toThrow(/JSON/)
    expect(() => stringifyExactJson({ number: Infinity })).toThrow()
    const sparse = new Array(2)
    sparse[1] = undefined
    expect(stringifyExactJson(sparse)).toBe('[null,null]')
  })
})
