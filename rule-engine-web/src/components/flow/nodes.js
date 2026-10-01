/**
 * LogicFlow 自定义节点注册
 * 节点类型：开始事件、结束事件、脚本任务、排他网关、聚合节点
 */

import {
  h,
  CircleNode, CircleNodeModel,
  RectNode, RectNodeModel,
  DiamondNode, DiamondNodeModel
} from '@logicflow/core'
import { wouldCreateCycleFromNewEdge } from '@/utils/flowGraphCycle'
import { normalizeEndScope, getEndNodeAppearance } from '@/utils/endNodeScope'

// ============================================================
// 工具函数
// ============================================================
function uuid() {
  return 'node_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9)
}

/**
 * LogicFlow 源端连线规则：禁止新增/调整后的边构成有向环（与决策流 DAG 编译一致）。
 */
function createDagSourceRule() {
  return {
    message: '流程图不支持环路（仅支持 DAG）：该连线会形成回到上游节点的循环。若有「重算直到满足」类需求，请在脚本任务节点内用循环实现。',
    validate(sourceNode, targetNode, _sourceAnchor, _targetAnchor, edgeId) {
      if (!sourceNode || !targetNode) return true
      const gm = sourceNode.graphModel
      return !wouldCreateCycleFromNewEdge(gm, sourceNode.id, targetNode.id, edgeId)
    }
  }
}

const TRACE_NODE_STYLES = {
  hit: { fill: '#f0fdf4', stroke: '#16a34a', text: '#166534' },
  done: { fill: '#eff6ff', stroke: '#2563eb', text: '#1d4ed8' },
  miss: { fill: '#fef2f2', stroke: '#dc2626', text: '#b91c1c' },
  skipped: { fill: '#f8fafc', stroke: '#94a3b8', text: '#64748b' },
  pending: { fill: '#ffffff', stroke: '#cbd5e1', text: '#64748b' },
}

function traceStyle(properties, fallback) {
  const status = properties && properties.traceStatus
  const statusStyle = TRACE_NODE_STYLES[status]
  return statusStyle ? { ...fallback, ...statusStyle } : fallback
}

// ============================================================
// 1. 开始事件 - 绿色圆形
// ============================================================
function StartEventFactory(CircleNode, CircleNodeModel) {
  class StartEventView extends CircleNode {
    getShape() {
      const { x, y, r, properties } = this.props.model
      const colors = traceStyle(properties, { fill: '#52c41a', stroke: '#389e0d', text: '#fff' })
      return h('g', {}, [
        h('circle', {
          cx: x,
          cy: y,
          r,
          fill: colors.fill,
          stroke: colors.stroke,
          strokeWidth: 2
        }),
        h('text', {
          x,
          y: y + 1,
          textAnchor: 'middle',
          dominantBaseline: 'central',
          fill: colors.text,
          fontSize: 12,
          fontWeight: 'bold'
        }, '开始')
      ])
    }
  }

  class StartEventModel extends CircleNodeModel {
    initNodeData(data) {
      super.initNodeData(data)
      this.r = 25
      this.text.editable = false
    }
    setAttributes() {
      this.text.value = ''
    }
    getNodeStyle() {
      const style = super.getNodeStyle()
      const colors = traceStyle(this.properties, { fill: '#52c41a', stroke: '#389e0d' })
      style.stroke = colors.stroke
      style.fill = colors.fill
      return style
    }
    getConnectedSourceRules() {
      const rules = super.getConnectedSourceRules()
      const notAsTarget = {
        message: '开始节点只能作为连线的起点',
        validate: () => true
      }
      rules.push(notAsTarget)
      rules.push(createDagSourceRule())
      return rules
    }
    getConnectedTargetRules() {
      const rules = super.getConnectedTargetRules()
      rules.push({
        message: '开始节点不能作为连线的终点',
        validate: () => false
      })
      return rules
    }
  }

  return { type: 'start-event', view: StartEventView, model: StartEventModel }
}

