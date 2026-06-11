/**
 * flowGraphCycle.spec.js
 * 决策流/决策树有向环检测工具的单元测试
 */

import {
  edgeWouldCompleteCycle,
  wouldCreateCycleFromNewEdge,
  graphContainsDirectedCycle
} from '@/utils/flowGraphCycle'

// ============================================================
// edgeWouldCompleteCycle
// ============================================================
describe('edgeWouldCompleteCycle', () => {
  test('空 sourceId / targetId 返回 false', () => {
    const getOut = () => []
    expect(edgeWouldCompleteCycle(getOut, '', 'b', null)).toBe(false)
    expect(edgeWouldCompleteCycle(getOut, 'a', '', null)).toBe(false)
    expect(edgeWouldCompleteCycle(getOut, null, null, null)).toBe(false)
  })

  test('source === target 返回 true', () => {
    const getOut = () => []
    expect(edgeWouldCompleteCycle(getOut, 'a', 'a', null)).toBe(true)
  })

  test('线性链路上新增边不成环', () => {
    // a → b → c → d
    const getOut = (id) => {
      const map = { a: [{ targetNodeId: 'b' }], b: [{ targetNodeId: 'c' }], c: [{ targetNodeId: 'd' }], d: [] }
      return map[id] || []
    }
    // 尝试加 a → c (不成环)
    expect(edgeWouldCompleteCycle(getOut, 'a', 'c', null)).toBe(false)
    // 尝试加 b → d (不成环)
    expect(edgeWouldCompleteCycle(getOut, 'b', 'd', null)).toBe(false)
  })

  test('形成三角环: a→b→c, 加边 a→c 不成环', () => {
    // edgeWouldCompleteCycle 检查 target(c) 能否到达 source(a)。
    // a→b→c 中，c 无法到达 a，因此加边 a→c 不会成环。
    const getOut = (id) => {
      const map = { a: [{ targetNodeId: 'b' }], b: [{ targetNodeId: 'c' }], c: [] }
      return map[id] || []
    }
    expect(edgeWouldCompleteCycle(getOut, 'a', 'c', null)).toBe(false)
  })

  test('形成反边环: a→b→c, 加边 c→a 成环', () => {
    // 从 a 出发可达 c，加边 c→a 形成 a→b→c→a 环
    const getOut = (id) => {
      const map = { a: [{ targetNodeId: 'b' }], b: [{ targetNodeId: 'c' }], c: [] }
      return map[id] || []
    }
    expect(edgeWouldCompleteCycle(getOut, 'c', 'a', null)).toBe(true)
  })

  test('复杂 DAG: a→b→d, a→c→d, 加边 b→c 不成环', () => {
    const getOut = (id) => {
      const map = { a: [{ targetNodeId: 'b' }, { targetNodeId: 'c' }], b: [{ targetNodeId: 'd' }], c: [{ targetNodeId: 'd' }], d: [] }
      return map[id] || []
    }
    expect(edgeWouldCompleteCycle(getOut, 'b', 'c', null)).toBe(false)
  })

  test('重连边时排除旧边: a→b→c, 重连 a→c (排除 a→b)', () => {
    // 此时图变成 b→c, 检查 a→c 不会因 a→b→c 路径成环
    const getOut = (id) => {
      const map = { a: [], b: [{ targetNodeId: 'c', id: 'edge-ab' }], c: [] }
      return map[id] || []
    }
    // excludeEdgeId='edge-ab' 时, a→b 这条边被排除
    expect(edgeWouldCompleteCycle(getOut, 'a', 'c', 'edge-ab')).toBe(false)
  })

  test('BFS 能遍历所有可达节点', () => {
    // a → b → c → e
    //      └→ d
    const getOut = (id) => {
      const map = { a: [{ targetNodeId: 'b' }], b: [{ targetNodeId: 'c' }, { targetNodeId: 'd' }], c: [{ targetNodeId: 'e' }], d: [], e: [] }
      return map[id] || []
    }
    expect(edgeWouldCompleteCycle(getOut, 'a', 'e', null)).toBe(false) // 不成环
    expect(edgeWouldCompleteCycle(getOut, 'd', 'a', null)).toBe(true)   // 反向成环
  })

  test('含 excludeEdgeId 的边不被遍历', () => {
    const getOut = (id) => {
      const map = { a: [{ targetNodeId: 'b', id: 'x' }, { targetNodeId: 'c' }], b: [], c: [{ targetNodeId: 'a' }] }
      return map[id] || []
    }
    // 排除 a→b 后, a 可达 c, c→a 成环
    expect(edgeWouldCompleteCycle(getOut, 'a', 'c', 'x')).toBe(true)
  })
})

