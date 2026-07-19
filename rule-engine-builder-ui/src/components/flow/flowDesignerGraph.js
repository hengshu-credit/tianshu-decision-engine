import { getEndNodeAppearance, normalizeEndScope } from '@/utils/endNodeScope'

export const FLOW_THEME_COLOR = '#2639E9'

const COMMON_MENU_OPTIONS = [
  { type: 'exclusive-gateway', label: '条件判断', icon: 'el-icon-sort', color: '#FA8C16' },
  { type: 'script-task', label: '执行动作', icon: 'el-icon-document', color: '#2F54EB' },
  { type: 'end-event', label: '结束', icon: 'el-icon-remove', color: '#FF4D4F' }
]

export const FLOW_MENU_OPTIONS = {
  flow: [COMMON_MENU_OPTIONS[0], COMMON_MENU_OPTIONS[1], { type: 'join-gateway', label: '聚合', icon: 'el-icon-copy-document', color: '#8C8C8C' }, COMMON_MENU_OPTIONS[2]],
  tree: COMMON_MENU_OPTIONS
}

const DIRECTION_VECTOR = {
  top: { x: 0, y: -1 },
  right: { x: 1, y: 0 },
  bottom: { x: 0, y: 1 },
  left: { x: -1, y: 0 }
}

export function resolveAnchorDirection(anchor, node) {
  const deltaX = anchor.x - node.x
  const deltaY = anchor.y - node.y
  if (Math.abs(deltaX) > Math.abs(deltaY)) return deltaX >= 0 ? 'right' : 'left'
  return deltaY >= 0 ? 'bottom' : 'top'
}

export function findAvailableNodePosition(nodes, sourceNode, direction, distance = 180) {
  const vector = DIRECTION_VECTOR[direction] || DIRECTION_VECTOR.bottom
  const graphNodes = nodes || []
  for (let step = 1; step <= 50; step++) {
    const position = {
      x: sourceNode.x + vector.x * distance * step,
      y: sourceNode.y + vector.y * distance * step
    }
    const occupied = graphNodes.some(node => node.id !== sourceNode.id && Math.abs(node.x - position.x) < 140 && Math.abs(node.y - position.y) < 100)
    if (!occupied) return position
  }
  return {
    x: sourceNode.x + vector.x * distance * 51,
    y: sourceNode.y + vector.y * distance * 51
  }
}

export function createFlowNodeData(type, { x, y, terminationScope } = {}) {
  const scope = normalizeEndScope(terminationScope)
  const labelMap = {
    'start-event': '开始',
    'end-event': getEndNodeAppearance(scope).name,
    'exclusive-gateway': '条件判断',
    'script-task': '执行动作',
    'join-gateway': '聚合'
  }
  const idSuffix = Date.now() + '_' + Math.random().toString(36).substr(2, 4).toUpperCase()
  return {
    type,
    x,
    y,
    properties: {
      nodeName: labelMap[type] || type,
      nodeCode: type.toUpperCase().replace(/-/g, '_') + '_' + idSuffix,
      nodeDesc: '',
      actionData: [],
      gatewayDirection: 'Diverging',
      ...(type === 'end-event' ? { terminationScope: scope } : {})
    }
  }
}

function getClosestAnchor(anchors, point) {
  return (anchors || []).reduce((closest, anchor) => {
    const distance = Math.pow(anchor.x - point.x, 2) + Math.pow(anchor.y - point.y, 2)
    if (!closest || distance < closest.distance) return { anchor, distance }
    return closest
  }, null)
}

function validateConnection(sourceNode, targetNode, sourceAnchor, targetAnchor) {
  const sourceResult = sourceNode.isAllowConnectedAsSource(targetNode, sourceAnchor, targetAnchor)
  if (!sourceResult.isAllPass) return sourceResult.msg || '该节点不允许作为连线起点'
  const targetResult = targetNode.isAllowConnectedAsTarget(sourceNode, sourceAnchor, targetAnchor)
  if (!targetResult.isAllPass) return targetResult.msg || '该节点不允许作为连线终点'
  return ''
}

