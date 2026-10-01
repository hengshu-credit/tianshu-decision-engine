import { operationColumnWidth } from '@/utils/operationColumnWidth'

describe('operationColumnWidth', () => {
  test('按按钮文案、间距和单元格内边距计算安全宽度', () => {
    expect(operationColumnWidth(['编辑', '进入', '鉴权', 'API', '删除']))
      .toBe(273)
  })

  test('条件按钮按最大可能组合预留且支持最小宽度', () => {
    expect(operationColumnWidth(['详情', '编辑', '发布', '下线', '转为全局', '删除']))
      .toBe(356)
    expect(operationColumnWidth(['删除'], { minWidth: 120 })).toBe(120)
  })

  test('空输入仍返回可用的最小宽度', () => {
    expect(operationColumnWidth([], { minWidth: 96 })).toBe(96)
  })
})
