import { collectQlScriptReferences, createQlReferenceCatalog, buildQlReferenceDecorations } from '@/utils/qlScriptReferences'

const option = (code, id, type = 'NUMBER', extra = {}) => ({
  varCode: code, varLabelText: `名称${id}`, varType: type,
  _varId: String(id), _refType: 'VARIABLE', ...extra,
})
const options = [
  option('age', 1, 'INTEGER', { varObj: { version: 3 } }),
  option('result', 2),
  option('客户.订单', 3, 'LIST'),
  option('客户.订单.金额', 4, 'NUMBER', { _refType: 'DATA_OBJECT', objectId: '10' }),
  option('客户.地址', 5, 'OBJECT'),
  option('客户.地址.城市', 6, 'STRING'),
  option('idx', 7),
]
const functions = [{ id: '8', funcCode: 'risk', funcName: '风险计算', version: 2 }]

describe('QL 脚本行内引擎引用', () => {
  test('两万项目录在连续编辑时复用索引，不重新遍历无关资源', () => {
    const unusedCode = vi.fn(() => 'unused')
    const large = [...options, ...Array.from({ length: 20000 }, (_, index) => option('field' + index, index + 100)),
      { ...option('unused', 30000), get varCode() { return unusedCode() } }]
    const bindings = [{ refCode: 'age', refType: 'VARIABLE', varId: '1' }]
    const catalog = createQlReferenceCatalog(large, functions, bindings)
    unusedCode.mockClear()
    for (let index = 0; index < 40; index++) {
      const refs = collectQlScriptReferences(`age + risk(${index})`, large, functions, bindings, [], catalog)
      expect(refs.map(ref => ref.code)).toEqual(['age', 'risk'])
      expect(refs[0]).toMatchObject({ bound: true, option: { _varId: '1' } })
    }
    expect(unusedCode).not.toHaveBeenCalled()
  })
  test('内置方法、普通方法和链式方法均逆向展示，不伪造函数 ID', () => {
    const script = 'return resourceVersionValue("VARIABLE", "548", "BUSINESS_VERSION:948", "") + obj.getName().trim();'
    const refs = collectQlScriptReferences(script)
    expect(refs.map(item => item.code)).toEqual(['resourceVersionValue', 'obj.getName', 'trim'])
    expect(refs[0].label).toBe('固定版本取值')
    expect(refs[0].option.builtin).toBe(true)
    expect(refs.every(item => item.option._varId == null)).toBe(true)
  })
  test('普通方法名不会命中对象原型属性而被冒充为引擎内置方法', () => {
    const refs = collectQlScriptReferences('constructor(); toString();')
    expect(refs.map(item => item.label)).toEqual(['constructor', 'toString'])
    expect(refs.every(item => item.option.builtin === false)).toBe(true)
  })
  test('固定版本调用按 ID、版本来源和版本 ID 精确反解，不能串当前资源或其他版本', () => {
    const script = 'resourceVersionValue("VARIABLE", "9007199254740993", "BUSINESS_VERSION:948", "")'
    const pinned = [{ refType: 'VARIABLE', refId: '9007199254740993', versionSource: 'BUSINESS_VERSION', versionId: '948', version: 7, displayVersion: 3, code: 'Old_AGE', label: '历史年龄', valueType: 'INTEGER' }]
    const refs = collectQlScriptReferences(script, options, [], [], pinned)
    expect(refs).toHaveLength(2)
    expect(refs[1]).toMatchObject({ inlineMetadata: true, code: 'Old_AGE', label: '历史年龄', version: 3, option: { _varId: '9007199254740993' } })
    expect(collectQlScriptReferences(script.replace(':948', ':949'), options, [], [], pinned)).toHaveLength(1)
    expect(script).toContain('"9007199254740993"')
  })
  test('入参、赋值出参和函数均可展示，源码与稳定引用不被改写', () => {
    const script = 'result = risk(age);'
    const bindings = [{ refCode: 'age', varId: '1', refType: 'VARIABLE' }]
    const before = JSON.stringify(bindings)
    const matches = collectQlScriptReferences(script, options, functions, bindings)
    expect(matches.map(ref => script.slice(ref.start, ref.end))).toEqual(['result', 'risk', 'age'])
    expect(matches.map(ref => ref.label)).toEqual(['名称2', '风险计算', '名称1'])
    expect(matches[2]).toMatchObject({ bound: true, version: 3 })
    expect(JSON.stringify(bindings)).toBe(before)
  })

  test('对象和列表只标注最长已知路径，保留下标、动态下标和未知后缀', () => {
    const script = '客户.地址.城市 + 客户.订单[0].金额 + 客户.订单[idx].金额 + 客户.地址.邮编'
    const matches = collectQlScriptReferences(script, options)
    expect(matches.map(ref => script.slice(ref.start, ref.end))).toEqual([
      '客户.地址.城市', '客户.订单[0].金额', '客户.订单[idx].金额', 'idx', '客户.地址',
    ])
    expect(matches.map(ref => ref.option._varId)).toEqual(['6', '4', '4', '7', '5'])
  })

  test('字符串、注释、相似名称、其他对象成员和非调用函数名不被渲染', () => {
    const script = `// age risk(age)\n"age" + 'result'; /* 客户.地址.城市 */\nother.age + ageExtra + risk + obj.risk(age);`
    expect(collectQlScriptReferences(script, options, functions).map(ref => script.slice(ref.start, ref.end))).toEqual(['obj.risk', 'age'])
  })

  test('支持带空白的路径和字符串下标，未闭合字符串不误标注', () => {
    const script = `客户 ["地址"] . 城市 + age; "age`
    expect(collectQlScriptReferences(script, options).map(ref => script.slice(ref.start, ref.end))).toEqual(['客户 ["地址"] . 城市', 'age'])
  })

  test('对象的动态 key 无法静态确定时，只渲染可确认的父路径', () => {
    const script = '客户.地址[idx].城市'
    expect(collectQlScriptReferences(script, options).map(ref => script.slice(ref.start, ref.end))).toEqual(['客户.地址', 'idx'])
  })

  test('_result 顶层 key 按对应字段渲染，值和其他字符串保持各自语义', () => {
    const script = `result = age; _result = {"result": result, 'age': age, "unknown": "age"}; _result`
    const bindings = [{ refCode: 'result', varId: '2', refType: 'VARIABLE' }]
    const matches = collectQlScriptReferences(script, options, [], bindings)
    const keys = matches.filter(ref => ref.outputKey)
    expect(keys.map(ref => script.slice(ref.start, ref.end))).toEqual(['"result"', "'age'"])
    expect(keys.map(ref => ref.label)).toEqual(['名称2', '名称1'])
    expect(keys[0]).toMatchObject({ bound: true, option: { _varId: '2' } })
    expect(matches.map(ref => ref.code)).toEqual(['result', 'age', '"result"', "'age'"])
    expect(bindings).toEqual([{ refCode: 'result', varId: '2', refType: 'VARIABLE' }])
  })

  test('_result 的 value 表达式整体保留原始代码，包括同名字段、函数及嵌套对象', () => {
    const script = `age; _result = {"result": risk(age, 客户.订单[idx].金额), "unknown": {age: [age, result]}}; age;`
    const bindings = [{ refCode: 'age', varId: '1', refType: 'VARIABLE' }]
    const matches = collectQlScriptReferences(script, options, functions, bindings)
    expect(matches.map(ref => ref.code)).toEqual(['age', '"result"', 'risk', 'age'])
    expect(matches[1].outputKey).toBe(true)
  })

  test('只识别 _result 的直接字段，不误标普通对象、嵌套 key、注释或动态 key', () => {
    const script = `other = {"age": 1}; obj._result = {"age": 2};
      // _result = {"age": 3};
      _result = ({result: {"age": 4}, "age": risk(1, 2), [idx]: 6});`
    const keys = collectQlScriptReferences(script, options, functions).filter(ref => ref.outputKey)
    expect(keys.map(ref => ref.code)).toEqual(['result', '"age"'])
  })

  test('输出 key 要精确匹配，不把未知字段后缀当成父字段', () => {
    const script = '_result = {"客户.地址.城市": "上海", "客户.地址.未知": 1};'
    const keys = collectQlScriptReferences(script, options).filter(ref => ref.outputKey)
    expect(keys.map(ref => ref.option._varId)).toEqual(['6'])
  })

  test('同名引用按已保存的 ID 和类型区分，失效 ID 不回退到同名资源', () => {
    const duplicate = [...options, option('age', 99)]
    expect(collectQlScriptReferences('age', duplicate)).toEqual([])
    expect(collectQlScriptReferences('age', duplicate, [], [{ refCode: 'age', varId: '1', refType: 'VARIABLE' }])[0].option._varId).toBe('1')
    expect(collectQlScriptReferences('age', options, [], [{ refCode: 'age', varId: '999', refType: 'VARIABLE' }])).toEqual([])
  })

  test('固定版本优先于实时目录，缺少版本不伪造 v1', () => {
    const binding = { refCode: 'age', varId: '1', refType: 'VARIABLE', versionMode: 'FIXED', version: 2 }
    expect(collectQlScriptReferences('age', options, [], [binding])[0].version).toBe(2)
    expect(collectQlScriptReferences('result', options)[0].version).toBeUndefined()
  })

  test('行内装饰只添加显示文本，范围使用源码位置且转义悬浮内容', () => {
    const script = 'return age;'
    const matches = collectQlScriptReferences(script, [option('age', 1, 'INTEGER', { varLabelText: '<b>年龄</b>', varObj: { version: 3 } })])
    const model = { getPositionAt: offset => ({ lineNumber: 1, column: offset + 1 }) }
    const decorations = buildQlReferenceDecorations(matches, model)
    expect(decorations).toHaveLength(5)
    expect(decorations[3].range).toEqual({ startLineNumber: 1, startColumn: 8, endLineNumber: 1, endColumn: 11 })
    expect(decorations[2].options.before.content.trim()).toBe('<b>年龄</b>')
    expect(decorations[4].options.after.content.trim()).toBe('v3')
    expect(decorations[0].options.before.inlineClassName).toContain('content-ref__anchor-7')
    expect(decorations[2].options.before.inlineClassName).toContain('content-ref__anchor-7')
    expect(decorations[4].options.after.inlineClassName).toContain('content-ref__anchor-10')
    expect(decorations[3].options.inlineClassName).not.toContain('content-ref__metadata')
    expect(decorations.filter(item => item.options.hoverMessage)).toHaveLength(1)
    expect(decorations[3].options.hoverMessage.isTrusted).toBe(false)
    expect(decorations[3].options.hoverMessage.supportHtml).toBe(false)
  })

  test('通用编辑器模式按类型、编码、名称、版本展示引擎引用', () => {
    const script = 'return age;'
    const matches = collectQlScriptReferences(script, [option('age', 1, 'INTEGER', { varLabelText: '年龄', varObj: { version: 3 } })])
    const model = { getPositionAt: offset => ({ lineNumber: 1, column: offset + 1 }) }
    const decorations = buildQlReferenceDecorations(matches, model, {}, 'LIVE', { includeType: true })
    expect(decorations).toHaveLength(5)
    expect(decorations[0].options.before.content.trim()).toBe('变量')
    expect(decorations[1].options.before.content.trim()).toBe('123')
    expect(decorations[1].options.before.inlineClassName).toContain('content-ref__type--number')
    expect(decorations[3].options.inlineClassName).toContain('content-ref__code')
    expect(decorations[2].options.before.content.trim()).toBe('年龄')
    expect(decorations[4].options.after.content.trim()).toBe('v3')
  })
})
