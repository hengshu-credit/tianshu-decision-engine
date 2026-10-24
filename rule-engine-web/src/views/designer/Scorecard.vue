<template>
  <div class="sc-designer uiue-compact-workbench uiue-compact-designer">
    <!-- 顶部工具栏 -->
    <div class="sc-header">
      <div class="sc-title-area">
        <el-button
          size="small"
          :icon="ElIconBack"
          class="uiue-detail-back"
          aria-label="返回"
          data-designer-back
          title="返回"
          @click="$router.back()"
        />
        <el-icon class="sc-title-icon"><el-icon-data-line /></el-icon>
        <span class="sc-title">评分卡设计器</span>
        <el-tag size="small" type="info" style="margin-left: 8px"
          >{{ model.scoreItems.length }} 个评分项</el-tag
        >
      </div>
      <div class="sc-toolbar designer-version-toolbar">
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
          @test-frozen="openTestDialog"
          @open-migration="$router.push('/transfer')"
          @retry="startViewedRevisionRefresh(true)"
          @go-lifecycle="goRuleLifecycle"
          @fork="forkViewRevision"
          @change-source="switchDesignerSource"
          @delete-source="deleteDesignerSource"
        />
        <el-button size="small" :icon="ElIconPlus" @click="addScoreItem"
          >添加评分项</el-button
        >
        <el-button size="small" :icon="ElIconPlus" @click="addThreshold"
          >添加等级</el-button
        >
        <el-divider direction="vertical" />
        <rule-designer-action-bar
          @check="handleCheck"
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
          @test="openTestDialog"
          @publish="handlePublish"
          @restore="restoreDesignerRecovery"
          @discard-recovery="discardDesignerRecovery"
        />
      </div>
    </div>

    <rule-designer-status :state="designerActionState" :field-count="varPickerOptions.length" :loading="loadingVars" :source-label="viewRevisionLabel" :reference-mode="referenceCatalogMode" :can-use-current-resources="canEnterDesignerLivePreview" @use-current="runDesignerAction(enterDesignerLivePreview)" />
    <rule-designer-dialogs :choice="designerChoice" @resolve="resolveDesignerChoice" />



    <!-- 基础配置 -->
    <div class="sc-card sc-base-config">
      <div class="sc-card-title">
        <el-icon><el-icon-setting /></el-icon> 基础配置
      </div>
      <div class="base-config-row">
        <div class="base-config-item">
          <span class="base-config-label">初始分数</span>
          <el-input-number
            v-model="model.initialScore"
            :min="0"
            :max="1000"
            size="small"
            style="width: 130px"
          />
        </div>
        <div class="base-config-item">
          <span class="base-config-label">评分赋值字段</span>
          <div class="result-var-picker">
            <operand-picker
              :vars="varPickerOptions"
              :selected-vars="selectedVarPickerOptions"
              :value="model.resultVar.operand"
              :allowed-kinds="writeOperandKinds"
              writable-only
              placeholder="选择结果字段或路径"
              width="200px"
              @input="onResultOperandSelect"
            />
          </div>
          <span v-if="model.resultVar.varLabel" class="result-var-label">{{
            model.resultVar.varLabel
          }}</span>
        </div>
        <div class="base-config-item">
          <span class="base-config-label">等级赋值字段（可选）</span>
          <operand-picker
            data-testid="scorecard-level-target"
            :vars="varPickerOptions"
            :selected-vars="selectedVarPickerOptions"
            :value="model.levelVar?.operand"
            :allowed-kinds="writeOperandKinds"
            writable-only
            placeholder="不选则仅输出分数"
            width="220px"
            @input="setLevelOperand"
          />
        </div>
      </div>
      <div class="level-config-hint">未选择等级赋值字段或未配置等级区间时，仅输出分数。</div>
    </div>

    <!-- 评分项配置 -->
    <div class="sc-card">
      <div class="sc-card-title sc-card-title-row">
        <span
          ><el-icon><el-icon-s-check /></el-icon> 评分项配置</span
        >
        <div class="weight-summary">
          <span class="weight-label">总权重：</span>
          <el-progress
            :percentage="totalWeightPercent"
            :color="totalWeightColor"
            :stroke-width="10"
            style="width: 150px; display: inline-block; vertical-align: middle"
          />
          <span class="weight-value" :style="{ color: totalWeightColor }">{{
            totalWeightDisplay
          }}</span>
        </div>
      </div>

      <div class="score-items">
        <div
          v-for="(item, idx) in model.scoreItems"
          :key="idx"
          class="score-item-card"
          :data-validation-path="'$.scoreItems[' + idx + ']'"
        >
          <div class="score-item-header">
            <span class="item-index">{{ idx + 1 }}</span>
            <el-input
              v-model="item.conditionLabel"
              size="small"
              placeholder="评分项名称（如 纳税信用等级）"
              class="item-label-input"
            />
            <el-button
              link
              size="small"
              :icon="ElIconDelete"
              class="btn-delete"
              @click="removeScoreItem(idx)"
            />
          </div>

          <div class="score-item-body">
            <div class="score-item-row">
              <span class="item-field-label">条件</span>
              <div class="condition-row">
                <operand-picker
                  :vars="varPickerOptions"
                  :selected-vars="selectedVarPickerOptions"
                  :value="item.leftOperand"
                  :allowed-kinds="readOperandKinds"
                  placeholder="选择条件字段或路径"
                  width="100%"
                  class="cond-var"
                  @input="
                    (operand) =>
                      setScoreItemOperand(item, 'leftOperand', operand)
                  "
                />
                <el-select
                  v-model="item.condOperator"
                  size="small"
                  class="cond-op"
                  @change="onConditionOperatorChange(item)"
                >
                  <el-option-group
                    v-for="operatorGroup in conditionOperatorGroups(item)"
                    :key="operatorGroup.label"
                    :label="operatorGroup.label"
                  >
                    <el-option
                      v-for="option in operatorGroup.options"
                      :key="option.value"
                      :label="option.label"
                      :value="option.value"
                    />
                  </el-option-group>
                </el-select>
                <operand-picker
                  v-if="conditionRequiresValue(item)"
                  :value="item.rightOperand"
                  :condition-operator="item.condOperator"
                  :vars="varPickerOptions"
                  :functions="projectFunctions"
                  :selected-vars="selectedVarPickerOptions"
                  :allowed-kinds="conditionRightAllowedKinds(item)"
                  :context="conditionRightContext(item)"
                  :expected-type="conditionRightExpectedType(item)"
                  placeholder="选择阈值、路径或字段"
                  class="cond-val"
                  @input="
                    (operand) =>
                      setScoreItemOperand(item, 'rightOperand', operand)
                  "
                />
              </div>
            </div>

            <div class="score-item-row score-weight-row">
              <div class="score-col">
                <span class="item-field-label">命中得分</span>
                <el-input-number
                  v-model="item.score"
                  :min="0"
                  :max="9999"
                  size="small"
                  style="width: 120px"
                />
              </div>
              <div class="weight-col">
                <span class="item-field-label">
                  权重
                  <el-tooltip
                    content="权重决定该项分数在总分中的占比（0~2，推荐各项权重加总=1.0）"
                    placement="top"
                    effect="light"
                  >
                    <el-icon class="tip-icon"><el-icon-question /></el-icon>
                  </el-tooltip>
                </span>
                <div class="weight-slider-row">
                  <el-slider
                    v-model="item.weight"
                    :min="0"
                    :max="2"
                    :step="0.05"
                    :format-tooltip="(v) => (v == null ? '0.00' : v.toFixed(2))"
                    show-input
                    input-size="small"
                    style="flex: 1"
                  />
                </div>
              </div>
              <div class="weighted-score">
                <span class="item-field-label">加权分</span>
                <span class="weighted-value">{{
                  ((item.score || 0) * (item.weight || 0)).toFixed(1)
                }}</span>
              </div>
            </div>
          </div>
        </div>

        <div v-if="model.scoreItems.length === 0" class="sc-empty">
          <el-icon class="sc-empty-icon"><el-icon-s-check /></el-icon>
          <p>暂无评分项，点击「添加评分项」开始配置</p>
        </div>
      </div>
    </div>

    <!-- 计算公式预览 -->
    <div class="sc-card sc-formula">
      <div class="sc-card-title">
        <el-icon><el-icon-files /></el-icon> 计算公式预览
      </div>
      <div class="formula-content">
        <div class="formula-text">
          <code>{{ scoreResultLabel }}</code>
          <span class="op"> = </span>
          <span v-if="model.initialScore !== 0">
            <code>{{ model.initialScore }}</code>
            <span class="op"> + </span>
          </span>
          <template v-for="(item, idx) in model.scoreItems" :key="idx">
            <span class="formula-term">
              <span class="formula-cond">满足（{{ scoreConditionSummary(item) }}）时计</span>
              <code>{{ item.score }}</code>
              <template v-if="item.weight !== 1">
                <span class="op"> × </span>
                <code>{{ (item.weight || 0).toFixed(2) }}</code>
              </template>
            </span>
            <span v-if="idx < model.scoreItems.length - 1" class="op"> + </span>
          </template>
          <span v-if="model.scoreItems.length === 0" class="formula-empty"
            >（暂未配置评分项）</span
          >
        </div>
      </div>
    </div>

    <!-- 分数等级配置 -->
    <div class="sc-card">
      <div class="sc-card-title">
        <el-icon><el-icon-medal /></el-icon> 分数等级配置（可选）
      </div>
      <score-threshold-editor
        :thresholds="model.thresholds"
        compact
        @change-min="(threshold, value) => threshold.min = value"
        @change-max="(threshold, value) => threshold.max = value"
        @remove="removeThreshold"
      >
        <template #result="{ threshold }">
          <operand-picker
            :value="threshold.resultOperand"
            :vars="varPickerOptions"
            :functions="projectFunctions"
            :allowed-kinds="valueOperandKinds"
            expected-type="STRING"
            placeholder="选择等级结果值"
            style="width: 100%; min-width: 240px"
            @input="(operand) => setThresholdOperand(threshold, operand)"
          />
        </template>
        <template #empty><div class="sc-empty">暂未配置等级，点击「添加等级」设置分数区间</div></template>
      </score-threshold-editor>
    </div>

    <!-- 脚本预览/编辑面板 -->
    <script-panel
      v-if="definitionId"
      :compile-result="designerCompileResult"
      :references="projectRefs"
      :version-references="scriptVersionReferences"
      :functions="projectFunctions"
    />

    <designer-test-dialog
      v-model:visible="testVisible"
      :execution-source="designerTestExecutionSource"
      :execution-source-name="viewRevisionLabel"
      :definition-id="definitionId"
      :project-id="projectIdForRefs"
      model-type="SCORE"
      :model-json-provider="serializeDesignerDraft"
      :params-template="testParamsTemplate"
    />
  </div>
