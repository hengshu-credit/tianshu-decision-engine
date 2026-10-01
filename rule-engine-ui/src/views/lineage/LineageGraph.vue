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

    <div class="lineage-canvas-header">
      <div class="legend-row">
        <span
          v-for="item in nodeTypeOptions"
          :key="item.value"
          class="legend-item"
        >
          <i :style="{ background: nodeColor(item.value) }" />{{ item.label }}
        </span>
      </div>
      <div v-if="startNode" class="graph-toolbar" @pointerdown.stop>
        <el-button size="small" :icon="ElIconMagicStick" @click="resetToBestLayout">一键美化</el-button>
        <el-button size="small" :icon="ElIconMapLocation" :class="{ 'is-tool-active': miniMapVisible }" @click="toggleMiniMap">小地图</el-button>
        <el-button-group size="small">
          <el-button :icon="ElIconZoomIn" aria-label="放大血缘图" title="放大" @click="zoomIn" />
          <el-button :icon="ElIconZoomOut" aria-label="缩小血缘图" title="缩小" @click="zoomOut" />
          <el-button :icon="ElIconRank" aria-label="适配血缘图" title="适配画布" @click="fitGraph" />
        </el-button-group>
        <span class="zoom-percent">{{ zoomPercent }}%</span>
        <graph-designer-navigator
          v-model:target="graphNavigationTarget"
          :options="graphNavigationOptions"
          :issues="[]"
          :show-mini-map="false"
          :show-issues="false"
          @search="searchGraphElements"
          @locate="locateGraphNavigationItem"
        />
        <el-select v-model="globalEdgeLineType" size="small" style="width: 88px" aria-label="连线类型" @change="onGlobalEdgeLineTypeChange">
          <el-option label="折线" value="polyline" />
          <el-option label="直线" value="line" />
          <el-option label="弧线" value="bezier" />
        </el-select>
        <el-button size="small" :icon="ElIconRefreshLeft" aria-label="重置血缘图" @click="resetView">重置</el-button>
      </div>
    </div>

    <div class="lineage-graph-layout">
      <transition name="panel-slide">
        <aside v-if="selectedNode" class="lineage-node-panel" data-testid="lineage-node-info-panel">
          <div class="lineage-node-panel__header">
            <span>节点信息</span>
            <button type="button" aria-label="关闭节点信息" @click="closeNodePanel">×</button>
          </div>
          <div class="lineage-node-panel__type" :style="{ color: nodeColor(selectedNode.type) }">
            {{ nodeTypeLabel(selectedNode.type) }}
          </div>
          <dl class="lineage-node-panel__details">
            <div><dt>名称</dt><dd>{{ selectedNode.label || selectedNode.code || '-' }}</dd></div>
            <div><dt>编码</dt><dd class="is-code">{{ selectedNode.code || '-' }}</dd></div>
            <div><dt>节点 ID</dt><dd class="is-code">{{ selectedNode.id || '-' }}</dd></div>
            <div><dt>业务 ID</dt><dd>{{ selectedNode.refId ?? '-' }}</dd></div>
            <div><dt>关系方向</dt><dd>{{ selectedNodeMeta.direction }}</dd></div>
            <div><dt>上游节点</dt><dd>{{ selectedNodeMeta.upstreamCount }}</dd></div>
            <div><dt>下游节点</dt><dd>{{ selectedNodeMeta.downstreamCount }}</dd></div>
            <div v-if="selectedNode.dataObject"><dt>所属对象</dt><dd>{{ selectedNode.dataObject.label || selectedNode.dataObject.code }}</dd></div>
            <div v-if="selectedNode.projectCode"><dt>所属项目</dt><dd>{{ selectedNode.projectCode }}</dd></div>
          </dl>
          <div v-if="selectedNode.description || selectedNode.nodeDesc" class="lineage-node-panel__section">
            <div class="lineage-node-panel__section-title">节点说明</div>
            <p>{{ selectedNode.description || selectedNode.nodeDesc }}</p>
          </div>
          <div v-if="selectedNodeMeta.relatedLabels.length" class="lineage-node-panel__section">
            <div class="lineage-node-panel__section-title">直接关联</div>
            <ul class="lineage-node-panel__related">
              <li v-for="item in selectedNodeMeta.relatedLabels" :key="item.id">
                <span>{{ item.direction === 'UPSTREAM' ? '上游' : '下游' }}</span>{{ item.label }}
              </li>
            </ul>
          </div>
          <div class="lineage-node-panel__hint">点击画布空白处可关闭面板</div>
        </aside>
      </transition>

      <div
        ref="graphWrap"
        class="graph-wrap"
        :style="{ height: graphHeight + 'px' }"
        v-loading="loading"
      >
        <div v-if="!startNode" class="empty-graph">请选择起点后生成血缘图</div>
        <div ref="graphContainer" class="graph-canvas" :class="{ 'is-hidden': !startNode }" aria-label="血缘关系图" />
      </div>
    </div>
  </div>