export function addConnectedNode(lf, { sourceNode, sourceAnchor, type, direction, terminationScope, edgeType }) {
  const graph = lf.getGraphData() || {}
  const position = findAvailableNodePosition(graph.nodes, sourceNode, direction)
  const targetNode = lf.addNode(createFlowNodeData(type, { ...position, terminationScope }))
  try {
    const closest = getClosestAnchor(targetNode.anchors, sourceAnchor)
    if (!closest) throw new Error('目标节点缺少可连接锚点')
    const message = validateConnection(sourceNode, targetNode, sourceAnchor, closest.anchor)
    if (message) throw new Error(message)
    const edge = lf.addEdge({
      ...(edgeType ? { type: edgeType } : {}),
      sourceNodeId: sourceNode.id,
      targetNodeId: targetNode.id,
      sourceAnchorId: sourceAnchor.id,
      targetAnchorId: closest.anchor.id
    })
    lf.selectElementById(targetNode.id)
    return { node: targetNode, edge }
  } catch (error) {
    lf.deleteNode(targetNode.id)
    throw error
  }
}

export function calculateGroupBounds(models, padding = 40) {
  if (!models || models.length === 0) return null
  const bounds = models.map(model => {
    if (typeof model.getBounds === 'function') return model.getBounds()
    const halfWidth = (model.width || 0) / 2
    const halfHeight = (model.height || 0) / 2
    return {
      minX: model.x - halfWidth,
      maxX: model.x + halfWidth,
      minY: model.y - halfHeight,
      maxY: model.y + halfHeight
    }
  })
  const minX = Math.min(...bounds.map(item => item.minX)) - padding
  const maxX = Math.max(...bounds.map(item => item.maxX)) + padding
  const minY = Math.min(...bounds.map(item => item.minY)) - padding
  const maxY = Math.max(...bounds.map(item => item.maxY)) + padding
  return {
    x: (minX + maxX) / 2,
    y: (minY + maxY) / 2,
    width: maxX - minX,
    height: maxY - minY
  }
}

export function createDynamicGroup(lf) {
  const selected = lf.getSelectElements(true) || {}
  const dynamicGroup = lf.extension.dynamicGroup
  const models = (selected.nodes || [])
    .map(node => lf.getNodeModelById(node.id))
    .filter(model => model && !model.isGroup && !dynamicGroup.nodeGroupMap.has(model.id))
  if (models.length < 2) throw new Error('请至少选择两个尚未分组的节点')
  const bounds = calculateGroupBounds(models)
  const children = models.map(model => model.id)
  const group = lf.addNode({
    type: 'dynamic-group',
    x: bounds.x,
    y: bounds.y,
    text: '分组',
    properties: {
      children,
      width: bounds.width,
      height: bounds.height,
      collapsible: true,
      isRestrict: false,
      autoResize: true
    }
  })
  lf.clearSelectElements()
  lf.selectElementById(group.id)
  return group
}

export function getPersistableGraphData(lf) {
  const graph = lf.getGraphData() || {}
  const virtualEdgeIds = new Set((lf.graphModel.edges || []).filter(edge => edge.virtual).map(edge => edge.id))
  return {
    ...graph,
    nodes: graph.nodes || [],
    edges: (graph.edges || []).filter(edge => !virtualEdgeIds.has(edge.id))
  }
}

export function getBusinessGraphData(graph) {
  const nodes = (graph.nodes || []).filter(node => node.type !== 'dynamic-group' && !(node.properties && node.properties.isGroup))
  const nodeIds = new Set(nodes.map(node => node.id))
  return {
    nodes,
    edges: (graph.edges || []).filter(edge => nodeIds.has(edge.sourceNodeId) && nodeIds.has(edge.targetNodeId))
  }
}
