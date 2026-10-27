<template>
  <div
    class="tree-designer uiue-compact-workbench uiue-compact-designer"
    @mousedown.capture="onDesignerMouseDown"
  >
    <!-- 顶部工具栏 -->
    <div class="tree-toolbar">
      <div class="toolbar-row toolbar-row-primary">
      <div class="toolbar-left">
        <el-button
          size="small"
          :icon="ElIconBack"
          class="uiue-detail-back"
          aria-label="返回"
          data-designer-back
          title="返回"
          @click="$router.back()"
        />
        <el-icon class="toolbar-icon"><el-icon-set-up /></el-icon>
        <span class="toolbar-title">决策树设计器</span>
        <el-tag size="small" type="info" style="margin-left: 8px"
          >可视化树形规则</el-tag
        >
        <span class="toolbar-id" v-if="definitionId"
          >ID: {{ definitionId }}</span
        >
      </div>
      <div class="toolbar-center toolbar-context designer-version-toolbar">
        <rule-draft-read-only
          :visible="!canEditDraft"
          :loading="!draftGuardLoaded"
          :load-error="Boolean(draftGuardError) || varsLoadError"
          :revision-label="viewRevisionLabel"
          :revision-state="viewRevision ? viewRevision.state : ''"
          :can-fork="canForkViewRevision"
          :has-editable-draft="hasPendingDraft"
          :source-options="designerSourceOptions"
          :selected-source="selectedDesignerSource"
          :source-loading="designerSourcesLoading"
          :disabled="designerBusy"
          :read-only-reason="designerReadOnlyReason"
          :can-test-frozen="designerCanTest && referenceCatalogMode === 'FROZEN'"
          :migration-required="draftGuardLoaded && frozenReferenceCatalog?.localEditingAllowed === false"
          @test-frozen="handleTest"
          @open-migration="$router.push('/transfer')"
          @retry="startViewedRevisionRefresh(true)"
          @go-lifecycle="goRuleLifecycle"
          @fork="forkViewRevision"
          @change-source="switchDesignerSource"
          @delete-source="deleteDesignerSource"
        />
        <graph-designer-navigator
          v-model:target="graphNavigationTarget"
          :options="graphNavigationOptions"
          :issues="graphConfigurationIssues"
          check-label="检查"
          :show-check="false"
          :show-mini-map="false"
          @search="searchGraphElements"
          @locate="locateGraphNavigationItem"
          @locate-issue="locateGraphElement"
          @check="handleCheck"
        />
        <el-select
          v-model="globalEdgeLineType"
          class="toolbar-edge-select"
          size="small"
          aria-label="连线类型"
          @change="onGlobalEdgeLineTypeChange"
        >
          <el-option label="折线" value="polyline" />
          <el-option label="直线" value="line" />
          <el-option label="弧线" value="bezier" />
        </el-select>
        <canvas-background-settings
          :grid-visible="gridVisible"
          :snap-grid-enabled="snapGridEnabled"
          :grid-size="gridSize"
          :grid-type="gridType"
          :grid-thickness="gridThickness"
          :grid-color="gridColor"
          :background-opacity="backgroundOpacity"
          :background-color="backgroundColor"
          :background-enabled="backgroundEnabled"
          :edge-animation-enabled="edgeAnimationEnabled"
          @change="onCanvasSettingsChange"
        />
      </div>
      </div>
      <div class="toolbar-row toolbar-row-actions">
      <div class="toolbar-center toolbar-node-actions">
        <span class="toolbar-label">添加节点：</span>
        <el-button
          size="small"
          class="toolbar-node-button"
          :disabled="startNodeExists"
          @click="addNode('start-event')"
        >
          <span class="node-dot" style="background: #52c41a" />开始
        </el-button>
        <el-button size="small" class="toolbar-node-button" @click="addNode('exclusive-gateway')">
          <span class="node-dot" style="background: #fa8c16" />条件判断
        </el-button>
        <el-button
          size="small"
          class="toolbar-node-button btn-script-task"
          @click="addNode('script-task')"
        >
          <span class="node-dot" />执行动作
        </el-button>
        <el-button size="small" class="toolbar-node-button" @click="addNode('end-event')">
          <span class="node-dot" style="background: #ff4d4f" />结束
        </el-button>
      </div>
      <div class="toolbar-edit-actions">
        <el-button-group>
          <el-button
            size="small"
            :icon="ElIconDelete"
            class="btn-delete"
            :disabled="!hasSelection"
            @click="deleteSelected"
            >删除</el-button
          >
          <el-button size="small" :icon="ElIconRefreshLeft" @click="undo"
            >撤销</el-button
          >
          <el-button size="small" :icon="ElIconRefreshRight" @click="redo"
            >重做</el-button
          >
        </el-button-group>
        <el-button-group>
          <el-button
            size="small"
            :icon="ElIconCrop"
            :class="{ 'is-tool-active': selectionMode }"
            @click="toggleSelectionMode"
            >选区</el-button
          >
          <el-button
            size="small"
            :icon="ElIconFolderAdd"
            :disabled="selectedBusinessNodeCount < 2"
            @click="groupSelectedNodes"
            >分组</el-button
          >
          <el-button size="small" :icon="ElIconMagicStick" @click="beautifyGraph"
            >一键美化</el-button
          >
          <el-button
            size="small"
            :icon="ElIconMapLocation"
            :class="{ 'is-tool-active': miniMapVisible }"
            @click="toggleMiniMap"
            >小地图</el-button
          >
        </el-button-group>
        <el-button-group>
          <el-button
            size="small"
            :icon="ElIconZoomIn"
            aria-label="放大画布"
            title="放大画布"
            @click="zoomIn"
          />
          <el-button
            size="small"
            :icon="ElIconZoomOut"
            aria-label="缩小画布"
            title="缩小画布"
            @click="zoomOut"
          />
          <el-button
            size="small"
            :icon="ElIconRank"
            aria-label="重置画布缩放"
            title="重置画布缩放"
            @click="resetZoom"
          />
        </el-button-group>
        <el-select
          v-model="zoomInput"
          class="zoom-input"
          size="small"
          filterable
          allow-create
          default-first-option
          aria-label="画布缩放比例"
          @change="onZoomInputChange"
          @keyup.enter="onZoomInputChange"
        >
          <el-option v-for="preset in zoomPresets" :key="preset" :label="`${preset}%`" :value="`${preset}%`" />
        </el-select>
      </div>
      <div class="toolbar-right">
        <rule-designer-action-bar
          @check="handleCheck"
          @locate="locateGraphValidationIssue"
          :issue-context="incomingValidationIssue"
            :can-edit="canEditDraft"
            :can-test="designerCanTest"
            :state="designerActionState"
          :busy="designerBusy"
            :recovery="designerRecoveryCandidate"
            :report="designerValidationReport"
            @save="handleSave"
            @compile="handleCompile"
            @test="handleTest"
            @publish="handlePublish"
            @restore="restoreDesignerRecovery"
            @discard-recovery="discardDesignerRecovery"
          />
      </div>
      </div>
    </div>

    <rule-designer-status :state="designerActionState" :field-count="varPickerOptions.length" :loading="loadingVars" :source-label="viewRevisionLabel" :reference-mode="referenceCatalogMode" :can-use-current-resources="canEnterDesignerLivePreview" @use-current="runDesignerAction(enterDesignerLivePreview)" />
    <rule-designer-dialogs :choice="designerChoice" @resolve="resolveDesignerChoice" />

    <!-- 主体：画布 + 属性面板 -->
    <div class="tree-body">
      <!-- LogicFlow 画布 -->
      <div
        ref="canvasContainer"
        class="tree-canvas"
        :class="{
          'is-node-active': activeElement && activeElement.baseType === 'node',
        }"
      />

      <flow-node-add-menu
        :visible="anchorMenu.visible"
        :x="anchorMenu.x"
        :y="anchorMenu.y"
        :options="anchorMenuOptions"
        @select="selectConnectedNodeType"
        @close="closeAnchorMenu"
      />

      <!-- 右侧属性面板 -->
      <transition name="panel-slide">
        <div
          v-if="activeElement"
          class="tree-property"
          :style="{ width: propertyPanelWidth + 'px' }"
        >
          <div
            class="property-resize-handle"
            @mousedown.prevent="startPropertyResize"
          />
          <div class="prop-header">
            <span class="prop-title">
              <app-icon :name="propIcon" />
              {{ isEdge ? '连线属性' : '节点属性配置' }}
            </span>
            <el-icon
              class="prop-close"
              role="button"
              tabindex="0"
              aria-label="关闭属性面板"
              @click="closePropertyPanel"
              @keydown.enter="closePropertyPanel"
            ><el-icon-close /></el-icon>
          </div>

          <!-- ========== 连线属性（可视化+脚本双模式） ========== -->
          <template v-if="isEdge">
            <div class="prop-section">
              <edge-properties-editor
                :model-value="edgeProps"
                :condition-root="edgeConditionRoot"
                :mode="edgeCondMode"
                :vars="varPickerOptions"
                :functions="projectFunctions"
                :list-options="projectLists"
                :get-var-options-fn="getVarOptions"
                :selected-vars="selectedVarPickerOptions"
                @update:model-value="updateActiveEdgeForm"
                @update:mode="changeActiveEdgeMode"
                @condition-change="persistEdgeConditionDraft(true)"
                @generate="applyEdgeCondVisual"
              />
              <div class="hint-box" style="margin-top: 6px">
                <el-icon><el-icon-info /></el-icon>
                拖动连线两端的圆点，可切换节点的连接锚点
              </div>
            </div>
          </template>

          <!-- ========== 节点属性 ========== -->
          <template v-else>
            <div class="prop-section">
              <el-form size="small" label-width="70px" class="prop-form">
                <el-form-item label="节点名称">
                  <el-input
                    v-model="nodeProps.nodeName"
                    placeholder="节点名称"
                    @update:model-value="onNodeChange"
                  />
                </el-form-item>
                <el-form-item label="节点类型">
                  <el-tag
                    :type="nodeTypeTag(activeElement.type)"
                    size="small"
                    >{{ nodeTypeLabel(activeElement.type) }}</el-tag
                  >
                </el-form-item>
              </el-form>
            </div>

            <div v-if="activeElement.type === 'end-event'" class="prop-section">
              <el-alert
                :title="
                  endNodeAppearance(activeElement.properties.terminationScope)
                    .name
                "
                :description="
                  endNodeDescription(activeElement.properties.terminationScope)
                "
                :type="
                  endNodeAppearance(activeElement.properties.terminationScope)
                    .tagType
                "
                :closable="false"
                show-icon
              />
            </div>

            <!-- ===== 条件节点：直接编辑分支出口 ===== -->
            <template v-if="activeElement.type === 'exclusive-gateway'">
              <div class="prop-section">
                <div class="section-title"><span>分支出口</span></div>
                <div class="out-edges">
                  <gateway-branch-editor
                    v-for="(edge, ei) in outEdges"
                    :key="edge.id"
                    :edge="edge"
                    :index="ei"
                    :count="outEdges.length"
                    :priority="edgePriority(edge, ei)"
                    :target-name="edgeTargetName(edge)"
                    :parse-condition-config="parseConditionConfig"
                    :vars="varPickerOptions"
                    :functions="projectFunctions"
                    :list-options="projectLists"
                    :get-var-options-fn="getVarOptions"
                    :selected-vars="selectedVarPickerOptions"
                    @change="onEdgeChange(edge.id, $event)"
                    @move="moveGatewayEdge(edge.id, $event)"
                  />
                  <div v-if="outEdges.length === 0" class="hint-box">
                    <el-icon><el-icon-info /></el-icon>
                    从节点锚点拖拽到目标节点创建分支
                  </div>
                </div>
                <div class="hint-box" style="margin-top: 6px">
                  <el-icon><el-icon-info /></el-icon>
                  点击分支标题可展开或收起，也可单独点击画布连线修改属性
                </div>
              </div>
            </template>

            <!-- ===== 执行动作节点 ===== -->
            <template v-if="activeElement.type === 'script-task'">
              <div class="prop-section">
                <div class="section-title">
                  <span>动作配置</span>
                  <el-radio-group v-model="actionMode" size="small">
                    <el-radio-button value="visual">可视化</el-radio-button>
                    <el-radio-button value="script">脚本预览</el-radio-button>
                  </el-radio-group>
                </div>

                <div v-if="actionMode === 'visual'">
                  <action-block-editor
                    :action-data="currentActionData"
                    :vars="varPickerOptions"
                    :selected-vars="selectedVarPickerOptions"
                    :functions="projectFunctions"
                    :list-options="projectLists"
                :api-options="projectApis"
                    :rules="projectRules"
                    :current-rule-id="definitionId"
                    :current-rule-code="currentRuleCode"
                    :validate-rule-call-cycle="validateRuleCallCycle"
                    @update="onActionDataUpdate"
                  />
                </div>

                <div v-else class="cond-script">
                  <pre class="script-readonly">{{
                    scriptPreview || '（请在可视化模式配置动作）'
                  }}</pre>
                </div>

                <div class="hint-box" style="margin-top: 6px">
                  <el-icon><el-icon-info /></el-icon>
                  支持赋值、条件分支、Switch、函数调用、循环、三元、IN判断、动态字符串
                </div>
              </div>
            </template>

            <!-- 操作按钮 -->
            <div class="prop-section" style="padding-top: 4px">
              <el-button
                type="danger"
                size="small"
                :icon="ElIconDelete"
                class="delete-current-node"
                @click="deleteCurrentNode"
              >
                删除此节点
              </el-button>
            </div>
          </template>
        </div>
      </transition>

      <!-- 无选中提示 -->
      <div v-if="!activeElement" class="tree-hint">
        <el-icon class="hint-icon"><el-icon-share /></el-icon>
        <p>从工具栏添加节点，拖拽锚点连线</p>
        <p>单击节点/连线可编辑属性</p>
      </div>
    </div>

    <!-- 脚本预览/编辑面板 -->
    <div class="tree-script-area" v-if="definitionId">
      <script-panel
        :compile-result="designerCompileResult"
        :references="projectRefs"
      :version-references="scriptVersionReferences"
        :functions="projectFunctions"
      />
    </div>

    <end-node-scope-dialog
      :visible="endNodeScopeVisible"
      @update:visible="onEndNodeScopeVisibleChange"
      @confirm="confirmEndNode"
    />

    <!-- 测试执行弹窗 -->
    <designer-test-dialog
      @locate="locateDesignerIssue"
      v-model:visible="testVisible"
      :execution-source="designerTestExecutionSource"
      :execution-source-name="viewRevisionLabel"
      :definition-id="definitionId"
      :project-id="projectIdForRefs"
      model-type="TREE"
      :model-json-provider="serializeDesignerDraft"
      :params-template="testParamsTemplate"
    />
  </div>
