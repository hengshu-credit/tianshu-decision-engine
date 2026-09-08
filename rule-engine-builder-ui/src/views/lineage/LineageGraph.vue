<template>
  <div
    class="uiue-list-page lineage-page"
    :class="{ 'is-embedded': embedded }"
  >
    <div v-if="!embedded" class="module-hint">
      <div class="hint-title">血缘分析</div>
      <div class="hint-text">
        从变量、规则、项目、API、数据库、名单或模型出发，查看上游依赖与下游引用关系。
      </div>
    </div>
    <div v-if="!embedded" class="usage-guide">
      <div
        v-for="item in lineageGuideCards"
        :key="item.title"
        class="guide-item"
      >
        <div class="guide-title">{{ item.title }}</div>
        <div class="guide-text">{{ item.text }}</div>
      </div>
    </div>

    <div v-if="!embedded" class="query-panel">
      <el-form :inline="true" size="small">
        <el-form-item label="节点类型">
          <el-select
            v-model="query.nodeType"
            style="width: 130px"
            @change="onNodeTypeChange"
          >
            <el-option
              v-for="item in nodeTypeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
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
            style="width: 320px"
          >
            <el-option
              v-for="item in options"
              :key="item.type + '-' + item.id"
              :label="item.displayName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="方向">
          <el-radio-group v-model="query.direction" @change="onDirectionChange">
            <el-radio-button value="ALL">全部</el-radio-button>
            <el-radio-button value="UPSTREAM">上游</el-radio-button>
            <el-radio-button value="DOWNSTREAM">下游</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            :icon="ElIconShare"
            :loading="loading"
            @click="loadGraph"
            >生成血缘图</el-button
          >
        </el-form-item>
      </el-form>
    </div>

    <div class="legend-row">
      <span
        v-for="item in nodeTypeOptions"
        :key="item.value"
        class="legend-item"
      >
        <i :style="{ background: nodeColor(item.value) }" />{{ item.label }}
      </span>
    </div>

    <div
      ref="graphWrap"
      class="graph-wrap"
      :style="{ height: graphHeight + 'px' }"
      :class="{ 'is-interacting': pointerInteraction }"
      v-loading="loading"
      @pointerdown="beginCanvasPan"
      @pointermove="onPointerMove"
      @pointerup="endPointerInteraction"
      @pointercancel="endPointerInteraction"
    >
      <div v-if="!startNode" class="empty-graph">请选择起点后生成血缘图</div>
      <div v-else class="graph-toolbar" @pointerdown.stop>
        <button type="button" aria-label="缩小血缘图" title="缩小" @click="zoomOut">
          −
        </button>
        <span class="zoom-percent">{{ zoomPercent }}%</span>
        <button type="button" aria-label="放大血缘图" title="放大" @click="zoomIn">
          +
        </button>
        <button
          type="button"
          class="best-layout-button"
          aria-label="一键回到最佳分布"
          title="清除手工位置并适配画布"
          @click="resetToBestLayout"
        >
          最佳分布
        </button>
      </div>
      <div
        v-if="startNode"
        class="graph-canvas"
        :style="{
          width: canvasSize.width + 'px',
          height: canvasSize.height + 'px',
          ...viewportTransformStyle,
        }"
      >
        <div v-if="showUpstream" class="side-caption is-upstream">
          {{ upstreamRoots.length ? '上游' : '暂无上游' }}
        </div>
        <div class="side-caption is-current">当前节点</div>
        <div v-if="showDownstream" class="side-caption is-downstream">
          {{ downstreamRoots.length ? '下游' : '暂无下游' }}
        </div>

        <svg
          class="edge-layer"
          :width="canvasSize.width"
          :height="canvasSize.height"
        >
          <g v-for="edge in edgeLines" :key="edge.key">
            <path :d="edge.path" class="edge-path" marker-end="url(#arrow)" />
            <text :x="edge.labelX" :y="edge.labelY" class="edge-label">
              {{ edge.label }}
            </text>
          </g>
          <defs>
            <marker
              id="arrow"
              markerWidth="8"
              markerHeight="8"
              refX="6"
              refY="3"
              orient="auto"
              markerUnits="strokeWidth"
            >
              <path
                d="M0,0 L0,6 L7,3 z"
                fill="var(--tianshu-border-strong)"
              />
            </marker>
          </defs>
        </svg>

        <div
          class="graph-node current-node"
          :style="currentNodeStyle"
          @pointerdown.stop="beginNodeDrag($event, 'CURRENT')"
        >
          <button
            v-if="startNode.type === 'DATA_OBJECT'"
            type="button"
            class="branch-toggle object-toggle"
            :aria-label="expandedObjects[startNode.id] ? '收起字段' : '展开字段'"
            @pointerdown.stop
            @click.stop="toggleObject(startNode.id)"
          >{{ expandedObjects[startNode.id] ? '−' : '+' }}</button>
          <div class="node-head">
            <span
              class="node-type"
              :style="{ color: nodeColor(startNode.type) }"
              >{{ nodeTypeLabel(startNode.type) }}</span
            >
            <span class="current-badge">当前</span>
          </div>
          <div class="node-label" :title="startNode.label || startNode.code">
            {{ startNode.label || startNode.code }}
          </div>
          <div class="node-code" :title="startNode.code">
            {{ startNode.code }}
          </div>
        </div>

        <div
          v-for="item in visibleBranches"
          :key="item.branch.instanceId"
          class="graph-node branch-node"
          :class="[
            item.side === 'UPSTREAM' ? 'is-upstream' : 'is-downstream',
            { 'is-cycle': item.branch.cycle },
          ]"
          :style="branchStyle(item)"
          :data-node-id="item.branch.node.id"
          @pointerdown.stop="beginNodeDrag($event, item.branch.instanceId)"
        >
          <button
            v-if="canToggle(item.branch)"
            type="button"
            class="branch-toggle"
            :aria-label="item.branch.objectGroup ? (item.branch.expanded ? '收起字段' : '展开字段') : (item.branch.expanded ? '收起节点' : '展开节点')"
            @pointerdown.stop
            @click.stop="toggleBranch(item.branch)"
          >
            <app-icon
              :name="
                item.branch.loading
                  ? 'Loading'
                  : item.branch.expanded
                  ? 'Minus'
                  : 'Plus'
              "
              :class="{ 'is-loading': item.branch.loading }"
            />
          </button>
          <div class="node-head">
            <span
              class="node-type"
              :style="{ color: nodeColor(item.branch.node.type) }"
              >{{ nodeTypeLabel(item.branch.node.type) }}</span
            >
            <span v-if="item.branch.cycle" class="cycle-badge">循环引用</span>
          </div>
          <div
            class="node-label"
            :title="item.branch.node.label || item.branch.node.code"
          >
            {{ item.branch.node.label || item.branch.node.code }}
          </div>
          <div class="node-code" :title="item.branch.node.code">
            {{ item.branch.node.code }}
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { markRaw } from 'vue'
import { Share as ElIconShare } from '@element-plus/icons-vue'
import { getLineageGraph, listLineageOptions } from '@/api/lineage'
import { lineageLayers } from '@/utils/lineageLayers'