</template>

<script>
import ScoreThresholdEditor from '@/components/decision/ScoreThresholdEditor.vue'
import { parseDesignerModelJson } from '@/utils/designerModelJson'
import { markRaw } from 'vue'
import {
  DataLine as ElIconDataLine,
  Setting as ElIconSetting,
  Finished as ElIconSCheck,
  QuestionFilled as ElIconQuestion,
  Files as ElIconFiles,
  Medal as ElIconMedal,
  Back as ElIconBack,
  Plus as ElIconPlus,
  Document as ElIconDocument,
  Cpu as ElIconCpu,
  VideoPlay as ElIconVideoPlay,
  Delete as ElIconDelete,
} from '@element-plus/icons-vue'
import varPickerMixin from '@/mixins/varPickerMixin'
import ruleDraftMixin from '@/mixins/ruleDraftMixin'
import { formatExpressionFormula, formatConditionDescription } from '@/utils/expressionDisplay'
import DesignerTestDialog from '@/components/common/DesignerTestDialog.vue'
import { levelVarFromOperand, normalizeScorecardLevelVar } from '@/utils/scorecardLevel'
import OperandPicker from '@/components/common/OperandPicker.vue'
import ScriptPanel from '@/components/common/ScriptPanel.vue'
import RuleDraftReadOnly from '@/components/rule/RuleDraftReadOnly.vue'
import RuleDesignerActionBar from '@/components/rule/RuleDesignerActionBar.vue'
import { addCode, buildSampleParamsFromCodes } from '@/utils/testSampleParams'
import {
  collectOperandReferences,
  compileOperand,
  createLiteralOperand,
  inferOperandType,
  operandFromReferenceFields,
  syncOperandReference,
} from '@/utils/operand'
import { getExpressionContext } from '@/constants/expressionContexts'
import {
  conditionOperatorAllowsVarValue,
  conditionLiteralOperandKinds,
  conditionOperatorRequiresValue,
  findConditionOperator,
  getConditionOperatorGroups,
  normalizeConditionOperator,
} from '@/constants/conditionOperators'

