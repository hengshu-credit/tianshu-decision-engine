<template>
  <div class="flow-designer uiue-compact-workbench uiue-compact-designer">
    <!-- 顶部工具栏 -->
    <div class="flow-toolbar">
      <div class="toolbar-left">
        <el-button
          link
          :icon="ElIconBack"
          class="toolbar-back"
          aria-label="返回"
          title="返回"
          @click="$router.back()"
        />
        <el-icon class="toolbar-icon"><el-icon-share /></el-icon>
        <span class="toolbar-title">决策流设计器</span>
        <el-tag size="small" type="info" style="margin-left: 8px"
          >可视化流程规则</el-tag
        >
        <span class="toolbar-id" v-if="definitionId"
          >ID: {{ definitionId }}</span
        >
      </div>
      <div class="toolbar-center">
        <span class="toolbar-label">添加节点：</span>
        <el-button size="small" @click="addNode('start-event')">
          <span class="node-dot" style="background: #52c41a" />开始
        </el-button>
        <el-button size="small" @click="addNode('exclusive-gateway')">
          <span class="node-dot" style="background: #fa8c16" />条件判断
        </el-button>
        <el-button
          size="small"
          class="btn-script-task"
          @click="addNode('script-task')"
        >
          <span class="node-dot" />执行动作
        </el-button>
        <el-button size="small" @click="addNode('join-gateway')">
          <span class="node-dot" style="background: #8c8c8c" />聚合
        </el-button>
        <el-button size="small" @click="addNode('end-event')">
          <span class="node-dot" style="background: #ff4d4f" />结束
        </el-button>
        <el-divider direction="vertical" />
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
        <el-divider direction="vertical" />
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
        <el-divider direction="vertical" />
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
        <span class="zoom-text">{{ zoomPercent }}%</span>
        <el-divider direction="vertical" />
        <graph-designer-navigator
          v-model:target="graphNavigationTarget"
          :options="graphNavigationOptions"
          :issues="graphConfigurationIssues"
          :show-mini-map="false"
          @search="searchGraphElements"
          @locate="locateGraphNavigationItem"
          @locate-issue="locateGraphElement"
          @check="checkGraphConfiguration"
        />
        <el-divider direction="vertical" />
        <span class="toolbar-label">连线：</span>
        <el-select
          v-model="globalEdgeLineType"
          size="small"
          style="width: 110px"
          @change="onGlobalEdgeLineTypeChange"
        >
          <el-option label="折线" value="polyline" />
          <el-option label="直线" value="line" />
          <el-option label="弧线" value="bezier" />
        </el-select>
      </div>
      <div class="toolbar-right">
        <el-button size="small" :icon="ElIconCircleCheck" @click="handleValidate"
          >验证</el-button
        >
        <el-button size="small" :icon="ElIconDocument" @click="handleSave"
          >临时保存配置</el-button
        >
        <el-button
          size="small"
          type="warning"
          :icon="ElIconCpu"
          @click="handleCompile"
          >保存并编译</el-button
        >
        <el-button
          size="small"
          type="primary"
          :icon="ElIconVideoPlay"
          @click="handleTest"
          >编译后测试</el-button
        >
      </div>
    </div>

    <!-- 主体：画布 + 属性面板 -->
    <div class="flow-body">
      <!-- LogicFlow 画布 -->
      <div
        ref="canvasContainer"
        class="flow-canvas"
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
          class="flow-property"
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
            <el-icon class="prop-close"><el-icon-close /></el-icon>
          </div>

          <!-- ========== 连线属性（可视化+脚本双模式） ========== -->
          <template v-if="isEdge">
            <div class="prop-section">
              <el-form size="small" label-width="80px" class="prop-form">
                <el-form-item label="连接线类型">
                  <el-select
                    v-model="edgeProps.edgeLineType"
                    size="small"
                    style="width: 95%"
                    @change="onEdgeLineShapeChange"
                  >
                    <el-option label="跟随全局" value="" />
                    <el-option label="折线" value="polyline" />
                    <el-option label="直线" value="line" />
                    <el-option label="弧线" value="bezier" />
                  </el-select>
                </el-form-item>
                <el-form-item label="分支标签">
                  <el-input
                    v-model="edgeProps.conditionName"
                    placeholder="如：是、否、金额>500"
                    @update:model-value="onEdgeChange"
                  />
                </el-form-item>
              </el-form>
            </div>

            <div class="prop-section">
              <div class="section-title">
                <span>条件表达式</span>
                <el-radio-group v-model="edgeCondMode" size="small">
                  <el-radio-button value="visual">可视化</el-radio-button>
                  <el-radio-button value="script">脚本</el-radio-button>
                </el-radio-group>
              </div>

              <!-- 可视化条件构建 -->
              <div v-if="edgeCondMode === 'visual'" class="cond-builder">
                <condition-group-editor
                  v-if="edgeConditionRoot"
                  :group="edgeConditionRoot"
                  :vars="varPickerOptions"
                  :functions="projectFunctions"
                  :list-options="projectLists"
                  :get-var-options-fn="getVarOptions"
                  :selected-vars="selectedVarPickerOptions"
                />
                <el-button
                  type="primary"
                  size="small"
                  :icon="ElIconCheck"
                  style="width: 100%; margin-top: 8px"
                  @click="applyEdgeCondVisual"
                >
                  生成表达式
                </el-button>
                <div v-if="edgeProps.conditionExpr" class="generated-expr">
                  <code>{{ edgeProps.conditionExpr }}</code>
                </div>
              </div>

              <!-- 脚本模式 -->
              <div v-else class="cond-script">
                <el-input
                  v-model="edgeProps.conditionExpr"
                  type="textarea"
                  :rows="3"
                  placeholder="QLExpress 表达式，如：amount > 100000"
                  class="mono-input"
                  @update:model-value="onEdgeChange"
                />
              </div>

              <div class="hint-box" style="margin-top: 6px">
                <el-icon><el-icon-info /></el-icon> 条件为空表示默认分支（else）
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

            <!-- ===== 条件节点：只显示分支出口 ===== -->
            <template v-if="activeElement.type === 'exclusive-gateway'">
              <div class="prop-section">
                <div class="section-title"><span>分支出口</span></div>
                <div class="out-edges">
                  <div
                    v-for="(edge, ei) in outEdges"
                    :key="edge.id"
                    class="out-edge-item"
                    @click="selectEdgeById(edge.id)"
                  >
                    <span class="edge-idx">{{ ei + 1 }}</span>
                    <el-icon class="edge-arrow"><el-icon-right /></el-icon>
                    <span class="edge-name">{{
                      edgeLabel(edge) || '（点击配置条件）'
                    }}</span>
                    <el-tag v-if="!edgeLabel(edge)" size="small" type="warning"
                      >未设置</el-tag
                    >
                  </div>
                  <div v-if="outEdges.length === 0" class="hint-box">
                    <el-icon><el-icon-info /></el-icon>
                    从节点锚点拖拽到目标节点创建分支
                  </div>
                </div>
                <div class="hint-box" style="margin-top: 6px">
                  <el-icon><el-icon-info /></el-icon>
                  点击分支连线可配置条件表达式（可视化或脚本）
                </div>
              </div>
            </template>

            <!-- ===== 聚合节点 ===== -->
            <template v-if="activeElement.type === 'join-gateway'">
              <div class="prop-section">
                <div class="section-title"><span>聚合配置</span></div>
                <div class="hint-box">
                  <el-icon><el-icon-info /></el-icon>
                  聚合节点用于多条分支汇合，所有入边的分支都到达后，继续向下执行
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
                  支持赋值、条件分支、Switch、函数调用、执行规则、循环、三元、IN判断、动态字符串
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
      <div v-if="!activeElement" class="flow-hint">
        <el-icon class="hint-icon"><el-icon-set-up /></el-icon>
        <p>从工具栏添加节点，拖拽锚点连线</p>
        <p>单击节点/连线可编辑属性</p>
      </div>
    </div>

    <!-- 脚本预览/编辑面板 -->
    <div class="flow-script-area" v-if="definitionId">
      <script-panel
        ref="scriptPanel"
        :definitionId="definitionId"
        :onBeforeCompile="handleSave"
        @mode-change="onScriptModeChange"
      />
    </div>

    <end-node-scope-dialog
      :visible="endNodeScopeVisible"
      @update:visible="onEndNodeScopeVisibleChange"
      @confirm="confirmEndNode"
    />

    <!-- 测试执行弹窗 -->
    <designer-test-dialog
      v-model:visible="testVisible"
      :definition-id="definitionId"
      :project-id="projectIdForRefs"
      model-type="FLOW"
      :model-json-provider="buildBackendModel"
      :params-template="testParamsTemplate"
    />
  </div>