const CARD_W = 200
const CARD_H = 88
const LEVEL_STEP = 264
const ROW_STEP = 112
const PADDING_X = 48
const PADDING_Y = 48
const MIN_CANVAS_W = 960
const MIN_CANVAS_H = 440
const MIN_SCALE = 0.4
const MAX_SCALE = 2
const ZOOM_STEP = 0.1
const VIEWPORT_PADDING = 32

export default {
  props: {
    embedded: { type: Boolean, default: false },
    initialNodeType: { type: String, default: '' },
    initialNodeId: { type: [String, Number], default: '' },
    initialDirection: { type: String, default: 'ALL' },
  },
  data() {
    return {
      lineageGuideCards: [
        {
          title: '选择起点',
          text: '先选择节点类型，再输入编码或名称搜索起点；适合从变量、规则、API、DB、名单或模型定位影响范围。',
        },
        {
          title: '两跳展开',
          text: '首次展示上下游各两层关系；数据对象默认收起字段，点击加号展开。共享节点只显示一次，收起分支仍保留其他路径的引用。',
        },
        {
          title: '静态分析边界',
          text: '血缘基于结构化配置和脚本静态分析生成；复杂动态脚本引用需结合规则测试与执行日志复核。',
        },
      ],
      loading: false,
      optionLoading: false,
      options: [],
      startNode: null,
      upstreamRoots: [],
      downstreamRoots: [],
      knownNodes: {},
      knownEdges: [],
      loadedDirections: { UPSTREAM: false, DOWNSTREAM: false },
      expandedObjects: {},
      graphHeight: 440,
      viewport: { x: 0, y: 0, scale: 1 },
      positionOverrides: {},
      pointerInteraction: null,
      query: { nodeType: 'VARIABLE', nodeId: '', direction: 'ALL' },
      nodeTypeOptions: [
        { label: '项目', value: 'PROJECT' },
        { label: '变量', value: 'VARIABLE' },
        { label: '数据对象', value: 'DATA_OBJECT' },
        { label: '规则', value: 'RULE' },
        { label: '模型', value: 'MODEL' },
        { label: 'API', value: 'API' },
        { label: '数据库', value: 'DB' },
        { label: '名单', value: 'LIST' },
        { label: '外数源', value: 'DATASOURCE' },
      ],
      ElIconShare: markRaw(ElIconShare),
    }
  },
  name: 'LineageGraph',
  watch: {
    startNode: {
      flush: 'post',
      handler(node) {
        const graphWrap = this.$refs.graphWrap
        if (!graphWrap) return
        graphWrap.removeEventListener('wheel', this.onCanvasWheel)
        // 画布缩放必须阻止页面同时滚动；空画布无需注册监听。
        if (node) graphWrap.addEventListener('wheel', this.onCanvasWheel, { passive: false })
      },
    },
  },
  beforeUnmount() {
    this.$refs.graphWrap.removeEventListener('wheel', this.onCanvasWheel)
    this.resizeObserver?.disconnect()
    window.removeEventListener('resize', this.resizeGraph)
  },
  mounted() {
    this.resizeGraph()
    window.addEventListener('resize', this.resizeGraph)
    if (typeof ResizeObserver !== 'undefined') {
      this.resizeObserver = new ResizeObserver(() => this.resizeGraph())
      this.resizeObserver.observe(this.$el)
    }
  },
  created() {
    if (this.initialNodeType) this.query.nodeType = this.initialNodeType
    if (this.initialNodeId) this.query.nodeId = this.initialNodeId
    if (this.initialDirection) this.query.direction = this.initialDirection
    if (this.embedded && this.query.nodeId) {
      this.loadGraph()
    } else {
      this.loadOptions('')
    }
  },
  computed: {
    showUpstream() {
      return this.query.direction !== 'DOWNSTREAM'
    },
    showDownstream() {
      return this.query.direction !== 'UPSTREAM'
    },
    branchPaths() {
      const result = []
      const collect = (branches, side, depth, parentId) => {
        const list = branches || []
        list.forEach((branch) => {
          result.push({ branch, side, depth, parentId })
          if (branch.expanded && branch.children.length) {
            collect(branch.children, side, depth + 1, branch.instanceId)
          }
        })
      }
      if (this.showUpstream)
        collect(this.upstreamRoots, 'UPSTREAM', 1, 'CURRENT')
      if (this.showDownstream)
        collect(this.downstreamRoots, 'DOWNSTREAM', 1, 'CURRENT')
      return result
    },
    visibleGraph() {
      const nodes = new Map()
      const edges = new Map()
      const currentId = this.startNode?.id
      const canonicalId = id => id === currentId ? 'CURRENT' : id
      const objects = new Map()
      this.branchPaths.forEach(item => {
        const object = item.branch.node.dataObject
        if (object) objects.set(object.id, object)
      })
      if (this.startNode?.type === 'DATA_OBJECT') objects.set(currentId, this.startNode)
      const displayId = id => {
        const object = this.knownNodes[id]?.dataObject
        return canonicalId(object && !this.expandedObjects[object.id] ? object.id : id)
      }
      const addNode = item => {
        const id = item.branch.node.id
        if (id === currentId) return
        const previous = nodes.get(id)
        if (!previous) nodes.set(id, item)
        else if (!item.branch.cycle && previous.branch.cycle) nodes.set(id, item)
      }
      const addEdge = (fromId, toId, label) => {
        if (fromId === toId) return
        const key = `${fromId}->${toId}:${label}`
        edges.set(key, { key, fromId, toId, label })
      }
      this.branchPaths.forEach(item => {
        const { branch } = item
        const object = branch.node.dataObject
        if (object) {
          const expanded = Boolean(this.expandedObjects[object.id])
          addNode({
            ...item,
            branch: { node: object, instanceId: object.id, objectGroup: true, expanded, children: [] },
          })
          if (expanded) {
            addNode(item)
            addEdge(canonicalId(object.id), canonicalId(branch.node.id), '包含字段')
          }
        } else if (!objects.has(branch.node.id)) {
          addNode(item)
        }
      })
      // 折叠仅决定节点可见性；仍可见的共享节点之间保留全部已加载的真实依赖。
      this.knownEdges.forEach(edge => addEdge(displayId(edge.from), displayId(edge.to), edge.label || ''))
      const visibleIds = new Set(['CURRENT', ...nodes.keys()])
      return {
        nodes: [...nodes.values()],
        edges: [...edges.values()].filter(edge => visibleIds.has(edge.fromId) && visibleIds.has(edge.toId)),
      }
    },
    visibleBranches() {
      return this.visibleGraph.nodes
    },
    mindMapLayout() {
      const nodeIds = ['CURRENT', ...this.visibleBranches.map(item => item.branch.instanceId)]
      const layers = lineageLayers(nodeIds, this.visibleGraph.edges, 'CURRENT')
      const maxDepth = Math.max(1, ...[...layers.values()].map(Math.abs))
      const width = Math.max(
        MIN_CANVAS_W,
        PADDING_X * 2 + CARD_W + maxDepth * LEVEL_STEP * 2
      )
      const columns = new Map()
      nodeIds.forEach(id => {
        const layer = layers.get(id)
        if (!columns.has(layer)) columns.set(layer, [])
        columns.get(layer).push(id)
      })
      const positions = {}
      let maxOffset = 0
      columns.forEach((ids, layer) => {
        // 固定排序，避免收起某条路径后共享节点换用另一个路径实例而乱序。
        ids.sort((a, b) => a.localeCompare(b, 'en', { numeric: true }))
        const currentIndex = ids.indexOf('CURRENT')
        const center = currentIndex >= 0 ? currentIndex : (ids.length - 1) / 2
        ids.forEach((id, index) => {
          const offset = (index - center) * ROW_STEP
          maxOffset = Math.max(maxOffset, Math.abs(offset))
          positions[id] = { left: (width - CARD_W) / 2 + layer * LEVEL_STEP, top: offset }
        })
      })
      const height = Math.max(MIN_CANVAS_H, PADDING_Y * 2 + CARD_H + maxOffset * 2)
      Object.values(positions).forEach(pos => { pos.top += (height - CARD_H) / 2 })
      return { width, height, positions }
    },
    canvasSize() {
      return {
        width: this.mindMapLayout.width,
        height: this.mindMapLayout.height,
      }
    },
    viewportTransformStyle() {
      return {
        transform: `translate(${this.viewport.x}px, ${this.viewport.y}px) scale(${this.viewport.scale})`,
      }
    },
    zoomPercent() {
      return Math.round(this.viewport.scale * 100)
    },
    edgeLines() {
      return this.visibleGraph.edges
        .map((edge) => {
          const from = this.nodePosition(edge.fromId)
          const to = this.nodePosition(edge.toId)
          const sameLayer = from.left === to.left
          const forward = from.left < to.left
          const x1 = from.left + (forward || sameLayer ? CARD_W : 0)
          const y1 = from.top + CARD_H / 2
          const x2 = to.left + (forward ? 0 : CARD_W)
          const y2 = to.top + CARD_H / 2
          const midX = sameLayer ? x1 + 48 : (x1 + x2) / 2
          return {
            ...edge,
            path: `M ${x1} ${y1} C ${midX} ${y1}, ${midX} ${y2}, ${x2} ${y2}`,
            labelX: midX,
            labelY: (y1 + y2) / 2 - 8,
          }
        })
        .filter(Boolean)
    },
    currentNodeStyle() {
      const pos = this.nodePosition('CURRENT')
      return {
        left: pos.left + 'px',
        top: pos.top + 'px',
        borderColor: this.nodeColor(this.startNode.type),
        boxShadow: 'inset 4px 0 0 ' + this.nodeColor(this.startNode.type),
      }
    },
  },
  methods: {
    mergeGraph(data) {
      ;[...(data.nodes || []), data.startNode].filter(Boolean).forEach(node => {
        this.knownNodes[node.id] = node
      })
      const edges = new Map(this.knownEdges.map(edge => [`${edge.from}->${edge.to}:${edge.label || ''}`, edge]))
      ;(data.edges || []).forEach(edge => edges.set(`${edge.from}->${edge.to}:${edge.label || ''}`, edge))
      this.knownEdges = [...edges.values()]
    },
    resizeGraph() {
      const wrap = this.$refs.graphWrap
      if (!wrap) return
      // 使用容器在滚动内容中的位置，避免滚动页面时画布不断增高。
      const main = wrap.closest('.layout-main')
      const top = wrap.getBoundingClientRect().top + (main?.scrollTop || 0)
      const bottom = main ? Math.min(window.innerHeight, main.getBoundingClientRect().bottom) : window.innerHeight
      const padding = main ? parseFloat(getComputedStyle(main).paddingBottom) || 0 : 16
      const pagePadding = parseFloat(getComputedStyle(this.$el).paddingBottom) || 0
      const height = Math.max(this.embedded ? 360 : 440, bottom - top - padding - pagePadding)
      if (this.graphHeight !== height) {
        this.graphHeight = height
        this.$nextTick(() => this.fitGraph())
      }
    },
    toggleObject(id) {
      this.expandedObjects[id] = !this.expandedObjects[id]
      this.$nextTick(() => this.fitGraph())
    },
    async loadOptions(keyword) {
      this.optionLoading = true
      try {
        const res = await listLineageOptions({
          nodeType: this.query.nodeType,
          keyword,
        })
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
      this.knownNodes = {}
      this.knownEdges = []
      this.loadedDirections = { UPSTREAM: false, DOWNSTREAM: false }
      this.expandedObjects = {}
      this.positionOverrides = {}
      this.pointerInteraction = null
      this.viewport = { x: 0, y: 0, scale: 1 }
    },
    fitGraph() {
      const graphWrap = this.$refs.graphWrap
      if (!graphWrap) return
      const width = graphWrap.clientWidth
      const height = graphWrap.clientHeight
      if (!width || !height) return
      const availableWidth = Math.max(1, width - VIEWPORT_PADDING * 2)
      const availableHeight = Math.max(1, height - VIEWPORT_PADDING * 2)
      const scale = Math.max(
        MIN_SCALE,
        Math.min(
          1,
          availableWidth / this.canvasSize.width,
          availableHeight / this.canvasSize.height
        )
      )
      this.viewport = {
        x: (width - this.canvasSize.width * scale) / 2,
        y: (height - this.canvasSize.height * scale) / 2,
        scale,
      }
    },
    resetToBestLayout() {
      this.positionOverrides = {}
      this.$nextTick(() => this.fitGraph())
    },
    setZoom(nextScale, clientPoint) {
      const graphWrap = this.$refs.graphWrap
      if (!graphWrap) return
      const scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, nextScale))
      const rect = graphWrap.getBoundingClientRect()
      const focusX = clientPoint
        ? clientPoint.clientX - rect.left
        : graphWrap.clientWidth / 2
      const focusY = clientPoint
        ? clientPoint.clientY - rect.top
        : graphWrap.clientHeight / 2
      const contentX = (focusX - this.viewport.x) / this.viewport.scale
      const contentY = (focusY - this.viewport.y) / this.viewport.scale
      this.viewport = {
        x: focusX - contentX * scale,
        y: focusY - contentY * scale,
        scale,
      }
    },
    zoomIn() {
      this.setZoom(this.viewport.scale + ZOOM_STEP)
    },
    zoomOut() {
      this.setZoom(this.viewport.scale - ZOOM_STEP)
    },
    onCanvasWheel(event) {
      if (!this.startNode) return
      event.preventDefault()
      const direction = event.deltaY > 0 ? -ZOOM_STEP : ZOOM_STEP
      this.setZoom(this.viewport.scale + direction, event)
    },
    beginCanvasPan(event) {
      if (!this.startNode || (event.button != null && event.button !== 0))
        return
      if (
        event.target &&
        event.target.closest &&
        event.target.closest('.graph-node, .graph-toolbar')
      )
        return
      if (event.currentTarget && event.currentTarget.setPointerCapture) {
        event.currentTarget.setPointerCapture(event.pointerId)
      }
      this.pointerInteraction = {
        type: 'pan',
        startX: event.clientX,
        startY: event.clientY,
        originX: this.viewport.x,
        originY: this.viewport.y,
      }
    },
    beginNodeDrag(event, instanceId) {
      if (event.button != null && event.button !== 0) return
      if (
        event.target &&
        event.target.closest &&
        event.target.closest('.node-label, .node-code')
      )
        return
      if (event.currentTarget && event.currentTarget.setPointerCapture) {
        event.currentTarget.setPointerCapture(event.pointerId)
      }
      const position = this.nodePosition(instanceId)
      this.pointerInteraction = {
        type: 'node',
        instanceId,
        startX: event.clientX,
        startY: event.clientY,
        originLeft: position.left,
        originTop: position.top,
      }
    },
    onPointerMove(event) {
      const interaction = this.pointerInteraction
      if (!interaction) return
      const deltaX = event.clientX - interaction.startX
      const deltaY = event.clientY - interaction.startY
      if (interaction.type === 'pan') {
        this.viewport = {
          ...this.viewport,
          x: interaction.originX + deltaX,
          y: interaction.originY + deltaY,
        }
        return
      }
      this.positionOverrides[interaction.instanceId] = {
        left: interaction.originLeft + deltaX / this.viewport.scale,
        top: interaction.originTop + deltaY / this.viewport.scale,
      }
    },
    endPointerInteraction() {
      this.pointerInteraction = null
    },
    nodePosition(instanceId) {
      return (
        this.positionOverrides[instanceId] ||
        this.mindMapLayout.positions[instanceId] || { left: 0, top: 0 }
      )
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
        this.mergeGraph(data)
        if (this.query.direction !== 'DOWNSTREAM') {
          this.upstreamRoots = this.buildBranches(data, 'UPSTREAM', 2)
          this.loadedDirections.UPSTREAM = true
        }
        if (this.query.direction !== 'UPSTREAM') {
          this.downstreamRoots = this.buildBranches(data, 'DOWNSTREAM', 2)
          this.loadedDirections.DOWNSTREAM = true
        }
        this.$nextTick(() => this.fitGraph())
      } catch (e) {
        this.resetGraph()
        this.$message.error('血缘图加载失败，请重试')
      } finally {
        this.loading = false
      }
    },
    async onDirectionChange(direction) {
      if (!this.query.nodeId || !this.startNode) return
      const required =
        direction === 'ALL' ? ['UPSTREAM', 'DOWNSTREAM'] : [direction]
      const missing = required.filter((item) => !this.loadedDirections[item])
      if (!missing.length) return
      this.loading = true
      try {
        for (const item of missing) {
          const res = await getLineageGraph({
            nodeType: this.query.nodeType,
            nodeId: this.query.nodeId,
            direction: item,
            maxDepth: 2,
          })
          const data = (res && res.data) || {}
          this.mergeGraph(data)
          if (!this.startNode) this.startNode = data.startNode || null
          if (item === 'UPSTREAM')
            this.upstreamRoots = this.buildBranches(data, item, 2)
          if (item === 'DOWNSTREAM')
            this.downstreamRoots = this.buildBranches(data, item, 2)
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
      return this.createBranches(data, direction, maxDepth, start.id, [
        start.id,
      ])
    },
    buildDirectChildren(parent, data) {
      return this.createBranches(
        data,
        parent.direction,
        1,
        parent.node.id,
        parent.path
      )
    },
    createBranches(data, direction, maxDepth, parentNodeId, parentPath) {
      const nodeMap = {}
      ;((data && data.nodes) || []).forEach((node) => {
        if (node && node.id) nodeMap[node.id] = node
      })
      if (data && data.startNode && data.startNode.id) {
        nodeMap[data.startNode.id] = data.startNode
      }
      const adjacency = {}
      ;((data && data.edges) || []).forEach((edge) => {
        const parentId = direction === 'UPSTREAM' ? edge.to : edge.from
        const childId = direction === 'UPSTREAM' ? edge.from : edge.to
        if (!adjacency[parentId]) adjacency[parentId] = []
        adjacency[parentId].push({ childId, edge })
      })
      const build = (nodeId, path, depth) => {
        if (depth > maxDepth) return []
        return (adjacency[nodeId] || [])
          .map((item) => {
            const node = nodeMap[item.childId]
            if (!node) return null
            const cycle = path.includes(node.id)
            const nextPath = path.concat(node.id)
            const hasMore =
              !cycle &&
              Boolean(
                direction === 'UPSTREAM' ? node.hasUpstream : node.hasDownstream
              )
            const children =
              cycle || depth >= maxDepth
                ? []
                : build(node.id, nextPath, depth + 1)
            return {
              instanceId: node.id === this.startNode?.id ? 'CURRENT' : node.id,
              node,
              direction,
              relationLabel: item.edge.label || '',
              path: nextPath,
              children,
              expanded: children.length > 0,
              loaded: cycle || depth < maxDepth || !hasMore,
              loading: false,
              hasMore,
              cycle,
            }
          })
          .filter(Boolean)
      }
      return build(parentNodeId, parentPath, 1)
    },
    async toggleBranch(branch) {
      if (branch?.objectGroup) {
        this.toggleObject(branch.node.id)
        return
      }
      if (!branch || branch.loading || branch.cycle) return
      const matching = []
      const collect = branches => branches.forEach(item => {
        if (!item.cycle && item.node.id === branch.node.id && item.direction === branch.direction) matching.push(item)
        collect(item.children)
      })
      collect([...this.upstreamRoots, ...this.downstreamRoots])
      if (branch.expanded) {
        matching.forEach(item => { item.expanded = false })
        return
      }
      if (matching.every(item => item.loaded)) {
        matching.forEach(item => { item.expanded = item.children.length > 0 })
        return
      }
      matching.forEach(item => { item.loading = true })
      try {
        const res = await getLineageGraph({
          nodeType: branch.node.type,
          nodeId: branch.node.refId,
          direction: branch.direction,
          maxDepth: 1,
        })
        this.mergeGraph((res && res.data) || {})
        matching.forEach(item => {
          item.children = this.buildDirectChildren(item, (res && res.data) || {})
          item.loaded = true
          item.hasMore = item.children.length > 0
          item.expanded = item.children.length > 0
        })
      } catch (e) {
        this.$message.error('血缘分支加载失败，请重试')
      } finally {
        matching.forEach(item => { item.loading = false })
      }
    },
    canToggle(branch) {
      if (branch?.loaded && branch.node.dataObject && branch.direction === 'UPSTREAM' &&
        branch.children.every(child => child.node.id === branch.node.dataObject.id)) return false
      return Boolean(
        branch && (branch.objectGroup || (!branch.cycle && (branch.hasMore || branch.children.length)))
      )
    },
    branchStyle(item) {
      const pos = this.nodePosition(item.branch.instanceId)
      return {
        left: pos.left + 'px',
        top: pos.top + 'px',
        borderColor: this.nodeColor(item.branch.node.type),
        boxShadow: 'inset 4px 0 0 ' + this.nodeColor(item.branch.node.type),
      }
    },
    nodeColor(type) {
      return (
        {
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
        }[type] || '#64748B'
      )
    },
    nodeTypeLabel(type) {
      const found = this.nodeTypeOptions.find((item) => item.value === type)
      if (found) return found.label
      if (type === 'DATA_FIELD') return '数据字段'
      return type
    },
  },
}
</script>

