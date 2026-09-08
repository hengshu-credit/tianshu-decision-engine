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
