<template>
  <div class="uiue-list-page lineage-page">
    <div class="module-hint">
      <div class="hint-title">血缘分析</div>
      <div class="hint-text">从变量、规则、项目、API、数据库、名单或模型出发，查看上游依赖与下游引用关系。</div>
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
          <el-radio-group v-model="query.direction">
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

    <div class="graph-wrap" v-loading="loading">
      <div v-if="!nodes.length" class="empty-graph">请选择起点后生成血缘图</div>
      <div v-else class="graph-canvas" :style="{ width: canvasSize.width + 'px', height: canvasSize.height + 'px' }">
        <svg class="edge-layer" :width="canvasSize.width" :height="canvasSize.height">
          <g v-for="edge in edgeLines" :key="edge.key">
            <line :x1="edge.x1" :y1="edge.y1" :x2="edge.x2" :y2="edge.y2" stroke="#94A3B8" stroke-width="1.5" marker-end="url(#arrow)" />
            <text :x="edge.mx" :y="edge.my" class="edge-label">{{ edge.label }}</text>
          </g>
          <defs>
            <marker id="arrow" markerWidth="8" markerHeight="8" refX="6" refY="3" orient="auto" markerUnits="strokeWidth">
              <path d="M0,0 L0,6 L7,3 z" fill="#94A3B8" />
            </marker>
          </defs>
        </svg>
        <div
          v-for="node in nodes"
          :key="node.id"
          class="graph-node"
          :class="{ 'is-start': startNode && node.id === startNode.id }"
          :style="nodeStyle(node)"
        >
          <div class="node-type">{{ nodeTypeLabel(node.type) }}</div>
          <div class="node-label">{{ node.label || node.code }}</div>
          <div class="node-code">{{ node.code }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { getLineageGraph, listLineageOptions } from '@/api/lineage'

const CARD_W = 190
const CARD_H = 86
const GAP_X = 48
const GAP_Y = 40

export default {
  name: 'LineageGraph',
  data() {
    return {
      loading: false,
      optionLoading: false,
      options: [],
      startNode: null,
      nodes: [],
      edges: [],
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
    positions() {
      const map = {}
      const cols = Math.max(1, Math.ceil(Math.sqrt(this.nodes.length || 1)))
      this.nodes.forEach((node, index) => {
        const row = Math.floor(index / cols)
        const col = index % cols
        map[node.id] = {
          left: 32 + col * (CARD_W + GAP_X),
          top: 32 + row * (CARD_H + GAP_Y)
        }
      })
      return map
    },
    canvasSize() {
      const count = this.nodes.length || 1
      const cols = Math.max(1, Math.ceil(Math.sqrt(count)))
      const rows = Math.ceil(count / cols)
      return {
        width: Math.max(760, 64 + cols * CARD_W + (cols - 1) * GAP_X),
        height: Math.max(360, 64 + rows * CARD_H + (rows - 1) * GAP_Y)
      }
    },
    edgeLines() {
      return (this.edges || []).map((edge, index) => {
        const from = this.positions[edge.from]
        const to = this.positions[edge.to]
        if (!from || !to) return null
        const x1 = from.left + CARD_W
        const y1 = from.top + CARD_H / 2
        const x2 = to.left
        const y2 = to.top + CARD_H / 2
        return {
          key: edge.from + edge.to + index,
          x1, y1, x2, y2,
          mx: (x1 + x2) / 2,
          my: (y1 + y2) / 2 - 6,
          label: edge.label || ''
        }
      }).filter(Boolean)
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
      this.nodes = []
      this.edges = []
      this.startNode = null
      this.loadOptions('')
    },
    async loadGraph() {
      if (!this.query.nodeId) {
        this.$message.warning('请先选择血缘起点')
        return
      }
      this.loading = true
      try {
        const res = await getLineageGraph(this.query)
        const data = (res && res.data) || {}
        this.startNode = data.startNode || null
        this.nodes = data.nodes || []
        this.edges = data.edges || []
      } finally {
        this.loading = false
      }
    },
    nodeStyle(node) {
      const pos = this.positions[node.id] || { left: 0, top: 0 }
      return {
        left: pos.left + 'px',
        top: pos.top + 'px',
        borderColor: this.nodeColor(node.type),
        boxShadow: 'inset 4px 0 0 ' + this.nodeColor(node.type)
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
    padding: 12px 14px;
    margin-bottom: 14px;
    display: flex;
    align-items: center;
    gap: 12px;
  }
  .hint-title { color:#1F2937; font-weight:700; white-space:nowrap; }
  .hint-text { color:#64748B; }
  .query-panel {
    background: #fff;
    border: 1px solid #E5E7EB;
    border-radius: 4px;
    padding: 12px 12px 0;
    margin-bottom: 10px;
  }
  .legend-row {
    display: flex;
    gap: 12px;
    flex-wrap: wrap;
    color: #475569;
    font-size: 12px;
    margin-bottom: 10px;
  }
  .legend-item {
    display: inline-flex;
    align-items: center;
    gap: 5px;
  }
  .legend-item i {
    width: 10px;
    height: 10px;
    border-radius: 2px;
  }
  .graph-wrap {
    background: #fff;
    border: 1px solid #E5E7EB;
    border-radius: 4px;
    min-height: 420px;
    overflow: auto;
    position: relative;
  }
  .empty-graph {
    color: #94A3B8;
    text-align: center;
    padding: 120px 0;
  }
  .graph-canvas {
    position: relative;
    min-width: 100%;
  }
  .edge-layer {
    position: absolute;
    left: 0;
    top: 0;
    pointer-events: none;
  }
  .edge-label {
    fill: #64748B;
    font-size: 12px;
    paint-order: stroke;
    stroke: #fff;
    stroke-width: 3px;
  }
  .graph-node {
    position: absolute;
    width: 190px;
    height: 86px;
    background: #fff;
    border: 1px solid #CBD5E1;
    border-radius: 6px;
    padding: 10px 12px;
    box-sizing: border-box;
  }
  .graph-node.is-start {
    background: #FFFBEB;
  }
  .node-type {
    color: #64748B;
    font-size: 12px;
    margin-bottom: 4px;
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
    margin-top: 5px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}
</style>
