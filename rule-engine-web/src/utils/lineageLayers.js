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
  // 从选定起点向外阅读时，无后继的资源应紧跟它的直接上游，
  // 否则独立的项目成员也会被推到最长依赖链的最后一列。
  const downstream = new Set()
  const predecessors = Array.from({ length: componentCount }, () => [])
  successors.forEach((targets, source) => targets.forEach(target => predecessors[target].push(source)))
  const collect = component => successors[component].forEach(next => {
    if (downstream.has(next)) return
    downstream.add(next)
    collect(next)
  })
  if (componentOf.has(currentId)) collect(componentOf.get(currentId))
  const adjusted = new Map()
  const position = component => {
    if (!downstream.has(component)) return layerOf(component) - origin
    if (!adjusted.has(component)) {
      adjusted.set(component, 1 + Math.max(...predecessors[component].map(position)))
    }
    return adjusted.get(component)
  }
  return new Map(nodeIds.map(id => [id, position(componentOf.get(id))]))
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
  const bypassBySource = new Map()
  edges.forEach(edge => {
    if (!nodes.has(edge.fromId) || !nodes.has(edge.toId)) return
    const fromLayer = layers.get(edge.fromId)
    const toLayer = layers.get(edge.toId)
    if (fromLayer >= toLayer) return // 同一循环分量留在原层，不递归展开。
    let previous = edge.fromId
    const route = []
    for (let layer = fromLayer + 1; layer < toLayer; layer++) {
      // 同一源节点跨过同一列时共用通道，项目直达数十个资源也只占一行。
      if (!bypassBySource.has(previous)) bypassBySource.set(previous, Symbol())
      const id = bypassBySource.get(previous)
      if (!nodes.has(id)) addNode(id, layer, `~${edge.fromId}:${layer}`)
      connect(previous, id)
      route.push(id)
      previous = id
    }
    connect(previous, edge.toId)
    routes.set(edge.key, route)
  })
  const compare = (a, b) => nodes.get(a).orderKey.localeCompare(nodes.get(b).orderKey, 'en', { numeric: true })
  // 每列只为实际卡片和跨层通道预留行，避免嵌套扇出时按最大子树宽度逐层相乘。
  const ordered = [...columns.entries()].sort(([a], [b]) => a - b).map(([, ids]) => ids.sort(compare))
  const rows = new Map()
  const center = ids => ids.forEach((id, index) => rows.set(id, index - (ids.length - 1) / 2))
  ordered.forEach(center)
  const sweep = (sequence, side) => {
    sequence.forEach(ids => {
      const barycenter = new Map(ids.map(id => {
        const neighbors = nodes.get(id)[side]
        let total = 0
        neighbors.forEach(next => { total += rows.get(next) })
        return [id, neighbors.size ? total / neighbors.size : rows.get(id)]
      }))
      ids.sort((a, b) => barycenter.get(a) - barycenter.get(b) || rows.get(a) - rows.get(b))
      center(ids)
    })
  }
  // 同时考虑上游和下游，收拢共用一个后继的分支；相同重心保持上一轮顺序。
  const reversed = [...ordered].reverse()
  for (let pass = 0; pass < 3; pass++) {
    sweep(ordered, 'incoming')
    sweep(reversed, 'outgoing')
  }
  const origin = rows.get(currentId) || 0
  const positions = new Map([...nodes].map(([id, node]) => [id, { layer: node.layer, row: rows.get(id) - origin }]))
  return {
    positions: new Map(nodeIds.map(id => [id, positions.get(id)])),
    routes: new Map([...routes].map(([key, ids]) => [key, ids.map(id => positions.get(id))])),
  }
}