</template>

<script>
import graphDesignerMethods from '@/components/flow/graphDesignerMethods'

import { parseDesignerModelJson } from '@/utils/designerModelJson'
import { markRaw } from 'vue'
import {
  SetUp as ElIconSetUp,
  Close as ElIconClose,
  InfoFilled as ElIconInfo,
  Share as ElIconShare,
  Back as ElIconBack,
  Delete as ElIconDelete,
  RefreshLeft as ElIconRefreshLeft,
  RefreshRight as ElIconRefreshRight,
  Crop as ElIconCrop,
  FolderAdd as ElIconFolderAdd,
  MagicStick as ElIconMagicStick,
  MapLocation as ElIconMapLocation,
  ZoomIn as ElIconZoomIn,
  ZoomOut as ElIconZoomOut,
  Rank as ElIconRank,
} from '@element-plus/icons-vue'

import CanvasBackgroundSettings from '@/components/flow/CanvasBackgroundSettings.vue'

import {
  getDefaultFlowData,
} from '@/components/flow/nodes'
import FlowNodeAddMenu from '@/components/flow/FlowNodeAddMenu.vue'
import { FLOW_MENU_OPTIONS, getBusinessGraphData, getPersistableGraphData, resolveAnchorDirection, validateDesignerGraphStructure } from '@/components/flow/flowDesignerGraph'
import { migrateModelJsonForEdgeLineTypes, prepareLogicFlowDataForRender } from '@/components/flow/edgeLineType'
import {
  generateScriptPreview,
  normalizeGraphActionData,
} from '@/utils/actionDataCodegen'
import varPickerMixin from '@/mixins/varPickerMixin'
import ruleCallMixin from '@/mixins/ruleCallMixin'
import ruleDraftMixin from '@/mixins/ruleDraftMixin'
import ScriptPanel from '@/components/common/ScriptPanel.vue'
import DesignerTestDialog from '@/components/common/DesignerTestDialog.vue'
import RuleDraftReadOnly from '@/components/rule/RuleDraftReadOnly.vue'
import RuleDesignerActionBar from '@/components/rule/RuleDesignerActionBar.vue'
import EndNodeScopeDialog from '@/components/flow/EndNodeScopeDialog.vue'
import ActionBlockEditor from '@/components/flow/ActionBlockEditor.vue'
import EdgePropertiesEditor from '@/components/flow/EdgePropertiesEditor.vue'
import GatewayBranchEditor from '@/components/flow/GatewayBranchEditor.vue'