// ============================================================
// 2. 结束事件 - 橙色表示返回当前规则，红色表示终止整体规则
// ============================================================
function EndEventFactory(CircleNode, CircleNodeModel) {
  class EndEventView extends CircleNode {
    getShape() {
      const { x, y, r, properties } = this.props.model
      const appearance = getEndNodeAppearance(properties && properties.terminationScope)
      const colors = traceStyle(properties, { fill: appearance.fill, stroke: appearance.stroke, text: '#fff' })
      return h('g', {}, [
        h('circle', {
          cx: x,
          cy: y,
          r,
          fill: colors.fill,
          stroke: colors.stroke,
          strokeWidth: 2
        }),
        h('text', {
          x,
          y: y + 1,
          textAnchor: 'middle',
          dominantBaseline: 'central',
          fill: colors.text,
          fontSize: 12,
          fontWeight: 'bold'
        }, appearance.text)
      ])
    }
  }

  class EndEventModel extends CircleNodeModel {
    initNodeData(data) {
      if (!data.properties) data.properties = {}
      data.properties.terminationScope = normalizeEndScope(data.properties.terminationScope)
      if (!data.properties.nodeName) {
        data.properties.nodeName = getEndNodeAppearance(data.properties.terminationScope).name
      }
      super.initNodeData(data)
      this.r = 25
      this.text.editable = false
    }
    setAttributes() {
      this.text.value = ''
    }
    getNodeStyle() {
      const style = super.getNodeStyle()
      const appearance = getEndNodeAppearance(this.properties && this.properties.terminationScope)
      const colors = traceStyle(this.properties, { fill: appearance.fill, stroke: appearance.stroke })
      style.stroke = colors.stroke
      style.fill = colors.fill
      return style
    }
    getConnectedSourceRules() {
      const rules = super.getConnectedSourceRules()
      rules.push({
        message: '结束节点不能作为连线的起点',
        validate: () => false
      })
      return rules
    }
  }

  return { type: 'end-event', view: EndEventView, model: EndEventModel }
}

// 矩形节点的尺寸、圆角、主题和文本行为由同一基类维护。
function createFlowRectModel(width, height) {
  return class FlowRectModel extends RectNodeModel {
    initNodeData(data) {
      super.initNodeData(data)
      this.width = width
      this.height = height
      this.radius = 6
      this.text.editable = false
    }
    setAttributes() {
      this.text.value = ''
    }
    getNodeStyle() {
      const properties = this.properties || {}
      const colors = traceStyle(properties, { fill: 'var(--tianshu-flow-node-bg)', stroke: 'var(--tianshu-flow-node-border)' })
      return {
        ...super.getNodeStyle(),
        stroke: colors.stroke,
        fill: colors.fill,
        strokeWidth: 2,
        radius: 6,
        ...(properties.style || {}),
      }
    }
  }
}

function renderFlowRect(model) {
  const { x, y, width, height, radius } = model
  return h('rect', {
    ...model.getNodeStyle(),
    x: x - width / 2,
    y: y - height / 2,
    width,
    height,
    rx: radius,
    ry: radius,
  })
}

// ============================================================
// 3. 脚本任务 - 主题浅色圆角矩形
// ============================================================
function ScriptTaskFactory(RectNode) {
  class ScriptTaskView extends RectNode {
    getShape() {
      const { x, y, properties } = this.props.model
      const name = properties.nodeName || '脚本任务'
      const colors = traceStyle(properties, { text: 'var(--tianshu-flow-node-text)' })
      return h('g', {}, [
        renderFlowRect(this.props.model),
        h('text', {
          x,
          y: y + 1,
          textAnchor: 'middle',
          dominantBaseline: 'central',
          fill: 'var(--tianshu-flow-node-text)',
          ...(colors.text !== 'var(--tianshu-flow-node-text)' ? { fill: colors.text } : {}),
          fontSize: 13,
          fontWeight: 'bold'
        }, name.length > 10 ? name.substr(0, 10) + '...' : name),
        properties.traceStatus && properties.traceStatus !== 'pending'
          ? h('text', {
            x,
            y: y + 15,
            textAnchor: 'middle',
            dominantBaseline: 'central',
            fill: colors.text,
            fontSize: 9,
          }, properties.traceStatusText || '')
          : null
      ])
    }
  }

  class ScriptTaskModel extends createFlowRectModel(160, 42) {
    initNodeData(data) {
      super.initNodeData(data)
      if (!data.properties) data.properties = {}
      if (!data.properties.nodeName) data.properties.nodeName = '脚本任务'
      if (!data.properties.nodeCode) data.properties.nodeCode = 'TASK_' + Date.now() + '_' + Math.random().toString(36).substr(2, 4).toUpperCase()
      if (!data.properties.nodeDesc) data.properties.nodeDesc = ''
      if (!data.properties.scriptMode) data.properties.scriptMode = 'visual'
      if (!data.properties.asyncExec) data.properties.asyncExec = false
      if (!data.properties.scriptContent) data.properties.scriptContent = ''
      if (!data.properties.actions) data.properties.actions = []
    }
    getConnectedSourceRules() {
      const rules = super.getConnectedSourceRules()
      rules.push(createDagSourceRule())
      return rules
    }
  }

  return { type: 'script-task', view: ScriptTaskView, model: ScriptTaskModel }
}