// ============================================================
// wouldCreateCycleFromNewEdge
// ============================================================
describe('wouldCreateCycleFromNewEdge', () => {
  test('graphModel 为 null 返回 false', () => {
    expect(wouldCreateCycleFromNewEdge(null, 'a', 'b', null)).toBe(false)
  })

  test('getNodeOutgoingEdge 不是函数返回 false', () => {
    expect(wouldCreateCycleFromNewEdge({}, 'a', 'b', null)).toBe(false)
  })

  test('调用 getNodeOutgoingEdge 并透传到 edgeWouldCompleteCycle', () => {
    const edges = [{ id: 'e1', targetNodeId: 'b' }, { id: 'e2', targetNodeId: 'c' }]
    const graphModel = {
      getNodeOutgoingEdge: jest.fn((nodeId) => {
        const map = { a: edges, b: [], c: [] }
        return map[nodeId] || []
      })
    }
    const result = wouldCreateCycleFromNewEdge(graphModel, 'a', 'c', null)
    // edgeWouldCompleteCycle 从 targetId='c' 开始 BFS，首次调用 getNodeOutgoingEdge('c')
    expect(graphModel.getNodeOutgoingEdge).toHaveBeenCalledWith('c')
    expect(result).toBe(false)
  })

  test('重连边时排除旧边', () => {
    const graphModel = {
      getNodeOutgoingEdge: jest.fn((nodeId) => {
        const map = { a: [{ id: 'ab', targetNodeId: 'b' }], b: [], c: [] }
        return map[nodeId] || []
      })
    }
    wouldCreateCycleFromNewEdge(graphModel, 'a', 'c', 'ab')
    expect(graphModel.getNodeOutgoingEdge).toHaveBeenCalled()
  })
})

// ============================================================
// graphContainsDirectedCycle
// ============================================================
describe('graphContainsDirectedCycle', () => {
  test('空节点列表返回 false', () => {
    expect(graphContainsDirectedCycle([], [])).toBe(false)
    expect(graphContainsDirectedCycle([], null)).toBe(false)
    expect(graphContainsDirectedCycle(null, [])).toBe(false)
  })

  test('无边的单节点图返回 false', () => {
    expect(graphContainsDirectedCycle([], ['a'])).toBe(false)
  })

  test('单节点自环返回 true', () => {
    expect(graphContainsDirectedCycle([{ sourceNodeId: 'a', targetNodeId: 'a' }], ['a'])).toBe(true)
  })

  test('线性链路无环', () => {
    const edges = [
      { sourceNodeId: 'a', targetNodeId: 'b' },
      { sourceNodeId: 'b', targetNodeId: 'c' },
      { sourceNodeId: 'c', targetNodeId: 'd' }
    ]
    expect(graphContainsDirectedCycle(edges, ['a', 'b', 'c', 'd'])).toBe(false)
  })

  test('三角环: a→b→c→a 返回 true', () => {
    const edges = [
      { sourceNodeId: 'a', targetNodeId: 'b' },
      { sourceNodeId: 'b', targetNodeId: 'c' },
      { sourceNodeId: 'c', targetNodeId: 'a' }
    ]
    expect(graphContainsDirectedCycle(edges, ['a', 'b', 'c'])).toBe(true)
  })

  test('完全 DAG 无环', () => {
    const edges = [
      { sourceNodeId: 'a', targetNodeId: 'b' },
      { sourceNodeId: 'a', targetNodeId: 'c' },
      { sourceNodeId: 'a', targetNodeId: 'd' },
      { sourceNodeId: 'b', targetNodeId: 'c' },
      { sourceNodeId: 'b', targetNodeId: 'd' },
      { sourceNodeId: 'c', targetNodeId: 'd' }
    ]
    expect(graphContainsDirectedCycle(edges, ['a', 'b', 'c', 'd'])).toBe(false)
  })

  test('两个分离的环各返回 true', () => {
    const edges = [
      { sourceNodeId: 'a', targetNodeId: 'b' },
      { sourceNodeId: 'b', targetNodeId: 'a' }, // 环 a↔b
      { sourceNodeId: 'c', targetNodeId: 'd' },
      { sourceNodeId: 'd', targetNodeId: 'c' }  // 环 c↔d
    ]
    expect(graphContainsDirectedCycle(edges, ['a', 'b', 'c', 'd'])).toBe(true)
  })

  test('部分节点有环: a→b→c, d→e→d', () => {
    const edges = [
      { sourceNodeId: 'a', targetNodeId: 'b' },
      { sourceNodeId: 'b', targetNodeId: 'c' },
      { sourceNodeId: 'd', targetNodeId: 'e' },
      { sourceNodeId: 'e', targetNodeId: 'd' }
    ]
    expect(graphContainsDirectedCycle(edges, ['a', 'b', 'c', 'd', 'e'])).toBe(true)
  })

  test('边指向不存在的节点被忽略', () => {
    const edges = [
      { sourceNodeId: 'a', targetNodeId: 'b' },
      { sourceNodeId: 'b', targetNodeId: 'ghost' }
    ]
    expect(graphContainsDirectedCycle(edges, ['a', 'b'])).toBe(false)
  })

  test('duplicate nodeIds 只处理一次', () => {
    const edges = [{ sourceNodeId: 'a', targetNodeId: 'a' }]
    expect(graphContainsDirectedCycle(edges, ['a', 'a', 'a'])).toBe(true)
  })

  test('空 edges 数组无环', () => {
    expect(graphContainsDirectedCycle([], ['a', 'b', 'c'])).toBe(false)
  })

  test('含重复边的图按拓扑排序计算', () => {
    const edges = [
      { sourceNodeId: 'a', targetNodeId: 'b' },
      { sourceNodeId: 'a', targetNodeId: 'b' }, // 重复边
      { sourceNodeId: 'b', targetNodeId: 'c' }
    ]
    expect(graphContainsDirectedCycle(edges, ['a', 'b', 'c'])).toBe(false)
  })
})