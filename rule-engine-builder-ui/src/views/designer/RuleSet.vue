<template>
  <div class="rs-designer uiue-compact-workbench uiue-compact-designer">
    <rule-draft-read-only
      :visible="!canEditDraft"
      :loading="!draftGuardLoaded"
      :load-error="Boolean(draftGuardError)"
      :revision-label="viewRevisionLabel"
      :revision-state="viewRevision ? viewRevision.state : ''"
      :can-fork="canForkViewRevision"
      :has-editable-draft="hasPendingDraft"
      :source-options="designerSourceOptions"
      :selected-source="selectedDesignerSource"
      :source-loading="designerSourcesLoading"
      @go-back="$router.back()"
      @retry="startViewedRevisionRefresh(true)"
      @go-lifecycle="goRuleLifecycle"
      @fork="forkViewRevision"
      @change-source="switchDesignerSource"
    />
    <div class="rs-header">
      <div class="rs-title-area">
        <el-button
          link
          :icon="ElIconBack"
          class="rs-back"
          aria-label="返回"
          title="返回"
          @click="$router.back()"
        />
        <el-icon class="rs-title-icon"><el-icon-s-claim /></el-icon>
        <span class="rs-title">规则集配置</span>
        <el-tag size="small" type="info"
          >共 {{ model.rules.length }} 条规则</el-tag
        >
        <el-tag size="small" type="success"
          >启用 {{ enabledRuleCount }} 条</el-tag
        >
      </div>
      <div class="rs-toolbar">
        <rule-designer-version-select
          v-if="canEditDraft && designerSourceOptions.length"
          :options="designerSourceOptions"
          :model-value="selectedDesignerSource"
          :loading="designerSourcesLoading"
          @change="switchDesignerSource"
          @delete="deleteDesignerSource"
          :disabled="designerBusy"
        />
        <span class="toolbar-label">执行模式</span>
        <el-select
          v-model="model.executionMode"
          size="small"
          class="mode-select"
        >
          <el-option label="串行命中退出" value="SERIAL" />
          <el-option label="并行全部运行" value="PARALLEL" />
        </el-select>
        <el-tooltip
          :content="executionModeDesc"
          placement="bottom"
          effect="light"
        >
          <el-icon class="tip-icon"><el-icon-question /></el-icon>
        </el-tooltip>
        <el-divider direction="vertical" />
        <el-button size="small" :icon="ElIconPlus" @click="addRule"
          >添加规则</el-button
        >
        <el-button size="small" :icon="ElIconTime" @click="openVersionDialog"
          >版本历史</el-button
        >
        <rule-designer-action-bar
          @locate="locateDesignerIssue"
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

    <rule-designer-status :state="designerActionState" :field-count="varPickerOptions.length" :loading="loadingVars" :source-label="viewRevisionLabel" />
    <rule-designer-dialogs :choice="designerChoice" @resolve="resolveDesignerChoice" />




    <div class="rs-output-config">
      <div class="output-config-title">
        <span
          ><el-icon><el-icon-collection-tag /></el-icon> 命中结果输出字段</span
        >
        <el-tag size="small" type="info">可选</el-tag>
      </div>
      <div class="output-config-body">
        <operand-picker
          :value="model.resultVar && model.resultVar.operand"
          :vars="varPickerOptions"
          :selected-vars="listResultVarSelectedOptions"
          :allowed-kinds="resultOutputKinds"
          type-filter="LIST"
          writable-only
          clearable
          placeholder="选择 LIST 字段或手输路径"
          width="320px"
          @input="onResultVarInput"
          @path-resolve="onResultPathResolve"
        />
        <span class="output-config-hint"
          >保存完整的 list[rule] 命中列表；未配置时仅保留原有顶层返回值</span
        >
      </div>
    </div>

    <div class="rs-summary">
      <div class="summary-item">
        <span class="summary-label">排序规则</span>
        <span class="summary-value"
          >优先级越大越先执行；同优先级按页面顺序</span
        >
      </div>
      <div class="summary-item">
        <span class="summary-label">返回结果</span>
        <span class="summary-value">list[rule]，仅包含命中的规则</span>
      </div>
    </div>

    <div class="rs-rule-list">
      <template v-if="contentLoaded && model.rules.length > 0">
        <div
          v-for="(rule, index) in model.rules"
          :key="rule.uid || index"
          class="rs-rule-card"
          :data-validation-path="'$.rules[' + index + ']'"
          :class="{ 'is-disabled': !rule.enabled }"
          draggable="true"
          @dragstart="onDragStart(index)"
          @dragover.prevent
          @drop="onDrop(index)"
        >
          <div class="rs-rule-head">
            <span class="drag-handle" title="拖拽排序"
              ><el-icon><el-icon-rank /></el-icon
            ></span>
            <span class="rule-index">#{{ index + 1 }}</span>
            <el-switch
              v-model="rule.enabled"
              active-text="启用"
              inactive-text="停用"
            />
            <el-input
              v-model="rule.ruleCode"
              size="small"
              class="rule-code"
              placeholder="规则编码"
            />
            <el-input
              v-model="rule.ruleName"
              size="small"
              class="rule-name"
              placeholder="规则名称"
            />
            <el-input-number
              v-model="rule.priority"
              :min="1"
              :max="9999"
              :precision="0"
              size="small"
              class="rule-priority"
            />
            <div class="rule-actions">
              <el-button
                link
                size="small"
                :icon="ElIconTop"
                :disabled="index === 0"
                @click="moveRule(index, -1)"
              />
              <el-button
                link
                size="small"
                :icon="ElIconBottom"
                :disabled="index === model.rules.length - 1"
                @click="moveRule(index, 1)"
              />
              <el-button link size="small" @click="copyRule(index)"
                >复制</el-button
              >
              <el-button
                link
                size="small"
                class="btn-delete"
                @click="removeRule(index)"
                >删除</el-button
              >
            </div>
          </div>

          <div class="rs-rule-body">
            <div class="condition-panel">
              <div class="panel-title">命中条件</div>
              <condition-group-editor
                v-if="rule.conditionRoot"
                :group="rule.conditionRoot"
                :vars="varPickerOptions"
                :functions="projectFunctions"
                :list-options="projectLists"
                :selected-vars="selectedVarPickerOptions"
                :get-var-options-fn="getVarOptions"
              />
            </div>
            <div class="action-panel">
              <div class="panel-title">命中动作</div>
              <action-block-editor
                :action-data="rule.actionData"
                :vars="varPickerOptions"
                :selected-vars="selectedVarPickerOptions"
                :functions="projectFunctions"
                :list-options="projectLists"
                :rules="projectRules"
                :current-rule-id="definitionId"
                :current-rule-code="currentRuleCode"
                :validate-rule-call-cycle="validateRuleCallCycle"
                @update="(data) => updateRuleActionData(index, data)"
              />
            </div>
          </div>
        </div>
      </template>

      <div v-if="!contentLoaded" class="rs-loading">
        <el-icon><el-icon-loading /></el-icon> 加载规则集数据...
      </div>
      <div
        v-else-if="contentLoaded && model.rules.length === 0"
        class="rs-empty"
      >
        <el-icon class="rs-empty-icon"><el-icon-s-claim /></el-icon>
        <p>暂无规则，请点击「添加规则」开始配置。</p>
      </div>
    </div>

    <script-panel
      v-if="definitionId"
      :compile-result="designerCompileResult"
    />

    <designer-test-dialog
      v-model:visible="testVisible"
      :definition-id="definitionId"
      :project-id="projectIdForRefs"
      model-type="RULE_SET"
      :model-json-provider="serializeDesignerDraft"
      :params-template="testParamsTemplate"
    />

    <el-dialog
      title="规则集版本历史"
      v-model="versionVisible"
      width="920px"
      append-to-body
    >
      <el-table
        :data="versions"
        border
        size="small"
        v-loading="versionLoading"
        max-height="360"
      >
        <el-table-column prop="version" label="版本" width="80">
          <template v-slot="{ row }">v{{ row.version }}</template>
        </el-table-column>
        <el-table-column
          prop="changeLog"
          label="变更说明"
          min-width="180"
          show-overflow-tooltip
        />
        <el-table-column prop="publishBy" label="发布人" width="120" />
        <el-table-column prop="publishTime" label="发布时间" width="170">
          <template v-slot="{ row }">{{
            formatVersionTime(row.publishTime)
          }}</template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template v-slot="{ row, $index }">
            <el-button
              link
              size="small"
              type="primary"
              @click="viewVersion(row)"
              >查看内容</el-button
            >
            <el-button
              link
              size="small"
              type="info"
              :disabled="$index === versions.length - 1"
              @click="compareVersion(row, versions[$index + 1])"
              >对比上一版</el-button
            >
            <el-button
              link
              size="small"
              type="warning"
              @click="loadVersionInDesigner(row)"
              >载入此版本</el-button
            >
            <el-button
              link
              size="small"
              type="success"
              @click="openLifecycle"
              >评审与发布</el-button
            >
          </template>
        </el-table-column>
      </el-table>

      <div v-if="versionCompare" class="version-compare">
        <el-alert
          :title="
            'v' +
            versionCompare.left.version +
            ' 与 v' +
            versionCompare.right.version +
            ' 对比'
          "
          :type="
            versionCompare.modelJsonChanged ||
            versionCompare.compiledScriptChanged
              ? 'warning'
              : 'success'
          "
          :closable="false"
        />
        <div class="version-diff-grid">
          <pre>{{ formatVersionJson(versionCompare.left.modelJson) }}</pre>
          <pre>{{ formatVersionJson(versionCompare.right.modelJson) }}</pre>
        </div>
      </div>
    </el-dialog>

    <el-dialog
      :title="versionViewTitle"
      v-model="versionViewVisible"
      width="760px"
      append-to-body
    >
      <pre class="version-json">{{ versionViewContent }}</pre>
    </el-dialog>
  </div>
