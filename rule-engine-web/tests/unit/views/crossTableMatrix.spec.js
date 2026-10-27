import CrossTable from '@/views/designer/CrossTable.vue'

function context() {
  const value = {
    ...CrossTable.data(),
    model: { rowVar: {}, colVar: {}, resultVar: {}, rowHeaders: ['A', 'B'], colHeaders: ['X', 'Y'], cells: [[0, false], [30, 40]], rowHeaderOperands: [null, null], colHeaderOperands: [null, null], cellOperands: [[null, null], [null, null]] },
    $confirm: vi.fn().mockResolvedValue(true), $message: { warning: vi.fn() },
  }
  for (const [name, method] of Object.entries(CrossTable.methods)) value[name] = method.bind(value)
  return value
}

test('删除普通交叉表行前说明有效结果数量，0 和 false 也是结果', async () => {
  const value = context()
  value.$confirm.mockRejectedValueOnce('cancel')
  await value.requestRemoveRow(0)
  expect(value.model.cells).toEqual([[0, false], [30, 40]])
  expect(value.$confirm).toHaveBeenCalledWith(expect.stringContaining('2'), expect.any(String), expect.any(Object))
  await value.requestRemoveRow(0)
  expect(value.model.rowHeaders).toEqual(['B'])
  expect(value.model.cells).toEqual([[30, 40]])
})

test('更换维度可取消，确认后只清空结果而保留表头输入原文', async () => {
  const value = context()
  const operand = { kind: 'REFERENCE', refId: 12, refType: 'VARIABLE', code: 'Mixed_Case', valueType: 'STRING' }
  value.$confirm.mockRejectedValueOnce('cancel')
  await value.requestDimOperand('rowVar', operand)
  expect(value.model.rowVar).toEqual({})
  await value.requestDimOperand('rowVar', operand)
  expect(value.model.rowVar._varId).toBe(12)
  expect(value.model.rowVar.varCode).toBe('Mixed_Case')
  expect(value.model.rowHeaders).toEqual(['A', 'B'])
  expect(value.model.cells).toEqual([['', ''], ['', '']])
  expect(value.model.cellOperands).toEqual([[null, null], [null, null]])
})