</template>

<script>
import { markRaw } from 'vue'
import {
  Share as ElIconShare,
  MagicStick as ElIconMagicStick,
  MapLocation as ElIconMapLocation,
  ZoomIn as ElIconZoomIn,
  ZoomOut as ElIconZoomOut,
  Rank as ElIconRank,
  RefreshLeft as ElIconRefreshLeft,
} from '@element-plus/icons-vue'
import { createGraphCanvas } from '@/components/flow/graphCanvas'
import { MiniMap } from '@logicflow/extension'
import { getLineageGraph, listLineageOptions } from '@/api/lineage'
import { lineageLayout } from '@/utils/lineageLayers'
import { LINEAGE_NODE_COLORS, LINEAGE_NODE_WIDTH as CARD_W, LINEAGE_NODE_HEIGHT as CARD_H } from '@/components/flow/nodes'
import GraphDesignerNavigator from '@/components/flow/GraphDesignerNavigator.vue'

const LEVEL_STEP = 320
const ROW_STEP = 144
const PADDING_X = 48
const PADDING_Y = 48
const MIN_CANVAS_W = 960
const MIN_CANVAS_H = 440
const MIN_SCALE = 0.4
const MAX_SCALE = 2

export default {
  props: {
    embedded: { type: Boolean, default: false },
    initialNodeType: { type: String, default: '' },
    initialNodeId: { type: [String, Number], default: '' },
    initialDirection: { type: String, default: 'ALL' },
    initialGraph: { type: Object, default: null },
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
      expandedFields: {},
      loadedHierarchy: {},
      hierarchyLoading: {},
      graphHeight: 440,
      viewport: { x: 0, y: 0, scale: 1 },
      positionOverrides: {},
      skipGraphRender: false,
      pointerInteraction: null,
      lf: null,
      selectedNodeId: null,
      miniMapVisible: true,
      globalEdgeLineType: 'polyline',
      graphNavigationTarget: '',
      graphNavigationKeyword: '',
      ElIconMagicStick: markRaw(ElIconMagicStick),
      ElIconMapLocation: markRaw(ElIconMapLocation),
      ElIconZoomIn: markRaw(ElIconZoomIn),
      ElIconZoomOut: markRaw(ElIconZoomOut),
      ElIconRank: markRaw(ElIconRank),
      ElIconRefreshLeft: markRaw(ElIconRefreshLeft),
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
  components: { GraphDesignerNavigator },
  watch: {
    initialGraph: {
      deep: true,
      handler(graph) {
        if (graph) this.loadInitialGraph(graph)
      },
    },
    startNode: {
      flush: 'post',
      handler(node) {
        const graphWrap = this.$refs.graphWrap
        if (!graphWrap) return
        graphWrap.removeEventListener('wheel', this.onCanvasWheel)
        if (node) graphWrap.addEventListener('wheel', this.onCanvasWheel, { passive: false })
      },
    },
    graphData: {
      flush: 'post',
      handler() {
        if (this.skipGraphRender) {
          this.skipGraphRender = false
          return
        }
        this.renderLineageGraph()
      },
    },
    selectedNodeId() {
      this.$nextTick(() => {
        this.resizeCanvas()
        this.fitGraph()
      })
    },
  },
  beforeUnmount() {
    this.$refs.graphWrap?.removeEventListener('wheel', this.onCanvasWheel)
    this.resizeObserver?.disconnect()
    window.removeEventListener('resize', this.resizeGraph)
    this.lf?.destroy()
    this.lf = null
  },
  mounted() {
    this.initLogicFlow()
    this.resizeGraph()
    window.addEventListener('resize', this.resizeGraph)
    if (typeof ResizeObserver !== 'undefined') {
      this.resizeObserver = new ResizeObserver(() => {
        this.resizeGraph()
        this.resizeCanvas()
      })
      this.resizeObserver.observe(this.$refs.graphWrap)
    }
    this.renderLineageGraph()
  },
  created() {
    if (this.initialNodeType) this.query.nodeType = this.initialNodeType
    if (this.initialNodeId) this.query.nodeId = this.initialNodeId
    if (this.initialDirection) this.query.direction = this.initialDirection
    if (this.initialGraph) {
      this.loadInitialGraph(this.initialGraph)
    } else if (this.embedded && this.query.nodeId) {
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
    fieldChildren() {
      const children = new Map()
      Object.values(this.knownNodes).forEach(node => {
        if (!node.dataObject) return
        const parentId = node.parentNodeId || node.dataObject.id
        if (!children.has(parentId)) children.set(parentId, [])
        children.get(parentId).push(node)
      })
      return children
    },
    visibleGraph() {
      const nodes = new Map()
      const edges = new Map()
      const currentId = this.startNode?.id
      const canonicalId = id => id === currentId ? 'CURRENT' : id
      const objects = new Map()
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
        const object = branch.node.dataObject || (branch.node.type === 'DATA_OBJECT' ? branch.node : null)
        if (object) {
          if (!objects.has(object.id)) objects.set(object.id, { ...item, node: object })
        } else {
          addNode(item)
        }
      })
      if (this.startNode?.type === 'DATA_OBJECT') {
        objects.set(currentId, { node: this.startNode, side: this.query.direction === 'UPSTREAM' ? 'UPSTREAM' : 'DOWNSTREAM' })
      }
      // 对象保持靠近依赖节点；字段按父子关系向上游左侧或下游右侧逐级展开。
      objects.forEach(item => {
        const addHierarchy = (node, parentId, path) => {
          if (path.has(node.id)) return
          const objectGroup = node.type === 'DATA_OBJECT'
          const expanded = Boolean((objectGroup ? this.expandedObjects : this.expandedFields)[node.id])
          addNode({ ...item, branch: {
            node, instanceId: canonicalId(node.id), objectGroup, fieldGroup: !objectGroup,
            expanded, loading: this.hierarchyLoading[node.id], children: [],
            hasMore: node.hasFieldChildren || (this.fieldChildren.get(node.id) || []).length > 0,
          } })
          if (parentId) {
            const upstream = item.side === 'UPSTREAM'
            addEdge(canonicalId(upstream ? node.id : parentId), canonicalId(upstream ? parentId : node.id),
              upstream ? (parentId === item.node.id ? '所属对象' : '所属字段') : '包含字段')
          }
          if (expanded || node.id === currentId && !objectGroup) {
            const nextPath = new Set([...path, node.id])
            ;(this.fieldChildren.get(node.id) || []).forEach(child => addHierarchy(child, node.id, nextPath))
          }
        }
        addHierarchy(this.knownNodes[item.node.id] || item.node, null, new Set())
      })
      const displayId = id => {
        const visited = new Set()
        let node = this.knownNodes[id]
        while (node?.dataObject && id !== currentId && !nodes.has(id) && !visited.has(id)) {
          visited.add(id)
          id = node.parentNodeId || node.dataObject.id
          node = this.knownNodes[id]
        }
        return canonicalId(id)
      }
      const objectForField = id => id === currentId ? undefined : objects.get(this.knownNodes[id]?.dataObject?.id)
      // 收起分支不删除共享依赖；靠近当前节点的一端始终汇总到对象，避免字段绕过对象直连规则。
      this.knownEdges.forEach(edge => {
        if (edge.label === '包含字段') return
        const sourceObject = objectForField(edge.from)
        const targetObject = objectForField(edge.to)
        const sameObject = sourceObject && sourceObject.node.id === targetObject?.node.id
        const fromId = sourceObject?.side === 'UPSTREAM' && !sameObject
          ? canonicalId(sourceObject.node.id) : displayId(edge.from)
        const toId = targetObject?.side === 'DOWNSTREAM' && !sameObject
          ? canonicalId(targetObject.node.id) : displayId(edge.to)
        addEdge(fromId, toId, edge.label || '')
      })
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
      const layout = lineageLayout(nodeIds, this.visibleGraph.edges, 'CURRENT')
      const points = [...layout.positions.values(), ...[...layout.routes.values()].flat()]
      const minLayer = Math.min(...points.map(point => point.layer))
      const maxLayer = Math.max(...points.map(point => point.layer))
      const contentWidth = CARD_W + (maxLayer - minLayer) * LEVEL_STEP
      const width = Math.max(
        MIN_CANVAS_W,
        PADDING_X * 2 + contentWidth
      )
      const minRow = Math.min(...points.map(point => point.row))
      const maxRow = Math.max(...points.map(point => point.row))
      const contentHeight = CARD_H + (maxRow - minRow) * ROW_STEP
      const height = Math.max(MIN_CANVAS_H, PADDING_Y * 2 + contentHeight)
      const position = point => ({
        left: (width - contentWidth) / 2 + (point.layer - minLayer) * LEVEL_STEP,
        top: (height - contentHeight) / 2 + (point.row - minRow) * ROW_STEP,
      })
      const positions = Object.fromEntries([...layout.positions].map(([id, point]) => [id, position(point)]))
      const routes = Object.fromEntries([...layout.routes].map(([key, route]) => [key, route.map(position)]))
      return { width, height, positions, routes, currentCenterX: positions.CURRENT.left + CARD_W / 2 }
    },
    canvasSize() {
      return { width: this.mindMapLayout.width, height: this.mindMapLayout.height }
    },
    viewportTransformStyle() {
      return { transform: `translate(${this.viewport.x}px, ${this.viewport.y}px) scale(${this.viewport.scale})` }
    },
    zoomPercent() {
      return Math.round(this.viewport.scale * 100)
    },
    edgeLines() {
      return this.visibleGraph.edges.map(edge => {
        const from = this.nodePosition(edge.fromId)
        const to = this.nodePosition(edge.toId)
        const forward = from.left < to.left
        const x1 = from.left + (forward ? CARD_W : 0)
        const y1 = from.top + CARD_H / 2
        const x2 = to.left + (forward ? 0 : CARD_W)
        const y2 = to.top + CARD_H / 2
        const route = this.mindMapLayout.routes[edge.key] || []
        const points = [{ x: x1, y: y1 }, ...route.map(point => ({ x: point.left + CARD_W / 2, y: point.top + CARD_H / 2 })), { x: x2, y: y2 }]
        const path = points.slice(1).reduce((value, point, index) => {
          const previous = points[index]
          const controlX = (previous.x + point.x) / 2
          return `${value} C ${controlX} ${previous.y}, ${controlX} ${point.y}, ${point.x} ${point.y}`
        }, `M ${x1} ${y1}`)
        return { ...edge, path, labelX: (x1 + x2) / 2, labelY: (y1 + y2) / 2 - 8 }
      })
    },
    selectedNode() {
      if (!this.selectedNodeId) return null
      return this.selectedNodeId === this.startNode?.id
        ? this.startNode : this.knownNodes[this.selectedNodeId] || null
    },
    selectedNodeMeta() {
      const node = this.selectedNode
      if (!node) return { direction: '-', upstreamCount: 0, downstreamCount: 0, relatedLabels: [] }
      const incoming = this.knownEdges.filter(edge => edge.to === node.id)
      const outgoing = this.knownEdges.filter(edge => edge.from === node.id)
      const branch = this.findBranch(node.id)
      const related = [
        ...incoming.map(edge => ({ id: `up-${edge.from}`, direction: 'UPSTREAM', label: this.knownNodes[edge.from]?.label || this.knownNodes[edge.from]?.code || edge.from })),
        ...outgoing.map(edge => ({ id: `down-${edge.to}`, direction: 'DOWNSTREAM', label: this.knownNodes[edge.to]?.label || this.knownNodes[edge.to]?.code || edge.to })),
      ]
      return {
        direction: node.id === this.startNode?.id ? '当前节点' : branch?.direction === 'UPSTREAM' ? '上游' : '下游',
        upstreamCount: incoming.length,
        downstreamCount: outgoing.length,
        relatedLabels: related.slice(0, 8),
      }
    },
    graphNavigationOptions() {
      const keyword = this.graphNavigationKeyword.trim().toLowerCase()
      const nodes = this.graphData.nodes.map(node => ({
        key: node.id,
        kind: 'NODE',
        label: `${node.properties.nodeLabel || node.properties.nodeCode} (${node.properties.nodeCode || '-'})`,
        refType: node.properties.nodeType,
        refId: node.properties.nodeId,
      }))
      if (!keyword) return nodes
      return nodes.filter(item => `${item.label} ${item.refType} ${item.refId}`.toLowerCase().includes(keyword))
    },
    graphData() {
      if (!this.startNode) return { nodes: [], edges: [] }
      const nodes = [
        this.toGraphNode('CURRENT', this.startNode, {
          current: true,
          toggleable: this.startNode.type === 'DATA_OBJECT',
          toggleLabel: this.expandedObjects[this.startNode.id] ? '收起字段' : '展开字段',
          expanded: this.expandedObjects[this.startNode.id],
          loading: this.hierarchyLoading[this.startNode.id],
        }),
        ...this.visibleBranches.map(({ branch, side }) => this.toGraphNode(branch.instanceId, branch.node, {
          cycle: branch.cycle,
          side,
          toggleable: this.canToggle(branch),
          toggleLabel: branch.objectGroup
            ? (branch.expanded ? '收起字段' : '展开字段')
            : (branch.expanded ? '收起节点' : '展开节点'),
          expanded: branch.expanded,
          loading: branch.loading,
        })),
      ]
      const edges = this.visibleGraph.edges.map(edge => {
        const source = this.nodePosition(edge.fromId)
        const target = this.nodePosition(edge.toId)
        const forward = source.left < target.left
        const route = this.mindMapLayout.routes[edge.key] || []
        const startPoint = { x: source.left + CARD_W, y: source.top + CARD_H / 2 }
        const endPoint = { x: target.left, y: target.top + CARD_H / 2 }
        // 共享依赖的跨层通道仅提供布局点，绘制、拖动后的重算和箭头均交给 LogicFlow。
        const geometry = {}
        if (forward && route.length && !this.positionOverrides[edge.fromId] && !this.positionOverrides[edge.toId]) {
          const pointsList = [startPoint]
          const waypoints = [...route.map(point => ({ x: point.left + CARD_W / 2, y: point.top + CARD_H / 2 })), endPoint]
          waypoints.forEach(point => {
            const previous = pointsList.at(-1)
            const midX = (previous.x + point.x) / 2
            pointsList.push({ x: midX, y: previous.y }, { x: midX, y: point.y }, point)
          })
          Object.assign(geometry, { startPoint, endPoint, pointsList })
        }
        return {
          id: edge.key,
          type: this.globalEdgeLineType,
          sourceNodeId: edge.fromId,
          targetNodeId: edge.toId,
          ...(forward ? { sourceAnchorId: `${edge.fromId}_1`, targetAnchorId: `${edge.toId}_3` } : {}),
          text: edge.label || '',
          ...geometry,
        }
      })
      return { nodes, edges }
    },
  },
  methods: {
    initLogicFlow() {
      this.lf = createGraphCanvas({
        container: this.$refs.graphContainer,
        plugins: [MiniMap],
        pluginsOptions: {
          miniMap: {
            width: 180,
            height: 120,
            showEdge: true,
            isShowHeader: false,
            isShowCloseIcon: false,
            rightPosition: 16,
            bottomPosition: 16,
          },
        },
        edgeType: 'polyline',
        keyboard: { enabled: false },
        history: false,
        adjustEdge: false,
        adjustEdgeStartAndEnd: false,
        adjustNodePosition: true,
        stopMoveGraph: false,
        stopZoomGraph: true,
        hideAnchors: true,
        nodeTextEdit: false,
        edgeTextEdit: false,
        stopScrollGraph: true,
        snapGrid: true,
      })
      if (!this.lf) return
      this.lf.setZoomMiniSize(MIN_SCALE)
      this.lf.setZoomMaxSize(MAX_SCALE)
      this.lf.on('node:click', ({ data }) => {
        this.selectedNodeId = data.properties.nodeId
      })
      this.lf.on('blank:click', this.closeNodePanel)
      this.lf.on('graph:transform', this.updateZoom)
      this.lf.on('graph:rendered', this.onGraphRendered)
      this.lf.on('node:drop', ({ data }) => {
        const left = data.x - CARD_W / 2
        const top = data.y - CARD_H / 2
        this.skipGraphRender = true
        this.positionOverrides = {
          ...this.positionOverrides,
          [data.id]: { left, top },
        }
        const element = this.$refs.graphContainer?.querySelector(`[data-lineage-model-id="${data.id}"]`)
        if (element) {
          element.style.left = `${left}px`
          element.style.top = `${top}px`
          element.style.transform = `translate(${-left}px, ${-top}px)`
        }
      })
      this.lf.on('lineage:toggle', ({ data }) => {
        if (data.id === 'CURRENT') {
          this.toggleObject(this.startNode.id)
          return
        }
        const branch = this.visibleBranches.find(item => item.branch.instanceId === data.id)?.branch
        if (branch) this.toggleBranch(branch)
      })
    },
    onGraphRendered() {
      const miniMap = this.lf?.extension?.miniMap
      if (!miniMap) return
      if (this.miniMapVisible) miniMap.show()
      else miniMap.hide()
    },
    toggleMiniMap() {
      this.miniMapVisible = !this.miniMapVisible
      this.onGraphRendered()
    },
    resetView() {
      this.positionOverrides = {}
      this.viewport = { x: 0, y: 0, scale: 1 }
      this.lf?.resetZoom()
      this.blurToolbarFocus()
      this.$nextTick(() => this.fitGraph())
    },
    onGlobalEdgeLineTypeChange() {
      this.renderLineageGraph()
    },
    searchGraphElements(keyword) {
      this.graphNavigationKeyword = keyword || ''
    },
    locateGraphNavigationItem(key) {
      if (!key) return
      const node = this.graphData.nodes.find(item => item.id === key)
      if (!node) return
      this.selectedNodeId = node.properties.nodeId
      this.lf?.selectElementById(node.id)
    },
    closeNodePanel() {
      this.selectedNodeId = null
      this.lf?.clearSelectElements()
    },
    resizeCanvas() {
      if (!this.lf) return
      const container = this.$refs.graphContainer
      if (container?.clientWidth && container.clientHeight) this.lf.resize(container.clientWidth, container.clientHeight)
    },
    renderLineageGraph() {
      if (!this.lf) return
      const data = this.graphData
      // LogicFlow 会规范化输入文本对象，避免反写 Vue 的 computed 结果。
      this.lf.render(JSON.parse(JSON.stringify(data)))
      this.$nextTick(() => {
        this.$refs.graphContainer?.querySelectorAll('.lf-edge').forEach(edge => {
          edge.querySelector('path, polyline')?.classList.add('edge-path')
        })
        this.bindLineageNodeDrag()
      })
      const selected = data.nodes.find(node => node.properties.nodeId === this.selectedNodeId)
      if (selected) this.lf.selectElementById(selected.id)
      else this.selectedNodeId = null
      this.resizeCanvas()
      this.fitGraph()
    },
    bindLineageNodeDrag() {
      const container = this.$refs.graphContainer
      if (!container || !this.lf) return
      container.querySelectorAll('.lineage-lf-node').forEach(element => {
        if (element.dataset.lineageDragBound) return
        element.dataset.lineageDragBound = 'true'
        element.addEventListener('mousedown', event => {
          if (event.button !== 0 || event.target?.closest?.('button')) return
          const modelId = element.getAttribute('data-lineage-model-id')
          const model = modelId && this.lf.getNodeModelById(modelId)
          if (!model) return
          event.preventDefault()
          event.stopPropagation()
          const startX = event.clientX
          const startY = event.clientY
          const originX = model.x
          const originY = model.y
          const onMove = moveEvent => {
            const scale = this.lf.getTransform().SCALE_X || 1
            const x = Math.round((originX + (moveEvent.clientX - startX) / scale) / 20) * 20
            const y = Math.round((originY + (moveEvent.clientY - startY) / scale) / 20) * 20
            this.lf.graphModel.moveNode2Coordinate(modelId, x, y)
          }
          const onUp = () => {
            document.removeEventListener('mousemove', onMove)
            document.removeEventListener('mouseup', onUp)
            const current = this.lf.getNodeModelById(modelId)
            if (current) {
              this.positionOverrides = {
                ...this.positionOverrides,
                [modelId]: { left: current.x - CARD_W / 2, top: current.y - CARD_H / 2 },
              }
            }
          }
          document.addEventListener('mousemove', onMove)
          document.addEventListener('mouseup', onUp, { once: true })
        })
      })
    },
    toGraphNode(id, node, properties) {
      const position = this.nodePosition(id)
      return {
        id,
        type: 'lineage-node',
        x: position.left + CARD_W / 2,
        y: position.top + CARD_H / 2,
        properties: {
          nodeId: node.id,
          nodeType: node.type,
          nodeTypeLabel: this.nodeTypeLabel(node.type),
          nodeLabel: node.label || node.code,
          nodeCode: node.code,
          color: this.nodeColor(node.type),
          ...properties,
          toggleText: properties.loading ? '…' : properties.expanded ? '−' : '+',
        },
      }
    },
    mergeGraph(data) {
      ;[...(data.nodes || []), data.startNode].filter(Boolean).forEach(node => {
        ;[node.dataObject, ...(node.ancestorFields || []), node].filter(Boolean).forEach(item => {
          this.knownNodes[item.id] = { ...this.knownNodes[item.id], ...item }
        })
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
    async toggleObject(id) {
      await this.toggleHierarchy(id, this.expandedObjects)
    },
    async toggleHierarchy(id, expanded) {
      if (this.hierarchyLoading[id]) return
      const node = this.knownNodes[id]
      if (!expanded[id] && !this.fieldChildren.has(id) && !this.loadedHierarchy[id]) {
        this.hierarchyLoading[id] = true
        try {
          const res = await getLineageGraph({ nodeType: node.type, nodeId: node.refId, direction: 'DOWNSTREAM', maxDepth: 1 })
          this.mergeGraph(res?.data || {})
          this.loadedHierarchy[id] = true
        } catch (e) {
          this.$message.error('字段层级加载失败，请重试')
          return
        } finally {
          this.hierarchyLoading[id] = false
        }
      }
      expanded[id] = !expanded[id]
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
      this.expandedFields = {}
      this.loadedHierarchy = {}
      this.hierarchyLoading = {}
      this.positionOverrides = {}
      this.pointerInteraction = null
      this.viewport = { x: 0, y: 0, scale: 1 }
      this.selectedNodeId = null
    },
    loadInitialGraph(data) {
      if (!data || !data.startNode) return
      this.resetGraph()
      this.query.direction = 'ALL'
      this.startNode = data.startNode
      this.mergeGraph(data)
      this.upstreamRoots = this.buildBranches(data, 'UPSTREAM', Number.MAX_SAFE_INTEGER)
      this.downstreamRoots = this.buildBranches(data, 'DOWNSTREAM', Number.MAX_SAFE_INTEGER)
      this.loadedDirections = { UPSTREAM: true, DOWNSTREAM: true }
      this.$nextTick(() => this.fitGraph())
    },
    fitGraph() {
      if (!this.lf || !this.startNode) return
      this.lf.fitView(80, 80)
      if (this.lf.getTransform().SCALE_X > 1) {
        this.lf.zoom(1)
        this.lf.translateCenter()
      }
      this.updateZoom()
    },
    resetToBestLayout() {
      this.positionOverrides = {}
      this.lf?.resetZoom()
      this.blurToolbarFocus()
      this.$nextTick(() => {
        this.renderLineageGraph()
        this.fitGraph()
      })
    },
    updateZoom() {
      if (this.lf) {
        this.viewport = { ...this.viewport, scale: this.lf.getTransform().SCALE_X || this.viewport.scale }
      }
    },
    blurToolbarFocus() {
      const active = typeof document !== 'undefined' ? document.activeElement : null
      if (active?.closest?.('.graph-toolbar')) active.blur()
    },
    zoomIn() {
      this.setZoom(this.viewport.scale + 0.1)
    },
    zoomOut() {
      this.setZoom(this.viewport.scale - 0.1)
    },
    setZoom(nextScale, clientPoint) {
      const graphWrap = this.$refs.graphWrap
      if (!graphWrap) return
      const scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, nextScale))
      const rect = graphWrap.getBoundingClientRect()
      const focusX = clientPoint ? clientPoint.clientX - rect.left : graphWrap.clientWidth / 2
      const focusY = clientPoint ? clientPoint.clientY - rect.top : graphWrap.clientHeight / 2
      const contentX = (focusX - this.viewport.x) / this.viewport.scale
      const contentY = (focusY - this.viewport.y) / this.viewport.scale
      this.viewport = { x: focusX - contentX * scale, y: focusY - contentY * scale, scale }
      if (this.lf) {
        const point = this.getZoomPoint(clientPoint, graphWrap)
        this.lf.zoom(scale, point)
        this.updateZoom()
      }
    },
    getZoomPoint(clientPoint, graphWrap) {
      const graphModel = this.lf?.graphModel
      if (!graphModel?.getPointByClient) return undefined
      const container = this.$refs.graphContainer || graphWrap
      const rect = container.getBoundingClientRect()
      const x = clientPoint?.clientX ?? rect.left + (container.clientWidth || graphWrap.clientWidth) / 2
      const y = clientPoint?.clientY ?? rect.top + (container.clientHeight || graphWrap.clientHeight) / 2
      const point = graphModel.getPointByClient({ x, y })?.canvasOverlayPosition
      return point ? [point.x, point.y] : undefined
    },
    onCanvasWheel(event) {
      if (!this.startNode) return
      event.preventDefault()
      this.setZoom(this.viewport.scale + (event.deltaY > 0 ? -0.1 : 0.1), event)
    },
    beginCanvasPan(event) {
      if (!this.startNode || (event.button != null && event.button !== 0)) return
      if (event.target?.closest?.('.graph-toolbar, .lf-graph')) return
      this.pointerInteraction = { type: 'pan', startX: event.clientX, startY: event.clientY, originX: this.viewport.x, originY: this.viewport.y }
    },
    beginNodeDrag(event, instanceId) {
      if (event.button != null && event.button !== 0) return
      const position = this.nodePosition(instanceId)
      this.pointerInteraction = { type: 'node', instanceId, startX: event.clientX, startY: event.clientY, originLeft: position.left, originTop: position.top }
    },
    onPointerMove(event) {
      const interaction = this.pointerInteraction
      if (!interaction) return
      const deltaX = event.clientX - interaction.startX
      const deltaY = event.clientY - interaction.startY
      if (interaction.type === 'pan') {
        this.viewport = { ...this.viewport, x: interaction.originX + deltaX, y: interaction.originY + deltaY }
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
        await this.toggleObject(branch.node.id)
        return
      }
      if (branch?.fieldGroup) {
        await this.toggleHierarchy(branch.node.id, this.expandedFields)
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
      if (branch?.fieldGroup) return Boolean(branch.hasMore)
      return Boolean(
        branch && (branch.objectGroup || (!branch.cycle && (branch.hasMore || branch.children.length)))
      )
    },
    nodeColor(type) {
      return LINEAGE_NODE_COLORS[type] || '#64748B'
    },
    findBranch(nodeId) {
      const queue = [...this.upstreamRoots, ...this.downstreamRoots]
      while (queue.length) {
        const branch = queue.shift()
        if (branch.node?.id === nodeId) return branch
        queue.push(...(branch.children || []))
      }
      return null
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
    flex: 0 1 auto;
    gap: 12px;
    flex-wrap: wrap;
    align-items: center;
    justify-content: center;
    color: var(--tianshu-text-secondary);
    font-size: 12px;
    margin: 0;
  }
  .lineage-canvas-header {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: center;
    gap: 10px 16px;
    min-width: 0;
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
  .lineage-graph-layout {
    position: relative;
    min-width: 0;
  }
  .lineage-node-panel {
    position: absolute;
    top: 0;
    right: 0;
    bottom: 0;
    z-index: 8;
    max-height: none;
    overflow-y: auto;
    width: min(380px, 38vw);
    padding: 16px;
    box-sizing: border-box;
    background: var(--tianshu-bg-surface);
    border: 1px solid var(--tianshu-border-subtle);
    border-radius: 6px 0 0 6px;
    box-shadow: var(--tianshu-shadow-large, 0 12px 32px rgba(0, 0, 0, 0.24));
  }
  .lineage-node-panel__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding-bottom: 12px;
    border-bottom: 1px solid var(--tianshu-border-subtle);
    color: var(--tianshu-text-primary);
    font-size: 14px;
    font-weight: 700;
  }
  .lineage-node-panel__header button {
    width: 24px;
    height: 24px;
    border: 0;
    color: var(--tianshu-text-tertiary);
    background: transparent;
    cursor: pointer;
    font-size: 20px;
    line-height: 1;
  }
  .lineage-node-panel__header button:hover {
    color: var(--tianshu-text-primary);
  }
  .lineage-node-panel__type {
    margin: 16px 0 12px;
    font-size: 12px;
    font-weight: 700;
  }
  .lineage-node-panel__details {
    margin: 0;
  }
  .lineage-node-panel__details > div {
    display: grid;
    grid-template-columns: 56px minmax(0, 1fr);
    gap: 8px;
    padding: 8px 0;
    border-bottom: 1px solid var(--tianshu-border-subtle);
  }
  .lineage-node-panel__details dt {
    color: var(--tianshu-text-tertiary);
    font-size: 12px;
  }
  .lineage-node-panel__details dd {
    min-width: 0;
    margin: 0;
    overflow-wrap: anywhere;
    color: var(--tianshu-text-primary);
    font-size: 12px;
  }
  .lineage-node-panel__details dd.is-code {
    font-family: Menlo, Monaco, Consolas, monospace;
  }
  .lineage-node-panel__hint {
    margin-top: 16px;
    color: var(--tianshu-text-tertiary);
    font-size: 12px;
    line-height: 1.5;
  }
  .lineage-node-panel__section {
    margin-top: 16px;
    padding-top: 12px;
    border-top: 1px solid var(--tianshu-border-subtle);
  }
  .lineage-node-panel__section-title {
    margin-bottom: 8px;
    color: var(--tianshu-text-primary);
    font-size: 12px;
    font-weight: 700;
  }
  .lineage-node-panel__section p {
    margin: 0;
    color: var(--tianshu-text-secondary);
    font-size: 12px;
    line-height: 1.6;
    white-space: pre-wrap;
  }
  .lineage-node-panel__related {
    display: grid;
    gap: 6px;
    margin: 0;
    padding: 0;
    list-style: none;
  }
  .lineage-node-panel__related li {
    overflow: hidden;
    color: var(--tianshu-text-secondary);
    font-size: 12px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .lineage-node-panel__related span {
    display: inline-block;
    min-width: 42px;
    margin-right: 6px;
    color: var(--el-color-primary);
  }
  .graph-wrap {
    width: 100%;
    min-width: 0;
    background-color: var(--tianshu-bg-soft);
    border: 1px solid var(--tianshu-border-subtle);
    border-radius: 4px;
    min-height: 440px;
    overflow: hidden;
    position: relative;
    touch-action: none;
    cursor: grab;
    user-select: none;
  }
  .empty-graph {
    color: var(--tianshu-text-tertiary);
    text-align: center;
    padding: 128px 0;
  }
  .graph-canvas {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
  }
  .graph-canvas.is-hidden {
    visibility: hidden;
    pointer-events: none;
  }
  :deep(.lf-graph) {
    width: 100%;
    height: 100%;
  }
  .graph-toolbar {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 6px;
    padding: 4px;
    border: 1px solid var(--tianshu-border-subtle);
    border-radius: 6px;
    background: var(--tianshu-bg-surface);
    box-shadow: var(--tianshu-shadow-medium);
    z-index: 2;
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
  .graph-toolbar :deep(.el-button),
  .graph-toolbar :deep(.el-select .el-input__wrapper) {
    border-color: var(--tianshu-border);
    background: var(--tianshu-bg-surface);
    color: var(--tianshu-text-primary);
  }
  .graph-toolbar :deep(.el-button:hover),
  .graph-toolbar :deep(.el-button:focus-visible),
  .graph-toolbar :deep(.is-tool-active) {
    border-color: var(--el-color-primary);
    color: var(--el-color-primary);
    background: var(--tianshu-designer-accent-bg);
  }
  .graph-toolbar :deep(.el-button:focus:not(:focus-visible)) {
    border-color: var(--tianshu-border);
    color: var(--tianshu-text-primary);
    background: var(--tianshu-bg-surface);
    box-shadow: none;
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
  @media (max-width: 1200px) {
    .usage-guide {
      grid-template-columns: repeat(1, minmax(0, 1fr));
    }
  }
  @media (max-width: 800px) {
    .lineage-node-panel {
      right: 0;
      z-index: 8;
      width: min(380px, 88vw);
    }
  }

  .panel-slide-enter-active,
  .panel-slide-leave-active {
    transition: transform 0.2s ease, opacity 0.2s ease;
  }
  .panel-slide-enter-from,
  .panel-slide-leave-to {
    transform: translateX(100%);
    opacity: 0;
  }
}
</style>
