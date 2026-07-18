import { buildFieldTreeRows, renderFieldTreeRows } from '@/utils/apiDoc/fieldTree'
import { apiDocStyles } from '@/utils/apiDoc/styles'

describe('API 文档树形字段表', () => {
  test('把点路径构造成保留原名和输入顺序的树形行', () => {
    const rows = buildFieldTreeRows([
      { path: 'params.customer.name', type: 'STRING', label: '姓名' },
      { path: 'params.customer.age', type: 'INTEGER', label: '年龄' },
      { path: 'params.RiskCode', type: 'STRING', label: '风险编码' }
    ])

    expect(rows.map(row => [row.name, row.depth, row.hasChildren])).toEqual([
      ['params', 0, true],
      ['customer', 1, true],
      ['name', 2, false],
      ['age', 2, false],
      ['RiskCode', 1, false]
    ])
    expect(rows.find(row => row.name === 'RiskCode').path).toBe('params.RiskCode')
  })

  test('已有父字段元数据覆盖自动生成的对象节点', () => {
    const rows = buildFieldTreeRows([
      { path: 'data.result', type: 'OBJECT', required: true, label: '执行结果' },
      { path: 'data.result.code', type: 'STRING', required: false, label: '业务码' }
    ])

    expect(rows.find(row => row.path === 'data.result')).toEqual(expect.objectContaining({
      type: 'OBJECT',
      required: true,
      label: '执行结果',
      hasChildren: true
    }))
  })

  test('渲染可访问的折叠按钮、层级和父子标记', () => {
    const html = renderFieldTreeRows([
      { path: 'params.customer.name', type: 'STRING', required: true, label: '<姓名>', exampleValue: '张三' }
    ])

    expect(html).toContain('data-field-toggle="params"')
    expect(html).toContain('aria-expanded="true"')
    expect(html).toContain('data-parent-id="params.customer"')
    expect(html).toContain('--field-depth:2')
    expect(html).toContain('&lt;姓名&gt;')
  })

  test('树形字段样式按层级缩进并保留折叠控制位', () => {
    expect(apiDocStyles).toContain('.field-name')
    expect(apiDocStyles).toContain('var(--field-depth)')
    expect(apiDocStyles).toContain('.field-toggle')
  })
})
