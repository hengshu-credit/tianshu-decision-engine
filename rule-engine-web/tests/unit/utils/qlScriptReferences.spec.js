import { collectQlScriptReferences, buildQlReferenceDecorations } from '@/utils/qlScriptReferences'

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
    expect(collectQlScriptReferences(script, options, functions).map(ref => script.slice(ref.start, ref.end))).toEqual(['age'])
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
    expect(matches.map(ref => ref.code)).toEqual(['age', '"result"', 'age'])
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
    expect(decorations).toHaveLength(2)
    expect(decorations[0].range).toEqual({ startLineNumber: 1, startColumn: 8, endLineNumber: 1, endColumn: 11 })
    expect(decorations[1].options.before.content).toBe(' <b>年龄</b> ')
    expect(decorations[1].options.after.content).toBe('v3')
    expect(decorations[0].options.hoverMessage.isTrusted).toBe(false)
    expect(decorations[0].options.hoverMessage.supportHtml).toBe(false)
  })

  test('通用编辑器模式按类型、编码、名称、版本展示引擎引用', () => {
    const script = 'return age;'
    const matches = collectQlScriptReferences(script, [option('age', 1, 'INTEGER', { varLabelText: '年龄', varObj: { version: 3 } })])
    const model = { getPositionAt: offset => ({ lineNumber: 1, column: offset + 1 }) }
    const decorations = buildQlReferenceDecorations(matches, model, {}, 'LIVE', { includeType: true })
    expect(decorations).toHaveLength(3)
    expect(decorations[0].options.before.content).toBe('123 ')
    expect(decorations[1].options.inlineClassName).toBe('se-reference-code')
    expect(decorations[2].options.before.content).toBe(' 年龄 ')
    expect(decorations[2].options.after.content).toBe('v3')
  })
})
