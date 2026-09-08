import { lineageLayers } from '@/utils/lineageLayers'

test('共享依赖存在直达结果的边时，仍满足全部依赖层级且不受返回顺序影响', () => {
  const nodes = ['current', 'project', 'ruleA', 'ruleB', 'object', 'field']
  const edges = [
    ['project', 'current'], ['project', 'ruleA'], ['project', 'ruleB'],
    ['object', 'field'], ['field', 'ruleA'], ['field', 'ruleB'],
    ['ruleA', 'current'], ['ruleB', 'current'],
  ].map(([fromId, toId]) => ({ fromId, toId }))
  const layers = lineageLayers(nodes, edges, 'current')
  expect(layers.get('current')).toBe(0)
  expect(layers.get('ruleA')).toBe(-1)
  expect(layers.get('ruleB')).toBe(-1)
  expect(layers.get('field')).toBe(-2)
  expect(layers.get('object')).toBe(-3)
  for (const { fromId, toId } of edges) expect(layers.get(fromId)).toBeLessThan(layers.get(toId))
  expect(lineageLayers([...nodes].reverse(), [...edges].reverse(), 'current')).toEqual(layers)
})

test('真实循环共用层级，不影响循环前后的依赖层级', () => {
  const nodes = ['source', 'rule', 'current', 'output']
  const edges = [
    ['source', 'rule'], ['rule', 'current'], ['current', 'rule'], ['current', 'output']
  ].map(([fromId, toId]) => ({ fromId, toId }))
  const layers = lineageLayers(nodes, edges, 'current')
  expect(layers.get('source')).toBe(-1)
  expect(layers.get('rule')).toBe(0)
  expect(layers.get('current')).toBe(0)
  expect(layers.get('output')).toBe(1)
})

test('单节点、自引用和缺失端点不会生成无效层级', () => {
  expect(lineageLayers(['current'], [
    { fromId: 'current', toId: 'current' },
    { fromId: 'missing', toId: 'current' }
  ], 'current')).toEqual(new Map([['current', 0]]))
})
