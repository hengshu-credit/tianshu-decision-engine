import { buildResourcePreview, pairPreviewSections } from '@/utils/resourceVersionPreview'

describe('版本业务预览', () => {
  test('仅开放接口响应映射变化时也能从规则快照识别业务差异', () => {
    const preview = targetField => buildResourcePreview({
      modelType: 'TABLE', content: { modelJson: '{"rules":[]}', openApiConfigJson: JSON.stringify({ responseMappings: [{ targetField }] }) },
    }, 'RULE')
    const rows = pairPreviewSections(preview('old_score').sections, preview('new_score').sections)
    expect(rows.filter(row => row.status !== 'same').map(row => row.key)).toEqual(['openApiConfigJson'])
    const mapping = rows.find(row => row.key === 'openApiConfigJson').right.nodes[0].children[0]
    expect(mapping.title).toBe('响应字段映射')
    expect(mapping.children[0].children[0].text).toBe('new_score')
  })
  test('嵌套来源与枚举可读，默认值保留原文，零值和 false 不丢失', () => {
    const result = buildResourcePreview(JSON.stringify({ varLabel: '风险分', varSource: 'DB', defaultValue: '{"原文":0}', recordResult: false, sourceConfig: '{"sql":"select score from risk","datasourceId":9223372036854775806}', options: [{ value: 0, label: '低风险' }] }), 'VARIABLE')
    const text = JSON.stringify(result)
    expect(result.warning).toBe('')
    expect(text).toContain('数据库查询')
    expect(text).toContain('查询 SQL')
    expect(text).toContain('9223372036854775806')
    expect(result.sections.find(row => row.key === 'defaultValue').nodes[0].text).toBe('{"原文":0}')
    expect(result.sections.find(row => row.key === 'recordResult').nodes[0].text).toBe('否')
    expect(result.sections.find(row => row.key === 'options').nodes[0].children[0].children[0].text).toBe('0')
  })

  test('读取真实规则快照 content.modelJson，展示条件和动作，不使用实时资源', () => {
    const snapshot = { ruleName: '授信准入', modelType: 'TABLE', content: { modelJson: JSON.stringify({ hitPolicy: 'FIRST', rules: [{ ruleName: '成年客户', conditionRoot: { type: 'group', op: 'AND', children: [{ type: 'leaf', leftOperand: { kind: 'REFERENCE', refId: '9007199254740993', refType: 'VARIABLE', label: '申请年龄' }, operator: '>=', rightOperand: { kind: 'LITERAL', value: 18 } }] }, actions: [{ targetOperand: { kind: 'REFERENCE', refId: '2', refType: 'VARIABLE', label: '审批结果' }, valueOperand: { kind: 'LITERAL', value: '通过' } }] }] }) } }
    const result = buildResourcePreview(snapshot, 'RULE')
    expect(result.warning).toBe('')
    const text = JSON.stringify(result.sections)
    for (const term of ['首条命中即停止', '申请年龄', '9007199254740993', '18', '审批结果', '通过']) expect(text).toContain(term)
  })

  test.each(['TABLE', 'TREE', 'FLOW', 'RULE_SET', 'CROSS', 'SCORE', 'CROSS_ADV', 'SCORE_ADV', 'SCRIPT'])('%s 规则预览均可生成', modelType => {
    const result = buildResourcePreview({ modelType, content: { modelJson: '{"script":"return 1;"}' } }, 'RULE')
    expect(result.warning).toBe('')
    expect(result.sections.some(section => section.key.startsWith('rule:'))).toBe(true)
  })

  test('未知配置保留；新增、删除、修改按同一字段对齐；对象键顺序不影响比较', () => {
    const left = buildResourcePreview({ varLabel: '旧名称', sourceConfig: '{"sql":"select 1","datasourceId":1}', defaultValue: 0 }, 'VARIABLE')
    const right = buildResourcePreview({ varLabel: '新名称', sourceConfig: '{"datasourceId":1,"sql":"select 1"}', customSetting: false }, 'VARIABLE')
    expect(pairPreviewSections(left.sections, right.sections).map(row => [row.key, row.status])).toEqual([
      ['varLabel', 'changed'], ['defaultValue', 'removed'], ['sourceConfig', 'same'], ['customSetting', 'added'],
    ])
  })

  test.each(['null', '[]', '{bad'])('异常快照 %s 显示提示而不是空白', value => {
    expect(buildResourcePreview(value, 'MODEL').warning).toContain('格式异常')
  })

  test('损坏规则结构保留基本信息，并提示查看原始快照', () => {
    const result = buildResourcePreview({ ruleName: '损坏规则', modelType: 'TABLE', content: { modelJson: '{"rules":{}}' } }, 'RULE')
    expect(result.warning).toContain('无法预览')
    expect(result.sections[0].nodes[0].text).toBe('损坏规则')
  })

  test('模型二进制不挤占预览但内容改变仍可标识', () => {
    const left = buildResourcePreview({ modelContent: 'AAAA' }, 'MODEL')
    const right = buildResourcePreview({ modelContent: 'BBBB' }, 'MODEL')
    expect(left.sections[0].nodes[0].text).not.toContain('AAAA')
    expect(pairPreviewSections(left.sections, right.sections)[0].status).toBe('changed')
  })
})