// ============================================================
// 4. 排他网关 - 橙色菱形
// ============================================================
function ExclusiveGatewayFactory(DiamondNode, DiamondNodeModel) {
  class ExclusiveGatewayView extends DiamondNode {
    getShape() {
      const { x, y, rx, ry, properties } = this.props.model
      const nodeName = (properties && properties.nodeName) || '条件判断'
      const points = [
        [x, y - ry],
        [x + rx, y],
        [x, y + ry],
        [x - rx, y]
      ].map(p => p.join(',')).join(' ')
      const shortName = nodeName.length > 4 ? nodeName.substr(0, 4) : nodeName
      const colors = traceStyle(properties, { fill: '#fa8c16', stroke: '#d46b08', text: '#fff' })
      return h('g', {}, [
        h('polygon', {
          points,
          fill: colors.fill,
          stroke: colors.stroke,
          strokeWidth: 2
        }),
        h('text', {
          x,
          y: y + 1,
          textAnchor: 'middle',
          dominantBaseline: 'central',
          fill: colors.text,
          fontSize: 11,
          fontWeight: 'bold'
        }, shortName)
      ])
    }
  }

  class ExclusiveGatewayModel extends DiamondNodeModel {
    initNodeData(data) {
      super.initNodeData(data)
      this.rx = 28
      this.ry = 28
      this.text.editable = false
      if (!data.properties) data.properties = {}
      if (!data.properties.nodeName) data.properties.nodeName = '条件判断'
      if (!data.properties.nodeCode) data.properties.nodeCode = 'DECISION_' + Date.now() + '_' + Math.random().toString(36).substr(2, 4).toUpperCase()
      if (!data.properties.nodeDesc) data.properties.nodeDesc = ''
      if (!data.properties.gatewayDirection) data.properties.gatewayDirection = 'Diverging'
      if (!data.properties.defaultBranch) data.properties.defaultBranch = ''
    }
    setAttributes() {
      this.text.value = ''
    }
    getNodeStyle() {
      const style = super.getNodeStyle()
      const colors = traceStyle(this.properties, { fill: '#fa8c16', stroke: '#d46b08' })
      style.stroke = colors.stroke
      style.fill = colors.fill
      Object.assign(style, this.properties && this.properties.style ? this.properties.style : {})
      return style
    }
    getConnectedSourceRules() {
      const rules = super.getConnectedSourceRules()
      rules.push(createDagSourceRule())
      return rules
    }
  }

  return { type: 'exclusive-gateway', view: ExclusiveGatewayView, model: ExclusiveGatewayModel }
}

// ============================================================
// 5. 聚合节点 - 灰色菱形
// ============================================================
function JoinGatewayFactory(DiamondNode, DiamondNodeModel) {
  class JoinGatewayView extends DiamondNode {
    getShape() {
      const { x, y, rx, ry, properties } = this.props.model
      const colors = traceStyle(properties, { fill: '#8c8c8c', stroke: '#595959', text: '#fff' })
      const points = [
        [x, y - ry],
        [x + rx, y],
        [x, y + ry],
        [x - rx, y]
      ].map(p => p.join(',')).join(' ')
      return h('g', {}, [
        h('polygon', {
          points,
          fill: colors.fill,
          stroke: colors.stroke,
          strokeWidth: 2
        }),
        h('text', {
          x,
          y: y + 1,
          textAnchor: 'middle',
          dominantBaseline: 'central',
          fill: colors.text,
          fontSize: 12,
          fontWeight: 'bold'
        }, '聚合')
      ])
    }
  }

  class JoinGatewayModel extends DiamondNodeModel {
    initNodeData(data) {
      super.initNodeData(data)
      this.rx = 28
      this.ry = 28
      this.text.editable = false
      if (!data.properties) data.properties = {}
      if (!data.properties.nodeName) data.properties.nodeName = '聚合'
      if (!data.properties.nodeCode) data.properties.nodeCode = 'JOIN_' + Date.now() + '_' + Math.random().toString(36).substr(2, 4).toUpperCase()
      if (!data.properties.nodeDesc) data.properties.nodeDesc = ''
    }
    setAttributes() {
      this.text.value = ''
    }
    getNodeStyle() {
      const style = super.getNodeStyle()
      const colors = traceStyle(this.properties, { fill: '#8c8c8c', stroke: '#595959' })
      style.stroke = colors.stroke
      style.fill = colors.fill
      Object.assign(style, this.properties && this.properties.style ? this.properties.style : {})
      return style
    }
    getConnectedSourceRules() {
      const rules = super.getConnectedSourceRules()
      rules.push(createDagSourceRule())
      return rules
    }
  }

  return { type: 'join-gateway', view: JoinGatewayView, model: JoinGatewayModel }
}

