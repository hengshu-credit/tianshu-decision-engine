<template>
  <section class="flow-trace-graph" aria-label="决策流执行图">
    <div class="flow-trace-graph__head">
      <div>
        <div class="flow-trace-graph__title">决策流执行图</div>
        <div class="flow-trace-graph__subtitle">点击节点或连线查看本次执行的条件、表达式和结果</div>
      </div>
      <div class="flow-trace-graph__legend" aria-label="执行状态图例">
        <span v-for="item in statusLegend" :key="item.value" class="flow-trace-legend-item">
          <i class="flow-trace-legend-dot" :class="'is-' + item.value" aria-hidden="true"></i>
          {{ item.label }}
        </span>
      </div>
    </div>

    <div class="flow-trace-graph__body">
      <div class="flow-trace-canvas-wrap">
        <div class="flow-trace-canvas" data-testid="flow-trace-canvas"></div>
        <div v-if="!graphMounted" class="flow-trace-fallback" data-testid="flow-trace-fallback">
          <div class="flow-trace-fallback__title">执行节点</div>
          <div class="flow-trace-fallback__list">
            <button
              v-for="node in graphSnapshot.nodes"
              :key="node.id"
              type="button"
              class="flow-trace-fallback__node"
              :class="'is-' + node.properties.traceStatus"
              @click="selectNode(node)"
            >
              <span>{{ node.properties.nodeName }}</span>
              <small>{{ statusLabel(node.properties.traceStatus) }}</small>
            </button>
          </div>
        </div>
      </div>

      <aside class="flow-trace-detail" data-testid="flow-trace-detail">
        <div class="flow-trace-detail__head">
          <span class="flow-trace-detail__eyebrow">执行详情</span>
          <button
            v-if="selected"
            type="button"
            class="flow-trace-detail__clear"
            aria-label="清除执行详情"
            @click="selected = null"
          >
            清除
          </button>
        </div>
        <template v-if="selected">
          <div class="flow-trace-detail__title-row">
            <strong>{{ selected.title }}</strong>
            <span class="flow-trace-status" :class="'is-' + selected.status">
              {{ statusLabel(selected.status) }}
            </span>
          </div>
          <div class="flow-trace-detail__meta">{{ selected.typeLabel }}</div>

          <template v-if="selected.kind === 'node' && selected.execution">
            <div v-if="selected.execution.conditions && selected.execution.conditions.length" class="flow-trace-detail__section">
              <div class="flow-trace-detail__label">判断条件</div>
              <div v-for="(condition, index) in selected.execution.conditions" :key="index" class="flow-trace-condition">
                <code>{{ condition.varLabel || condition.varCode }}</code>
                <span>{{ condition.operator }}</span>
                <code>{{ condition.compareDisplay }}</code>
                <b :class="condition.result ? 'is-hit' : 'is-miss'">{{ condition.result ? '满足' : '不满足' }}</b>
              </div>
            </div>
            <div v-if="selected.execution.expression" class="flow-trace-detail__section">
              <div class="flow-trace-detail__label">规则表达式</div>
              <pre>{{ selected.execution.expression }}</pre>
            </div>
            <div v-if="selected.execution.resultDisplay !== undefined" class="flow-trace-detail__section">
              <div class="flow-trace-detail__label">执行结果</div>
              <pre>{{ selected.execution.resultDisplay }}</pre>
            </div>
            <div v-if="selected.execution.actions && selected.execution.actions.length" class="flow-trace-detail__section">
              <div class="flow-trace-detail__label">动作赋值</div>
              <div v-for="(action, index) in selected.execution.actions" :key="index" class="flow-trace-action">
                <code>{{ action.targetLabel || action.targetVar }}</code>
                <span>=</span>
                <code>{{ action.valueDisplay }}</code>
              </div>
            </div>
          </template>

          <template v-if="selected.kind === 'edge'">
            <div class="flow-trace-detail__section">
              <div class="flow-trace-detail__label">分支条件</div>
              <pre>{{ selected.condition || '无条件直连' }}</pre>
            </div>
            <div v-if="selected.sourceName || selected.targetName" class="flow-trace-detail__section">
              <div class="flow-trace-detail__label">路径</div>
              <div class="flow-trace-path">{{ selected.sourceName }} <span>→</span> {{ selected.targetName }}</div>
            </div>
          </template>
        </template>
        <div v-else class="flow-trace-detail__empty">
          <span class="flow-trace-detail__empty-icon">↗</span>
          <strong>选择图中元素</strong>
          <span>节点查看实际执行结果，连线查看分支条件和是否通过。</span>
        </div>
      </aside>
    </div>
  </section>
