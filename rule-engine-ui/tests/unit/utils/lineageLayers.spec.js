import { lineageLayers, lineageLayout } from '@/utils/lineageLayers'

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

test('同一出口的分支和其后继居中均分，共享汇合节点回到中线', () => {
  const nodes = ['root', 'a', 'b', 'c', 'aa', 'bb', 'cc', 'current']
  const edges = [['root', 'a'], ['root', 'b'], ['root', 'c'], ['a', 'aa'], ['b', 'bb'], ['c', 'cc'], ['aa', 'current'], ['bb', 'current'], ['cc', 'current']]
    .map(([fromId, toId]) => ({ key: `${fromId}-${toId}`, fromId, toId }))
  const layout = lineageLayout(nodes, edges, 'current')
  const row = id => layout.positions.get(id).row
  expect(row('root')).toBe(0)
  expect(row('current')).toBe(0)
  expect(row('a') + row('c')).toBe(0)
  expect(row('b')).toBe(0)
  expect(row('aa')).toBe(row('a'))
  expect(row('bb')).toBe(row('b'))
  expect(row('cc')).toBe(row('c'))
  expect(lineageLayout([...nodes].reverse(), [...edges].reverse(), 'current')).toEqual(layout)
})

test('跨层直达边使用空通道，经过每个中间列时都避开实体卡片', () => {
  const ids = ['project', 'source', 'api', 'current']
  const edges = [['project', 'source'], ['source', 'api'], ['api', 'current'], ['project', 'current']]
    .map(([fromId, toId]) => ({ key: `${fromId}-${toId}`, fromId, toId }))
  const layout = lineageLayout(ids, edges, 'current')
  const route = layout.routes.get('project-current')
  expect(route).toHaveLength(2)
  route.forEach(point => {
    const sameColumn = [...layout.positions.values()].filter(node => node.layer === point.layer)
    sameColumn.forEach(node => expect(Math.abs(node.row - point.row)).toBeGreaterThanOrEqual(1))
  })
  expect(layout.positions.get('project').row).toBe(layout.positions.get('current').row)
  expect(lineageLayout([...ids].reverse(), [...edges].reverse(), 'current')).toEqual(layout)
})

test('循环中的节点同层分开，所有坐标有限，单节点也能最佳布局', () => {
  const result = lineageLayout(['source', 'a', 'current', 'end'], [
    { fromId: 'source', toId: 'a' }, { fromId: 'a', toId: 'current' },
    { fromId: 'current', toId: 'a' }, { fromId: 'current', toId: 'end' }
  ], 'current')
  const a = result.positions.get('a'), current = result.positions.get('current')
  expect(a.layer).toBe(current.layer)
  expect(Math.abs(a.row - current.row)).toBeGreaterThanOrEqual(1)
  result.positions.forEach(point => expect(Number.isFinite(point.row)).toBe(true))
  expect(lineageLayout(['current'], [], 'current').positions.get('current')).toEqual({ layer: 0, row: 0 })
})