// ============================================================
// 6. 血缘节点 - 与设计器复用 LogicFlow 画布和节点基类
// ============================================================
export const LINEAGE_NODE_WIDTH = 200
export const LINEAGE_NODE_HEIGHT = 88
export const LINEAGE_NODE_COLORS = {
  PROJECT: '#2563EB',
  VARIABLE: '#059669',
  RULE: '#DC2626',
  MODEL: '#7C3AED',
  API: '#EA580C',
  DB: '#0F766E',
  LIST: '#C026D3',
  DATASOURCE: '#64748B',
  DATA_FIELD: '#0891B2',
  DATA_OBJECT: '#0284C7',
}

const LINEAGE_ICON_PATHS = {
  PROJECT: 'M3 5h7l2 2h9v12H3z',
  VARIABLE: 'M5 4h14v16H5z M8 8h8 M8 12h8 M8 16h5',
  DATA_OBJECT: 'M4 6h16v12H4z M4 10h16 M9 6v12 M15 6v12',
  DATA_FIELD: 'M4 5h16v14H4z M4 10h16 M4 15h16 M10 5v14 M15 5v14',
  RULE: 'M12 3l7 3v5c0 4.5-3 8-7 10-4-2-7-5.5-7-10V6z M8 12l2.5 2.5L16 9',
  MODEL: 'M12 3l8 4.5v9L12 21l-8-4.5v-9z M12 12l8-4.5 M12 12v9 M12 12L4 7.5',
  API: 'M8 5v5 M16 5v5 M6 10h12v4a4 4 0 0 1-4 4h-4a4 4 0 0 1-4-4z M12 18v3',
  DB: 'M4 6c0-2 16-2 16 0v12c0 2-16 2-16 0z M4 6c0 2 16 2 16 0 M4 12c0 2 16 2 16 0',
  LIST: 'M5 5h14 M5 12h14 M5 19h14 M2 5h.01 M2 12h.01 M2 19h.01',
  DATASOURCE: 'M5 18h13a4 4 0 0 0 .5-8A6 6 0 0 0 7 8a5 5 0 0 0-2 10z',
}

function renderLineageIcon(type, color) {
  const path = LINEAGE_ICON_PATHS[type] || LINEAGE_ICON_PATHS.VARIABLE
  return h('svg', {
    class: 'node-icon',
    viewBox: '0 0 24 24',
    width: 16,
    height: 16,
    'aria-hidden': 'true',
  }, [h('path', {
    d: path,
    fill: 'none',
    stroke: color,
    strokeWidth: 1.8,
    strokeLinecap: 'round',
    strokeLinejoin: 'round',
  })])
}

