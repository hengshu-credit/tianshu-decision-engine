import { getSvgTextSize } from '@logicflow/core'

/**
 * LogicFlow 内置连线类型：折线、直线、贝塞尔（产品「弧线」）
 */
export const EDGE_LINE_TYPES = ['polyline', 'line', 'bezier']

/**
 * 将任意值规范为合法的默认连线类型，非法或缺失时回退为 polyline
 * @param {string|undefined|null} v - 来自 modelJson 或 UI 的值
 * @returns {'polyline'|'line'|'bezier'}
 */
export function normalizeDefaultEdgeLineType(v) {
  if (v != null && v !== '' && EDGE_LINE_TYPES.includes(v)) return v
  return 'polyline'
}

/**
 * 将解析后的规则 model 规范为新版连线字段（不保留旧 JSON 特殊分支；缺省补 defaultEdgeLineType）
 * @param {Record<string, unknown>} modelData - JSON.parse(modelJson) 得到的对象
 */
export function migrateModelJsonForEdgeLineTypes(modelData) {
  if (!modelData || typeof modelData !== 'object') return
  modelData.defaultEdgeLineType = normalizeDefaultEdgeLineType(modelData.defaultEdgeLineType)
}

/**
 * 计算单条边渲染时应采用的 LogicFlow type：单边 properties.edgeLineType 优先，否则使用全局默认
 * @param {{ type?: string, properties?: Record<string, unknown> }} edge - LogicFlow 边数据
 * @param {'polyline'|'line'|'bezier'} globalDefault - 已规范化的 model 级默认连线类型
 * @returns {'polyline'|'line'|'bezier'}
 */
export function resolveEdgeLineType(edge, globalDefault) {
  const p = edge.properties && edge.properties.edgeLineType
  if (p && EDGE_LINE_TYPES.includes(p)) return p
  return normalizeDefaultEdgeLineType(globalDefault)
}

/**
 * 克隆 logicflow 图并规范化每条边的 type，供 render 前使用
 * @param {{ nodes?: unknown[], edges?: unknown[] }} lfGraph - LogicFlow 图数据
 * @param {'polyline'|'line'|'bezier'} globalDefault - 已规范化的全局默认连线类型
 * @returns {{ nodes: unknown[], edges: unknown[] }}
 */
export function prepareLogicFlowDataForRender(lfGraph, globalDefault) {
  const g = normalizeDefaultEdgeLineType(globalDefault)
  const nodes = lfGraph && Array.isArray(lfGraph.nodes) ? lfGraph.nodes.map(n => ({ ...n })) : []
  const edges = (lfGraph && Array.isArray(lfGraph.edges) ? lfGraph.edges : []).map(e => ({
    ...e,
    type: resolveEdgeLineType(e, g)
  }))
  return { ...lfGraph, nodes, edges }
}

function interpolate(start, end, t) {
  return { x: start.x + (end.x - start.x) * t, y: start.y + (end.y - start.y) * t }
}

function* labelPositions(edge) {
  const { startPoint, endPoint, pointsList } = edge
  if (edge.type === 'polyline' && pointsList.length > 1) {
    // 从目标往回找，先尝试分叉后的线段，再尝试主干。
    for (let i = pointsList.length - 1; i > 0; i--) {
      const start = pointsList[i - 1], end = pointsList[i]
      if (start.x === end.x && start.y === end.y) continue
      for (const t of [0.5, 0.75, 0.25]) yield interpolate(start, end, t)
    }
    return
  }
  for (const t of [2 / 3, 0.5, 0.75, 0.4, 0.8, 0.3]) {
    if (edge.type === 'bezier') {
      // 三次贝塞尔插值，锚点方向不对称时标签也落在曲线上。
      const [start, first, second, end] = pointsList
      const middle = interpolate(first, second, t)
      const left = interpolate(interpolate(start, first, t), middle, t)
      const right = interpolate(middle, interpolate(second, end, t), t)
      yield interpolate(left, right, t)
    } else {
      yield interpolate(startPoint, endPoint, t)
    }
  }
}

// LogicFlow 更换模型时会复制旧标签坐标；只在实际切换线型时重新定位。
export function changeEdgeLineType(lf, edgeId, type) {
  if (lf.getEdgeModelById(edgeId)?.type === type) return
  lf.changeEdgeType(edgeId, type)
  const edge = lf.getEdgeModelById(edgeId)
  if (!edge?.text?.value) return
  const rows = edge.text.value.split(/\r?\n/)
  const { width, height } = getSvgTextSize({ rows, rowsLength: rows.length, fontSize: edge.getTextStyle().fontSize })
  let preferred
  for (const point of labelPositions(edge)) {
    preferred ??= point
    const overlaps = lf.graphModel.nodes.some(node => !node.isGroup &&
      Math.abs(point.x - node.x) < (node.width + width) / 2 + 4 &&
      Math.abs(point.y - node.y) < (node.height + height) / 2 + 4)
    if (!overlaps) {
      edge.setText(point)
      return
    }
  }
  if (preferred) edge.setText(preferred)
}

/**
 * 更新 LogicFlow 实例的默认边类型，并将「跟随全局」的边（无 edgeLineType）改为该类型
 * @param {*} lf - LogicFlow 实例
 * @param {'polyline'|'line'|'bezier'} globalType - 新的全局类型
 */
export function applyGlobalEdgeTypeToInheritedEdges(lf, globalType) {
  const g = normalizeDefaultEdgeLineType(globalType)
  lf.setDefaultEdgeType(g)
  const edges = lf.getGraphData().edges || []
  edges.forEach(e => {
    const explicit = e.properties && e.properties.edgeLineType
    if (explicit && EDGE_LINE_TYPES.includes(explicit)) return
    try {
      changeEdgeLineType(lf, e.id, g)
    } catch (err) {
      /* ignore */
    }
  })
}

/**
 * 从表单状态合并连线业务属性，并在「跟随全局」时移除 properties.edgeLineType
 * @param {Record<string, unknown>} currentProps - 当前边上的 properties
 * @param {{ conditionName?: string, conditionExpr?: string, edgeLineType?: string }} form - 属性面板绑定值
 * @returns {Record<string, unknown>}
 */
export function mergeEdgePropertiesFromForm(currentProps, form) {
  const next = { ...(currentProps || {}) }
  next.conditionName = form.conditionName != null ? form.conditionName : ''
  next.conditionExpr = form.conditionExpr != null ? form.conditionExpr : ''
  if (Number.isFinite(form.priority)) next.priority = form.priority
  ;['leftVarId', 'leftRefType', 'rightVarId', 'rightRefType', 'conditionConfig'].forEach(key => {
    if (Object.prototype.hasOwnProperty.call(form, key)) {
      next[key] = form[key] || null
    }
  })
  if (form.edgeLineType) next.edgeLineType = form.edgeLineType
  else delete next.edgeLineType
  return next
}