</template>

<script>
import { createGraphCanvas } from '@/components/flow/graphCanvas'

const NODE_TYPES = {
  start: 'start-event',
  end: 'end-event',
  task: 'script-task',
  decision: 'exclusive-gateway',
  join: 'join-gateway',
}

const NODE_TYPE_LABELS = {
  start: '开始节点',
  end: '结束节点',
  task: '动作节点',
  decision: '判断节点',
  join: '聚合节点',
}

const STATUS_LABELS = {
  hit: '命中',
  done: '已执行',
  miss: '未命中',
  skipped: '未执行',
  pending: '未到达',
}

const STATUS_STYLES = {
  hit: { stroke: 'var(--tianshu-success-text)', fill: 'var(--tianshu-success-bg)' },
  done: { stroke: 'var(--tianshu-info-text)', fill: 'var(--tianshu-info-bg)' },
  miss: { stroke: 'var(--tianshu-danger-text)', fill: 'var(--tianshu-danger-bg)' },
  skipped: { stroke: 'var(--tianshu-text-tertiary)', fill: 'var(--tianshu-bg-muted)' },
  pending: { stroke: 'var(--tianshu-border-strong)', fill: 'var(--tianshu-bg-surface)' },
}

export default {
  name: 'FlowTraceGraph',
  props: {
    modelData: { type: Object, default: null },
    cards: { type: Array, default: () => [] },
  },
  data() {
    return {
      lf: null,
      graphMounted: false,
      selected: null,
    }
  },
  computed: {
    statusLegend() {
      return [
        { value: 'hit', label: '命中路径' },
        { value: 'done', label: '已执行' },
        { value: 'miss', label: '条件未满足' },
        { value: 'skipped', label: '未执行分支' },
      ]
    },
    graphSnapshot() {
      const source = this.modelData && this.modelData.logicflow
        ? this.modelData.logicflow
        : this.modelData || {}
      const sourceNodes = Array.isArray(source.nodes) ? source.nodes : []
      const sourceEdges = Array.isArray(source.edges) ? source.edges : []
      const nodeDetails = this.buildNodeDetails(sourceNodes)
      const nodeById = new Map(nodeDetails.map(item => [item.node.id, item]))
      const nodes = nodeDetails.map(item => item.node)
      const edges = sourceEdges.map((edge, index) => {
        const sourceId = edge.sourceNodeId || edge.source
        const targetId = edge.targetNodeId || edge.target
        const sourceNode = nodeById.get(sourceId)
        const targetNode = nodeById.get(targetId)
        const status = this.edgeStatus(sourceId, targetId, nodeDetails)
        const condition = edge.conditionExpression || edge.conditionExpr || edge.name || (edge.properties && edge.properties.conditionName) || ''
        const style = STATUS_STYLES[status] || STATUS_STYLES.pending
        return {
          ...edge,
          id: edge.id || `trace-edge-${index}`,
          type: edge.type || 'polyline',
          sourceNodeId: sourceId,
          targetNodeId: targetId,
          text: condition ? { value: condition } : undefined,
          properties: {
            ...(edge.properties || {}),
            conditionName: condition,
            conditionExpr: condition,
            traceStatus: status,
            style: {
              ...(edge.properties && edge.properties.style ? edge.properties.style : {}),
              stroke: style.stroke,
              selectedStroke: 'var(--el-color-primary)',
              hoverStroke: style.stroke,
              strokeWidth: status === 'hit' ? 3 : 1.5,
              strokeDasharray: status === 'skipped' || status === 'pending' ? '6 4' : undefined,
            },
          },
          trace: {
            kind: 'edge',
            status,
            title: condition || '无条件直连',
            typeLabel: '分支连线',
            condition,
            sourceName: sourceNode ? sourceNode.node.properties.nodeName : sourceId,
            targetName: targetNode ? targetNode.node.properties.nodeName : targetId,
          },
        }
      })
      return { nodes, edges }
    },
  },
  watch: {
    graphSnapshot: {
      deep: true,
      handler() {
        if (this.lf) this.renderGraph()
      },
    },
  },
  mounted() {
    this.renderGraph()
  },
  beforeUnmount() {
    if (this.lf && typeof this.lf.destroy === 'function') this.lf.destroy()
    this.lf = null
  },
  methods: {
    statusLabel(status) {
      return STATUS_LABELS[status] || '未知状态'
    },
    normalizedNodes() {
      const source = this.modelData && this.modelData.logicflow
        ? this.modelData.logicflow
        : this.modelData || {}
      return Array.isArray(source.nodes) ? source.nodes : []
    },
    buildNodeDetails(sourceNodes) {
      const candidates = sourceNodes.map((raw, index) => {
        const type = raw.type && NODE_TYPES[raw.type] ? raw.type : Object.keys(NODE_TYPES).find(key => NODE_TYPES[key] === raw.type) || 'task'
        const properties = raw.properties || {}
        return {
          raw,
          type,
          node: {
            ...raw,
            id: raw.id || `trace-node-${index}`,
            type: NODE_TYPES[type],
            x: Number.isFinite(Number(raw.x)) ? Number(raw.x) : 180 + index * 220,
            y: Number.isFinite(Number(raw.y)) ? Number(raw.y) : 240,
            properties: {
              ...properties,
              nodeName: properties.nodeName || raw.name || (type === 'start' ? '开始' : type === 'end' ? '结束' : type === 'join' ? '聚合' : '未命名节点'),
              nodeCode: properties.nodeCode || raw.id || `trace-node-${index}`,
            },
          },
        }
      })
      const used = new Set()
      const executions = []
      this.cards.filter(card => card && card.stepType !== 'end').forEach(card => {
        const type = card.stepType === 'decision' ? 'decision' : 'task'
        const title = card.title || ''
        let match = candidates.find(item => !used.has(item.node.id) && item.type === type && item.node.properties.nodeName === title)
        if (!match && type === 'task' && card.targetVar) {
          match = candidates.find(item => !used.has(item.node.id) && item.type === type && this.nodeTargets(item.raw, card.targetVar))
        }
        if (!match) match = candidates.find(item => !used.has(item.node.id) && item.type === type)
        if (!match) return
        used.add(match.node.id)
        const status = type === 'decision' ? (card.status === 'hit' ? 'hit' : 'miss') : 'done'
        const trace = {
          kind: 'node',
          status,
          title: match.node.properties.nodeName,
          typeLabel: NODE_TYPE_LABELS[match.type],
          execution: card,
        }
        executions.push({ id: match.node.id, trace })
      })
      const executionMap = new Map(executions.map(item => [item.id, item.trace]))
      return candidates.map(item => {
        const trace = executionMap.get(item.node.id) || {
          kind: 'node',
          status: item.type === 'start' || (item.type === 'end' && this.cards.length) ? 'done' : 'pending',
          title: item.node.properties.nodeName,
          typeLabel: NODE_TYPE_LABELS[item.type],
          execution: null,
        }
        const style = STATUS_STYLES[trace.status] || STATUS_STYLES.pending
        item.node.properties = {
          ...item.node.properties,
          traceStatus: trace.status,
          traceStatusText: this.statusLabel(trace.status),
          style: {
            ...(item.node.properties.style || {}),
            stroke: style.stroke,
            fill: style.fill,
            strokeWidth: trace.status === 'hit' ? 3 : 2,
          },
          trace,
        }
        return { ...item, trace }
      })
    },
    nodeTargets(raw, target) {
      const actions = raw && (raw.actionData || (raw.properties && raw.properties.actionData))
      return Array.isArray(actions) && actions.some(action => action && action.target === target)
    },
    edgeStatus(sourceId, targetId, nodeDetails) {
      const source = nodeDetails.find(item => item.node.id === sourceId)
      const target = nodeDetails.find(item => item.node.id === targetId)
      if (!source || !target) return 'pending'
      if (source.type === 'start' && target.trace.status !== 'pending') return 'hit'
      if (source.trace.status !== 'pending' && target.trace.status !== 'pending') return source.type === 'decision' && target.trace.status === 'miss' ? 'skipped' : 'hit'
      if (source.trace.status === 'miss') return 'skipped'
      return 'pending'
    },
    renderGraph() {
      const canvas = this.$el && this.$el.querySelector
        ? this.$el.querySelector('[data-testid="flow-trace-canvas"]')
        : null
      if (!canvas) return
      if (this.lf && typeof this.lf.destroy === 'function') this.lf.destroy()
      try {
        this.lf = createGraphCanvas({
          container: canvas,
          width: canvas.clientWidth || 820,
          height: 470,
          isSilentMode: true,
          stopScrollGraph: false,
          stopZoomGraph: false,
          adjustNodePosition: false,
          keyboard: { enabled: false },
        })
      } catch (error) {
        console.warn('决策流执行图初始化失败，回退为节点列表', error)
        this.lf = null
      }
      if (!this.lf) {
        this.graphMounted = false
        return
      }
      this.graphMounted = true
      this.lf.render(this.graphSnapshot)
      if (typeof this.lf.fitView === 'function') this.lf.fitView(24, 24)
      if (typeof this.lf.on === 'function') {
        this.lf.on('node:click', payload => {
          const id = payload && payload.data && payload.data.id
          const node = this.graphSnapshot.nodes.find(item => item.id === id)
          if (node) this.selectNode(node)
        })
        this.lf.on('edge:click', payload => {
          const id = payload && payload.data && payload.data.id
          const edge = this.graphSnapshot.edges.find(item => item.id === id)
          if (edge) this.selected = edge.trace
        })
      }
    },
    selectNode(node) {
      this.selected = node && node.properties && node.properties.trace
        ? node.properties.trace
        : null
    },
  },
}
</script>

