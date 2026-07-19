import { buildGraphNavigationItems, collectGraphConfigurationIssues } from '@/utils/graphDesignerTools'

export default {
  data() {
    return {
      graphNavigationTarget: '',
      graphNavigationOptions: [],
      graphConfigurationIssues: [],
      graphMiniMapVisible: false
    }
  },
  methods: {
    graphDataForTools() {
      if (!this.lf || typeof this.lf.getGraphData !== 'function') return { nodes: [], edges: [] }
      return this.lf.getGraphData() || { nodes: [], edges: [] }
    },
    searchGraphElements(keyword) {
      this.graphNavigationOptions = buildGraphNavigationItems(this.graphDataForTools(), keyword).map(function (item, index) {
        return Object.assign({ key: item.kind + ':' + item.elementId + ':' + index }, item)
      })
    },
    locateGraphNavigationItem(key) {
      const item = this.graphNavigationOptions.find(option => option.key === key)
      if (item) this.locateGraphElement(item)
    },
    locateGraphElement(item) {
      if (!this.lf || !item || !item.elementId) return
      try {
        this.lf.selectElementById(item.elementId, false, true)
        this.lf.focusOn(item.elementId)
      } catch (e) { /* 画布仍可通过属性面板定位 */ }
      const graph = this.graphDataForTools()
      if (item.baseType === 'edge') {
        const edge = (graph.edges || []).find(value => value.id === item.elementId)
        if (edge && typeof this.selectEdgeData === 'function') this.selectEdgeData(edge)
      } else {
        const node = (graph.nodes || []).find(value => value.id === item.elementId)
        if (node && typeof this.selectNodeData === 'function') this.selectNodeData(node)
      }
    },
    checkGraphConfiguration() {
      const modelType = this.$options.name === 'DecisionTree' ? 'TREE' : 'FLOW'
      this.graphConfigurationIssues = collectGraphConfigurationIssues(this.graphDataForTools(), modelType)
      if (this.graphConfigurationIssues.length === 0 && this.$message) this.$message.success('未发现未配置项')
    },
    toggleGraphMiniMap() {
      const miniMap = this.lf && this.lf.extension && this.lf.extension.miniMap
      if (!miniMap) {
        if (this.$message) this.$message.warning('缩略图尚未初始化')
        return
      }
      this.graphMiniMapVisible = !this.graphMiniMapVisible
      if (this.graphMiniMapVisible) miniMap.show()
      else miniMap.hide()
    }
  }
}