</template>

<script>
import { markRaw } from 'vue'
import {
  DocumentChecked as ElIconSClaim,
  QuestionFilled as ElIconQuestion,
  Loading as ElIconLoading,
  CollectionTag as ElIconCollectionTag,
  Rank as ElIconRank,
  Back as ElIconBack,
  Plus as ElIconPlus,
  Clock as ElIconTime,
  Document as ElIconDocument,
  Cpu as ElIconCpu,
  VideoPlay as ElIconVideoPlay,
  Top as ElIconTop,
  Bottom as ElIconBottom,
} from '@element-plus/icons-vue'
import {
  listVersions,
  getVersion,
  compareVersions,
} from '@/api/definition'
import varPickerMixin from '@/mixins/varPickerMixin'
import ruleCallMixin from '@/mixins/ruleCallMixin'
import ruleDraftMixin from '@/mixins/ruleDraftMixin'
import ScriptPanel from '@/components/common/ScriptPanel.vue'
import DesignerTestDialog from '@/components/common/DesignerTestDialog.vue'
import RuleDraftReadOnly from '@/components/rule/RuleDraftReadOnly.vue'
import RuleDesignerVersionSelect from '@/components/rule/RuleDesignerVersionSelect.vue'
import RuleDesignerActionBar from '@/components/rule/RuleDesignerActionBar.vue'
import OperandPicker from '@/components/common/OperandPicker.vue'
import ConditionGroupEditor from '@/components/decision/ConditionGroupEditor.vue'
import ActionBlockEditor from '@/components/flow/ActionBlockEditor.vue'
import { actionDataToBlocks, newBlock } from '@/utils/actionDataCodegen'
import {
  createEmptyGroup,
  createEmptyLeaf,
  migrateRuleConditionsToTree,
  collectVarCodesFromConditionTree,
  walkConditionLeaves,
  normalizeConditionTreeOperands,
} from '@/utils/decisionConditionTree'
import {
  collectOperandReferences,
  operandFromReferenceFields,
  syncOperandReference,
} from '@/utils/operand'
import {
  buildSampleParamsFromCodes,
  collectActionDataInputCodes,
} from '@/utils/testSampleParams'