<style lang="scss" scoped>
.lineage-page {
  &.is-embedded {
    padding: 0;

    .graph-wrap {
      min-height: 360px;
    }
  }

  .module-hint {
    background: var(--tianshu-bg-soft);
    border: 1px solid var(--tianshu-border-subtle);
    border-radius: 4px;
    padding: 12px 16px;
    margin-bottom: 16px;
    display: flex;
    align-items: center;
    gap: 12px;
  }
  .hint-title {
    color: var(--tianshu-info-text);
    font-weight: 700;
    white-space: nowrap;
  }
  .hint-text {
    color: var(--tianshu-text-tertiary);
  }
  .usage-guide {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 12px;
    margin-bottom: 16px;
  }
  .guide-item {
    border: 1px solid var(--tianshu-border-subtle);
    border-radius: 4px;
    padding: 12px;
    background: var(--tianshu-bg-surface);
  }
  .guide-title {
    color: var(--tianshu-text-primary);
    font-weight: 700;
    margin-bottom: 4px;
  }
  .guide-text {
    color: var(--tianshu-text-tertiary);
    font-size: 12px;
    line-height: 1.6;
  }
  .query-panel {
    background: var(--tianshu-bg-surface);
    border: 1px solid var(--tianshu-border-subtle);
    border-radius: 4px;
    padding: 12px 12px 0;
    margin-bottom: 12px;
  }
  .legend-row {
    display: flex;
    gap: 12px;
    flex-wrap: wrap;
    color: var(--tianshu-text-secondary);
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
    background-color: var(--tianshu-bg-soft);
    background-image: radial-gradient(
      var(--tianshu-border-strong) 0.8px,
      transparent 0.8px
    );
    background-size: 16px 16px;
    border: 1px solid var(--tianshu-border-subtle);
    border-radius: 4px;
    min-height: 440px;
    overflow: hidden;
    position: relative;
    touch-action: none;
    cursor: grab;
    user-select: none;
  }
  .graph-wrap.is-interacting {
    cursor: grabbing;
  }
  .empty-graph {
    color: var(--tianshu-text-tertiary);
    text-align: center;
    padding: 128px 0;
  }
  .graph-canvas {
    position: relative;
    transform-origin: 0 0;
    will-change: transform;
  }
  .graph-toolbar {
    position: absolute;
    top: 12px;
    right: 12px;
    display: flex;
    align-items: center;
    gap: 4px;
    padding: 4px;
    border: 1px solid var(--tianshu-border-subtle);
    border-radius: 6px;
    background: var(--tianshu-bg-surface);
    box-shadow: var(--tianshu-shadow-medium);
    z-index: 6;
  }
  .graph-toolbar button {
    min-width: 30px;
    height: 30px;
    padding: 0 8px;
    border: 1px solid transparent;
    border-radius: 4px;
    color: var(--tianshu-text-primary);
    background: transparent;
    cursor: pointer;
  }
  .graph-toolbar button:hover,
  .graph-toolbar button:focus-visible {
    color: var(--el-color-primary);
    border-color: var(--el-color-primary-light-5);
    background: var(--el-color-primary-light-9);
    outline: none;
  }
  .graph-toolbar .best-layout-button {
    margin-left: 4px;
    color: var(--tianshu-brand-foreground);
    background: var(--el-color-primary);
  }
  .graph-toolbar .best-layout-button:hover,
  .graph-toolbar .best-layout-button:focus-visible {
    color: var(--tianshu-brand-foreground);
    border-color: var(--el-color-primary-dark-2);
    background: var(--el-color-primary-dark-2);
  }
  .zoom-percent {
    min-width: 44px;
    color: var(--tianshu-text-secondary);
    font-size: 12px;
    text-align: center;
  }
  .side-caption {
    position: absolute;
    top: 16px;
    color: var(--tianshu-text-tertiary);
    font-size: 12px;
    font-weight: 700;
    letter-spacing: 0.08em;
    z-index: 2;
  }
  .side-caption.is-upstream {
    left: 24px;
  }
  .side-caption.is-current {
    left: 50%;
    transform: translateX(-50%);
  }
  .side-caption.is-downstream {
    right: 24px;
  }
  .edge-layer {
    position: absolute;
    left: 0;
    top: 0;
    // 节点可拖出初始布局范围；连线只由外层画布裁剪。
    overflow: visible;
    pointer-events: none;
  }
  .edge-path {
    fill: none;
    stroke: var(--tianshu-border-strong);
    stroke-width: 1.5;
  }
  .edge-label {
    fill: var(--tianshu-text-secondary);
    font-size: 12px;
    text-anchor: middle;
    paint-order: stroke;
    stroke: var(--tianshu-bg-soft);
    stroke-width: 4px;
  }
  .graph-node {
    position: absolute;
    width: 200px;
    height: 88px;
    background: var(--tianshu-bg-surface);
    border: 1px solid var(--tianshu-border);
    border-radius: 6px;
    padding: 12px;
    box-sizing: border-box;
    cursor: move;
    z-index: 3;
  }
  .branch-node {
    box-shadow: var(--tianshu-shadow-small);
  }
  .current-node {
    background: var(--tianshu-warning-bg);
    border-width: 2px;
    box-shadow: var(--tianshu-shadow-medium);
  }
  .graph-node.is-cycle {
    background: var(--tianshu-warning-bg);
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
    color: var(--tianshu-warning-text);
    background: var(--tianshu-warning-bg);
  }
  .cycle-badge {
    color: var(--tianshu-warning-text);
    background: var(--tianshu-warning-bg);
  }
  .node-label {
    color: var(--tianshu-text-primary);
    font-weight: 700;
    cursor: text;
    user-select: text;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .node-code {
    color: var(--tianshu-text-tertiary);
    cursor: text;
    font-family: Menlo, Monaco, Consolas, monospace;
    font-size: 12px;
    margin-top: 4px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    user-select: text;
  }
  .branch-toggle {
    position: absolute;
    top: 50%;
    width: 26px;
    height: 26px;
    margin-top: -13px;
    padding: 0;
    border: 1px solid var(--tianshu-border);
    border-radius: 50%;
    color: var(--tianshu-text-secondary);
    background: var(--tianshu-bg-surface);
    box-shadow: var(--tianshu-shadow-small);
    cursor: pointer;
    z-index: 4;
  }
  .branch-toggle:hover,
  .branch-toggle:focus {
    color: var(--el-color-primary);
    border-color: var(--el-color-primary);
    outline: none;
  }
  .branch-node.is-upstream .branch-toggle {
    left: -13px;
  }
  .branch-node.is-downstream .branch-toggle {
    right: -13px;
  }
  .object-toggle {
    right: -13px;
  }
  @media (max-width: 1200px) {
    .usage-guide {
      grid-template-columns: repeat(1, minmax(0, 1fr));
    }
  }
}
</style>