function scoreConditionType(item) {
  return (
    inferOperandType(item && item.leftOperand) ||
    (item && item.condVarType) ||
    'STRING'
  )
}

export default {
  data() {
    return {
      definitionId: null,
      contentLoaded: false,
      model: {
        initialScore: 0,
        scoreItems: [],
        resultVar: { varCode: '', varLabel: '' },
        levelVar: levelVarFromOperand(null),
        thresholds: [],
        testParams: null,
      },
      testVisible: false,
      testParamsTemplate: {},
      testParamsJson: '{}',
      testResult: null,
      testJsonError: '',
      testDialogKey: 1,
      testReady: false,
      testExecuting: false,
      jsonError: '',
      readOperandKinds: getExpressionContext('READ_EXPRESSION').allowedKinds,
      writeOperandKinds: getExpressionContext('WRITE_TARGET').allowedKinds,
      valueOperandKinds: getExpressionContext('READ_EXPRESSION').allowedKinds,
      ElIconBack: markRaw(ElIconBack),
      ElIconPlus: markRaw(ElIconPlus),
      ElIconDocument: markRaw(ElIconDocument),
      ElIconCpu: markRaw(ElIconCpu),
      ElIconVideoPlay: markRaw(ElIconVideoPlay),
      ElIconDelete: markRaw(ElIconDelete),
    }
  },
  components: {
    ScoreThresholdEditor,
    RuleDesignerActionBar,
    RuleDraftReadOnly,
    DesignerTestDialog,
    OperandPicker,
    ScriptPanel,
    ElIconDataLine,
    ElIconSetting,
    ElIconSCheck,
    ElIconQuestion,
    ElIconFiles,
    ElIconMedal,
  },
  name: 'Scorecard',
  mixins: [varPickerMixin, ruleDraftMixin],
  computed: {
    scoreResultLabel() {
      return formatExpressionFormula(this.model.resultVar.operand, { humanReadable: true }) || this.model.resultVar.varCode || '结果分数'
    },
    totalWeight() {
      return this.model.scoreItems.reduce(
        (sum, item) => sum + (item.weight || 0),
        0
      )
    },
    totalWeightPercent() {
      return Math.min(100, Math.round(this.totalWeight * 100))
    },
    totalWeightDisplay() {
      return this.totalWeight.toFixed(2)
    },
    totalWeightColor() {
      const w = this.totalWeight
      if (Math.abs(w - 1.0) < 0.05) return '#087a5d'
      if (w > 1.05) return '#c93333'
      return '#b45309'
    },
    isResultMap() {
      return (
        this.testResult &&
        this.testResult.result &&
        typeof this.testResult.result === 'object' &&
        !Array.isArray(this.testResult.result)
      )
    },
  },
  created() {
    this.definitionId = this.$route.params.id
    this.loadContent()
  },
  methods: {
    scoreConditionSummary(item) {
      const unboundLegacy = !item.leftOperand || (item.leftOperand.kind === 'REFERENCE' && (item.leftOperand.refId == null || !item.leftOperand.refType))
      if (item.condition && unboundLegacy && item._varId == null) return '自定义表达式（请查看原配置）'
      return formatConditionDescription({
        leftOperand: item.leftOperand,
        varCode: item.condVar, varType: item.condVarType, _varId: item._varId, _refType: item._refType,
        operator: item.condOperator, rightOperand: item.rightOperand, value: item.condValue,
      })
    },
    collectSelectedVarItems() {
      const items = []
      const add = (operand) =>
        collectOperandReferences(operand).forEach((reference) =>
          items.push({
            varCode: reference.code,
            varType: reference.valueType,
            _varId: reference.refId,
            _refType: reference.refType,
          })
        )
      add(this.model.resultVar && this.model.resultVar.operand)
      add(this.model.levelVar && this.model.levelVar.operand)
      ;(this.model.scoreItems || []).forEach((item) => {
        add(item.leftOperand)
        add(item.rightOperand)
      })
      ;(this.model.thresholds || []).forEach((item) => add(item.resultOperand))
      return items
    },
    async loadContent() {
      try {
        if (this.draftGuardPromise) await this.draftGuardPromise
        const content = this.viewRevision
        if (content && content.modelJson) {
          this.model = parseDesignerModelJson(content.modelJson)
        }
      } catch (e) {
        this.$message.error('加载内容失败: ' + (e.message || '未知错误'))
      } finally {
        this.normalizeModel()
        this.contentLoaded = true
        this._trySyncModelVarRefs()
        this.$nextTick(() =>
          this.initializeDesignerDraftTracking(this.serializeDesignerDraft())
        )
      }
    },
    /** 加载最新变量后，同步 model 中结果变量的 varCode 和 varLabel */
    _syncModelVarRefs() {
      let changed = false
      const sync = (holder, field) => {
        const result = syncOperandReference(
          holder[field],
          this.varPickerOptions
        )
        if (result.changed) {
          holder[field] = result.operand
          changed = true
        }
      }
      sync(this.model.resultVar, 'operand')
      if (this.model.levelVar) {
        const level = syncOperandReference(this.model.levelVar.operand, this.varPickerOptions)
        if (level.changed) this.setLevelOperand(level.operand)
      }
      ;(this.model.scoreItems || []).forEach((item) => {
        sync(item, 'leftOperand')
        sync(item, 'rightOperand')
      })
      ;(this.model.thresholds || []).forEach((item) =>
        sync(item, 'resultOperand')
      )
      if (changed) this.$forceUpdate()
    },
    normalizeModel() {
      this.model['levelVar'] = normalizeScorecardLevelVar(this.model)
      if (this.model.initialScore == null) this.model['initialScore'] = 0
      if (!this.model.scoreItems) this.model['scoreItems'] = []
      if (!this.model.resultVar)
        this.model['resultVar'] = { varCode: '', varLabel: '' }
      if (!this.model.resultVar.operand)
        this.model.resultVar['operand'] = operandFromReferenceFields(
          this.model.resultVar
        )
      if (!this.model.thresholds) this.model['thresholds'] = []
      this.model.scoreItems.forEach((item) => {
        if (item.score == null) item['score'] = 1
        if (item.weight == null) item['weight'] = 1
        if (!item.condVar && item.condition) {
          this.parseCondition(item)
        }
        if (item.condVar == null) item['condVar'] = ''
        if (item.condOperator == null) item['condOperator'] = '=='
        if (item.condValue == null) item['condValue'] = ''
        if (item.condVarType == null) item['condVarType'] = 'STRING'
        if (!item.leftOperand && item.condVar)
          item['leftOperand'] = operandFromReferenceFields({
            ...item,
            varCode: item.condVar,
            varLabel: item.conditionLabel,
            varType: item.condVarType,
          })
        if (!item.rightOperand && item.condValue !== '')
          item['rightOperand'] = createLiteralOperand(
            item.condValue,
            item.condVarType
          )
      })
      this.model.thresholds.forEach((item) => {
        if (!item.resultOperand && item.result !== '')
          item['resultOperand'] = createLiteralOperand(
            item.result,
            item.resultType || 'STRING'
          )
      })
    },
    /** 从已有 condition 字符串反解出结构化字段 */
    parseCondition(item) {
      const ops = ['>=', '<=', '!=', '==', '>', '<']
      for (const op of ops) {
        const idx = item.condition.indexOf(' ' + op + ' ')
        if (idx >= 0) {
          item.condVar = item.condition.substring(0, idx).trim()
          item.condOperator = op
          let val = item.condition.substring(idx + op.length + 2).trim()
          if (
            (val.startsWith('"') && val.endsWith('"')) ||
            (val.startsWith("'") && val.endsWith("'"))
          ) {
            val = val.slice(1, -1)
            item.condVarType = 'STRING'
          } else {
            item.condVarType = 'NUMBER'
          }
          item.condValue = val
          return
        }
      }
    },
    /** 统一条件表达式生成（评分项保存和反解共用） */
    buildCondition(condVar, condOperator, condValue, condVarType) {
      if (!condVar || !condOperator || condValue == null || condValue === '')
        return ''
      // 数值比较运算符始终使用数字（不加引号）
      const numericOps = ['>=', '<=', '>', '<']
      if (numericOps.includes(condOperator)) {
        return condVar + ' ' + condOperator + ' ' + condValue
      }
      const needQuote =
        !condVarType || condVarType === 'STRING' || condVarType === 'ENUM'
      const quoted = needQuote
        ? '"' + String(condValue).replace(/"/g, '\\"') + '"'
        : condValue
      return condVar + ' ' + condOperator + ' ' + quoted
    },
    onResultOperandSelect(operand) {
      const newCode = operand ? operand.code || operand.value || '' : ''
      // 检测结果变量是否与已有条件变量同名（评分卡中结果变量应为输出变量，不应与输入条件变量同名）
      const conflictItem = this.model.scoreItems.find(
        (item) => item.condVar === newCode
      )
      if (conflictItem) {
        this.$message.warning({
          message: `结果变量「${newCode}」与评分项「${
            conflictItem.conditionLabel ||
            '#' + (this.model.scoreItems.indexOf(conflictItem) + 1)
          }」的条件变量「${newCode}」同名，生成脚本时会相互覆盖。`,
          duration: 5000,
        })
      }
      this.model['resultVar'] = {
        ...this.model.resultVar,
        operand: operand || null,
        varCode: newCode,
        varLabel: (operand && operand.label) || newCode,
        _varId: operand && operand.refId != null ? operand.refId : null,
        _refType: (operand && operand.refType) || null,
      }
    },
    setScoreItemOperand(item, field, operand) {
      item[field] = operand || null
      if (field === 'leftOperand') {
        item['condVar'] = operand ? operand.code || operand.value || '' : ''
        item['condVarType'] = (operand && operand.valueType) || 'STRING'
        item['_varId'] = operand && operand.refId != null ? operand.refId : null
        item['_refType'] = (operand && operand.refType) || null
        item['condOperator'] = normalizeConditionOperator(
          item.condOperator || '==',
          scoreConditionType(item),
          operand
        )
        this.onConditionOperatorChange(item)
      } else {
        item['condValue'] =
          operand && operand.kind === 'LITERAL'
            ? operand.value
            : compileOperand(operand)
      }
    },
    conditionOperatorGroups(item) {
      return getConditionOperatorGroups(
        scoreConditionType(item),
        item && item.leftOperand
      )
    },
    conditionRequiresValue(item) {
      return conditionOperatorRequiresValue(
        item && item.condOperator,
        scoreConditionType(item),
        item && item.leftOperand
      )
    },
    conditionRightContext(item) {
      const option = findConditionOperator(
        item && item.condOperator,
        scoreConditionType(item),
        item && item.leftOperand
      )
      return (option && option.rightContext) || 'READ_EXPRESSION'
    },
    conditionRightExpectedType(item) {
      const option = findConditionOperator(
        item && item.condOperator,
        scoreConditionType(item),
        item && item.leftOperand
      )
      return (option && option.rightValueType) || scoreConditionType(item)
    },
    conditionRightAllowedKinds(item) {
      const context = this.conditionRightContext(item)
      if (context === 'LIST_QUERY_CONFIG')
        return getExpressionContext(context).allowedKinds
      return conditionOperatorAllowsVarValue(
        item && item.condOperator,
        scoreConditionType(item),
        item && item.leftOperand
      )
        ? getExpressionContext(context).allowedKinds
        : conditionLiteralOperandKinds(item && item.condOperator)
    },
    onConditionOperatorChange(item) {
      const type = scoreConditionType(item)
      const operator = normalizeConditionOperator(
        item.condOperator || '==',
        type,
        item.leftOperand
      )
      item['condOperator'] = operator
      if (!conditionOperatorRequiresValue(operator, type, item.leftOperand)) {
        item['rightOperand'] = null
        item['condValue'] = ''
      }
    },
    setLevelOperand(operand) {
      this.model['levelVar'] = levelVarFromOperand(operand)
    },
    setThresholdOperand(threshold, operand) {
      threshold['resultOperand'] = operand || null
      threshold['result'] =
        operand && operand.kind === 'LITERAL'
          ? operand.value
          : compileOperand(operand)
    },
    addScoreItem() {
      this.model.scoreItems.push({
        leftOperand: null,
        rightOperand: null,
        condVar: '',
        condOperator: '==',
        condValue: '',
        condVarType: 'STRING',
        condition: '',
        conditionLabel: '',
        score: 1,
        weight: 1.0,
      })
    },
    removeScoreItem(index) {
      this.model.scoreItems.splice(index, 1)
    },
    addThreshold() {
      const last = this.model.thresholds[this.model.thresholds.length - 1]
      const min = last ? last.max : 0
      this.model.thresholds.push({
        min,
        max: min + 50,
        result: '',
        resultOperand: null,
        resultType: 'STRING',
      })
    },
    removeThreshold(index) {
      this.model.thresholds.splice(index, 1)
    },
    async performDesignerSave() {
      const result = await this.saveDraftModel(this.serializeDesignerDraft())
      if (!result) return false
      this.refreshProjectRefs()

      this.$message.success('草稿已保存')
      return result
    },
    serializeDesignerDraft() {
      const model = JSON.parse(JSON.stringify(this.model))
      ;(model.scoreItems || []).forEach((item) => {
        if (item.leftOperand) {
          delete item.condition
          return
        }
        item.condition = this.buildCondition(
          item.condVar,
          item.condOperator,
          item.condValue,
          item.condVarType
        )
      })
      return JSON.stringify(model)
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
    async openTestDialog() {
      if (!this.ensureDesignerReadyForTest()) return
      const saved = this.model.testParams
      if (saved && saved !== '{}') {
        this.testParamsTemplate = saved
      } else {
        this.testParamsTemplate = this.buildTestParamsTemplate()
      }
      this.testVisible = true
    },
    buildTestParamsTemplate() {
      const codes = new Set()
      const items = this.model.scoreItems || []
      items.forEach((item) => {
        addCode(codes, item.condVar)
        collectOperandReferences(item.leftOperand).forEach((reference) =>
          addCode(codes, reference.code || reference.path)
        )
        collectOperandReferences(item.rightOperand).forEach((reference) =>
          addCode(codes, reference.code || reference.path)
        )
      })
      return buildSampleParamsFromCodes(Array.from(codes), this.projectRefs)
    },
    onJsonInput(val) {
      this.jsonError = ''
      if (val && val !== '{}') {
        try {
          JSON.parse(val)
        } catch (e) {
          this.jsonError = 'JSON 格式错误: ' + e.message
        }
      }
    },
    handleClearParams() {
      this.testParamsJson = '{}'
      this.testResult = null
      this.jsonError = ''
    },
    async saveTestParams() {
      // 将当前编辑的 JSON 保存到 model.testParams，下次打开时自动填充
      if (!this.testParamsJson || this.testParamsJson === '{}') {
        this.$message.warning('请先填写有效的测试参数')
        return
      }
      // 验证 JSON 格式
      try {
        JSON.parse(this.testParamsJson)
      } catch (e) {
        this.$message.error('JSON 格式错误: ' + e.message)
        return
      }
      this.model.testParams = this.testParamsJson
      const result = await this.handleSave()
      if (result) this.$message.success('测试样例已保存')
    },
  },
}
</script>

<style lang="scss" scoped>
.sc-designer {
  position: relative;
  background: var(--tianshu-bg-page);
  padding: 16px;
}
.sc-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--tianshu-bg-surface);
  border-radius: 4px;
  padding: 12px 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  margin-bottom: 12px;
  flex-wrap: wrap;
  gap: 8px;
}
.sc-title-area {
  display: flex;
  align-items: center;
}
.sc-title-icon {
  font-size: 18px;
  color: var(--el-color-primary);
  margin-right: 8px;
}
.sc-title {
  font-size: 16px;
  font-weight: bold;
  color: var(--tianshu-text-primary);
}
.sc-toolbar {
  display: flex;
  align-items: center;
  gap: 6px;
}
.sc-card {
  background: var(--tianshu-bg-surface);
  border-radius: 4px;
  padding: 12px 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  margin-bottom: 12px;
}
.sc-card-title {
  font-size: 14px;
  font-weight: bold;
  color: var(--tianshu-text-primary);
  margin-bottom: 10px;
  display: flex;
  align-items: center;
  gap: 6px;
  i {
    color: var(--el-color-primary);
  }
}
.sc-card-title-row {
  justify-content: space-between;
}
.level-config-hint {
  margin-top: 8px;
  color: var(--tianshu-text-secondary);
  font-size: 12px;
}