function LineageNodeFactory(RectNode) {
  class LineageNodeView extends RectNode {
    getShape() {
      const model = this.props.model
      const { x, y, width, height, properties = {} } = model
      const color = properties.color || LINEAGE_NODE_COLORS[properties.nodeType] || '#64748B'
      const left = x - width / 2
      const top = y - height / 2
      const title = properties.nodeLabel || properties.nodeCode || '未命名节点'
      const code = properties.nodeCode || '-'
      const typeLabel = properties.nodeTypeLabel || properties.nodeType || '节点'
      const badge = properties.current ? '当前' : properties.cycle ? '循环引用' : ''
      const nodeClass = [
        'lineage-lf-node',
        properties.current ? 'current-node' : 'branch-node',
        properties.side === 'UPSTREAM' ? 'is-upstream' : 'is-downstream',
        properties.cycle ? 'is-cycle' : '',
      ].filter(Boolean).join(' ')
      const toggle = properties.toggleable
        ? h('button', {
          type: 'button',
          class: 'branch-toggle',
          'data-lineage-toggle': properties.toggleKind || 'branch',
          'aria-label': properties.toggleLabel,
          disabled: properties.loading,
          onPointerDown: event => event.stopPropagation(),
          onClick: event => {
            event.stopPropagation()
            model.graphModel.eventCenter.emit('lineage:toggle', { data: model.getData() })
          },
        }, properties.toggleText || '+')
        : null
      const handleDragStart = event => {
        if (event.button !== 0 || event.target?.closest?.('button')) return
        event.preventDefault()
        event.stopPropagation()
        model.graphModel.eventCenter.emit('lineage:dragstart', { data: model.getData(), e: event })
      }
      return h('g', { onPointerDown: handleDragStart, onMouseDown: handleDragStart }, [
        renderFlowRect(model),
        h('foreignObject', { x: left, y: top, width, height, style: { overflow: 'visible' }, onPointerDown: handleDragStart, onMouseDown: handleDragStart },
          h('div', {
            class: nodeClass,
            'data-node-id': properties.nodeId,
            'data-lineage-model-id': model.id,
            tabindex: 0,
            'aria-label': `${typeLabel}：${title}`,
            onKeyDown: event => {
              if (event.target !== event.currentTarget || !['Enter', ' '].includes(event.key)) return
              event.preventDefault()
              model.graphModel.selectNodeById(model.id)
              model.graphModel.eventCenter.emit('node:click', { data: model.getData() })
            },
            style: {
              '--lineage-node-color': color,
              left: `${left}px`,
              top: `${top}px`,
              transform: `translate(${-left}px, ${-top}px)`,
            },
          }, [
            h('div', {
              class: 'node-color-strip',
              style: { background: color },
              'aria-hidden': 'true',
            }),
            toggle,
            h('div', { class: 'node-head' }, [
              h('span', { class: 'node-type-wrap' }, [renderLineageIcon(properties.nodeType, color), h('span', { class: 'node-type' }, typeLabel)]),
              badge ? h('span', { class: 'node-badge' }, badge) : null,
            ]),
            h('div', {
              class: 'node-label',
              title,
            }, title),
            h('div', {
              class: 'node-code',
              title: code,
            }, code),
          ])),
      ])
    }
  }

  class LineageNodeModel extends createFlowRectModel(LINEAGE_NODE_WIDTH, LINEAGE_NODE_HEIGHT) {
    getNodeStyle() {
      return {
        ...super.getNodeStyle(),
        stroke: this.properties.color || LINEAGE_NODE_COLORS[this.properties.nodeType] || '#64748B',
        fill: 'var(--tianshu-bg-surface)',
        ...(this.properties.current ? { strokeWidth: 3 } : {}),
        ...(this.properties.cycle ? { strokeDasharray: '6 4' } : {}),
      }
    }
  }

  return { type: 'lineage-node', view: LineageNodeView, model: LineageNodeModel }
}

// ============================================================
// 注册所有自定义节点
// ============================================================
export function registerCustomNodes(lf) {
  const nodes = [
    StartEventFactory(CircleNode, CircleNodeModel),
    EndEventFactory(CircleNode, CircleNodeModel),
    ScriptTaskFactory(RectNode, RectNodeModel),
    ExclusiveGatewayFactory(DiamondNode, DiamondNodeModel),
    JoinGatewayFactory(DiamondNode, DiamondNodeModel),
    LineageNodeFactory(RectNode, RectNodeModel)
  ]

  nodes.forEach(n => {
    lf.register(n)
  })
}

// 节点面板配置
export const NODE_PANEL_LIST = [
  {
    group: '事件节点',
    items: [
      { type: 'start-event', label: '开始事件', icon: 'VideoPlay', color: '#52c41a' },
      { type: 'end-event', label: '结束事件', icon: 'Remove', color: '#FF4D4F' }
    ]
  },
  {
    group: '任务节点',
    items: [
      { type: 'script-task', label: '脚本任务', icon: 'Document', color: '#2F54EB' }
    ]
  },
  {
    group: '网关节点',
    items: [
      { type: 'exclusive-gateway', label: '排他网关', icon: 'Sort', color: '#fa8c16' },
      { type: 'join-gateway', label: '聚合节点', icon: 'CopyDocument', color: '#8c8c8c' }
    ]
  }
]

// 默认流程数据
export function getDefaultFlowData() {
  return {
    nodes: [
      {
        id: uuid(),
        type: 'start-event',
        x: 160,
        y: 300,
        properties: { nodeName: '开始', nodeCode: 'START_' + Date.now(), nodeDesc: '流程开始节点' }
      }
    ],
    edges: []
  }
}
