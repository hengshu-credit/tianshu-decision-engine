import { buildFieldTree, filterFieldTree, pickerTypeMatches } from '@/utils/fieldPicker'

test.each(['DOUBLE', 'INTEGER', 'LONG', 'DECIMAL', 'PROBABILITY'])('模型%s字段允许绑定统一数值变量，仍排除字符串', expected => {
  expect(pickerTypeMatches('NUMBER', expected)).toBe(true)
  expect(pickerTypeMatches(expected, 'NUMBER')).toBe(true)
  expect(pickerTypeMatches('STRING', expected)).toBe(false)
})

test('DOUBLE模型字段的选择目录保留NUMBER变量的原始ID', () => {
  const tree = buildFieldTree([
    { _varId: 527, _refType: 'VARIABLE', varCode: 'Age', varLabel: '年龄', varType: 'NUMBER' },
    { _varId: 528, _refType: 'VARIABLE', varCode: 'Name', varLabel: '名称', varType: 'STRING' },
  ])
  const filtered = filterFieldTree(tree, 'DOUBLE')
  expect(filtered.nodes.get('VARIABLE:527')?.selectable).toBe(true)
  expect(filtered.nodes.has('VARIABLE:528')).toBe(false)
})