import graphDesignerToolsMixin from '@/mixins/graphDesignerToolsMixin'
import GraphDesignerNavigator from '@/components/flow/GraphDesignerNavigator.vue'

export default {
  data() {
    return {
      definitionId: null,
      lf: null,
      activeElement: null,
      startNodeExists: false,
      hasSelection: false,
      selectionMode: false,
      miniMapVisible: true,
      selectedBusinessNodeCount: 0,
      anchorMenuOptions: FLOW_MENU_OPTIONS.tree,
      anchorMenu: { visible: false, x: 0, y: 0 },
      pendingConnectedNode: null,
      anchorGesture: null,
      zoomPercent: 100,
      zoomInput: '100%',
      zoomPresets: [50, 75, 100, 125, 150, 200],
      gridVisible: true,
      snapGridEnabled: true,
      gridSize: 20,
      gridType: 'dot',
      gridThickness: 1,
      gridColor: '',
      backgroundOpacity: 1,
      backgroundColor: '',
      backgroundEnabled: false,
      edgeAnimationEnabled: true,
      animatedEdgeIds: [],
      nodeProps: {},
      edgeProps: {},
      nodeCondMode: 'visual',
      nodeCondVisual: {
        leftVar: '',
        leftLabel: '',
        leftVarId: null,
        leftRefType: null,
        operator: '>',
        rightValue: '',
        rightType: 'value',
        rightVar: '',
        rightVarId: null,
        rightRefType: null,
      },
      edgeCondMode: 'visual',
      edgeCondVisual: {
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
      },
      edgeConditionRoot: null,
      edgeOrderVersion: 0,
      actionMode: 'visual',
      currentActionData: [],
      scriptVarRefs: [],
      testVisible: false,
      endNodeScopeVisible: false,
      testParamsTemplate: {},
      testParamsJson: '{}',
      testResult: null,
      /** 工具栏全局默认连线类型（折线/直线/贝塞尔），新连线与「跟随全局」的边使用 */
      globalEdgeLineType: 'polyline',
      propertyPanelWidth: 640,
      resizingPropertyPanel: false,
      ElIconBack: markRaw(ElIconBack),
      ElIconDelete: markRaw(ElIconDelete),
      ElIconRefreshLeft: markRaw(ElIconRefreshLeft),
      ElIconRefreshRight: markRaw(ElIconRefreshRight),
      ElIconCrop: markRaw(ElIconCrop),
      ElIconFolderAdd: markRaw(ElIconFolderAdd),
      ElIconMagicStick: markRaw(ElIconMagicStick),
      ElIconMapLocation: markRaw(ElIconMapLocation),
      ElIconZoomIn: markRaw(ElIconZoomIn),
      ElIconZoomOut: markRaw(ElIconZoomOut),
      ElIconRank: markRaw(ElIconRank),
    }
  },
  components: {
    RuleDesignerActionBar,
    RuleDraftReadOnly,
    DesignerTestDialog,
    EndNodeScopeDialog,
    FlowNodeAddMenu,
    ScriptPanel,
    ActionBlockEditor,
    EdgePropertiesEditor,
    GatewayBranchEditor,
    GraphDesignerNavigator,
      CanvasBackgroundSettings,
    ElIconSetUp,
    ElIconClose,
    ElIconInfo,
    ElIconShare,
  },
  name: 'DecisionTree',
  mixins: [varPickerMixin, ruleCallMixin, graphDesignerToolsMixin, ruleDraftMixin],
  computed: {
    isEdge() {
      return this.activeElement && this.activeElement.baseType === 'edge'
    },
    propIcon() {
      if (this.isEdge) return 'Connection'
      if (!this.activeElement) return 'HelpFilled'
      const map = {
        'start-event': 'VideoPlay',
        'end-event': 'Remove',
        'exclusive-gateway': 'Sort',
        'script-task': 'Document',
        'join-gateway': 'CopyDocument',
      }
      return map[this.activeElement.type] || 'HelpFilled'
    },
    outEdges() {
      this.edgeOrderVersion
      if (!this.lf || !this.activeElement || this.isEdge) return []
      try {
        const edges = this.lf.getNodeEdges(this.activeElement.id)
        return (edges || [])
          .filter((e) => e.sourceNodeId === this.activeElement.id)
          .map((edge, index) => ({ edge, index }))
          .sort(
            (left, right) =>
              this.edgePriority(left.edge, left.index) -
                this.edgePriority(right.edge, right.index) ||
              left.index - right.index
          )
          .map((item) => item.edge)
      } catch (e) {
        return []
      }
    },
    scriptPreview() {
      return generateScriptPreview(this.currentActionData)
    },
  },
  created() {
    this.definitionId = this.$route.params.id
  },
  mounted() {
    this.initLogicFlow()
    window.addEventListener('tianshu-theme-change', this.applyCanvasSettings)
    this.loadRuleCallOptions(this.definitionId)
    this.loadContent()
  },
  activated() {
    window.addEventListener('tianshu-theme-change', this.applyCanvasSettings)
    this.applyCanvasSettings()
  },
  deactivated() {
    window.removeEventListener('tianshu-theme-change', this.applyCanvasSettings)
  },
  beforeUnmount() {
    window.removeEventListener('tianshu-theme-change', this.applyCanvasSettings)
    this.stopPropertyResize()
    this.clearSelectedEdgeAnimation()
    if (this.lf) this.lf.destroy()
    this.lf = null
  },
  methods: {
    ...graphDesignerMethods,

    bindEvents() {
      this.lf.on('node:click', ({ data }) => {
        this.closeAnchorMenu()
        this.selectNodeData(data)
        this.$nextTick(() => this.updateSelectedBusinessNodeCount())
      })
      this.lf.on('edge:click', ({ data }) => {
        this.closeAnchorMenu()
        this.selectEdgeData(data)
        this.$nextTick(() => this.updateSelectedBusinessNodeCount())
      })
      this.lf.on('blank:click', () => {
        this.closeAnchorMenu()
        this.closePropertyPanel()
      })
      this.lf.on('node:dnd-add', ({ data }) => {
        this.$nextTick(() => this.selectNodeData(data))
      })
      this.lf.on('node:dbclick', ({ data }) => this.selectNodeData(data))
      this.lf.on('edge:dbclick', ({ data }) => this.selectEdgeData(data))
      this.lf.on('history:change', () => {
        this.edgeOrderVersion++
        this.updateZoom()
        this.queueDesignerDraftCapture()
      })
      this.lf.on('graph:rendered', this.onGraphRendered)
      this.lf.on('anchor:mousedown', this.onAnchorMouseDown)
      this.lf.on('anchor:drag', this.onAnchorDrag)
      this.lf.on('anchor:dragend', this.onAnchorDragEnd)
      this.lf.on('anchor:click', this.onAnchorClick)
      this.lf.on('selection:selected', () =>
        this.updateSelectedBusinessNodeCount()
      )
      this._connectionNotAllowedHandler = (payload) =>
        this.handleConnectionNotAllowed(payload)
      this.lf.on('connection:not-allowed', this._connectionNotAllowedHandler)

      // 决策树禁止分支汇合：连线完成后检测目标节点入边数，超过1条则自动撤销
      this.lf.on('edge:add', ({ data }) => {
        this.$nextTick(() => {
          const edgeModel = this.lf.getEdgeModelById(data.id)
          if (!edgeModel || edgeModel.virtual) return
          const graphData = getBusinessGraphData(
            getPersistableGraphData(this.lf)
          )
          const edges = graphData.edges || []
          if (!edges.some((edge) => edge.id === data.id)) return
          const targetInEdges = edges.filter(
            (e) => e.targetNodeId === data.targetNodeId
          )
          if (targetInEdges.length > 1) {
            this.lf.deleteEdge(data.id)
            this.$message.warning(
              '决策树不允许一个节点有多条入边（分支汇合），请使用决策流'
            )
          }
        })
      })
    },

    openAnchorMenu({ data, e, nodeModel }) {
      const sourceNode =
        nodeModel || (data && this.lf.getNodeModelById(data.id))
      if (
        !sourceNode ||
        sourceNode.type === 'end-event' ||
        sourceNode.isGroup
      ) {
        this.closeAnchorMenu()
        return
      }
      const direction = resolveAnchorDirection(data, sourceNode)
      const rect = this.$refs.canvasContainer.getBoundingClientRect()
      const menuWidth = 188
      const menuHeight = 160
      const rawX =
        e && typeof e.clientX === 'number'
          ? e.clientX - rect.left + 10
          : rect.width / 2
      const rawY =
        e && typeof e.clientY === 'number'
          ? e.clientY - rect.top + 10
          : rect.height / 2
      this.pendingConnectedNode = { sourceNode, sourceAnchor: data, direction }
      this.anchorMenu = {
        visible: true,
        x: Math.max(6, Math.min(rawX, rect.width - menuWidth)),
        y: Math.max(6, Math.min(rawY, rect.height - menuHeight)),
      }
    },

    handleValidate() {
      const canvasGraph = getPersistableGraphData(this.lf)
      const graphData = getBusinessGraphData(canvasGraph)
      const errors = validateDesignerGraphStructure(graphData, {
        treeMode: true,
      })

      if (errors.length === 0) {
        this.$message.success('验证通过！')
      } else {
        this.$alert(
          errors.map((e, i) => i + 1 + '. ' + e).join('\n'),
          '验证失败',
          { type: 'warning' }
        )
      }
    },

    async loadContent() {
      try {
        if (this.draftGuardPromise) await this.draftGuardPromise
        this.scriptVarRefs = []
        const content = this.viewRevision
        if (content && content.modelJson && content.modelJson !== '{}') {
          const modelData = normalizeGraphActionData(
            parseDesignerModelJson(content.modelJson)
          )
          this.scriptVarRefs = modelData.scriptVarRefs || []
          migrateModelJsonForEdgeLineTypes(modelData)
          this.globalEdgeLineType = modelData.defaultEdgeLineType
          this.lf.setDefaultEdgeType(this.globalEdgeLineType)
          if (modelData.logicflow) {
            const prepared = prepareLogicFlowDataForRender(
              modelData.logicflow,
              this.globalEdgeLineType
            )
            this.lf.render(prepared)
          } else if (modelData.nodes && modelData.nodes.length > 0) {
            const converted = this.convertLegacyModel(modelData)
            const prepared = prepareLogicFlowDataForRender(
              converted,
              this.globalEdgeLineType
            )
            this.lf.render(prepared)
          } else {
            this.lf.render(getDefaultFlowData())
          }
        } else {
          this.globalEdgeLineType = 'polyline'
          this.lf.setDefaultEdgeType('polyline')
          this.lf.render(getDefaultFlowData())
        }
        this.refreshStartNodeState()
        this.updateZoom()
        this.$nextTick(() => this._syncModelVarRefs())
      } catch (e) {
        this.$message.error('加载内容失败: ' + (e.message || '未知错误'))
        this.globalEdgeLineType = 'polyline'
        this.lf.setDefaultEdgeType('polyline')
        this.lf.render(getDefaultFlowData())
      } finally {
        this.$nextTick(() =>
          this.initializeDesignerDraftTracking(this.serializeDesignerDraft())
        )
      }
    },

    /**
     * 同步节点/连线条件中引用变量的 varLabel。
     * 通过 _varId 从 projectRefs 查找最新变量信息，回填到 leftLabel。
     */
    _syncModelVarRefs() {
      if (!this.projectRefs || !this.projectRefs.length) return
      const findById = (id, refType) => {
        if (!id) return null
        for (const item of this.projectRefs) {
          const vid = item.varObj && item.varObj.id
          const typeMatches =
            !refType ||
            !item.refType ||
            String(item.refType) === String(refType)
          if (String(vid) === String(id) && typeMatches) return item
        }
        return null
      }
      const nodes = this.lf.getGraphData().nodes || []
      for (const node of nodes) {
        const props = node.properties || {}
        let changed = false
        if (props.leftVarId) {
          const info = findById(props.leftVarId, props.leftRefType)
          if (info) {
            props.leftVarLabel = info.refLabel.label + ' ' + info.refLabel.code
            props.leftRefType = info.refType
            changed = true
          }
        }
        if (props.rightVarId) {
          const info = findById(props.rightVarId, props.rightRefType)
          if (info) {
            props.rightVarLabel = info.refLabel.label + ' ' + info.refLabel.code
            props.rightRefType = info.refType
            changed = true
          }
        }
        if (this.syncActionDataVarRefs(props.actionData || [])) changed = true
        if (changed) this.lf.setProperties(node.id, props)
      }
      const edges = this.lf.getGraphData().edges || []
      for (const edge of edges) {
        const props = edge.properties || {}
        let changed = false
        if (props.leftVarId) {
          const info = findById(props.leftVarId, props.leftRefType)
          if (info) {
            props.leftVarLabel = info.refLabel.label + ' ' + info.refLabel.code
            props.leftRefType = info.refType
            changed = true
          }
        }
        if (props.rightVarId) {
          const info = findById(props.rightVarId, props.rightRefType)
          if (info) {
            props.rightVarLabel = info.refLabel.label + ' ' + info.refLabel.code
            props.rightRefType = info.refType
            changed = true
          }
        }
        if (this.syncConditionConfigVarRefs(props.conditionConfig))
          changed = true
        if (changed) this.lf.setProperties(edge.id, props)
      }
    },

  },
}
</script>

<style lang="scss" scoped>
@import '@/styles/graph-designer';
@include graph-designer('tree');
</style>

<style>
/* LogicFlow 全局覆盖 */
.lf-menu {
  background: var(--tianshu-bg-surface);
  border-radius: 4px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  border: 1px solid var(--tianshu-border-subtle);
  padding: 4px 0;
  min-width: 110px;
}
.lf-menu-item {
  padding: 6px 16px;
  font-size: 13px;
  cursor: pointer;
  color: var(--tianshu-text-primary);
}
.lf-menu-item:hover {
  background: var(--tianshu-designer-accent-bg);
  color: var(--el-color-primary);
}
</style>