.base-config-row {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  align-items: center;
}
.base-config-item {
  display: flex;
  align-items: center;
  gap: 8px;
}
.base-config-label {
  font-size: 13px;
  color: var(--tianshu-text-secondary);
  white-space: nowrap;
}
.result-var-picker {
  display: flex;
  align-items: center;
  gap: 4px;
}
.result-var-switch-btn {
  padding: 4px 8px;
  color: var(--tianshu-text-tertiary);
  &:hover {
    color: var(--el-color-primary);
  }
}
.result-var-label {
  font-size: 12px;
  color: var(--tianshu-text-tertiary);
  margin-left: 4px;
}
.weight-summary {
  display: flex;
  align-items: center;
  gap: 8px;
}
.weight-label {
  font-size: 13px;
  color: var(--tianshu-text-secondary);
  font-weight: normal;
}
.weight-value {
  font-weight: bold;
  font-size: 14px;
  min-width: 36px;
}
.tip-icon {
  color: var(--tianshu-text-tertiary);
  cursor: pointer;
  &:hover {
    color: var(--el-color-primary);
  }
}
.score-items {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.score-item-card {
  border: 1px solid var(--tianshu-border-subtle);
  border-radius: 6px;
  overflow: hidden;
  transition: box-shadow 0.2s;
  &:hover {
    box-shadow: 0 2px 8px rgba(24, 144, 255, 0.15);
    border-color: var(--tianshu-designer-accent-border);
  }
}
.score-item-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  background: var(--tianshu-bg-muted);
  border-bottom: 1px solid var(--tianshu-border-subtle);
}
.item-index {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--el-color-primary);
  color: #fff;
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-weight: bold;
}
.item-label-input {
  flex: 1;
}
.score-item-body {
  padding: 10px;
  background: var(--tianshu-bg-surface);
}
.score-item-row {
  margin-bottom: 8px;
  &:last-child {
    margin-bottom: 0;
  }
}
.condition-row {
  display: flex;
  align-items: center;
  gap: 6px;
}
.cond-var {
  flex: 3;
  min-width: 0;
}
.cond-op {
  flex: 0 0 108px;
  width: 108px;
}
.cond-val {
  flex: 2;
  min-width: 0;
}
.score-weight-row {
  display: flex;
  gap: 12px;
  align-items: flex-end;
}
.item-field-label {
  display: block;
  font-size: 12px;
  color: var(--tianshu-text-tertiary);
  margin-bottom: 5px;
}
.score-col {
  flex-shrink: 0;
}
.weight-col {
  flex: 1;
}
.weight-slider-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.weighted-score {
  flex-shrink: 0;
  text-align: center;
  min-width: 60px;
}
.weighted-value {
  font-size: 18px;
  font-weight: bold;
  color: var(--el-color-primary);
  display: block;
}
.sc-formula {
  background: var(--tianshu-warning-bg);
  border: 1px solid var(--tianshu-warning-border);
}
.formula-content {
  overflow-x: auto;
}
.formula-text {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  font-size: 13px;
  line-height: 1.8;
  code {
    font-family: 'Consolas', monospace;
    background: rgba(0, 0, 0, 0.05);
    padding: 1px 5px;
    border-radius: 3px;
    color: var(--tianshu-warning-text);
  }
}
.op {
  color: var(--tianshu-text-secondary);
  font-weight: bold;
  font-size: 13px;
}
.formula-term {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  background: var(--tianshu-designer-alt-bg);
  border: 1px solid var(--tianshu-designer-alt-border);
  border-radius: 4px;
  padding: 2px 8px;
}
.formula-cond {
  color: var(--tianshu-warning-text);
  font-size: 12px;
}
.formula-empty {
  color: var(--tianshu-text-tertiary);
  font-style: italic;
}
.sc-empty {
  text-align: center;
  padding: 30px;
  color: var(--tianshu-text-tertiary);
  font-size: 13px;
}
.sc-empty-icon {
  font-size: 36px;
  color: #ddd;
  display: block;
  margin-bottom: 8px;
}
.test-hint {
  font-size: 12px;
  color: var(--tianshu-text-tertiary);
  margin-bottom: 8px;
}
.test-result {
  margin-top: 16px;
}
.script-override-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  background: var(--tianshu-warning-bg);
  border: 1px solid var(--tianshu-warning-border);
  border-radius: 4px;
  margin-top: 8px;
  font-size: 12px;
  color: var(--tianshu-warning-text);
  i {
    color: var(--tianshu-warning-text);
    font-size: 14px;
  }
}
@media (max-width: 1200px) {
  .sc-designer {
    padding: 12px;
  }
  .base-config-row {
    gap: 12px;
  }
}
</style>