<style scoped>
.flow-trace-graph { margin: 0 0 16px; border: 1px solid var(--tianshu-border-subtle); border-radius: 10px; background: var(--tianshu-bg-surface); overflow: hidden; }
.flow-trace-graph__head { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 12px 16px; border-bottom: 1px solid var(--tianshu-border-subtle); background: var(--tianshu-bg-soft); }
.flow-trace-graph__title { color: var(--tianshu-text-primary); font-size: 14px; font-weight: 700; }
.flow-trace-graph__subtitle { margin-top: 3px; color: var(--tianshu-text-tertiary); font-size: 11px; }
.flow-trace-graph__legend { display: flex; flex-wrap: wrap; gap: 6px 12px; justify-content: flex-end; color: var(--tianshu-text-secondary); font-size: 11px; }
.flow-trace-legend-item { display: inline-flex; gap: 5px; align-items: center; white-space: nowrap; }
.flow-trace-legend-dot { width: 8px; height: 8px; border-radius: 50%; background: var(--tianshu-border-strong); }
.flow-trace-legend-dot.is-hit { background: var(--tianshu-success-text); }
.flow-trace-legend-dot.is-done { background: var(--tianshu-info-text); }
.flow-trace-legend-dot.is-miss { background: var(--tianshu-danger-text); }
.flow-trace-legend-dot.is-skipped { background: var(--tianshu-text-tertiary); }
.flow-trace-graph__body { display: grid; grid-template-columns: minmax(0, 1fr) 255px; min-height: 470px; }
.flow-trace-canvas-wrap { position: relative; min-width: 0; min-height: 470px; background: var(--tianshu-bg-workspace); }
.flow-trace-canvas { width: 100%; height: 470px; }
.flow-trace-detail { min-width: 0; padding: 14px; border-left: 1px solid var(--tianshu-border-subtle); background: var(--tianshu-bg-surface); }
.flow-trace-detail__head, .flow-trace-detail__title-row { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.flow-trace-detail__eyebrow, .flow-trace-detail__label { color: var(--tianshu-text-tertiary); font-size: 11px; font-weight: 700; }
.flow-trace-detail__clear { padding: 0; border: 0; background: transparent; color: var(--tianshu-info-text); font-size: 11px; cursor: pointer; }
.flow-trace-detail__title-row { margin-top: 12px; color: var(--tianshu-text-primary); font-size: 13px; }
.flow-trace-detail__title-row strong { min-width: 0; overflow-wrap: anywhere; }
.flow-trace-status { flex-shrink: 0; padding: 2px 7px; border-radius: 10px; font-size: 10px; font-weight: 700; }
.flow-trace-status.is-hit { background: var(--tianshu-success-bg); color: var(--tianshu-success-text); }
.flow-trace-status.is-done { background: var(--tianshu-info-bg); color: var(--tianshu-info-text); }
.flow-trace-status.is-miss { background: var(--tianshu-danger-bg); color: var(--tianshu-danger-text); }
.flow-trace-status.is-skipped, .flow-trace-status.is-pending { background: var(--tianshu-bg-muted); color: var(--tianshu-text-tertiary); }
.flow-trace-detail__meta { margin-top: 4px; color: var(--tianshu-text-tertiary); font-size: 11px; }
.flow-trace-detail__section { margin-top: 14px; padding-top: 10px; border-top: 1px solid var(--tianshu-border-subtle); }
.flow-trace-detail__section pre { max-height: 150px; margin: 6px 0 0; padding: 7px 8px; overflow: auto; white-space: pre-wrap; overflow-wrap: anywhere; border: 1px solid var(--tianshu-border-subtle); border-radius: 5px; background: var(--tianshu-bg-soft); color: var(--tianshu-text-secondary); font: 11px/1.5 Consolas, Monaco, monospace; }
.flow-trace-condition, .flow-trace-action { display: flex; flex-wrap: wrap; align-items: center; gap: 5px; margin-top: 7px; color: var(--tianshu-text-secondary); font-size: 11px; }
.flow-trace-condition code, .flow-trace-action code { padding: 1px 4px; border-radius: 3px; background: var(--tianshu-info-bg); color: var(--tianshu-info-text); font: 11px Consolas, Monaco, monospace; }
.flow-trace-condition b { margin-left: auto; font-size: 10px; }
.flow-trace-condition b.is-hit { color: var(--tianshu-success-text); }
.flow-trace-condition b.is-miss { color: var(--tianshu-danger-text); }
.flow-trace-path { display: flex; gap: 6px; margin-top: 6px; color: var(--tianshu-text-primary); font-size: 12px; overflow-wrap: anywhere; }
.flow-trace-path span { color: var(--tianshu-text-tertiary); }
.flow-trace-detail__empty { display: flex; flex-direction: column; align-items: center; gap: 6px; padding: 110px 8px 0; color: var(--tianshu-text-tertiary); text-align: center; font-size: 11px; line-height: 1.5; }
.flow-trace-detail__empty strong { color: var(--tianshu-text-secondary); font-size: 12px; }
.flow-trace-detail__empty-icon { display: inline-flex; width: 28px; height: 28px; align-items: center; justify-content: center; border-radius: 50%; background: var(--tianshu-info-bg); color: var(--tianshu-info-text); font-size: 16px; }
.flow-trace-fallback { position: absolute; inset: 0; padding: 18px; overflow: auto; }
.flow-trace-fallback__title { margin-bottom: 10px; color: var(--tianshu-text-secondary); font-size: 12px; font-weight: 700; }
.flow-trace-fallback__list { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; }
.flow-trace-fallback__node { display: flex; flex-direction: column; gap: 3px; min-width: 120px; padding: 9px 11px; border: 1px solid var(--tianshu-border-strong); border-radius: 7px; background: var(--tianshu-bg-surface); color: var(--tianshu-text-primary); text-align: left; cursor: pointer; }
.flow-trace-fallback__node small { color: var(--tianshu-text-tertiary); font-size: 10px; }
.flow-trace-fallback__node.is-hit { border-color: var(--tianshu-success-text); background: var(--tianshu-success-bg); }
.flow-trace-fallback__node.is-done { border-color: var(--tianshu-info-text); background: var(--tianshu-info-bg); }
.flow-trace-fallback__node.is-miss { border-color: var(--tianshu-danger-text); background: var(--tianshu-danger-bg); }
.flow-trace-fallback__node.is-skipped { border-color: var(--tianshu-text-tertiary); background: var(--tianshu-bg-muted); }
@media (max-width: 860px) { .flow-trace-graph__body { grid-template-columns: 1fr; } .flow-trace-detail { border-top: 1px solid var(--tianshu-border-subtle); border-left: 0; } .flow-trace-detail__empty { padding: 20px 8px; } }
</style>
