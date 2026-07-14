<template>
  <div class="uiue-list-page lineage-page">
    <div class="module-hint">
      <div class="hint-title">血缘分析</div>
      <div class="hint-text">从变量、规则、项目、API、数据库、名单或模型出发，查看上游依赖与下游引用关系。</div>
    </div>
    <div class="usage-guide">
      <div v-for="item in lineageGuideCards" :key="item.title" class="guide-item">
        <div class="guide-title">{{ item.title }}</div>
        <div class="guide-text">{{ item.text }}</div>
      </div>
    </div>

    <div class="query-panel">
      <el-form :inline="true" size="small">
        <el-form-item label="节点类型">
          <el-select v-model="query.nodeType" style="width:130px" @change="onNodeTypeChange">
            <el-option v-for="item in nodeTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="起点">
          <el-select
            v-model="query.nodeId"
            filterable
            remote
            reserve-keyword
            clearable
            :remote-method="loadOptions"
            :loading="optionLoading"
            placeholder="输入编码或名称搜索"
            style="width:320px"
          >
            <el-option v-for="item in options" :key="item.type + '-' + item.id" :label="item.displayName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="方向">
          <el-radio-group v-model="query.direction" @change="onDirectionChange">
            <el-radio-button label="ALL">全部</el-radio-button>
            <el-radio-button label="UPSTREAM">上游</el-radio-button>
            <el-radio-button label="DOWNSTREAM">下游</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="el-icon-share" :loading="loading" @click="loadGraph">生成血缘图</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="legend-row">
      <span v-for="item in nodeTypeOptions" :key="item.value" class="legend-item">
        <i :style="{ background: nodeColor(item.value) }" />{{ item.label }}
      </span>
    </div>

    <div ref="graphWrap" class="graph-wrap" v-loading="loading">
      <div v-if="!startNode" class="empty-graph">请选择起点后生成血缘图</div>
      <div v-else class="graph-canvas" :style="{ width: canvasSize.width + 'px', height: canvasSize.height + 'px' }">
        <div v-if="showUpstream" class="side-caption is-upstream">{{ upstreamRoots.length ? '上游' : '暂无上游' }}</div>
        <div class="side-caption is-current">当前节点</div>
        <div v-if="showDownstream" class="side-caption is-downstream">{{ downstreamRoots.length ? '下游' : '暂无下游' }}</div>

        <svg class="edge-layer" :width="canvasSize.width" :height="canvasSize.height">
          <g v-for="edge in edgeLines" :key="edge.key">
            <path :d="edge.path" class="edge-path" marker-end="url(#arrow)" />
            <text :x="edge.labelX" :y="edge.labelY" class="edge-label">{{ edge.label }}</text>
          </g>
          <defs>
            <marker id="arrow" markerWidth="8" markerHeight="8" refX="6" refY="3" orient="auto" markerUnits="strokeWidth">
              <path d="M0,0 L0,6 L7,3 z" fill="#94A3B8" />
            </marker>
          </defs>
        </svg>

        <div
          class="graph-node current-node"
          :style="currentNodeStyle"
        >
          <div class="node-head">
            <span class="node-type" :style="{ color: nodeColor(startNode.type) }">{{ nodeTypeLabel(startNode.type) }}</span>
            <span class="current-badge">当前</span>
          </div>
          <div class="node-label" :title="startNode.label || startNode.code">{{ startNode.label || startNode.code }}</div>
          <div class="node-code" :title="startNode.code">{{ startNode.code }}</div>
        </div>

        <div
          v-for="item in visibleBranches"
          :key="item.branch.instanceId"
          class="graph-node branch-node"
          :class="[
            item.side === 'UPSTREAM' ? 'is-upstream' : 'is-downstream',
            { 'is-cycle': item.branch.cycle }
          ]"
          :style="branchStyle(item)"
        >
          <button
            v-if="canToggle(item.branch)"
            type="button"
            class="branch-toggle"
            :aria-label="item.branch.expanded ? '收起节点' : '展开节点'"
            @click.stop="toggleBranch(item.branch)"
          >
            <i :class="item.branch.loading ? 'el-icon-loading' : (item.branch.expanded ? 'el-icon-minus' : 'el-icon-plus')" />
          </button>
          <div class="node-head">
            <span class="node-type" :style="{ color: nodeColor(item.branch.node.type) }">{{ nodeTypeLabel(item.branch.node.type) }}</span>
            <span v-if="item.branch.cycle" class="cycle-badge">循环引用</span>
          </div>
          <div class="node-label" :title="item.branch.node.label || item.branch.node.code">
            {{ item.branch.node.label || item.branch.node.code }}
          </div>
          <div class="node-code" :title="item.branch.node.code">{{ item.branch.node.code }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { getLineageGraph, listLineageOptions } from '@/api/lineage'

const CARD_W = 200
const CARD_H = 88
const LEVEL_STEP = 264
const ROW_STEP = 112
const PADDING_X = 48
const PADDING_Y = 48
const MIN_CANVAS_W = 960
const MIN_CANVAS_H = 440

export default {
  name: 'LineageGraph',
  data() {
    return {
      lineageGuideCards: [
        { title: '选择起点', text: '先选择节点类型，再输入编码或名称搜索起点；适合从变量、规则、API、DB、名单或模型定位影响范围。' },
        { title: '两跳展开', text: '首次自动展示上下游各两层关系；点击第二层节点的加号继续展开，点击减号收起整条分支。' },
        { title: '静态分析边界', text: '血缘基于结构化配置和脚本静态分析生成；复杂动态脚本引用需结合规则测试与执行日志复核。' }
      ],
      loading: false,
      optionLoading: false,
      options: [],
      startNode: null,
      upstreamRoots: [],
      downstreamRoots: [],
      loadedDirections: { UPSTREAM: false, DOWNSTREAM: false },
      branchSequence: 0,
      query: { nodeType: 'VARIABLE', nodeId: '', direction: 'ALL' },
      nodeTypeOptions: [
        { label: '项目', value: 'PROJECT' },
        { label: '变量', value: 'VARIABLE' },
        { label: '规则', value: 'RULE' },
        { label: '模型', value: 'MODEL' },
        { label: 'API', value: 'API' },
        { label: '数据库', value: 'DB' },
        { label: '名单', value: 'LIST' },
        { label: '外数源', value: 'DATASOURCE' }
      ]
    }
  },
  created() {
    this.loadOptions('')
  },
  computed: {
    showUpstream() {
      return this.query.direction !== 'DOWNSTREAM'
    },
    showDownstream() {
      return this.query.direction !== 'UPSTREAM'
    },
    visibleBranches() {
      const result = []
      const collect = (branches, side, depth, parentId) => {
        const list = branches || []
        list.forEach(branch => {
          result.push({ branch, side, depth, parentId })
          if (branch.expanded && branch.children.length) {
            collect(branch.children, side, depth + 1, branch.instanceId)
          }
        })
      }
      if (this.showUpstream) collect(this.upstreamRoots, 'UPSTREAM', 1, 'CURRENT')
      if (this.showDownstream) collect(this.downstreamRoots, 'DOWNSTREAM', 1, 'CURRENT')
      return result
    },
    mindMapLayout() {
      const upstream = this.layoutSide(this.showUpstream ? this.upstreamRoots : [])
      const downstream = this.layoutSide(this.showDownstream ? this.downstreamRoots : [])
      const maxDepth = Math.max(1, upstream.maxDepth, downstream.maxDepth)
      const width = Math.max(MIN_CANVAS_W, PADDING_X * 2 + CARD_W + maxDepth * LEVEL_STEP * 2)
      const height = Math.max(MIN_CANVAS_H, PADDING_Y * 2 + Math.max(upstream.height, downstream.height))
      const currentLeft = (width - CARD_W) / 2
      const currentCenterY = height / 2
      const positions = {
        CURRENT: { left: currentLeft, top: currentCenterY - CARD_H / 2 }
      }
      this.applySidePositions(positions, upstream, 'UPSTREAM', currentLeft, currentCenterY)
      this.applySidePositions(positions, downstream, 'DOWNSTREAM', currentLeft, currentCenterY)
      return { width, height, positions }
    },
    canvasSize() {
      return {
        width: this.mindMapLayout.width,
        height: this.mindMapLayout.height
      }
    },
    edgeLines() {
      return this.visibleBranches.map(item => {
        const branchPos = this.mindMapLayout.positions[item.branch.instanceId]
        const parentPos = this.mindMapLayout.positions[item.parentId]
        if (!branchPos || !parentPos) return null
        const upstream = item.side === 'UPSTREAM'
        const x1 = upstream ? branchPos.left + CARD_W : parentPos.left + CARD_W
        const y1 = upstream ? branchPos.top + CARD_H / 2 : parentPos.top + CARD_H / 2
        const x2 = upstream ? parentPos.left : branchPos.left
        const y2 = upstream ? parentPos.top + CARD_H / 2 : branchPos.top + CARD_H / 2
        const midX = (x1 + x2) / 2
        return {
          key: item.branch.instanceId + '-' + item.parentId,
          fromId: upstream ? item.branch.instanceId : item.parentId,
          toId: upstream ? item.parentId : item.branch.instanceId,
          path: `M ${x1} ${y1} C ${midX} ${y1}, ${midX} ${y2}, ${x2} ${y2}`,
          labelX: midX,
          labelY: (y1 + y2) / 2 - 8,
          label: item.branch.relationLabel || ''
        }
      }).filter(Boolean)
    },
    currentNodeStyle() {
      const pos = this.mindMapLayout.positions.CURRENT
      return {
        left: pos.left + 'px',
        top: pos.top + 'px',
        borderColor: this.nodeColor(this.startNode.type),
        boxShadow: 'inset 4px 0 0 ' + this.nodeColor(this.startNode.type)
      }
    }
  },
  methods: {
    async loadOptions(keyword) {
      this.optionLoading = true
      try {
        const res = await listLineageOptions({ nodeType: this.query.nodeType, keyword })
        this.options = (res && res.data) || []
      } finally {
        this.optionLoading = false
      }
    },
    onNodeTypeChange() {
      this.query.nodeId = ''
      this.resetGraph()
      this.loadOptions('')
    },
    resetGraph() {
      this.startNode = null
      this.upstreamRoots = []
      this.downstreamRoots = []
      this.loadedDirections = { UPSTREAM: false, DOWNSTREAM: false }
      this.branchSequence = 0
    },
    centerGraphOnCurrent() {
      const graphWrap = this.$refs.graphWrap
      if (!graphWrap) return
      graphWrap.scrollLeft = Math.max(0, (graphWrap.scrollWidth - graphWrap.clientWidth) / 2)
    },
    async loadGraph() {
      if (!this.query.nodeId) {
        this.$message.warning('请先选择血缘起点')
        return
      }
      this.loading = true
      try {
        const res = await getLineageGraph({ ...this.query, maxDepth: 2 })
        const data = (res && res.data) || {}
        this.resetGraph()
        this.startNode = data.startNode || null
        if (this.query.direction !== 'DOWNSTREAM') {
          this.upstreamRoots = this.buildBranches(data, 'UPSTREAM', 2)
          this.loadedDirections.UPSTREAM = true
        }
        if (this.query.direction !== 'UPSTREAM') {
          this.downstreamRoots = this.buildBranches(data, 'DOWNSTREAM', 2)
          this.loadedDirections.DOWNSTREAM = true
        }
        this.$nextTick(() => this.centerGraphOnCurrent())
      } catch (e) {
        this.resetGraph()
        this.$message.error('血缘图加载失败，请重试')
      } finally {
        this.loading = false
      }
    },
    async onDirectionChange(direction) {
      if (!this.query.nodeId || !this.startNode) return
      const required = direction === 'ALL' ? ['UPSTREAM', 'DOWNSTREAM'] : [direction]
      const missing = required.filter(item => !this.loadedDirections[item])
      if (!missing.length) return
      this.loading = true
      try {
        for (const item of missing) {
          const res = await getLineageGraph({
            nodeType: this.query.nodeType,
            nodeId: this.query.nodeId,
            direction: item,
            maxDepth: 2
          })
          const data = (res && res.data) || {}
          if (!this.startNode) this.startNode = data.startNode || null
          if (item === 'UPSTREAM') this.upstreamRoots = this.buildBranches(data, item, 2)
          if (item === 'DOWNSTREAM') this.downstreamRoots = this.buildBranches(data, item, 2)
          this.loadedDirections[item] = true
        }
      } catch (e) {
        this.$message.error('血缘方向加载失败，请重试')
      } finally {
        this.loading = false
      }
    },
    buildBranches(data, direction, maxDepth) {
      const start = data && data.startNode
      if (!start || !start.id) return []
      return this.createBranches(data, direction, maxDepth, start.id, [start.id])
    },
    buildDirectChildren(parent, data) {
      return this.createBranches(data, parent.direction, 1, parent.node.id, parent.path)
    },
    createBranches(data, direction, maxDepth, parentNodeId, parentPath) {
      const nodeMap = {}
      ;((data && data.nodes) || []).forEach(node => {
        if (node && node.id) nodeMap[node.id] = node
      })
      if (data && data.startNode && data.startNode.id) {
        nodeMap[data.startNode.id] = data.startNode
      }
      const adjacency = {}
      ;((data && data.edges) || []).forEach(edge => {
        const parentId = direction === 'UPSTREAM' ? edge.to : edge.from
        const childId = direction === 'UPSTREAM' ? edge.from : edge.to
        if (!adjacency[parentId]) adjacency[parentId] = []
        adjacency[parentId].push({ childId, edge })
      })
      const build = (nodeId, path, depth) => {
        if (depth > maxDepth) return []
        return (adjacency[nodeId] || []).map(item => {
          const node = nodeMap[item.childId]
          if (!node) return null
          const cycle = path.includes(node.id)
          const nextPath = path.concat(node.id)
          const hasMore = !cycle && Boolean(direction === 'UPSTREAM' ? node.hasUpstream : node.hasDownstream)
          const children = cycle || depth >= maxDepth ? [] : build(node.id, nextPath, depth + 1)
          return {
            instanceId: 'branch-' + (++this.branchSequence),
            node,
            direction,
            relationLabel: item.edge.label || '',
            path: nextPath,
            children,
            expanded: children.length > 0,
            loaded: cycle || depth < maxDepth || !hasMore,
            loading: false,
            hasMore,
            cycle
          }
        }).filter(Boolean)
      }
      return build(parentNodeId, parentPath, 1)
    },
    async toggleBranch(branch) {
      if (!branch || branch.loading || branch.cycle) return
      if (branch.expanded) {
        branch.expanded = false
        return
      }
      if (branch.loaded) {
        branch.expanded = branch.children.length > 0
        return
      }
      branch.loading = true
      try {
        const res = await getLineageGraph({
          nodeType: branch.node.type,
          nodeId: branch.node.refId,
          direction: branch.direction,
          maxDepth: 1
        })
        branch.children = this.buildDirectChildren(branch, (res && res.data) || {})
        branch.loaded = true
        branch.hasMore = branch.children.length > 0
        branch.expanded = branch.children.length > 0
      } catch (e) {
        this.$message.error('血缘分支加载失败，请重试')
      } finally {
        branch.loading = false
      }
    },
    canToggle(branch) {
      return Boolean(branch && !branch.cycle && (branch.hasMore || branch.children.length))
    },
    layoutSide(roots) {
      const rawPositions = {}
      let leafIndex = 0
      let maxDepth = 0
      const place = (branch, depth) => {
        maxDepth = Math.max(maxDepth, depth)
        const children = branch.expanded ? branch.children : []
        let centerY
        if (children.length) {
          const childCenters = children.map(child => place(child, depth + 1))
          centerY = (childCenters[0] + childCenters[childCenters.length - 1]) / 2
        } else {
          centerY = leafIndex * ROW_STEP + CARD_H / 2
          leafIndex += 1
        }
        rawPositions[branch.instanceId] = { centerY, depth }
        return centerY
      }
      ;(roots || []).forEach(root => place(root, 1))
      const centers = Object.values(rawPositions).map(item => item.centerY)
      const minCenter = centers.length ? Math.min(...centers) : CARD_H / 2
      const maxCenter = centers.length ? Math.max(...centers) : CARD_H / 2
      return {
        rawPositions,
        maxDepth,
        centerY: (minCenter + maxCenter) / 2,
        height: centers.length ? maxCenter - minCenter + CARD_H : CARD_H
      }
    },
    applySidePositions(positions, layout, side, currentLeft, currentCenterY) {
      const offsetY = currentCenterY - layout.centerY
      Object.keys(layout.rawPositions).forEach(instanceId => {
        const raw = layout.rawPositions[instanceId]
        positions[instanceId] = {
          left: side === 'UPSTREAM'
            ? currentLeft - raw.depth * LEVEL_STEP
            : currentLeft + raw.depth * LEVEL_STEP,
          top: raw.centerY + offsetY - CARD_H / 2
        }
      })
    },
    branchStyle(item) {
      const pos = this.mindMapLayout.positions[item.branch.instanceId] || { left: 0, top: 0 }
      return {
        left: pos.left + 'px',
        top: pos.top + 'px',
        borderColor: this.nodeColor(item.branch.node.type),
        boxShadow: 'inset 4px 0 0 ' + this.nodeColor(item.branch.node.type)
      }
    },
    nodeColor(type) {
      return {
        PROJECT: '#2563EB',
        VARIABLE: '#059669',
        RULE: '#DC2626',
        MODEL: '#7C3AED',
        API: '#EA580C',
        DB: '#0F766E',
        LIST: '#C026D3',
        DATASOURCE: '#64748B',
        DATA_FIELD: '#0891B2'
      }[type] || '#64748B'
    },
    nodeTypeLabel(type) {
      const found = this.nodeTypeOptions.find(item => item.value === type)
      if (found) return found.label
      if (type === 'DATA_FIELD') return '数据字段'
      return type
    }
  }
}
</script>

<style lang="scss" scoped>
.lineage-page {
  .module-hint {
    background: #F8FAFC;
    border: 1px solid #E2E8F0;
    border-radius: 4px;
    padding: 12px 16px;
    margin-bottom: 16px;
    display: flex;
    align-items: center;
    gap: 12px;
  }
  .hint-title { color:#1F2937; font-weight:700; white-space:nowrap; }
  .hint-text { color:#64748B; }
  .usage-guide {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 12px;
    margin-bottom: 16px;
  }
  .guide-item {
    border: 1px solid #E2E8F0;
    border-radius: 4px;
    padding: 12px;
    background: #FFFFFF;
  }
  .guide-title {
    color: #0F172A;
    font-weight: 700;
    margin-bottom: 4px;
  }
  .guide-text {
    color: #64748B;
    font-size: 12px;
    line-height: 1.6;
  }
  .query-panel {
    background: #FFFFFF;
    border: 1px solid #E5E7EB;
    border-radius: 4px;
    padding: 12px 12px 0;
    margin-bottom: 12px;
  }
  .legend-row {
    display: flex;
    gap: 12px;
    flex-wrap: wrap;
    color: #475569;
    font-size: 12px;
    margin-bottom: 12px;
  }
  .legend-item {
    display: inline-flex;
    align-items: center;
    gap: 4px;
  }
  .legend-item i {
    width: 10px;
    height: 10px;
    border-radius: 2px;
  }
  .graph-wrap {
    background: #F8FAFC;
    border: 1px solid #E5E7EB;
    border-radius: 4px;
    min-height: 440px;
    overflow: auto;
    position: relative;
  }
  .empty-graph {
    color: #94A3B8;
    text-align: center;
    padding: 128px 0;
  }
  .graph-canvas {
    position: relative;
    min-width: 100%;
    background-image: radial-gradient(#CBD5E1 0.8px, transparent 0.8px);
    background-size: 16px 16px;
  }
  .side-caption {
    position: absolute;
    top: 16px;
    color: #64748B;
    font-size: 12px;
    font-weight: 700;
    letter-spacing: 0.08em;
    z-index: 2;
  }
  .side-caption.is-upstream { left: 24px; }
  .side-caption.is-current { left: 50%; transform: translateX(-50%); }
  .side-caption.is-downstream { right: 24px; }
  .edge-layer {
    position: absolute;
    left: 0;
    top: 0;
    pointer-events: none;
  }
  .edge-path {
    fill: none;
    stroke: #94A3B8;
    stroke-width: 1.5;
  }
  .edge-label {
    fill: #64748B;
    font-size: 12px;
    text-anchor: middle;
    paint-order: stroke;
    stroke: #F8FAFC;
    stroke-width: 4px;
  }
  .graph-node {
    position: absolute;
    width: 200px;
    height: 88px;
    background: #FFFFFF;
    border: 1px solid #CBD5E1;
    border-radius: 6px;
    padding: 12px;
    box-sizing: border-box;
    z-index: 3;
  }
  .branch-node {
    box-shadow: 0 8px 20px rgba(15, 23, 42, 0.06);
  }
  .current-node {
    background: #FFFBEB;
    border-width: 2px;
    box-shadow: 0 12px 24px rgba(15, 23, 42, 0.10);
  }
  .graph-node.is-cycle {
    background: #FFF7ED;
    border-style: dashed;
  }
  .node-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    margin-bottom: 4px;
  }
  .node-type {
    font-size: 12px;
    font-weight: 700;
  }
  .current-badge,
  .cycle-badge {
    border-radius: 12px;
    padding: 2px 8px;
    font-size: 12px;
    font-weight: 700;
  }
  .current-badge {
    color: #92400E;
    background: #FEF3C7;
  }
  .cycle-badge {
    color: #9A3412;
    background: #FFEDD5;
  }
  .node-label {
    color: #111827;
    font-weight: 700;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .node-code {
    color: #64748B;
    font-family: Menlo, Monaco, Consolas, monospace;
    font-size: 12px;
    margin-top: 4px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .branch-toggle {
    position: absolute;
    top: 50%;
    width: 26px;
    height: 26px;
    margin-top: -13px;
    padding: 0;
    border: 1px solid #CBD5E1;
    border-radius: 50%;
    color: #334155;
    background: #FFFFFF;
    box-shadow: 0 2px 6px rgba(15, 23, 42, 0.12);
    cursor: pointer;
    z-index: 4;
  }
  .branch-toggle:hover,
  .branch-toggle:focus {
    color: #2563EB;
    border-color: #2563EB;
    outline: none;
  }
  .branch-node.is-upstream .branch-toggle { left: -13px; }
  .branch-node.is-downstream .branch-toggle { right: -13px; }
  @media (max-width: 1200px) {
    .usage-guide {
      grid-template-columns: repeat(1, minmax(0, 1fr));
    }
  }
}
</style>