</template>

<script>
import { markRaw } from 'vue'
import {
  Share as ElIconShare,
  Close as ElIconClose,
  InfoFilled as ElIconInfo,
  Right as ElIconRight,
  SetUp as ElIconSetUp,
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
  CircleCheck as ElIconCircleCheck,
  Document as ElIconDocument,
  Cpu as ElIconCpu,
  VideoPlay as ElIconVideoPlay,
  Check as ElIconCheck,
} from '@element-plus/icons-vue'
import LogicFlow from '@logicflow/core'
import {
  SelectionSelect,
  Menu,
  Snapshot,
  DynamicGroup,
  MiniMap,
} from '@logicflow/extension'
import { Dagre } from '@logicflow/layout'
import '@logicflow/core/es/index.css'
import '../../styles/logicflow-extension.css'

import {
  registerCustomNodes,
  getDefaultFlowData,
} from '@/components/flow/nodes'
import FlowNodeAddMenu from '@/components/flow/FlowNodeAddMenu.vue'
import {
  FLOW_MENU_OPTIONS,
  FLOW_THEME_COLOR,
  addConnectedNode,
  createAnchorGesture,
  createDynamicGroup,
  createFlowNodeData,
  getBusinessGraphData,
  getPersistableGraphData,
  isAnchorClickGesture,
  layoutGraphByAnchors,
  resolveAnchorDirection,
  updateAnchorGesture,
} from '@/components/flow/flowDesignerGraph'
import {
  normalizeDefaultEdgeLineType,
  migrateModelJsonForEdgeLineTypes,
  prepareLogicFlowDataForRender,
  applyGlobalEdgeTypeToInheritedEdges,
  mergeEdgePropertiesFromForm,
} from '@/components/flow/edgeLineType'
import {
  saveContent,
  compileRule,
  executeRule,
  getContent,
  refreshFields,
} from '@/api/definition'
import {
  generateScript,
  normalizeGraphActionData,
} from '@/utils/actionDataCodegen'
import { graphContainsDirectedCycle } from '@/utils/flowGraphCycle'
import varPickerMixin from '@/mixins/varPickerMixin'
import ruleCallMixin from '@/mixins/ruleCallMixin'
import ScriptPanel from '@/components/common/ScriptPanel.vue'
import DesignerTestDialog from '@/components/common/DesignerTestDialog.vue'
import EndNodeScopeDialog from '@/components/flow/EndNodeScopeDialog.vue'
import ActionBlockEditor from '@/components/flow/ActionBlockEditor.vue'
import ConditionGroupEditor from '@/components/decision/ConditionGroupEditor.vue'
import {
  createEmptyGroup,
  createEmptyLeaf,
  collectVarCodesFromConditionTree,
  walkConditionLeaves,
  compileConditionTreeExpression,
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
import { isSuccessResult, resultErrorMessage } from '@/utils/apiResponse'
import { clampDesignerPanelWidth } from '@/utils/designerPanelWidth'
import {
  END_SCOPE_ALL_RULES,
  normalizeEndScope,
  getEndNodeAppearance,
} from '@/utils/endNodeScope'
import graphDesignerToolsMixin from '@/mixins/graphDesignerToolsMixin'
import GraphDesignerNavigator from '@/components/flow/GraphDesignerNavigator.vue'

export default {
  data() {
    return {
      definitionId: null,
      lf: null,
      activeElement: null,
      hasSelection: false,
      selectionMode: false,
      miniMapVisible: true,
      selectedBusinessNodeCount: 0,
      anchorMenuOptions: FLOW_MENU_OPTIONS.flow,
      anchorMenu: { visible: false, x: 0, y: 0 },
      pendingConnectedNode: null,
      anchorGesture: null,
      zoomPercent: 100,
      scriptMode: 'visual',
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
      actionMode: 'visual',
      currentActionData: [],
      testVisible: false,
      endNodeScopeVisible: false,
      testParamsTemplate: {},
      testParamsJson: '{}',
      testResult: null,
      /** 工具栏全局默认连线类型（折线/直线/贝塞尔），新连线与「跟随全局」的边使用 */
      globalEdgeLineType: 'polyline',
      propertyPanelWidth: 640,
      resizingPropertyPanel: false,
      /** contentLoaded 标志：当 model 内容与 projectRefs 均加载完成后解锁变量引用同步 */
      contentLoaded: false,
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
      ElIconCircleCheck: markRaw(ElIconCircleCheck),
      ElIconDocument: markRaw(ElIconDocument),
      ElIconCpu: markRaw(ElIconCpu),
      ElIconVideoPlay: markRaw(ElIconVideoPlay),
      ElIconCheck: markRaw(ElIconCheck),
    }
  },
  components: {
    DesignerTestDialog,
    EndNodeScopeDialog,
    FlowNodeAddMenu,
    ScriptPanel,
    ActionBlockEditor,
    ConditionGroupEditor,
    GraphDesignerNavigator,
    ElIconShare,
    ElIconClose,
    ElIconInfo,
    ElIconRight,
    ElIconSetUp,
  },
  name: 'DecisionFlow',
  mixins: [varPickerMixin, ruleCallMixin, graphDesignerToolsMixin],
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
      if (!this.lf || !this.activeElement || this.isEdge) return []
      try {
        const edges = this.lf.getNodeEdges(this.activeElement.id)
        return (edges || []).filter(
          (e) => e.sourceNodeId === this.activeElement.id
        )
      } catch (e) {
        return []
      }
    },
    scriptPreview() {
      return generateScript(this.currentActionData)
    },
  },
  created() {
    this.definitionId = this.$route.params.id
  },
  mounted() {
    this.initLogicFlow()
    this.loadRuleCallOptions(this.definitionId)
    this.loadContent()
  },
  beforeUnmount() {
    this.stopPropertyResize()
    if (this.lf) this.lf.destroy()
    this.lf = null
  },
  methods: {
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
    parseConditionConfig(config, expr) {
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
      return this.createConditionRootFromVisual(
        this.syncCondVisualFromExpr(expr)
      )
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
      this.lf = new LogicFlow({
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
        grid: { size: 20, visible: true },
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
        snapline: true,
        history: true,
        style: {
          nodeText: { overflowMode: 'ellipsis', fontSize: 12 },
          edgeText: { fontSize: 12, background: { fill: '#fff' } },
          polyline: {
            stroke: FLOW_THEME_COLOR,
            hoverStroke: FLOW_THEME_COLOR,
            selectedStroke: FLOW_THEME_COLOR,
            strokeWidth: 1.5,
          },
          line: {
            stroke: FLOW_THEME_COLOR,
            hoverStroke: FLOW_THEME_COLOR,
            selectedStroke: FLOW_THEME_COLOR,
            strokeWidth: 1.5,
          },
          bezier: {
            stroke: FLOW_THEME_COLOR,
            hoverStroke: FLOW_THEME_COLOR,
            selectedStroke: FLOW_THEME_COLOR,
            strokeWidth: 1.5,
          },
          arrow: { stroke: FLOW_THEME_COLOR, fill: FLOW_THEME_COLOR },
          anchor: { stroke: FLOW_THEME_COLOR, fill: '#fff', r: 4 },
          anchorHover: {
            stroke: FLOW_THEME_COLOR,
            fill: FLOW_THEME_COLOR,
            r: 5,
          },
          anchorLine: { stroke: FLOW_THEME_COLOR },
        },
        guards: { beforeClone: () => true, beforeDelete: () => true },
      })

      registerCustomNodes(this.lf)
      this.setupContextMenu()
      this.bindEvents()
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
        this.activeElement = null
        this.hasSelection = false
        this.updateSelectedBusinessNodeCount()
      })
      this.lf.on('node:dnd-add', ({ data }) => {
        this.$nextTick(() => this.selectNodeData(data))
      })
      this.lf.on('node:dbclick', ({ data }) => this.selectNodeData(data))
      this.lf.on('edge:dbclick', ({ data }) => this.selectEdgeData(data))
      this.lf.on('history:change', () => this.updateZoom())
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
    },

    onGraphRendered() {
      const miniMap = this.lf && this.lf.extension.miniMap
      if (!miniMap || !this.miniMapVisible) return
      if (miniMap.isShow) miniMap.hide()
      miniMap.show()
    },

    /**
     * LogicFlow 连线规则校验失败时提示用户（如禁止构成有向环）
     */
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
      const menuHeight = 190
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
    },

    selectEdgeData(data) {
      this.activeElement = {
        id: data.id,
        type: data.type,
        baseType: 'edge',
        sourceNodeId: data.sourceNodeId,
        targetNodeId: data.targetNodeId,
        properties: data.properties
          ? JSON.parse(JSON.stringify(data.properties))
          : {},
      }
      this.edgeProps = {
        conditionName: this.activeElement.properties.conditionName || '',
        conditionExpr: this.activeElement.properties.conditionExpr || '',
        conditionConfig: this.activeElement.properties.conditionConfig || null,
        edgeLineType: this.activeElement.properties.edgeLineType || '',
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
        this.edgeProps.conditionExpr
      )
      this.edgeCondMode =
        this.edgeProps.conditionExpr &&
        !this.edgeCondVisual.leftVar &&
        !this.edgeProps.conditionConfig
          ? 'script'
          : 'visual'
      this.hasSelection = true
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

    /**
     * 同步连线分支文案与条件等到模型，并保持与连接线类型相关的 properties 一致
     */
    onEdgeChange() {
      if (
        !this.lf ||
        !this.activeElement ||
        this.activeElement.baseType !== 'edge'
      )
        return
      const curProps = this.lf.getProperties(this.activeElement.id) || {}
      const props = mergeEdgePropertiesFromForm(curProps, this.edgeProps)
      this.lf.setProperties(this.activeElement.id, props)
      const effectiveType = this.edgeProps.edgeLineType
        ? normalizeDefaultEdgeLineType(this.edgeProps.edgeLineType)
        : this.globalEdgeLineType
      try {
        this.lf.changeEdgeType(this.activeElement.id, effectiveType)
      } catch (e) {
        /* ignore */
      }
      try {
        this.lf.updateText(
          this.activeElement.id,
          this.edgeProps.conditionName || ''
        )
      } catch (e) {
        /* ignore */
      }
    },

    /**
     * 仅修改当前连线的连接线类型（跟随全局或单独指定）
     */
    onEdgeLineShapeChange() {
      this.onEdgeChange()
    },

    /**
     * 修改工具栏全局连线类型：更新默认类型及所有未单独指定的边
     */
    onGlobalEdgeLineTypeChange(val) {
      this.globalEdgeLineType = normalizeDefaultEdgeLineType(val)
      if (this.lf)
        applyGlobalEdgeTypeToInheritedEdges(this.lf, this.globalEdgeLineType)
    },

    addNode(type) {
      this.pendingConnectedNode = null
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
      const xPos = 300 + Math.random() * 200
      const yPos = 200 + Math.random() * 150
      this.lf.addNode(
        createFlowNodeData(type, { x: xPos, y: yPos, terminationScope })
      )
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
      this.updateSelectedBusinessNodeCount()
    },

    deleteCurrentNode() {
      if (!this.activeElement || this.activeElement.baseType !== 'node') return
      this.$confirm('确认删除该节点及其关联连线？', '提示', { type: 'warning' })
        .then(() => {
          this.lf.deleteNode(this.activeElement.id)
          this.activeElement = null
          this.hasSelection = false
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

    edgeLabel(edge) {
      if (edge.properties && edge.properties.conditionName)
        return edge.properties.conditionName
      if (edge.properties && edge.properties.conditionExpr)
        return edge.properties.conditionExpr
      return ''
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
    updateZoom() {
      this.$nextTick(() => {
        try {
          const t = this.lf.getTransform()
          this.zoomPercent = Math.round((t.SCALE_X || 1) * 100)
        } catch (e) {
          this.zoomPercent = 100
        }
      })
    },
    undo() {
      this.lf.undo()
    },
    redo() {
      this.lf.redo()
    },

    handleValidate() {
      const errors = []
      const canvasGraph = getPersistableGraphData(this.lf)
      const graphData = getBusinessGraphData(canvasGraph)
      const nodes = graphData.nodes || []
      const edges = graphData.edges || []

      const starts = nodes.filter((n) => n.type === 'start-event')
      if (starts.length === 0) errors.push('缺少开始节点')
      if (starts.length > 1) errors.push('开始节点只能有一个')
      nodes
        .filter((n) => n.type === 'exclusive-gateway')
        .forEach((gw) => {
          const outEdges = edges.filter((e) => e.sourceNodeId === gw.id)
          if (outEdges.length < 2) {
            errors.push(
              '条件判断节点「' +
                ((gw.properties && gw.properties.nodeName) || gw.id) +
                '」至少需要两个出口'
            )
          }
        })

      nodes
        .filter((n) => n.type === 'join-gateway')
        .forEach((jn) => {
          const inEdges = edges.filter((e) => e.targetNodeId === jn.id)
          if (inEdges.length < 2) {
            errors.push(
              '聚合节点「' +
                ((jn.properties && jn.properties.nodeName) || jn.id) +
                '」至少需要两个入边'
            )
          }
        })

      const nodeIds = nodes.map((n) => n.id)
      if (graphContainsDirectedCycle(edges, nodeIds)) {
        errors.push(
          '存在有向环路：决策流仅支持 DAG，请删除或调整形成回路的连线'
        )
      }

      errors.push(...this.validateRuleCallsInModel(this.buildBackendModel()))

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
        const res = await getContent(this.definitionId)
        const content = res && res.data ? res.data : res
        if (content && content.modelJson && content.modelJson !== '{}') {
          const modelData = normalizeGraphActionData(
            JSON.parse(content.modelJson)
          )
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
        this.updateZoom()
        this.contentLoaded = true
        this.$nextTick(() => this._syncModelVarRefs())
      } catch (e) {
        this.$message.error('加载内容失败: ' + (e.message || '未知错误'))
        this.globalEdgeLineType = 'polyline'
        this.lf.setDefaultEdgeType('polyline')
        this.lf.render(getDefaultFlowData())
      }
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
        },
      }))
      return { nodes: lfNodes, edges: lfEdges }
    },

    /**
     * 同步节点/连线条件中引用变量的 varLabel。
     * 通过 _varId 从 projectRefs 查找最新变量信息，回填 leftLabel。
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
        // leftVarId / rightVarId 优先从 properties 读（由 var-picker 选择时写入），
        // 同时兼容从节点根级读（save 时写入 nodes[i].leftVarId，load 时未迁移到 properties）
        const leftId = props.leftVarId || node.leftVarId
        const leftRefType = props.leftRefType || node.leftRefType
        const rightId = props.rightVarId || node.rightVarId
        const rightRefType = props.rightRefType || node.rightRefType
        let changed = false
        if (leftId) {
          const info = findById(leftId, leftRefType)
          if (info) {
            props.leftVarLabel = info.refLabel.label + ' ' + info.refLabel.code
            props.leftRefType = info.refType
            changed = true
          }
        }
        if (rightId) {
          const info = findById(rightId, rightRefType)
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
        const leftId = props.leftVarId || edge.leftVarId
        const leftRefType = props.leftRefType || edge.leftRefType
        const rightId = props.rightVarId || edge.rightVarId
        const rightRefType = props.rightRefType || edge.rightRefType
        let changed = false
        if (leftId) {
          const info = findById(leftId, leftRefType)
          if (info) {
            props.leftVarLabel = info.refLabel.label + ' ' + info.refLabel.code
            props.leftRefType = info.refType
            changed = true
          }
        }
        if (rightId) {
          const info = findById(rightId, rightRefType)
          if (info) {
            props.rightVarLabel = info.refLabel.label + ' ' + info.refLabel.code
            props.rightRefType = info.refType
            changed = true
          }
        }
        if (this.syncConditionConfigVarRefs(props.conditionConfig))
          changed = true
        if (changed) this.lf.setEdgeData(edge.id, { properties: props })
      }
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

    buildBackendModel() {
      // 保存前将当前编辑中的 actionData 同步到 LogicFlow 模型，确保配置不丢失
      if (
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
      if (
        this.activeElement &&
        this.activeElement.baseType === 'edge' &&
        this.edgeCondMode === 'visual' &&
        this.edgeConditionRoot
      ) {
        const expr = compileConditionTreeExpression(this.edgeConditionRoot)
        this.edgeProps.conditionExpr = expr
        this.edgeProps.conditionConfig = JSON.parse(
          JSON.stringify(this.edgeConditionRoot)
        )
        this.onEdgeChange()
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

      const edges = (graphData.edges || []).map((e) => {
        const props = e.properties || {}
        return {
          id: e.id,
          source: e.sourceNodeId,
          target: e.targetNodeId,
          conditionExpression: props.conditionExpr || '',
          conditionConfig: props.conditionConfig || null,
          name: props.conditionName || '',
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
      return {
        nodes,
        edges,
        defaultEdgeLineType: this.globalEdgeLineType,
        logicflow: { nodes: logicflowNodes, edges: canvasGraph.edges || [] },
      }
    },

    async handleSave() {
      const model = this.buildBackendModel()
      this.repairLegacyRuleCallRefs(model)
      const ruleCallErrors = this.validateRuleCallsInModel(model)
      if (ruleCallErrors.length) {
        this.showRuleCallErrors(ruleCallErrors)
        return false
      }
      const modelJson = JSON.stringify(model)
      await saveContent({ definitionId: this.definitionId, modelJson })
      await refreshFields(this.definitionId, modelJson)
      this.refreshProjectRefs()

      this.$message.success('保存成功')
      return true
    },

    buildRuleCallValidationModel() {
      return this.buildBackendModel()
    },

    async handleCompile() {
      if ((await this.handleSave()) === false) return
      const res = await compileRule(this.definitionId)
      if (isSuccessResult(res)) {
        this.$message.success('编译成功')
        // 异步刷新变量映射和脚本面板
        await this.loadProjectVars(this.definitionId)
        if (this.$refs.scriptPanel) {
          this.$refs.scriptPanel.refresh()
        }
      } else {
        this.$message.error('编译失败: ' + resultErrorMessage(res))
      }
    },

    handleTest() {
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
    async doTest() {
      let params = {}
      try {
        params = JSON.parse(this.testParamsJson || '{}')
      } catch (e) {
        this.$message.error('参数 JSON 格式错误')
        return
      }
      const res = await executeRule({ definitionId: this.definitionId, params })
      this.testResult = res && res.data ? res.data : res
    },
    onScriptModeChange(mode) {
      this.scriptMode = mode
    },
    varTypeTag(varType) {
      return this.varTypeColor(varType)
    },
    // === 条件可视化构建 ===
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
      const expr = compileConditionTreeExpression(this.edgeConditionRoot)
      this.edgeProps.conditionExpr = expr
      this.edgeProps.conditionConfig = JSON.parse(
        JSON.stringify(this.edgeConditionRoot)
      )
      this.edgeProps.leftVarId = null
      this.edgeProps.leftRefType = null
      this.edgeProps.rightVarId = null
      this.edgeProps.rightRefType = null
      if (!this.edgeProps.conditionName) {
        this.edgeProps.conditionName = expr
      }
      this.onEdgeChange()

      this.$message.success('已生成: ' + expr)
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
    },
  },
}
</script>

<style lang="scss" scoped>
.flow-designer {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 82px);
  background: #fff;
  border-radius: 4px;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}
.flow-script-area {
  flex-shrink: 0;
  max-height: 60%;
  overflow-y: auto;
  border-top: 1px solid #e8e8e8;
  background: #fff;
}
.flow-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 7px 14px;
  background: #2639e9;
  color: #fff;
  flex-shrink: 0;
  flex-wrap: wrap;
  gap: 6px;
}
.toolbar-back {
  color: #fff !important;
}
.toolbar-icon {
  font-size: 18px;
  margin-right: 6px;
}
.toolbar-title {
  font-weight: bold;
  font-size: 15px;
}
.toolbar-id {
  font-size: 12px;
  opacity: 0.7;
  margin-left: 4px;
}
.toolbar-left,
.toolbar-center,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.toolbar-label {
  font-size: 12px;
  opacity: 0.85;
}
.toolbar-right .el-button,
.toolbar-center .el-button {
  color: #fff;
  border-color: rgba(255, 255, 255, 0.4);
  background: rgba(255, 255, 255, 0.1);
  min-height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  white-space: nowrap;
  &:hover {
    background: rgba(255, 255, 255, 0.2);
    border-color: rgba(255, 255, 255, 0.7);
  }
  &.is-tool-active {
    background: #ffffff;
    border-color: #ffffff;
    color: #1d39c4;
  }
  &.el-button--primary {
    background: #ffffff;
    background-color: #ffffff !important;
    color: #1d39c4;
    color: #1d39c4 !important;
    border-color: #ffffff !important;
    font-weight: 600;
    box-shadow: 0 1px 3px rgba(15, 23, 42, 0.16);
  }
  &.el-button--primary:hover,
  &.el-button--primary:focus {
    background-color: #eef2ff !important;
    border-color: #d6deff !important;
    color: #1428a0 !important;
    box-shadow: 0 2px 5px rgba(15, 23, 42, 0.2);
  }
  &.el-button--primary:active {
    background-color: #dde5ff !important;
    border-color: #c3ceff !important;
    color: #1428a0 !important;
    box-shadow: inset 0 1px 2px rgba(15, 23, 42, 0.16);
  }
  &.el-button--warning {
    background: #b45309;
    color: #fff;
    border-color: #b45309;

    &:hover,
    &:focus {
      background: #92400e;
      border-color: #92400e;
    }
  }
  &.is-disabled,
  &.is-disabled:hover {
    color: rgba(255, 255, 255, 0.55);
    border-color: rgba(255, 255, 255, 0.24);
    background: rgba(255, 255, 255, 0.08);
  }
}
.toolbar-right .el-button {
  min-width: 88px;
}
.toolbar-center .btn-script-task {
  /*// background: #EAF2FF;*/ /*// border-color: #ADC6FF;*/
  color: #ffffff;
  font-weight: 600;
  &:hover {
    // background: #D6E4FF;
    border-color: #85a5ff;
    // color: #1D39C4;
  }
  .node-dot {
    background: #2639e9;
  }
}
.delete-current-node {
  width: 100%;
  color: #fff !important;
}
.node-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 5px;
}
.zoom-text {
  font-size: 12px;
  opacity: 0.85;
  min-width: 40px;
  text-align: center;
}
.flow-body {
  flex: 1;
  display: flex;
  min-height: 0;
  position: relative;
  background: #f7f8fa;
}
.flow-canvas {
  flex: 1;
  min-height: 0;
  min-width: 0;
}
.flow-canvas.is-node-active :deep(.lf-mini-map) {
  pointer-events: none;
}
.flow-hint {
  position: absolute;
  bottom: 24px;
  left: 50%;
  transform: translateX(-50%);
  text-align: center;
  color: #475569;
  pointer-events: none;
  .hint-icon {
    font-size: 32px;
    display: block;
    margin-bottom: 6px;
  }
  p {
    margin: 2px 0;
    font-size: 13px;
    line-height: 1.6;
  }
}
.flow-property {
  height: 100%;
  background: #fff;
  border-left: 1px solid #e8e8e8;
  overflow-y: auto;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  position: relative;
  min-width: 320px;
  max-width: 80vw;
}
.property-resize-handle {
  position: absolute;
  left: -4px;
  top: 0;
  width: 8px;
  height: 100%;
  cursor: col-resize;
  z-index: 5;
}
.property-resize-handle:hover {
  background: var(--tianshu-primary-shadow);
}
.prop-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-bottom: 1px solid #e8e8e8;
  background: #fafafa;
  flex-shrink: 0;
}
.prop-title {
  font-weight: bold;
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.prop-close {
  cursor: pointer;
  color: #64748b;
  &:hover {
    color: #333;
  }
}
.prop-section {
  padding: 10px 14px;
  border-bottom: 1px solid #f0f0f0;
  &:last-child {
    border-bottom: none;
  }
}
.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  font-size: 13px;
  font-weight: 600;
  color: #333;
}
.section-title :deep(.el-radio-button__inner) {
  min-width: 72px;
  padding: 6px 12px;
  color: #334155;
  border-color: #cbd5e1;
  background: #ffffff;
  box-shadow: none;
}
.section-title :deep(.el-radio-button__orig-radio:checked + .el-radio-button__inner) {
  background: #2639e9;
  border-color: #2639e9;
  color: #ffffff;
  box-shadow: none;
}
.prop-form {
  padding: 0;
}
.cond-builder,
.cond-script {
  padding: 0;
}
.cond-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
}
.cond-label {
  font-size: 12px;
  color: #888;
  width: 42px;
  flex-shrink: 0;
  text-align: right;
}
.generated-expr {
  margin-top: 8px;
  padding: 6px 8px;
  background: #f0f7ff;
  border: 1px solid #d6e4ff;
  border-radius: 4px;
  code {
    font-family: 'Consolas', monospace;
    font-size: 12px;
    color: var(--el-color-primary);
    word-break: break-all;
  }
}
.action-builder {
  padding: 0;
}
.branch-card {
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  margin-bottom: 8px;
  background: #fafafa;
  overflow: hidden;
  &.branch-else {
    border-color: #d9d9d9;
    background: #f5f5f5;
  }
}
.branch-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 8px;
  background: #f0f0f0;
  border-bottom: 1px solid #e8e8e8;
}
.branch-tag {
  font-size: 11px;
  font-weight: bold;
  padding: 1px 8px;
  border-radius: 3px;
  color: #fff;
  &.tag-if {
    background: var(--el-color-primary);
  }
  &.tag-elseif {
    background: #fa8c16;
  }
  &.tag-else {
    background: #8c8c8c;
  }
}
.branch-cond {
  padding: 6px 8px;
  border-bottom: 1px dashed #e8e8e8;
}
.branch-assigns {
  padding: 6px 8px;
}
.branch-actions {
  display: flex;
  gap: 6px;
  margin-top: 4px;
  flex-wrap: wrap;
}
.assign-row {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 6px;
}
.assign-target {
  flex: 1;
  min-width: 0;
}
.assign-eq {
  font-weight: bold;
  color: #64748b;
  flex-shrink: 0;
  font-size: 14px;
}
.assign-value {
  flex: 1;
  min-width: 0;
}
.out-edges {
  padding: 0;
}
.out-edge-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  border: 1px solid #e8e8e8;
  border-radius: 4px;
  margin-bottom: 6px;
  cursor: pointer;
  transition: all 0.15s;
  &:hover {
    background: #f0f7ff;
    border-color: var(--el-color-primary);
  }
}
.edge-idx {
  font-size: 11px;
  font-weight: bold;
  color: #fff;
  background: var(--el-color-primary);
  padding: 1px 6px;
  border-radius: 3px;
  flex-shrink: 0;
}
.edge-arrow {
  color: var(--el-color-primary);
  font-size: 13px;
}
.edge-name {
  flex: 1;
  font-size: 12px;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.hint-box {
  font-size: 12px;
  color: #64748b;
  padding: 6px 8px;
  background: #fafafa;
  border-radius: 4px;
  line-height: 1.6;
}
.mono-input :deep(textarea) {
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.5;
}
.script-readonly {
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.6;
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 10px 12px;
  color: #333;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 300px;
  overflow: auto;
  margin: 0;
}
.el-divider {
  margin: 10px 0 4px;
}
.panel-slide-enter-active,
.panel-slide-leave-active {
  transition: width 0.2s ease, opacity 0.2s;
}
.panel-slide-enter-from,
.panel-slide-leave-to {
  width: 0;
  opacity: 0;
}
@media (max-width: 1200px) {
  .flow-property {
    max-width: 60%;
  }
}
.test-hint {
  font-size: 12px;
  color: #64748b;
  margin-bottom: 8px;
}
.test-result {
  margin-top: 12px;
}
.result-pre {
  background: #f5f7fa;
  padding: 6px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-family: 'Consolas', monospace;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 150px;
  overflow: auto;
  margin: 0;
}
</style>

<style>
/* LogicFlow 全局覆盖 */
.lf-menu {
  background: #fff;
  border-radius: 4px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  border: 1px solid #e8e8e8;
  padding: 4px 0;
  min-width: 110px;
}
.lf-menu-item {
  padding: 6px 16px;
  font-size: 13px;
  cursor: pointer;
  color: #333;
}
.lf-menu-item:hover {
  background: #f0f7ff;
  color: var(--el-color-primary);
}
</style>
