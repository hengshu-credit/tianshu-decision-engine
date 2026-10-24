import { createGraphCanvas } from '@/components/flow/graphCanvas'

import {
  SelectionSelect,
  Menu,
  Snapshot,
  DynamicGroup,
  MiniMap,
} from '@logicflow/extension'
// 根入口还会加载未使用的 ELK；按当前固定版本直接使用 Dagre 模块。
import { Dagre } from '@logicflow/layout/es/dagre/index.js'

import { FLOW_THEME_COLOR, addConnectedNode, blurActiveDesignerControl, createAnchorGesture, createDynamicGroup, createFlowNodeData, findToolbarNodePosition, getBusinessGraphData, getPersistableGraphData, isAnchorClickGesture, isDesignerInteractiveTarget, layoutGraphByAnchors, updateAnchorGesture } from '@/components/flow/flowDesignerGraph'
import { normalizeDefaultEdgeLineType, applyGlobalEdgeTypeToInheritedEdges, mergeEdgePropertiesFromForm } from '@/components/flow/edgeLineType'

import {
  createEmptyGroup,
  createEmptyLeaf,
  collectVarCodesFromConditionTree,
  walkConditionLeaves,
  previewConditionTreeExpression,
  normalizeConditionTreeOperands,
} from '@/utils/decisionConditionTree'
import {
  collectOperandReferences,
  createLiteralOperand,
  operandFromReferenceFields,
  syncOperandReference,
} from '@/utils/operand'
import {
  addCode,
  applyActionDataSampleValues,
  applyConditionExpressionSamples,
  buildSampleParamsFromCodes,
  collectActionDataInputCodes,
  collectScriptInputCodes,
  refCodeById,
} from '@/utils/testSampleParams'
import { clampDesignerPanelWidth } from '@/utils/designerPanelWidth'
import {
  END_SCOPE_ALL_RULES,
  normalizeEndScope,
  getEndNodeAppearance,
} from '@/utils/endNodeScope'