export default {
  data() {
    return {
      definitionId: null,
      model: {
        executionMode: 'SERIAL',
        resultVar: {},
        rules: [],
      },
      resultOutputKinds: ['PATH', 'REFERENCE'],
      contentLoaded: false,
      draggedIndex: -1,
      testVisible: false,
      testParamsTemplate: {},
      testParams: {},
      testResult: null,
      versionVisible: false,
      versionLoading: false,
      versions: [],
      versionCompare: null,
      versionViewVisible: false,
      versionViewTitle: '',
      versionViewContent: '',
      ElIconBack: markRaw(ElIconBack),
      ElIconPlus: markRaw(ElIconPlus),
      ElIconTime: markRaw(ElIconTime),
      ElIconDocument: markRaw(ElIconDocument),
      ElIconCpu: markRaw(ElIconCpu),
      ElIconVideoPlay: markRaw(ElIconVideoPlay),
      ElIconTop: markRaw(ElIconTop),
      ElIconBottom: markRaw(ElIconBottom),
    }
  },
  components: {
    RuleDesignerActionBar,
    RuleDraftReadOnly,
    RuleDesignerVersionSelect,
    DesignerTestDialog,
    ScriptPanel,
    OperandPicker,
    ConditionGroupEditor,
    ActionBlockEditor,
    ElIconSClaim,
    ElIconQuestion,
    ElIconLoading,
    ElIconCollectionTag,
    ElIconRank,
  },
  name: 'RuleSet',
  mixins: [varPickerMixin, ruleCallMixin, ruleDraftMixin],
  computed: {
    enabledRuleCount() {
      return (this.model.rules || []).filter((rule) => rule.enabled !== false)
        .length
    },
    executionModeDesc() {
      if (this.model.executionMode === 'PARALLEL') {
        return '并行全部运行：按优先级和页面顺序检查全部规则，命中的规则都会执行动作并进入返回列表'
      }
      return '串行命中退出：按优先级和页面顺序检查规则，命中第一条后执行动作并停止'
    },
    listResultVarSelectedOptions() {
      return (this.selectedVarPickerOptions || []).filter(
        (item) =>
          String(item.varType || item.valueType || '').toUpperCase() === 'LIST'
      )
    },
    testVarCodeList() {
      const s = new Set()
      ;(this.model.rules || []).forEach((rule) => {
        if (rule.enabled === false) return
        collectVarCodesFromConditionTree(rule.conditionRoot, s)
        collectActionDataInputCodes(rule.actionData, this.projectRefs, s)
      })
      return Array.from(s)
    },
  },
  created() {
    this.definitionId = this.$route.params.id
    this.loadProjectVars(this.definitionId)
    this.loadRuleCallOptions(this.definitionId)
    this.loadContent()
  },
  methods: {
    async loadContent() {
      try {
        if (this.draftGuardPromise) await this.draftGuardPromise
        const content = this.viewRevision
        if (content && content.modelJson) {
          this.model = JSON.parse(content.modelJson)
        }
        this.normalizeModel()
      } catch (e) {
        this.$message.error('加载内容失败: ' + (e.message || '未知错误'))
      } finally {
        this.contentLoaded = true
        this.$nextTick(() =>
          this.initializeDesignerDraftTracking(this.serializeDesignerDraft())
        )
      }
    },
    normalizeModel() {
      if (!this.model || typeof this.model !== 'object') {
        this.model = { executionMode: 'SERIAL', resultVar: {}, rules: [] }
      }
      if (!this.model.executionMode) this.model['executionMode'] = 'SERIAL'
      if (!this.model.resultVar || typeof this.model.resultVar !== 'object')
        this.model['resultVar'] = {}
      if (!this.model.resultVar.operand) {
        const operand = operandFromReferenceFields(this.model.resultVar)
        if (operand) this.model.resultVar['operand'] = operand
      }
      if (!Array.isArray(this.model.rules)) this.model['rules'] = []
      const legacyCols = Array.isArray(this.model.conditions)
        ? this.model.conditions
        : []
      this.model.rules.forEach((rule, index) => {
        if (!rule.uid) rule['uid'] = this.createRuleUid()
        if (!rule.ruleCode) rule['ruleCode'] = this.nextRuleCode(index)
        if (!rule.ruleName) rule['ruleName'] = '规则' + (index + 1)
        if (rule.priority === undefined || rule.priority === null)
          rule['priority'] = 1
        if (rule.enabled === undefined) rule['enabled'] = rule.status !== 0
        if (!rule.conditionRoot) {
          const migrated =
            Array.isArray(rule.conditions) && rule.conditions.length
              ? migrateRuleConditionsToTree(rule.conditions, legacyCols)
              : createEmptyGroup('AND')
          if (!migrated.children || !migrated.children.length)
            migrated.children = [createEmptyLeaf()]
          rule['conditionRoot'] = migrated
        }
        normalizeConditionTreeOperands(rule.conditionRoot)
        const actionData = actionDataToBlocks(rule.actionData)
        rule['actionData'] = actionData.length
          ? actionData
          : [newBlock('assign')]
        if (rule.status !== undefined) delete rule.status
        if (rule.conditions !== undefined) delete rule.conditions
      })
      if (this.model.conditions !== undefined) delete this.model.conditions
      if (this.model.actions !== undefined) delete this.model.actions
    },
    _syncModelVarRefs() {
      let changed = false
      const sync = (leaf, field) => {
        const result = syncOperandReference(leaf[field], this.varPickerOptions)
        if (!result.changed) return
        leaf[field] = result.operand
        changed = true
      }
      if (this.model.resultVar && this.model.resultVar.operand) {
        const result = syncOperandReference(
          this.model.resultVar.operand,
          this.varPickerOptions
        )
        if (result.changed) {
          this.setResultVarOperand(result.operand)
          changed = true
        }
      }
      (this.model.rules || []).forEach((rule) => {
        walkConditionLeaves(rule.conditionRoot, (leaf) => {
          sync(leaf, 'leftOperand')
          sync(leaf, 'rightOperand')
        })
        if (this.syncActionDataVarRefs(rule.actionData || [])) changed = true
      })
      if (changed) this.$forceUpdate()
    },
    collectSelectedVarItems() {
      const items = []
      const addOperand = (operand) =>
        collectOperandReferences(operand).forEach((reference) =>
          items.push({
            varCode: reference.code,
            varType: reference.valueType,
            _varId: reference.refId,
            _refType: reference.refType,
          })
        )
      addOperand(this.model.resultVar && this.model.resultVar.operand)
      ;(this.model.rules || []).forEach((rule) => {
        walkConditionLeaves(rule.conditionRoot, (leaf) => {
          addOperand(leaf.leftOperand)
          addOperand(leaf.rightOperand)
        })
        items.push(...this.collectActionDataVarItems(rule.actionData || []))
      })
      return items
    },
    resultVarError(operand) {
      if (!operand) return ''
      if (String(operand.valueType || '').toUpperCase() !== 'LIST')
        return '规则集命中结果输出字段必须是 LIST 类型'
      if (
        operand.resolved !== true ||
        operand.refId == null ||
        !operand.refType
      )
        return '规则集命中结果输出字段必须反查到稳定字段引用'
      if (!['VARIABLE', 'DATA_OBJECT'].includes(operand.refType))
        return '规则集命中结果输出字段仅支持普通变量或数据对象字段'
      if (!['PATH', 'REFERENCE'].includes(operand.kind))
        return '规则集命中结果输出字段必须是字段引用'
      return ''
    },
    setResultVarOperand(operand) {
      const value = JSON.parse(JSON.stringify(operand))
      const code = value.code || value.value || ''
      this.model['resultVar'] = {
        operand: value,
        varCode: code,
        varLabel: value.label || code,
        varType: value.valueType || '',
        _varId: value.refId,
        _refType: value.refType,
      }
    },
    onResultVarInput(operand) {
      if (!operand) {
        this.model['resultVar'] = {}
        return
      }
      const error = this.resultVarError(operand)
      if (!error) {
        this.setResultVarOperand(operand)
      } else if (
        operand.kind !== 'PATH' &&
        operand.resolved === true &&
        this.$message
      ) {
        this.$message.warning(error)
      }
    },
    onResultPathResolve(payload) {
      const operand = payload && payload.operand
      const candidates = (payload && payload.candidates) || []
      if (candidates.length > 1) {
        if (this.$message)
          this.$message.warning(
            '该路径匹配到多个字段，请从候选项中选择唯一字段'
          )
        return
      }
      if (!operand || operand.resolved !== true) {
        if (this.$message)
          this.$message.warning('未找到该路径对应的字段，无法配置规则集输出')
        return
      }
      const error = this.resultVarError(operand)
      if (error) {
        if (this.$message) this.$message.warning(error)
        return
      }
      this.setResultVarOperand(operand)
    },
    validateResultVar() {
      const resultVar = this.model && this.model.resultVar
      if (!resultVar || !Object.keys(resultVar).length) return ''
      return this.resultVarError(resultVar.operand)
    },
    addRule() {
      this.model.rules.push({
        uid: this.createRuleUid(),
        ruleCode: this.nextRuleCode(this.model.rules.length),
        ruleName: '规则' + (this.model.rules.length + 1),
        priority: 1,
        enabled: true,
        conditionRoot: {
          type: 'group',
          op: 'AND',
          children: [createEmptyLeaf()],
        },
        actionData: [newBlock('assign')],
      })
    },
    copyRule(index) {
      const orig = this.model.rules[index]
      if (!orig) return
      const copy = JSON.parse(JSON.stringify(orig))
      copy.uid = this.createRuleUid()
      copy.ruleCode = this.nextRuleCode(this.model.rules.length)
      copy.ruleName = copy.ruleName + ' 副本'
      this.model.rules.splice(index + 1, 0, copy)
    },
    removeRule(index) {
      this.$confirm('确认删除该规则？', '提示', { type: 'warning' })
        .then(() => {
          this.model.rules.splice(index, 1)
        })
        .catch(() => {})
    },
    moveRule(index, step) {
      const target = index + step
      if (target < 0 || target >= this.model.rules.length) return
      const rows = this.model.rules
      const item = rows.splice(index, 1)[0]
      rows.splice(target, 0, item)
    },
    onDragStart(index) {
      this.draggedIndex = index
    },
    onDrop(index) {
      if (this.draggedIndex < 0 || this.draggedIndex === index) return
      const item = this.model.rules.splice(this.draggedIndex, 1)[0]
      this.model.rules.splice(index, 0, item)
      this.draggedIndex = -1
    },
    updateRuleActionData(index, data) {
      const rule = this.model.rules[index]
      if (!rule) return
      rule['actionData'] = Array.isArray(data) ? data : []
    },
    nextRuleCode(index) {
      let text = String(index + 1)
      while (text.length < 4) text = '0' + text
      return 'R' + text
    },
    createRuleUid() {
      return 'rs-' + Date.now() + '-' + Math.random().toString(36).slice(2, 8)
    },
    async performDesignerSave() {
      this.normalizeModel()
      this.repairLegacyRuleCallRefs(this.model)
      const model = this.serializeModel()
      const modelJson = JSON.stringify(model)
      const result = await this.saveDraftModel(modelJson)
      if (!result) return false
      this.refreshProjectRefs()
      this.$message.success('草稿已保存')
      return result

    },
    serializeModel() {
      const copy = JSON.parse(JSON.stringify(this.model))
      ;(copy.rules || []).forEach((rule) => {
        delete rule.uid
        rule.status = rule.enabled === false ? 0 : 1
      })
      if (!copy.resultVar || !copy.resultVar.operand) delete copy.resultVar
      return copy
    },
    serializeDesignerDraft() {
      return JSON.stringify(this.serializeModel())
    },
    buildRuleCallValidationModel() {
      return this.serializeModel()
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
    buildTestParamsTemplate() {
      return buildSampleParamsFromCodes(this.testVarCodeList, this.projectRefs)
    },
    async handleTest() {
      if (!this.ensureDesignerReadyForTest()) return
      const template = this.buildTestParamsTemplate()
      this.testParamsTemplate = template
      this.testParams = template
      this.testResult = null
      this.testVisible = true
    },
    async doTest() {
      const res = await this.executeDesignerPreview(this.testParams, 'RULE_SET')
      this.testResult = res && res.data ? res.data : res
    },
    testVarLabel(code) {
      const ref = this.projectRefs.find((r) => r.refCode === code)
      if (ref && ref.varObj && ref.varObj.varLabel) return ref.varObj.varLabel
      if (ref && ref.refLabel && ref.refLabel.label) return ref.refLabel.label
      return code
    },
    testVarMeta(code) {
      const ref = this.projectRefs.find((r) => r.refCode === code)
      const vt = (ref && ref.varType) || 'STRING'
      let enumOptions = ''
      if (vt === 'ENUM' && ref && ref.varObj) {
        const opts = this.getVarOptions(code) || []
        enumOptions = opts
          .map((o) => o.value || o.optionValue)
          .filter(Boolean)
          .join(',')
      }
      return { varType: vt, enumOptions }
    },
    testEnumOpts(code) {
      const m = this.testVarMeta(code)
      return m.enumOptions
        ? m.enumOptions
            .split(',')
            .map((s) => s.trim())
            .filter(Boolean)
        : []
    },
    formatResult(val) {
      if (val === null || val === undefined) return '(空)'
      try {
        return JSON.stringify(
          typeof val === 'string' ? JSON.parse(val) : val,
          null,
          2
        )
      } catch (e) {
        return String(val)
      }
    },
    async openVersionDialog() {
      this.versionVisible = true
      this.versionCompare = null
      await this.loadVersions()
    },
    async loadVersions() {
      if (!this.definitionId) return
      this.versionLoading = true
      try {
        const res = await listVersions(this.definitionId)
        this.versions = Array.isArray(res.data)
          ? res.data
          : Array.isArray(res)
          ? res
          : []
      } catch (e) {
        this.$message.error(e.message || '加载版本历史失败')
      } finally {
        this.versionLoading = false
      }
    },
    async viewVersion(row) {
      if (!row || !row.version) return
      try {
        const res = await getVersion(this.definitionId, row.version)
        const version = res && res.data ? res.data : res
        this.versionViewTitle = '规则集版本 v' + row.version
        this.versionViewContent = this.formatVersionJson(version.modelJson)
        this.versionViewVisible = true
      } catch (e) {
        this.$message.error(e.message || '查看版本失败')
      }
    },
    async compareVersion(left, right) {
      if (!left || !right) return
      try {
        const res = await compareVersions(
          this.definitionId,
          left.version,
          right.version
        )
        this.versionCompare = res && res.data ? res.data : res
      } catch (e) {
        this.$message.error(e.message || '版本对比失败')
      }
    },
    async loadVersionInDesigner(row) {
      if (!row || !row.id) {
        this.$message.warning('版本缺少记录 ID，请刷新版本列表')
        return
      }
      try {
        await this.switchDesignerSource(`VERSION:${row.id}`)
        if (this.selectedDesignerSource === `VERSION:${row.id}`) {
          this.versionVisible = false
        }
      } catch (e) {
        this.$message.error(e.message || '版本载入失败')
      }
    },
    openLifecycle() {
      this.$router.push({
        name: 'RuleDetail',
        params: { id: this.definitionId },
        query: { focus: 'lifecycle' },
      })
    },
    formatVersionTime(value) {
      return value ? String(value).replace('T', ' ') : '-'
    },
    formatVersionJson(value) {
      if (!value) return ''
      try {
        return JSON.stringify(JSON.parse(value), null, 2)
      } catch (e) {
        return String(value)
      }
    },
  },
}
</script>

<style lang="scss" scoped>
.rs-designer {
  position: relative;
  background: var(--tianshu-bg-surface);
  border-radius: 4px;
  padding: 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
  min-height: 100%;
  box-sizing: border-box;
}
.rs-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}
.rs-title-area,
.rs-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.rs-back {
  color: var(--tianshu-text-secondary);
}
.rs-title-icon {
  font-size: 18px;
  color: var(--el-color-primary);
}
.rs-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--tianshu-text-primary);
}
.toolbar-label {
  font-size: 13px;
  color: var(--tianshu-text-secondary);
}
.mode-select {
  width: 148px;
}
.tip-icon {
  color: var(--tianshu-text-tertiary);
  cursor: pointer;
}
.rs-var-status {
  margin: 8px 0;
  font-size: 12px;
  color: var(--el-color-primary);
}
.rs-output-config {
  margin: 10px 0;
  padding: 10px;
  border: 1px solid var(--tianshu-designer-accent-border);
  border-radius: 6px;
  background: var(--tianshu-designer-accent-bg);
}
.output-config-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  color: var(--tianshu-text-primary);
  font-size: 13px;
  font-weight: 700;
}
.output-config-title i {
  color: var(--el-color-primary);
}
.output-config-body {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.output-config-hint {
  color: var(--tianshu-text-tertiary);
  font-size: 12px;
}
.rs-summary {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 8px;
  margin: 10px 0;
  padding: 8px 10px;
  border: 1px solid var(--tianshu-border-subtle);
  border-radius: 6px;
  background: var(--tianshu-bg-soft);
}
.summary-item {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.summary-label {
  color: var(--tianshu-text-tertiary);
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}
.summary-value {
  color: var(--tianshu-text-primary);
  font-size: 13px;
  overflow-wrap: anywhere;
}
.rs-rule-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.rs-rule-card {
  border: 1px solid var(--tianshu-border-subtle);
  border-radius: 6px;
  background: var(--tianshu-bg-surface);
  padding: 10px;
}
.rs-rule-card.is-disabled {
  background: var(--tianshu-bg-soft);
  opacity: 0.78;
}
.rs-rule-head {
  display: grid;
  grid-template-columns: 26px 42px 108px minmax(110px, 150px) minmax(160px, 1fr) 120px auto;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
.drag-handle {
  cursor: move;
  color: var(--tianshu-text-disabled);
  font-size: 16px;
  text-align: center;
}
.rule-index {
  font-weight: 700;
  color: var(--tianshu-text-secondary);
}
.rule-code,
.rule-name,
.rule-priority {
  width: 100%;
}
.rule-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 4px;
}
.btn-delete {
  color: var(--tianshu-danger-text);
}
.rs-rule-body {
  display: grid;
  grid-template-columns: minmax(360px, 1.1fr) minmax(340px, 0.9fr);
  gap: 12px;
}
.condition-panel,
.action-panel {
  min-width: 0;
  border: 1px solid var(--tianshu-border-subtle);
  border-radius: 6px;
  padding: 8px;
  background: var(--tianshu-bg-soft);
}
.panel-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--tianshu-text-primary);
  margin-bottom: 8px;
}
.rs-loading,
.rs-empty {
  text-align: center;
  color: var(--tianshu-text-tertiary);
  padding: 36px 0;
  border: 1px dashed var(--tianshu-border);
  border-radius: 6px;
}
.rs-empty-icon {
  font-size: 30px;
  color: var(--tianshu-text-tertiary);
}
.script-override-banner {
  margin-top: 10px;
  padding: 8px 10px;
  border: 1px solid var(--tianshu-warning-border);
  background: var(--tianshu-warning-bg);
  color: var(--tianshu-warning-text);
  border-radius: 4px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.test-alert {
  margin-bottom: 10px;
}
.result-pre,
.version-json,
.version-diff-grid pre {
  margin: 0;
  padding: 10px;
  background: #0f172a;
  color: #e2e8f0;
  border-radius: 4px;
  max-height: 360px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
}
.text-danger {
  color: var(--tianshu-danger-text);
}
.version-compare {
  margin-top: 12px;
}
.version-diff-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-top: 10px;
}
@media (max-width: 980px) {
  .rs-rule-head,
  .rs-rule-body,
  .version-diff-grid {
    grid-template-columns: 1fr;
  }
  .rule-actions {
    justify-content: flex-start;
  }
}
@media (max-width: 1200px) {
  .rs-designer {
    padding: 12px;
  }
}
</style>
