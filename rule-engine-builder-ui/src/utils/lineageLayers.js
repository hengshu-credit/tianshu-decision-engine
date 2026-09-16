// 先将真正的循环依赖缩成一个分量，再按依赖边分层，避免用某条展示路径的深度定位共享节点。
export function lineageLayers(nodeIds, edges, currentId) {
  const outgoing = new Map(nodeIds.map(id => [id, new Set()]))
  edges.forEach(({ fromId, toId }) => {
    if (outgoing.has(fromId) && outgoing.has(toId)) outgoing.get(fromId).add(toId)
  })
  const indices = new Map()
  const lowest = new Map()
  const stack = []
  const onStack = new Set()
  const componentOf = new Map()
  let sequence = 0
  let componentCount = 0
  const visit = id => {
    indices.set(id, sequence)
    lowest.set(id, sequence++)
    stack.push(id)
    onStack.add(id)
    outgoing.get(id).forEach(next => {
      if (!indices.has(next)) {
        visit(next)
        lowest.set(id, Math.min(lowest.get(id), lowest.get(next)))
      } else if (onStack.has(next)) {
        lowest.set(id, Math.min(lowest.get(id), indices.get(next)))
      }
    })
    if (lowest.get(id) !== indices.get(id)) return
    let member
    do {
      member = stack.pop()
      onStack.delete(member)
      componentOf.set(member, componentCount)
    } while (member !== id)
    componentCount++
  }
  nodeIds.forEach(id => { if (!indices.has(id)) visit(id) })

  const successors = Array.from({ length: componentCount }, () => new Set())
  outgoing.forEach((targets, source) => targets.forEach(target => {
    const from = componentOf.get(source)
    const to = componentOf.get(target)
    if (from !== to) successors[from].add(to)
  }))
  // 使用到下游终点的最长距离，同一结果的直接依赖保持在同一列。
  const layers = new Map()
  const layerOf = component => {
    if (!layers.has(component)) {
      let layer = 0
      successors[component].forEach(next => { layer = Math.min(layer, layerOf(next) - 1) })
      layers.set(component, layer)
    }
    return layers.get(component)
  }
  const origin = layerOf(componentOf.get(currentId))
  return new Map(nodeIds.map(id => [id, layerOf(componentOf.get(id)) - origin]))
}

// 跨层边也占一条分支通道，避免直达边穿过中间层的卡片。
export function lineageLayout(nodeIds, edges, currentId) {
  const layers = lineageLayers(nodeIds, edges, currentId)
  const columns = new Map()
  const nodes = new Map()
  const routes = new Map()
  const addNode = (id, layer, orderKey) => {
    nodes.set(id, { id, layer, orderKey, incoming: new Set(), outgoing: new Set() })
    if (!columns.has(layer)) columns.set(layer, [])
    columns.get(layer).push(id)
  }
  const connect = (from, to) => {
    nodes.get(from).outgoing.add(to)
    nodes.get(to).incoming.add(from)
  }
  nodeIds.forEach(id => addNode(id, layers.get(id), String(id)))
  edges.forEach(edge => {
    if (!nodes.has(edge.fromId) || !nodes.has(edge.toId)) return
    const fromLayer = layers.get(edge.fromId)
    const toLayer = layers.get(edge.toId)
    if (fromLayer >= toLayer) return // 同一循环分量留在原层，不递归展开。
    let previous = edge.fromId
    const route = []
    for (let layer = fromLayer + 1; layer < toLayer; layer++) {
      const id = Symbol()
      addNode(id, layer, `~${edge.fromId}->${edge.toId}:${edge.label || ''}`)
      connect(previous, id)
      route.push(id)
      previous = id
    }
    connect(previous, edge.toId)
    routes.set(edge.key, route)
  })
  const compare = (a, b) => nodes.get(a).orderKey.localeCompare(nodes.get(b).orderKey, 'en', { numeric: true })
  const spans = new Map()
  const span = id => {
    if (!spans.has(id)) {
      const children = [...nodes.get(id).outgoing]
      const step = Math.max(1, ...children.map(child => nodes.get(child).incoming.size > 1 ? 1 : span(child)))
      spans.set(id, Math.max(1, children.length * step))
    }
    return spans.get(id)
  }
  const branchOffsets = new Map()
  nodes.forEach(node => {
    const children = [...node.outgoing].sort(compare)
    const step = Math.max(1, ...children.map(child => nodes.get(child).incoming.size > 1 ? 1 : span(child)))
    branchOffsets.set(node.id, new Map(children.map((id, index) => [id, (index - (children.length - 1) / 2) * step])))
  })
  const rows = new Map()
  ;[...columns.keys()].sort((a, b) => a - b).forEach(layer => {
    const ids = columns.get(layer).sort(compare)
    const roots = ids.filter(id => !nodes.get(id).incoming.size)
    const rootStep = Math.max(1, ...roots.map(span))
    const desired = new Map(ids.map(id => {
      const parents = [...nodes.get(id).incoming]
      const row = parents.length
        ? parents.reduce((sum, parent) => sum + rows.get(parent) + branchOffsets.get(parent).get(id), 0) / parents.length
        : (roots.indexOf(id) - (roots.length - 1) / 2) * rootStep
      return [id, row]
    }))
    ids.sort((a, b) => desired.get(a) - desired.get(b) || compare(a, b))
    let previous = -Infinity
    ids.forEach(id => {
      const row = Math.max(desired.get(id), previous + 1)
      rows.set(id, row)
      previous = row
    })
    // 碰撞让位后恢复整列的重心，防止分支全部向一侧偏移。
    const shift = ids.reduce((sum, id) => sum + rows.get(id) - desired.get(id), 0) / ids.length
    ids.forEach(id => rows.set(id, rows.get(id) - shift))
  })
  const origin = rows.get(currentId) || 0
  const positions = new Map([...nodes].map(([id, node]) => [id, { layer: node.layer, row: rows.get(id) - origin }]))
  return {
    positions: new Map(nodeIds.map(id => [id, positions.get(id)])),
    routes: new Map([...routes].map(([key, ids]) => [key, ids.map(id => positions.get(id))])),
  }
}