// 树与流共用的图编辑方法；模式校验、加载兼容与事件差异保留在各页面。
export default {
  startPropertyResize(event) {
    this.resizingPropertyPanel = true
    document.body.style.cursor = 'col-resize'
    document.body.style.userSelect = 'none'
    window.addEventListener('mousemove', this.onPropertyResize)
    window.addEventListener('mouseup', this.stopPropertyResize)
    this.onPropertyResize(event)
  },

  onPropertyResize(event) {
    if (!this.resizingPropertyPanel || !event) return
    const width = window.innerWidth - event.clientX
    this.propertyPanelWidth = clampDesignerPanelWidth(
      width,
      window.innerWidth
    )
  },

  stopPropertyResize() {
    window.removeEventListener('mousemove', this.onPropertyResize)
    window.removeEventListener('mouseup', this.stopPropertyResize)
    if (!this.resizingPropertyPanel) return
    this.resizingPropertyPanel = false
    document.body.style.cursor = ''
    document.body.style.userSelect = ''
  },

  edgePriority(edge, fallbackIndex = 0) {
    const value = edge && edge.properties && Number(edge.properties.priority)
    return Number.isFinite(value) ? value : (fallbackIndex + 1) * 10
  },

  moveGatewayEdge(edgeId, offset) {
    const ordered = this.outEdges.slice()
    const index = ordered.findIndex((edge) => edge.id === edgeId)
    const target = index + offset
    if (index < 0 || target < 0 || target >= ordered.length) return
    ;[ordered[index], ordered[target]] = [ordered[target], ordered[index]]
    ordered.forEach((edge, edgeIndex) => {
      const current = this.lf.getProperties(edge.id) || edge.properties || {}
      this.lf.setProperties(edge.id, {
        ...current,
        priority: (edgeIndex + 1) * 10,
      })
    })
    this.edgeOrderVersion++
  },

  persistEdgeConditionDraft(edited = false) {
    if (
      !this.activeElement ||
      this.activeElement.baseType !== 'edge' ||
      this.edgeCondMode !== 'visual' ||
      !this.edgeConditionRoot
    ) return
    const empty = this.edgeConditionRoot.type === 'group' && !this.edgeConditionRoot.children?.length
    if (empty && !edited && this.edgeProps.conditionExpr) return
    this.edgeProps.conditionExpr = empty ? '' : previewConditionTreeExpression(this.edgeConditionRoot).expression
    this.edgeProps.conditionConfig = empty ? null : JSON.parse(JSON.stringify(this.edgeConditionRoot))
    this.edgeProps.leftVarId = null
    this.edgeProps.leftRefType = null
    this.edgeProps.rightVarId = null
    this.edgeProps.rightRefType = null
    this.onEdgeChange()
  },

  flushActiveEditor() {
    if (!this.activeElement || !this.lf) return
    if (this.activeElement.baseType === 'edge') {
      this.persistEdgeConditionDraft()
      return
    }
    if (
      this.activeElement.type === 'script-task' &&
      typeof this.lf.getNodeModelById === 'function' &&
      typeof this.lf.setProperties === 'function'
    ) {
      const model = this.lf.getNodeModelById(this.activeElement.id)
      const current = model ? model.properties || {} : {}
      this.lf.setProperties(this.activeElement.id, {
        ...current,
        actionData: this.currentActionData || [],
      })
    }
  },

  closePropertyPanel() {
    blurActiveDesignerControl()
    this.flushActiveEditor()
    this.activeElement = null
    this.hasSelection = false
    this.selectedBusinessNodeCount = 0
    this.clearSelectedEdgeAnimation()
  },

  onDesignerMouseDown(event) {
    if (isDesignerInteractiveTarget(event && event.target)) return
    blurActiveDesignerControl()
  },

  refreshStartNodeState() {
    if (!this.lf) {
      this.startNodeExists = false
      return
    }
    const graph = this.lf.getGraphData() || { nodes: [] }
    this.startNodeExists = (graph.nodes || []).some(
      (node) => node.type === 'start-event'
    )
  },

  collectSelectedVarItems() {
    const items = []
    const pushVisual = (visual) => {
      if (!visual) return
      items.push({
        varCode: visual.leftVar,
        _varId: visual.leftVarId,
        _refType: visual.leftRefType,
      })
      items.push({
        varCode: visual.rightVar,
        _varId: visual.rightVarId,
        _refType: visual.rightRefType,
      })
    }
    const pushProps = (props) => {
      if (!props) return
      items.push({ _varId: props.leftVarId, _refType: props.leftRefType })
      items.push({ _varId: props.rightVarId, _refType: props.rightRefType })
      items.push(...this.collectConditionVarItems(props.conditionConfig))
      items.push(...this.collectActionDataVarItems(props.actionData || []))
    }
    pushVisual(this.nodeCondVisual)
    pushVisual(this.edgeCondVisual)
    items.push(...this.collectConditionVarItems(this.edgeConditionRoot))
    items.push(
      ...this.collectActionDataVarItems(this.currentActionData || [])
    )
    if (this.lf && typeof this.lf.getGraphData === 'function') {
      const graph = this.lf.getGraphData() || {}
      ;(graph.nodes || []).forEach((node) =>
        pushProps(node.properties || node)
      )
      ;(graph.edges || []).forEach((edge) =>
        pushProps(edge.properties || edge)
      )
    }
    return items
  },

  createConditionRootFromVisual(visual) {
    const root = createEmptyGroup('AND')
    if (!visual?.leftVar) return root
    const leaf = createEmptyLeaf()
    if (visual && visual.leftVar) {
      leaf.leftOperand = operandFromReferenceFields({
        varCode: visual.leftVar,
        varLabel: visual.leftLabel,
        _varId: visual.leftVarId,
        _refType: visual.leftRefType,
      })
      leaf.operator = visual.operator || '=='
      leaf.rightOperand =
        visual.rightType === 'var'
          ? operandFromReferenceFields({
              varCode: visual.rightVar,
              _varId: visual.rightVarId,
              _refType: visual.rightRefType,
            })
          : createLiteralOperand(visual.rightValue, '')
    }
    root.children.push(leaf)
    return root
  },

  parseConditionConfig(config, expr, references = {}) {
    if (config) {
      if (typeof config === 'object')
        return normalizeConditionTreeOperands(
          JSON.parse(JSON.stringify(config))
        )
      try {
        return normalizeConditionTreeOperands(JSON.parse(config))
      } catch (e) {
        /* ignore */
      }
    }
    const visual = this.syncCondVisualFromExpr(expr)
    visual.leftVarId = references.leftVarId || null
    visual.leftRefType = references.leftRefType || null
    if (references.rightVarId) {
      visual.rightType = 'var'
      visual.rightVar = visual.rightValue
      visual.rightVarId = references.rightVarId
      visual.rightRefType = references.rightRefType || null
    }
    return this.createConditionRootFromVisual(visual)
  },

  collectConditionVarItems(config) {
    const items = []
    const root = this.parseConditionConfig(config, '')
    walkConditionLeaves(root, (leaf) => {
      [leaf.leftOperand, leaf.rightOperand].forEach((operand) =>
        collectOperandReferences(operand).forEach((reference) =>
          items.push({
            varCode: reference.code,
            varType: reference.valueType,
            _varId: reference.refId,
            _refType: reference.refType,
          })
        )
      )
    })
    return items
  },

  syncConditionConfigVarRefs(config) {
    if (!config || typeof config !== 'object') return false
    let changed = false
    walkConditionLeaves(config, (leaf) => {
      ['leftOperand', 'rightOperand'].forEach((field) => {
        const result = syncOperandReference(
          leaf[field],
          this.varPickerOptions
        )
        if (result.changed) {
          leaf[field] = result.operand
          changed = true
        }
      })
    })
    return changed
  },

  initLogicFlow() {
    this.lf = createGraphCanvas({
      container: this.$refs.canvasContainer,
      plugins: [
        SelectionSelect,
        Menu,
        Snapshot,
        DynamicGroup,
        MiniMap,
        Dagre,
      ],
      pluginsOptions: {
        dynamicGroup: {
          cascadeDeleteChildren: false,
          disallowEdgeConnectToGroup: true,
          sensorOutline: { stroke: FLOW_THEME_COLOR, strokeWidth: 2 },
        },
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
      keyboard: {
        enabled: true,
        shortcuts: [
          { keys: ['ctrl+z', 'cmd+z'], callback: () => this.undo() },
          { keys: ['ctrl+y', 'cmd+y'], callback: () => this.redo() },
          {
            keys: ['backspace', 'delete'],
            callback: () => this.deleteSelected(),
          },
        ],
      },
      edgeType: this.globalEdgeLineType,
      adjustEdgeStartAndEnd: true,
      snapline: true,
      history: true,
      style: {
        arrow: { stroke: FLOW_THEME_COLOR, fill: FLOW_THEME_COLOR },
      },
      guards: { beforeClone: () => true, beforeDelete: () => true },
    })

    this.applyCanvasSettings()
    this.setupContextMenu()
    this.bindEvents()
    this.lf.on('graph:transform', ({ transform }) => this.updateZoom(transform))
  },

  setupContextMenu() {
    this.lf.addMenuConfig({
      nodeMenu: [
        { text: '编辑属性', callback: (node) => this.selectNodeData(node) },
        {
          text: '删除节点',
          callback: (node) => {
            this.lf.deleteNode(node.id)
            if (this.activeElement && this.activeElement.id === node.id)
              this.activeElement = null
          },
        },
      ],
      edgeMenu: [
        {
          text: '编辑条件',
          callback: (edge) => this.selectEdgeById(edge.id),
        },
        {
          text: '删除连线',
          callback: (edge) => {
            this.lf.deleteEdge(edge.id)
            if (this.activeElement && this.activeElement.id === edge.id)
              this.activeElement = null
          },
        },
      ],
      graphMenu: [{ text: '适应画布', callback: () => this.resetZoom() }],
    })
  },

  onGraphRendered() {
    if (typeof this.refreshStartNodeState === 'function')
      this.refreshStartNodeState()
    const miniMap = this.lf && this.lf.extension.miniMap
    if (!miniMap || !this.miniMapVisible) return
    if (miniMap.isShow) miniMap.hide()
    miniMap.show()
  },

  // 连线校验失败时提示用户；锚点单击新增节点不显示拖动失败提示。
  handleConnectionNotAllowed(payload) {
    if (isAnchorClickGesture(this.anchorGesture)) return
    const msg =
      (payload && (payload.msg || payload.message)) ||
      (payload &&
        payload.data &&
        (payload.data.msg || payload.data.message)) ||
      '该连线不被允许'
    this.$message.warning(msg)
  },

  onAnchorMouseDown(payload) {
    this.anchorGesture = createAnchorGesture(payload)
  },

  onAnchorDrag(payload) {
    this.anchorGesture = updateAnchorGesture(this.anchorGesture, payload)
  },

  onAnchorDragEnd(payload) {
    const gesture = updateAnchorGesture(this.anchorGesture, payload)
    this.anchorGesture = null
    if (!payload.edgeModel && isAnchorClickGesture(gesture))
      this.openAnchorMenu(payload)
  },

  onAnchorClick(payload) {
    this.anchorGesture = null
    this.openAnchorMenu(payload)
  },

  closeAnchorMenu() {
    this.anchorMenu.visible = false
    this.pendingConnectedNode = null
  },

  selectConnectedNodeType(option) {
    this.anchorMenu.visible = false
    if (option.type === 'end-event') {
      this.endNodeScopeVisible = true
      return
    }
    this.commitConnectedNode(option.type)
  },

  commitConnectedNode(type, terminationScope) {
    const pending = this.pendingConnectedNode
    this.pendingConnectedNode = null
    if (!pending) return
    try {
      const result = addConnectedNode(this.lf, {
        ...pending,
        type,
        terminationScope,
        edgeType: this.globalEdgeLineType,
      })
      this.selectNodeData({ id: result.node.id })
      this.updateSelectedBusinessNodeCount()
    } catch (error) {
      this.$message.warning(error.message || '新增节点失败')
    }
  },

  selectNodeData(data) {
    this.flushActiveEditor()
    const model = this.lf.getNodeModelById(data.id)
    if (!model) return
    this.activeElement = {
      id: data.id,
      type: model.type,
      baseType: 'node',
      properties: model.properties
        ? JSON.parse(JSON.stringify(model.properties))
        : {},
    }
    this.nodeProps = {
      nodeName: this.activeElement.properties.nodeName || '',
      nodeDesc: this.activeElement.properties.nodeDesc || '',
      gatewayDirection:
        this.activeElement.properties.gatewayDirection || 'Diverging',
    }
    this.hasSelection = true
    if (model.type === 'exclusive-gateway') {
      const scriptContent = this.activeElement.properties.scriptContent || ''
      this.nodeCondVisual = this.syncCondVisualFromExpr(scriptContent)
      this.nodeCondVisual.leftVarId =
        this.activeElement.properties.leftVarId || null
      this.nodeCondVisual.leftRefType =
        this.activeElement.properties.leftRefType || null
      this.nodeCondVisual.rightVarId =
        this.activeElement.properties.rightVarId || null
      this.nodeCondVisual.rightRefType =
        this.activeElement.properties.rightRefType || null
      this.nodeCondMode =
        scriptContent && !this.nodeCondVisual.leftVar ? 'script' : 'visual'
    }
    if (model.type === 'script-task') {
      this.currentActionData = this.activeElement.properties.actionData || []
      this.actionMode = 'visual'
    }
    this.syncSelectedEdgeAnimation()
  },

  selectEdgeData(data) {
    this.flushActiveEditor()
    const model =
      this.lf && typeof this.lf.getEdgeModelById === 'function'
        ? this.lf.getEdgeModelById(data.id)
        : null
    const properties = model ? model.properties || {} : data.properties || {}
    this.activeElement = {
      id: data.id,
      type: data.type,
      baseType: 'edge',
      sourceNodeId: data.sourceNodeId,
      targetNodeId: data.targetNodeId,
      properties: JSON.parse(JSON.stringify(properties)),
    }
    const sourceEdges = this.lf.getNodeEdges?.(model?.sourceNodeId || data.sourceNodeId) || []
    const sourceIndex = sourceEdges
      .filter(edge => edge.sourceNodeId === (model?.sourceNodeId || data.sourceNodeId))
      .findIndex(edge => edge.id === data.id)
    this.edgeProps = {
      ...this.activeElement.properties,
      conditionName: this.activeElement.properties.conditionName || '',
      conditionExpr: this.activeElement.properties.conditionExpr || '',
      conditionConfig: this.activeElement.properties.conditionConfig || null,
      edgeLineType: this.activeElement.properties.edgeLineType || '',
      priority: this.edgePriority(model || data, Math.max(0, sourceIndex)),
    }
    this.edgeCondVisual = this.syncCondVisualFromExpr(
      this.edgeProps.conditionExpr
    )
    this.edgeCondVisual.leftVarId =
      this.activeElement.properties.leftVarId || null
    this.edgeCondVisual.leftRefType =
      this.activeElement.properties.leftRefType || null
    this.edgeCondVisual.rightVarId =
      this.activeElement.properties.rightVarId || null
    this.edgeCondVisual.rightRefType =
      this.activeElement.properties.rightRefType || null
    this.edgeConditionRoot = this.parseConditionConfig(
      this.edgeProps.conditionConfig,
      this.edgeProps.conditionExpr,
      this.edgeProps
    )
    this.edgeCondMode =
      this.edgeProps.conditionExpr &&
      !this.edgeCondVisual.leftVar &&
      (!this.edgeProps.conditionConfig || (this.edgeConditionRoot.type === 'group' && !this.edgeConditionRoot.children?.length))
        ? 'script'
        : 'visual'
    this.hasSelection = true
    this.syncSelectedEdgeAnimation()
  },

  selectEdgeById(edgeId) {
    try {
      const model = this.lf.getEdgeModelById(edgeId)
      if (model) {
        this.selectEdgeData({
          id: model.id,
          type: model.type,
          sourceNodeId: model.sourceNodeId,
          targetNodeId: model.targetNodeId,
          properties: model.properties || {},
        })
      }
    } catch (e) {
      /* ignore */
    }
  },

  onNodeChange() {
    if (!this.lf || !this.activeElement) return
    const curProps = this.lf.getProperties(this.activeElement.id) || {}
    this.lf.setProperties(this.activeElement.id, {
      ...curProps,
      ...this.nodeProps,
    })
  },

  onActionDataUpdate(data) {
    this.currentActionData = data
    if (this.lf && this.activeElement) {
      this.lf.setProperties(this.activeElement.id, { actionData: data })
    }
  },

  updateActiveEdgeForm(form) {
    this.edgeProps = form
    this.onEdgeChange()
  },

  changeActiveEdgeMode(mode) {
    if (mode === 'visual') {
      this.edgeConditionRoot = this.parseConditionConfig(
        this.edgeProps.conditionConfig,
        this.edgeProps.conditionExpr,
        this.edgeProps
      )
    }
    this.edgeCondMode = mode
  },

  edgeTargetName(edge) {
    const node = this.lf?.getNodeModelById?.(edge.targetNodeId)
    return node?.properties?.nodeName || edge.targetNodeId || '未连接节点'
  },

  onEdgeChange(edgeId = this.activeElement?.id, form = this.edgeProps) {
    if (!this.lf || !edgeId) return
    const curProps = this.lf.getProperties(edgeId) || {}
    const props = mergeEdgePropertiesFromForm(curProps, form)
    this.lf.setProperties(edgeId, props)
    if (!form.edgeLineType && curProps.edgeLineType) {
      this.lf.deleteProperty(edgeId, 'edgeLineType')
    }
    const effectiveType = form.edgeLineType
      ? normalizeDefaultEdgeLineType(form.edgeLineType)
      : this.globalEdgeLineType
    try {
      this.lf.changeEdgeType(edgeId, effectiveType)
    } catch (e) {
      /* ignore */
    }
    try {
      this.lf.updateText(edgeId, form.conditionName || '')
    } catch (e) {
      /* ignore */
    }
    this.edgeOrderVersion++
  },

  onGlobalEdgeLineTypeChange(val) {
    this.globalEdgeLineType = normalizeDefaultEdgeLineType(val)
    if (this.lf)
      applyGlobalEdgeTypeToInheritedEdges(this.lf, this.globalEdgeLineType)
  },

  addNode(type) {
    this.pendingConnectedNode = null
    if (type === 'start-event') this.refreshStartNodeState()
    if (type === 'start-event' && this.startNodeExists) {
      this.$message.warning('开始节点只能有一个')
      return
    }
    if (type === 'end-event') {
      this.endNodeScopeVisible = true
      return
    }
    this.addNodeToCanvas(type)
  },

  confirmEndNode(scope) {
    this.endNodeScopeVisible = false
    const normalizedScope = normalizeEndScope(scope)
    if (this.pendingConnectedNode) {
      this.commitConnectedNode('end-event', normalizedScope)
      return
    }
    this.addNodeToCanvas('end-event', normalizedScope)
  },

  onEndNodeScopeVisibleChange(visible) {
    this.endNodeScopeVisible = visible
    if (!visible) this.pendingConnectedNode = null
  },

  addNodeToCanvas(type, terminationScope) {
    const graph =
      this.lf && typeof this.lf.getGraphData === 'function'
        ? getBusinessGraphData(getPersistableGraphData(this.lf))
        : { nodes: [] }
    const position = findToolbarNodePosition(graph.nodes)
    this.lf.addNode(
      createFlowNodeData(type, {
        x: position.x,
        y: position.y,
        terminationScope,
      })
    )
    if (typeof this.refreshStartNodeState === 'function')
      this.refreshStartNodeState()
  },

  deleteSelected() {
    if (!this.activeElement) return
    if (this.activeElement.baseType === 'node') {
      this.lf.deleteNode(this.activeElement.id)
    } else {
      this.lf.deleteEdge(this.activeElement.id)
    }
    this.activeElement = null
    this.hasSelection = false
    this.refreshStartNodeState()
    this.updateSelectedBusinessNodeCount()
  },

  deleteCurrentNode() {
    if (!this.activeElement || this.activeElement.baseType !== 'node') return
    this.$confirm('确认删除该节点及其关联连线？', '提示', { type: 'warning' })
      .then(() => {
        this.lf.deleteNode(this.activeElement.id)
        this.activeElement = null
        this.hasSelection = false
        this.refreshStartNodeState()
        this.updateSelectedBusinessNodeCount()
      })
      .catch(() => {})
  },

  nodeTypeLabel(type) {
    const map = {
      'start-event': '开始节点',
      'end-event': '结束节点',
      'exclusive-gateway': '条件判断（网关）',
      'script-task': '执行动作（脚本任务）',
      'join-gateway': '聚合节点',
    }
    return map[type] || type
  },

  endNodeAppearance(scope) {
    return getEndNodeAppearance(scope)
  },

  endNodeDescription(scope) {
    return normalizeEndScope(scope) === END_SCOPE_ALL_RULES
      ? '执行到此节点时，根规则及后续所有规则都会立即终止。'
      : '执行到此节点时，当前规则立即返回；外层规则仍可继续执行。'
  },

  nodeTypeTag(type) {
    const map = {
      'start-event': 'success',
      'end-event': 'danger',
      'exclusive-gateway': 'warning',
      'script-task': '',
      'join-gateway': 'info',
    }
    return map[type] || 'info'
  },

  updateSelectedBusinessNodeCount() {
    if (!this.lf) return
    const selected = this.lf.getSelectElements(true) || {}
    this.selectedBusinessNodeCount = (selected.nodes || []).filter((node) => {
      const model = this.lf.getNodeModelById(node.id)
      return model && !model.isGroup
    }).length
    this.hasSelection =
      Boolean(this.activeElement) ||
      this.selectedBusinessNodeCount > 0 ||
      (selected.edges || []).length > 0
  },

  toggleSelectionMode() {
    const selection = this.lf.extension.selectionSelect
    this.selectionMode = !this.selectionMode
    selection.setExclusiveMode(this.selectionMode)
    if (this.selectionMode) {
      this.closeAnchorMenu()
      selection.openSelectionSelect()
    } else {
      selection.closeSelectionSelect()
    }
  },

  groupSelectedNodes() {
    try {
      const group = createDynamicGroup(this.lf)
      const selection = this.lf.extension.selectionSelect
      selection.closeSelectionSelect()
      selection.setExclusiveMode(false)
      this.selectionMode = false
      this.selectNodeData({ id: group.id })
      this.updateSelectedBusinessNodeCount()
      this.$message.success('分组创建成功；删除分组时组内节点会保留')
    } catch (error) {
      this.$message.warning(error.message || '创建分组失败')
    }
  },

  beautifyGraph() {
    const canvasGraph = getPersistableGraphData(this.lf)
    const graph = getBusinessGraphData(canvasGraph)
    if (graph.nodes.length < 2) {
      this.$message.info('至少需要两个节点才能执行一键美化')
      return
    }
    try {
      this.lf.render(layoutGraphByAnchors(canvasGraph))
      this.$nextTick(() => {
        this.lf.fitView(40, 40)
        this.updateZoom()
      })
      this.$message.success('画布已根据连线锚点自动排布，请点击保存后生效')
    } catch (error) {
      try {
        this.lf.render(JSON.parse(JSON.stringify(canvasGraph)))
      } catch (e) {
        /* ignore */
      }
      this.$message.error('一键美化失败: ' + (error.message || '未知错误'))
    }
  },

  toggleMiniMap() {
    this.miniMapVisible = !this.miniMapVisible
    if (this.miniMapVisible) this.lf.extension.miniMap.show()
    else this.lf.extension.miniMap.hide()
  },

  onCanvasSettingsChange(patch) {
    Object.assign(this, patch || {})
    if (patch && patch.backgroundEnabled && !this.backgroundColor) {
      this.backgroundColor = this.getThemeCanvasColor()
    }
    this.applyCanvasSettings()
    this.syncSelectedEdgeAnimation()
  },

  getThemeCanvasColor() {
    return getComputedStyle(document.documentElement).getPropertyValue('--tianshu-bg-workspace').trim() || '#0f1629'
  },

  applyCanvasSettings() {
    if (!this.lf) return
    this.lf.graphModel?.updateGridOptions?.({
      visible: this.gridVisible,
      size: this.gridSize,
      type: this.gridType,
      config: {
        ...(this.lf.graphModel.grid?.config || {}),
        color: this.gridColor || getComputedStyle(document.documentElement).getPropertyValue('--tianshu-border-strong').trim(),
        thickness: this.gridThickness,
      },
    })
    this.lf.updateEditConfig?.({ snapGrid: this.snapGridEnabled })
    this.lf.graphModel?.updateBackgroundOptions?.({
      backgroundColor: this.backgroundEnabled ? (this.backgroundColor || this.getThemeCanvasColor()) : this.getThemeCanvasColor(),
      opacity: this.backgroundOpacity,
    })
  },

  syncSelectedEdgeAnimation() {
    if (!this.lf) return
    this.animatedEdgeIds.forEach((edgeId) => this.lf.closeEdgeAnimation?.(edgeId))
    this.animatedEdgeIds = []
    if (!this.edgeAnimationEnabled || !this.activeElement) return
    const graph = this.lf.getGraphData?.() || { edges: [] }
    const edgeIds = this.activeElement.baseType === 'edge'
      ? [this.activeElement.id]
      : (graph.edges || [])
          .filter((edge) => edge.sourceNodeId === this.activeElement.id || edge.targetNodeId === this.activeElement.id)
          .map((edge) => edge.id)
    edgeIds.forEach((edgeId) => this.lf.openEdgeAnimation?.(edgeId))
    this.animatedEdgeIds = edgeIds
  },

  clearSelectedEdgeAnimation() {
    if (!this.lf) return
    this.animatedEdgeIds.forEach((edgeId) => this.lf.closeEdgeAnimation?.(edgeId))
    this.animatedEdgeIds = []
  },

  zoomIn() {
    this.lf.zoom(true)
    this.updateZoom()
  },

  zoomOut() {
    this.lf.zoom(false)
    this.updateZoom()
  },

  resetZoom() {
    this.lf.resetZoom()
    this.lf.resetTranslate()
    this.updateZoom()
  },

  updateZoom(transform) {
    const apply = () => {
      try {
        const t = transform || this.lf.getTransform()
        this.zoomPercent = Math.round((t.SCALE_X || 1) * 100)
        this.zoomInput = `${this.zoomPercent}%`
      } catch (e) {
        this.zoomPercent = 100
        this.zoomInput = '100%'
      }
    }
    if (transform) apply()
    else this.$nextTick(apply)
  },

  onZoomInputChange() {
    const requested = Number.parseFloat(String(this.zoomInput).replace('%', ''))
    const nextPercent = Number.isFinite(requested)
      ? Math.min(400, Math.max(25, requested))
      : this.zoomPercent
    this.lf?.zoom?.(nextPercent / 100)
    this.updateZoom()
  },

  undo() {
    this.lf.undo()
  },

  redo() {
    this.lf.redo()
  },

  convertLegacyModel(old) {
    const lfNodes = (old.nodes || []).map((n, i) => {
      let type = 'script-task'
      if (n.type === 'start') type = 'start-event'
      else if (n.type === 'end') type = 'end-event'
      else if (n.type === 'decision') type = 'exclusive-gateway'
      else if (n.type === 'join') type = 'join-gateway'
      const properties = {
        nodeName: n.name || '',
        nodeCode: n.id,
        nodeDesc: '',
        actionData: n.actionData || [],
      }
      if (type === 'end-event') {
        properties.terminationScope = normalizeEndScope(n.terminationScope)
        if (!properties.nodeName)
          properties.nodeName = getEndNodeAppearance(
            properties.terminationScope
          ).name
      }
      return {
        id: n.id,
        type,
        x: n.x || 160 + i * 200,
        y: n.y || 300,
        properties,
      }
    })
    const lfEdges = (old.edges || []).map((e, i) => ({
      id: 'edge_' + Date.now() + '_' + i,
      type: 'polyline',
      sourceNodeId: e.source,
      targetNodeId: e.target,
      properties: {
        conditionName: e.conditionExpression || '',
        conditionExpr: e.conditionExpression || '',
        priority: Number.isFinite(Number(e.priority))
          ? Number(e.priority)
          : (i + 1) * 10,
      },
    }))
    return { nodes: lfNodes, edges: lfEdges }
  },

  lfTypeToBackend(lfType) {
    const map = {
      'start-event': 'start',
      'end-event': 'end',
      'script-task': 'task',
      'exclusive-gateway': 'decision',
      'join-gateway': 'join',
    }
    return map[lfType] || lfType
  },

  buildBackendModel(flushEditor = true) {
    if (flushEditor && typeof this.flushActiveEditor === 'function') {
      this.flushActiveEditor()
    }
    // 保存前将当前编辑中的 actionData 同步到 LogicFlow 模型，确保配置不丢失
    if (
      flushEditor &&
      this.activeElement &&
      this.activeElement.baseType === 'node' &&
      this.activeElement.type === 'script-task'
    ) {
      const model = this.lf.getNodeModelById(this.activeElement.id)
      const currentProps = model ? model.properties || {} : {}
      this.lf.setProperties(this.activeElement.id, {
        ...currentProps,
        actionData: this.currentActionData || [],
      })
    }
    const canvasGraph = getPersistableGraphData(this.lf)
    const graphData = getBusinessGraphData(canvasGraph)

    const nodes = (graphData.nodes || []).map((n) => {
      const props = n.properties || {}
      const actionData = props.actionData || []
      const backendNode = {
        id: n.id,
        type: this.lfTypeToBackend(n.type),
        name: props.nodeName || '',
        x: Math.round(n.x),
        y: Math.round(n.y),
        actionData: Array.isArray(actionData) ? actionData : [],
        gatewayDirection: props.gatewayDirection || '',
        // 变量引用 ID（用于 script-task / exclusive-gateway 的变量选择器）
        leftVarId: props.leftVarId || null,
        leftRefType: props.leftRefType || null,
        rightVarId: props.rightVarId || null,
        rightRefType: props.rightRefType || null,
      }
      if (n.type === 'end-event') {
        backendNode.terminationScope = normalizeEndScope(
          props.terminationScope
        )
      }
      return backendNode
    })

    const edgeCountBySource = {}
    const edgePriorityById = {}
    const edges = (graphData.edges || []).map((e) => {
      const props = e.properties || {}
      const sourceIndex = edgeCountBySource[e.sourceNodeId] || 0
      edgeCountBySource[e.sourceNodeId] = sourceIndex + 1
      const storedPriority = Number(props.priority)
      const priority = Number.isFinite(storedPriority)
        ? storedPriority
        : (sourceIndex + 1) * 10
      edgePriorityById[e.id] = priority
      return {
        id: e.id,
        source: e.sourceNodeId,
        target: e.targetNodeId,
        conditionExpression: props.conditionExpr || '',
        conditionConfig: props.conditionConfig || null,
        name: props.conditionName || '',
        priority,
        // 变量引用 ID（用于连线条件的变量选择器）
        leftVarId: props.leftVarId || null,
        leftRefType: props.leftRefType || null,
        rightVarId: props.rightVarId || null,
        rightRefType: props.rightRefType || null,
      }
    })

    // 确保 logicflow 中的节点包含完整的 actionData（与 nodes 一致）
    const logicflowNodes = (canvasGraph.nodes || []).map((n) => {
      const base = { ...n }
      const actionData = (n.properties && n.properties.actionData) || []
      base.properties = {
        ...(n.properties || {}),
        actionData: Array.isArray(actionData) ? actionData : [],
      }
      return base
    })
    const logicflowEdges = (canvasGraph.edges || []).map((edge) => ({
      ...edge,
      properties: {
        ...(edge.properties || {}),
        priority:
          edgePriorityById[edge.id] !== undefined
            ? edgePriorityById[edge.id]
            : this.edgePriority(edge),
      },
    }))
    return {
      nodes,
      edges,
      scriptVarRefs: JSON.parse(JSON.stringify(this.scriptVarRefs || [])),
      defaultEdgeLineType: this.globalEdgeLineType,
      logicflow: { nodes: logicflowNodes, edges: logicflowEdges },
    }
  },

  async performDesignerSave() {
    const model = this.buildBackendModel()
    this.repairLegacyRuleCallRefs(model)
    const modelJson = JSON.stringify(model)
    const result = await this.saveDraftModel(modelJson)
    if (!result) return false
    this.refreshProjectRefs()

    this.$message.success('草稿已保存')
    return result
  },

  serializeDesignerDraft() {
    return JSON.stringify(this.buildBackendModel(false))
  },

  buildRuleCallValidationModel() {
    return this.buildBackendModel()
  },

  handleSave() {
    return this.runDesignerAction(async () => {
      if (this.designerBusy) return false
      this.designerBusy = true
      try { return await this.performDesignerSave() } finally { this.designerBusy = false }
    })
  },

  handleCompile() {
    return this.runDesignerAction(async () => {
      if (this.designerBusy) return false
      this.designerBusy = true
      try { return await this.compileDesignerDraft() } finally { this.designerBusy = false }
    })
  },

  async handleTest() {
    if (!this.ensureDesignerReadyForTest()) return
    this.testParamsTemplate = this.buildTestParamsTemplate()
    this.testParamsJson = JSON.stringify(
      this.buildTestParamsTemplate(),
      null,
      2
    )
    this.testResult = null
    this.testVisible = true
  },

  buildTestParamsTemplate() {
    const codes = new Set()
    const model = this.buildBackendModel()
    const nodes = model.nodes || []
    nodes.forEach((node) => {
      addCode(
        codes,
        refCodeById(this.projectRefs, node.leftVarId, node.leftRefType)
      )
      addCode(
        codes,
        refCodeById(this.projectRefs, node.rightVarId, node.rightRefType)
      )
      collectActionDataInputCodes(node.actionData, this.projectRefs, codes)
    })
    const edges = model.edges || []
    edges.forEach((edge) => {
      collectVarCodesFromConditionTree(edge.conditionConfig, codes)
      collectScriptInputCodes(
        edge.conditionExpression,
        this.projectRefs,
        codes
      )
      addCode(
        codes,
        refCodeById(this.projectRefs, edge.leftVarId, edge.leftRefType)
      )
      addCode(
        codes,
        refCodeById(this.projectRefs, edge.rightVarId, edge.rightRefType)
      )
    })
    const params = buildSampleParamsFromCodes(
      Array.from(codes),
      this.projectRefs
    )
    nodes.forEach((node) =>
      applyActionDataSampleValues(params, node.actionData, this.projectRefs)
    )
    edges.forEach((edge) =>
      applyConditionExpressionSamples(
        params,
        edge.conditionExpression,
        this.projectRefs
      )
    )
    return params
  },

  varTypeTag(varType) {
    return this.varTypeColor(varType)
  },

  onNodeCondVarSelect(v, side) {
    if (!v) return
    const label = v.varLabel || v.varCode
    if (side === 'left') {
      this.nodeCondVisual.leftVar = v.varCode
      this.nodeCondVisual.leftLabel = label
      this.nodeCondVisual.leftVarId = v._varId || null
      this.nodeCondVisual.leftRefType = v._refType || v.refType || null
    } else {
      this.nodeCondVisual.rightVar = v.varCode
      this.nodeCondVisual.rightVarId = v._varId || null
      this.nodeCondVisual.rightRefType = v._refType || v.refType || null
    }
  },

  onEdgeCondVarSelect(v, side) {
    if (!v) return
    const label = v.varLabel || v.varCode
    if (side === 'left') {
      this.edgeCondVisual.leftVar = v.varCode
      this.edgeCondVisual.leftLabel = label
      this.edgeCondVisual.leftVarId = v._varId || null
      this.edgeCondVisual.leftRefType = v._refType || v.refType || null
    } else {
      this.edgeCondVisual.rightVar = v.varCode
      this.edgeCondVisual.rightVarId = v._varId || null
      this.edgeCondVisual.rightRefType = v._refType || v.refType || null
    }
  },

  buildCondExpr(visual) {
    const left = visual.leftVar
    if (!left) {
      this.$message.warning('请选择左侧变量')
      return null
    }
    const op = visual.operator
    let right =
      visual.rightType === 'var' ? visual.rightVar : visual.rightValue
    if (!right && right !== 0) {
      this.$message.warning('请填写比较值')
      return null
    }
    if (visual.rightType === 'value' && isNaN(right)) {
      right = "'" + right.replace(/'/g, "\\'") + "'"
    }
    return left + ' ' + op + ' ' + right
  },

  applyNodeCondVisual() {
    const expr = this.buildCondExpr(this.nodeCondVisual)
    if (expr === null) return
    this.nodeProps.scriptContent = expr
    this.nodeProps.leftVarId = this.nodeCondVisual.leftVarId || null
    this.nodeProps.leftRefType = this.nodeCondVisual.leftRefType || null
    this.nodeProps.rightVarId = this.nodeCondVisual.rightVarId || null
    this.nodeProps.rightRefType = this.nodeCondVisual.rightRefType || null
    this.onNodeChange()

    this.$message.success('已生成: ' + expr)
  },

  applyEdgeCondVisual() {
    if (!this.edgeConditionRoot) {
      this.edgeConditionRoot = this.createConditionRootFromVisual(
        this.edgeCondVisual
      )
    }
    const empty = this.edgeConditionRoot.type === 'group' && !this.edgeConditionRoot.children?.length
    const preview = empty ? { expression: '', error: '' } : previewConditionTreeExpression(this.edgeConditionRoot)
    const expr = preview.expression
    this.edgeProps.conditionExpr = expr
    this.edgeProps.conditionConfig = empty ? null : JSON.parse(JSON.stringify(this.edgeConditionRoot))
    this.edgeProps.leftVarId = null
    this.edgeProps.leftRefType = null
    this.edgeProps.rightVarId = null
    this.edgeProps.rightRefType = null
    if (!this.edgeProps.conditionName) {
      this.edgeProps.conditionName = expr
    }
    this.onEdgeChange()

    if (preview.error) this.$message.warning(preview.error)
    else this.$message.success(empty ? '已设为默认分支' : '已生成: ' + expr)
  },

  syncCondVisualFromExpr(expr) {
    const m = (expr || '').match(/^(\S+)\s*(==|!=|>=|<=|>|<|in)\s*(.+)$/)
    if (m) {
      return {
        leftVar: m[1],
        leftLabel: '',
        leftVarId: null,
        leftRefType: null,
        operator: m[2],
        rightValue: m[3].replace(/^'|'$/g, ''),
        rightType: 'value',
        rightVar: '',
        rightVarId: null,
        rightRefType: null,
      }
    }
    return {
      leftVar: '',
      leftLabel: '',
      leftVarId: null,
      leftRefType: null,
      operator: '==',
      rightValue: '',
      rightType: 'value',
      rightVar: '',
      rightVarId: null,
      rightRefType: null,
    }
  },

  insertVarCode(code) {
    if (navigator.clipboard) {
      navigator.clipboard.writeText(code).then(() => {
        this.$message({
          message: '已复制：' + code,
          type: 'success',
          duration: 1200,
        })
      })
    }
  },

  formatJson(obj) {
    if (obj === null || obj === undefined) return '(空)'
    try {
      return JSON.stringify(
        typeof obj === 'string' ? JSON.parse(obj) : obj,
        null,
        2
      )
    } catch (e) {
      return String(obj)
    }
  }
}
