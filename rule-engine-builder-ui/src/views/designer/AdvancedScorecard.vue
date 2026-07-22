<template>
  <div class="asc-designer uiue-compact-workbench uiue-compact-designer">
    <!-- 顶部工具栏 -->
    <div class="asc-header">
      <div class="asc-title-area">
        <el-button
          link
          :icon="ElIconBack"
          @click="$router.back()"
          style="color: #606266"
        />
        <el-icon class="asc-title-icon"><el-icon-data-line /></el-icon>
        <span class="asc-title">复杂评分卡设计器</span>
        <el-tag size="small" type="info" style="margin-left: 8px"
          >{{ totalDimensions }} 个评分维度</el-tag
        >
      </div>
      <div class="asc-toolbar">
        <el-button size="small" :icon="ElIconPlus" @click="addGroup"
          >添加维度组</el-button
        >
        <el-divider direction="vertical" />
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

    <!-- 基础配置 -->
    <div class="asc-card asc-base-config">
      <div class="asc-card-title">
        <el-icon><el-icon-setting /></el-icon> 基础配置
      </div>
      <div class="base-config-row">
        <div class="base-config-item">
          <span class="base-config-label">初始分数</span>
          <el-input-number
            v-model="model.initialScore"
            :min="0"
            :max="10000"
            size="small"
            style="width: 130px"
          />
        </div>
        <div class="base-config-item">
          <span class="base-config-label">结果变量</span>
          <div class="result-var-picker">
            <operand-picker
              :vars="varPickerOptions"
              :functions="projectFunctions"
              :selected-vars="selectedVarPickerOptions"
              :value="model.resultVar.operand"
              :allowed-kinds="writeOperandKinds"
              writable-only
              placeholder="选择结果字段或手输路径"
              width="200px"
              @input="
                (value) => setFieldOperand(model.resultVar, 'operand', value)
              "
            />
          </div>
          <span v-if="model.resultVar.varLabel" class="result-var-label">{{
            model.resultVar.varLabel
          }}</span>
        </div>
      </div>
    </div>

    <!-- 维度组列表 -->
    <div
      v-for="(group, gi) in model.dimensionGroups"
      :key="gi"
      class="asc-card asc-group"
    >
      <div class="asc-group-header">
        <div class="asc-group-left" @click="toggleGroup(gi)">
          <app-icon :name="group._collapsed ? 'ArrowRight' : 'ArrowDown'" />
          <el-input
            v-model="group.groupLabel"
            size="small"
            placeholder="维度组名称（如 客户基础信息）"
            class="group-label-input"
            @click.stop
          />
          <el-tag size="small" type="info"
            >{{ (group.dimensions || []).length }} 维度</el-tag
          >
          <div class="group-weight-summary" @click.stop>
            <span class="weight-label">组权重</span>
            <el-input-number
              v-model="group.weight"
              :min="0"
              :max="2"
              :step="0.1"
              :precision="2"
              size="small"
              controls-position="right"
              style="width: 100px"
            />
          </div>
        </div>
        <div class="asc-group-right">
          <el-button size="small" :icon="ElIconPlus" @click="addDimension(gi)"
            >添加维度</el-button
          >
          <el-button
            link
            size="small"
            :icon="ElIconDelete"
            class="btn-delete"
            @click="removeGroup(gi)"
          />
        </div>
      </div>

      <div v-show="!group._collapsed" class="asc-group-body">
        <div
          v-for="(dim, di) in group.dimensions"
          :key="di"
          class="asc-dimension"
        >
          <div class="dim-header">
            <span class="dim-index">{{ gi + 1 }}.{{ di + 1 }}</span>
            <el-input
              v-model="dim.varLabel"
              size="small"
              placeholder="维度名称"
              style="width: 160px"
            />
            <operand-picker
              :vars="varPickerOptions"
              :functions="projectFunctions"
              :selected-vars="selectedVarPickerOptions"
              :value="dim.operand"
              :allowed-kinds="readOperandKinds"
              placeholder="选择维度字段或路径"
              width="180px"
              @input="(value) => setFieldOperand(dim, 'operand', value)"
            />
            <div class="dim-weight-area">
              <span class="item-field-label">权重</span>
              <el-input-number
                v-model="dim.weight"
                :min="0"
                :max="2"
                :step="0.05"
                :precision="2"
                size="small"
                controls-position="right"
                style="width: 90px"
              />
            </div>
            <el-button size="small" :icon="ElIconPlus" @click="addRule(gi, di)"
              >添加规则</el-button
            >
            <el-button
              link
              size="small"
              :icon="ElIconDelete"
              class="btn-delete"
              @click="removeDimension(gi, di)"
            />
          </div>

          <!-- 规则表格 -->
          <table class="rule-table" v-if="dim.rules && dim.rules.length">
            <thead>
              <tr>
                <th class="col-idx">#</th>
                <th class="col-conditions">条件组合</th>
                <th class="col-score">分值</th>
                <th class="col-action">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(rule, ri) in dim.rules" :key="ri">
                <td class="col-idx">{{ ri + 1 }}</td>
                <td class="col-conditions">
                  <div
                    v-for="(cond, ci) in rule.conditions"
                    :key="ci"
                    class="condition-row"
                  >
                    <operand-picker
                      :vars="varPickerOptions"
                      :functions="projectFunctions"
                      :selected-vars="selectedVarPickerOptions"
                      :value="cond.leftOperand"
                      :allowed-kinds="readOperandKinds"
                      placeholder="选择左操作数"
                      width="100%"
                      class="cond-var"
                      @input="
                        (value) =>
                          setConditionOperand(cond, 'leftOperand', value)
                      "
                    />
                    <el-select
                      v-model="cond.operator"
                      size="small"
                      class="cond-op"
                      @change="onConditionOperatorChange(cond)"
                    >
                      <el-option-group
                        v-for="operatorGroup in conditionOperatorGroups(cond)"
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
                      v-if="conditionRequiresValue(cond)"
                      :value="cond.rightOperand"
                      :vars="varPickerOptions"
                      :functions="projectFunctions"
                      :selected-vars="selectedVarPickerOptions"
                      :allowed-kinds="conditionRightAllowedKinds(cond)"
                      :context="conditionRightContext(cond)"
                      :expected-type="conditionRightExpectedType(cond)"
                      placeholder="选择值或字段"
                      width="100%"
                      class="cond-val"
                      @input="
                        (value) =>
                          setConditionOperand(cond, 'rightOperand', value)
                      "
                    />
                    <el-button
                      v-if="rule.conditions.length > 1"
                      link
                      size="small"
                      :icon="ElIconClose"
                      style="color: #ccc"
                      @click="rule.conditions.splice(ci, 1)"
                    />
                    <span
                      v-if="ci < rule.conditions.length - 1"
                      class="cond-and"
                      >且</span
                    >
                  </div>
                  <el-button
                    link
                    size="small"
                    :icon="ElIconPlus"
                    @click="addCondition(rule)"
                    >添加条件</el-button
                  >
                </td>
                <td class="col-score">
                  <el-input-number
                    v-model="rule.score"
                    size="small"
                    :min="-9999"
                    :max="9999"
                    class="score-input"
                  />
                </td>
                <td class="col-action">
                  <el-button
                    link
                    size="small"
                    :icon="ElIconDelete"
                    class="btn-delete"
                    @click="dim.rules.splice(ri, 1)"
                  />
                </td>
              </tr>
            </tbody>
          </table>
          <div v-else class="dim-empty">暂无规则，点击「添加规则」</div>
        </div>

        <div
          v-if="!group.dimensions || group.dimensions.length === 0"
          class="group-empty"
        >
          暂无维度，点击「添加维度」
        </div>
      </div>
    </div>

    <div v-if="model.dimensionGroups.length === 0" class="asc-card asc-empty">
      <el-icon style="font-size: 36px; color: #ddd"><el-icon-s-data /></el-icon>
      <p>暂无维度组，点击「添加维度组」开始配置</p>
    </div>

    <!-- 计算公式预览 -->
    <div class="asc-card asc-formula" v-if="model.dimensionGroups.length > 0">
      <div class="asc-card-title">
        <el-icon style="color: #d46b08"><el-icon-files /></el-icon> 计算公式预览
      </div>
      <div class="formula-content">
        <div class="formula-text">
          <code>{{ model.resultVar.varCode || 'score' }}</code>
          <span class="op"> = </span>
          <span v-if="model.initialScore !== 0">
            <code>{{ model.initialScore }}</code>
            <span class="op"> + </span>
          </span>
          <template
            v-for="(group, gi) in model.dimensionGroups"
            :key="'g-' + gi"
          >
            <span class="formula-group">
              <span class="formula-group-label">{{
                group.groupLabel || '维度组' + (gi + 1)
              }}</span>
              <template v-if="group.weight != null && group.weight !== 1">
                <span class="op"> × </span>
                <code>{{ (group.weight || 0).toFixed(2) }}</code>
              </template>
              <span class="formula-dims">
                (
                <template
                  v-for="(dim, di) in group.dimensions || []"
                  :key="'d-' + di"
                >
                  <span class="formula-term">
                    {{ dim.varLabel || dim.varCode || '维度' + (di + 1) }}
                    <template v-if="dim.weight != null && dim.weight !== 1">
                      <span class="op">×</span
                      >{{ (dim.weight || 0).toFixed(2) }}
                    </template>
                  </span>
                  <span
                    v-if="di < (group.dimensions || []).length - 1"
                    class="op"
                  >
                    +
                  </span>
                </template>
                )
              </span>
            </span>
            <span v-if="gi < model.dimensionGroups.length - 1" class="op">
              +
            </span>
          </template>
        </div>
      </div>
    </div>

    <!-- 权重汇总 -->
    <div class="asc-card" v-if="model.dimensionGroups.length > 0">
      <div class="asc-card-title asc-card-title-row">
        <span
          ><el-icon><el-icon-s-check /></el-icon> 权重汇总</span
        >
        <div class="weight-summary">
          <span class="weight-label-sm">总权重：</span>
          <el-progress
            :percentage="totalWeightPercent"
            :color="totalWeightColor"
            :stroke-width="10"
            style="width: 150px; display: inline-block; vertical-align: middle"
          />
          <span class="weight-value" :style="{ color: totalWeightColor }">{{
            totalWeight.toFixed(2)
          }}</span>
        </div>
      </div>
      <div class="weight-detail-list">
        <div
          v-for="(group, gi) in model.dimensionGroups"
          :key="gi"
          class="weight-detail-item"
        >
          <span class="weight-detail-name">{{
            group.groupLabel || '维度组' + (gi + 1)
          }}</span>
          <span class="weight-detail-val"
            >组权重 {{ (group.weight || 1).toFixed(2) }}</span
          >
          <span class="weight-detail-dims">
            × (
            <template v-for="(dim, di) in group.dimensions || []" :key="di">
              <span
                >{{ dim.varLabel || '维度' }}:{{
                  (dim.weight || 1).toFixed(2)
                }}</span
              >
              <span v-if="di < (group.dimensions || []).length - 1">, </span>
            </template>
            )
          </span>
        </div>
      </div>
    </div>

    <!-- 分数等级配置 -->
    <div class="asc-card">
      <div class="asc-card-title asc-card-title-row">
        <span
          ><el-icon><el-icon-medal /></el-icon> 分数等级配置</span
        >
        <el-button size="small" :icon="ElIconPlus" @click="addThreshold"
          >添加等级</el-button
        >
      </div>
      <div class="threshold-list">
        <div
          v-for="(thresh, ti) in model.thresholds"
          :key="ti"
          class="threshold-item"
        >
          <div
            class="thresh-color-bar"
            :style="{ background: thresholdColor(ti) }"
          />
          <div class="thresh-range">
            <el-input-number
              v-model="thresh.min"
              size="small"
              :min="0"
              :controls="false"
              style="width: 100px"
            />
            <span class="thresh-sep">&le; 分数 &lt;</span>
            <el-input-number
              v-model="thresh.max"
              size="small"
              :min="thresh.min"
              :controls="false"
              style="width: 100px"
            />
          </div>
          <div class="thresh-result">
            <operand-picker
              :value="thresh.resultOperand"
              :vars="varPickerOptions"
              :functions="projectFunctions"
              :selected-vars="selectedVarPickerOptions"
              :allowed-kinds="valueOperandKinds"
              placeholder="选择等级结果或手输阈值"
              width="100%"
              @input="(value) => setThresholdOperand(thresh, value)"
            />
          </div>
          <el-tag
            :color="thresholdColor(ti)"
            effect="dark"
            size="small"
            class="thresh-badge"
          >
            {{ thresh.result || '等级 ' + (ti + 1) }}
          </el-tag>
          <el-button
            link
            size="small"
            :icon="ElIconDelete"
            class="btn-delete"
            @click="model.thresholds.splice(ti, 1)"
          />
        </div>
        <div v-if="model.thresholds.length === 0" class="group-empty">
          暂未配置等级，点击「添加等级」
        </div>
      </div>
    </div>

    <!-- 脚本预览 -->
    <script-panel
      v-if="definitionId"
      ref="scriptPanel"
      :definitionId="definitionId"
      :onBeforeCompile="handleSave"
      @mode-change="(mode) => (scriptMode = mode)"
    />

    <!-- 测试弹窗 -->
    <designer-test-dialog
      v-model:visible="testVisible"
      :definition-id="definitionId"
      :project-id="projectIdForRefs"
      model-type="SCORE_ADV"
      :model-json="model"
      :params-template="testParamsTemplate"
    />
  </div>
</template>

<script>
import { markRaw } from 'vue'
import {
  DataLine as ElIconDataLine,
  Setting as ElIconSetting,
  DataAnalysis as ElIconSData,
  Files as ElIconFiles,
  Finished as ElIconSCheck,
  Medal as ElIconMedal,
  Back as ElIconBack,
  Plus as ElIconPlus,
  Document as ElIconDocument,
  Cpu as ElIconCpu,
  VideoPlay as ElIconVideoPlay,
  Delete as ElIconDelete,
  Close as ElIconClose,
} from '@element-plus/icons-vue'
import {
  saveContent,
  compileRule,
  executeRule,
  getContent,
  refreshFields,
} from '@/api/definition'
import varPickerMixin from '@/mixins/varPickerMixin'
import OperandPicker from '@/components/common/OperandPicker.vue'
import ScriptPanel from '@/components/common/ScriptPanel.vue'
import DesignerTestDialog from '@/components/common/DesignerTestDialog.vue'
import {
  addCode,
  buildSampleParamsFromCodes,
  coerceSampleValue,
} from '@/utils/testSampleParams'
import { isSuccessResult, resultErrorMessage } from '@/utils/apiResponse'
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
  conditionOperatorRequiresValue,
  findConditionOperator,
  getConditionOperatorGroups,
  normalizeConditionOperator,
} from '@/constants/conditionOperators'

const THRESHOLD_COLORS = [
  '#52c41a',
  '#1890ff',
  '#fa8c16',
  '#f5222d',
  '#722ed1',
  '#13c2c2',
  '#eb2f96',
]

function conditionOperandType(condition) {
  return inferOperandType(condition && condition.leftOperand) || 'STRING'
}

export default {
  data() {
    return {
      definitionId: null,
      contentLoaded: false,
      model: {
        initialScore: 100,
        resultVar: { varCode: '', varLabel: '', _varId: null },
        dimensionGroups: [],
        thresholds: [],
      },
      scriptMode: 'visual',
      testVisible: false,
      testParamsTemplate: {},
      testParamsJson: '{}',
      testResult: null,
      readOperandKinds: getExpressionContext('READ_EXPRESSION').allowedKinds,
      writeOperandKinds: getExpressionContext('WRITE_TARGET').allowedKinds,
      valueOperandKinds: getExpressionContext('READ_EXPRESSION').allowedKinds,
      ElIconBack: markRaw(ElIconBack),
      ElIconPlus: markRaw(ElIconPlus),
      ElIconDocument: markRaw(ElIconDocument),
      ElIconCpu: markRaw(ElIconCpu),
      ElIconVideoPlay: markRaw(ElIconVideoPlay),
      ElIconDelete: markRaw(ElIconDelete),
      ElIconClose: markRaw(ElIconClose),
    }
  },
  components: {
    DesignerTestDialog,
    OperandPicker,
    ScriptPanel,
    ElIconDataLine,
    ElIconSetting,
    ElIconSData,
    ElIconFiles,
    ElIconSCheck,
    ElIconMedal,
  },
  name: 'AdvancedScorecard',
  mixins: [varPickerMixin],
  computed: {
    totalDimensions() {
      return this.model.dimensionGroups.reduce(
        (sum, g) => sum + (g.dimensions || []).length,
        0
      )
    },
    /** 所有维度组的有效权重之和 */
    totalWeight() {
      return this.model.dimensionGroups.reduce((sum, g) => {
        const groupWeight = g.weight != null ? g.weight : 1
        const dimWeights = (g.dimensions || []).reduce(
          (ds, d) => ds + (d.weight != null ? d.weight : 1),
          0
        )
        return sum + groupWeight * dimWeights
      }, 0)
    },
    totalWeightPercent() {
      return Math.min(100, Math.round(this.totalWeight * 100))
    },
    totalWeightColor() {
      const w = this.totalWeight
      if (Math.abs(w - 1.0) < 0.05) return '#52c41a'
      if (w > 1.05) return '#f5222d'
      return '#fa8c16'
    },
  },
  created() {
    this.definitionId = this.$route.params.id
    this.loadContent()
  },
  methods: {
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
      const groups = this.model.dimensionGroups || []
      groups.forEach((group) => {
        const dimensions = group.dimensions || []
        dimensions.forEach((dim) => {
          add(dim.operand)
          const rules = dim.rules || []
          rules.forEach((rule) => {
            const conditions = rule.conditions || []
            conditions.forEach((cond) => {
              add(cond.leftOperand)
              add(cond.rightOperand)
            })
          })
        })
      })
      return items
    },
    async loadContent() {
      try {
        const res = await getContent(this.definitionId)
        const content = res && res.data ? res.data : res
        if (content && content.modelJson && content.modelJson !== '{}') {
          this.model = JSON.parse(content.modelJson)
        }
      } catch (e) {
        this.$message.error('加载内容失败: ' + (e.message || '未知错误'))
      } finally {
        this.normalizeModel()
        this._syncModelVarRefs()
        this.contentLoaded = true
      }
    },
    normalizeModel() {
      if (this.model.initialScore == null) this.model['initialScore'] = 100
      if (!this.model.resultVar)
        this.model['resultVar'] = { varCode: '', varLabel: '', _varId: null }
      if (!this.model.dimensionGroups) this.model['dimensionGroups'] = []
      if (!this.model.thresholds) this.model['thresholds'] = []
      if (!this.model.resultVar.operand)
        this.model.resultVar['operand'] = operandFromReferenceFields(
          this.model.resultVar
        )
      this.model.dimensionGroups.forEach((g) => {
        if (g.weight == null) g['weight'] = 1
        ;(g.dimensions || []).forEach((d) => {
          if (d.weight == null) d['weight'] = 1
          if (!d.operand) d['operand'] = operandFromReferenceFields(d)
          ;(d.rules || []).forEach((rule) =>
            (rule.conditions || []).forEach((cond) => {
              if (!cond.leftOperand)
                cond['leftOperand'] = operandFromReferenceFields(cond)
              if (!cond.rightOperand)
                cond['rightOperand'] = createLiteralOperand(
                  cond.value,
                  cond.varType || 'STRING'
                )
            })
          )
        })
      })
      this.model.thresholds.forEach((threshold) => {
        if (!threshold.resultOperand)
          threshold['resultOperand'] = createLiteralOperand(
            threshold.result,
            'STRING'
          )
      })
    },
    /** 根据 modelJson 中已有的 _varId 同步填充变量元信息 */
    _syncModelVarRefs() {
      const refs = this.projectRefs || []
      const findRef = (varId, refType) => {
        if (varId == null) return null
        return (
          refs.find(
            (r) =>
              r.varObj &&
              String(r.varObj.id) === String(varId) &&
              (!refType || r.refType === refType)
          ) || null
        )
      }
      const fillRef = (v) => {
        if (!v) return
        const ref = findRef(v._varId, v._refType)
        if (ref) {
          v.varCode = ref.refCode
          v.varLabel = ref.refLabel.label + ' ' + ref.refLabel.code
          v.varType = ref.varType
          v._refType = ref.refType
        }
      }
      if (this.model.resultVar) fillRef(this.model.resultVar)
      ;(this.model.dimensionGroups || []).forEach((g) =>
        (g.dimensions || []).forEach((d) => fillRef(d))
      )
      this.syncAllOperands()
    },
    setFieldOperand(holder, field, value) {
      holder[field] = value
      holder.varCode = compileOperand(value)
      holder.varLabel =
        (value && (value.label || value.code || value.value)) || ''
      holder.varType = (value && value.valueType) || holder.varType
      holder._varId = value && value.refId != null ? value.refId : null
      holder._refType = (value && value.refType) || null
    },
    setConditionOperand(condition, field, value) {
      condition[field] = value
      if (field === 'leftOperand') {
        condition.varCode = compileOperand(value)
        condition.varLabel =
          (value && (value.label || value.code || value.value)) || ''
        condition._varId = value && value.refId != null ? value.refId : null
        condition._refType = (value && value.refType) || null
        condition['operator'] = normalizeConditionOperator(
          condition.operator || '==',
          conditionOperandType(condition),
          value
        )
        this.onConditionOperatorChange(condition)
      } else {
        condition.value = compileOperand(value)
      }
    },
    conditionOperandType(condition) {
      return conditionOperandType(condition)
    },
    conditionOperatorGroups(condition) {
      return getConditionOperatorGroups(
        conditionOperandType(condition),
        condition && condition.leftOperand
      )
    },
    conditionRequiresValue(condition) {
      return conditionOperatorRequiresValue(
        condition && condition.operator,
        conditionOperandType(condition),
        condition && condition.leftOperand
      )
    },
    conditionRightContext(condition) {
      const option = findConditionOperator(
        condition && condition.operator,
        conditionOperandType(condition),
        condition && condition.leftOperand
      )
      return (option && option.rightContext) || 'READ_EXPRESSION'
    },
    conditionRightExpectedType(condition) {
      const option = findConditionOperator(
        condition && condition.operator,
        conditionOperandType(condition),
        condition && condition.leftOperand
      )
      return (
        (option && option.rightValueType) || conditionOperandType(condition)
      )
    },
    conditionRightAllowedKinds(condition) {
      const context = this.conditionRightContext(condition)
      if (context === 'LIST_QUERY_CONFIG')
        return getExpressionContext(context).allowedKinds
      return conditionOperatorAllowsVarValue(
        condition && condition.operator,
        conditionOperandType(condition),
        condition && condition.leftOperand
      )
        ? getExpressionContext(context).allowedKinds
        : ['LITERAL']
    },
    onConditionOperatorChange(condition) {
      const type = conditionOperandType(condition)
      const operator = normalizeConditionOperator(
        condition.operator || '==',
        type,
        condition.leftOperand
      )
      condition['operator'] = operator
      if (
        !conditionOperatorRequiresValue(operator, type, condition.leftOperand)
      ) {
        condition['rightOperand'] = null
        condition.value = ''
      }
    },
    setThresholdOperand(threshold, value) {
      threshold['resultOperand'] = value
      threshold.result = compileOperand(value)
    },
    syncAllOperands() {
      const sync = (holder, field) => {
        if (!holder || !holder[field]) return
        const result = syncOperandReference(
          holder[field],
          this.varPickerOptions
        )
        if (result.changed) this.setFieldOperand(holder, field, result.operand)
      }
      sync(this.model.resultVar, 'operand')
      ;(this.model.dimensionGroups || []).forEach((group) =>
        (group.dimensions || []).forEach((dim) => {
          sync(dim, 'operand')
          const rules = dim.rules || []
          rules.forEach((rule) =>
            (rule.conditions || []).forEach((condition) => {
              ['leftOperand', 'rightOperand'].forEach((field) => {
                const result = syncOperandReference(
                  condition[field],
                  this.varPickerOptions
                )
                if (result.changed)
                  this.setConditionOperand(condition, field, result.operand)
              })
            })
          )
        })
      )
    },
    thresholdColor(idx) {
      return THRESHOLD_COLORS[idx % THRESHOLD_COLORS.length]
    },
    toggleGroup(gi) {
      const g = this.model.dimensionGroups[gi]
      g['_collapsed'] = !g._collapsed
    },
    addGroup() {
      this.model.dimensionGroups.push({
        groupLabel: '',
        dimensions: [],
        weight: 1.0,
        _collapsed: false,
      })
    },
    removeGroup(gi) {
      this.model.dimensionGroups.splice(gi, 1)
    },
    addDimension(gi) {
      this.model.dimensionGroups[gi].dimensions.push({
        varCode: '',
        varLabel: '',
        _varId: null,
        operand: null,
        weight: 1.0,
        rules: [],
      })
    },
    removeDimension(gi, di) {
      this.model.dimensionGroups[gi].dimensions.splice(di, 1)
    },
    addRule(gi, di) {
      this.model.dimensionGroups[gi].dimensions[di].rules.push({
        conditions: [
          {
            leftOperand: null,
            operator: '==',
            rightOperand: createLiteralOperand('', 'STRING'),
            varCode: '',
            value: '',
          },
        ],
        score: 0,
      })
    },
    addCondition(rule) {
      rule.conditions.push({
        leftOperand: null,
        operator: '==',
        rightOperand: createLiteralOperand('', 'STRING'),
        varCode: '',
        value: '',
      })
    },
    addThreshold() {
      const last = this.model.thresholds[this.model.thresholds.length - 1]
      const min = last ? last.max : 0
      this.model.thresholds.push({
        min,
        max: min + 50,
        result: '',
        resultOperand: createLiteralOperand('', 'STRING'),
      })
    },
    async handleSave() {
      const saveModel = JSON.parse(JSON.stringify(this.model))
      ;(saveModel.dimensionGroups || []).forEach((g) => {
        delete g._collapsed
      })
      await saveContent({
        definitionId: this.definitionId,
        modelJson: JSON.stringify(saveModel),
      })
      await refreshFields(this.definitionId, JSON.stringify(saveModel))
      this.refreshProjectRefs()

      this.$message.success('保存成功')
    },
    async handleCompile() {
      await this.handleSave()
      const res = await compileRule(this.definitionId)
      if (isSuccessResult(res)) {
        this.$message.success('编译成功')
        if (this.$refs.scriptPanel) this.$refs.scriptPanel.refresh()
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
      const groups = this.model.dimensionGroups || []
      groups.forEach((group) => {
        const dimensions = group.dimensions || []
        dimensions.forEach((dim) => {
          addCode(codes, dim.varCode)
          collectOperandReferences(dim.operand).forEach((reference) =>
            addCode(codes, reference.code || reference.path)
          )
          const rules = dim.rules || []
          rules.forEach((rule) => {
            const conditions = rule.conditions || []
            conditions.forEach((cond) => {
              addCode(codes, cond.varCode)
              collectOperandReferences(cond.leftOperand).forEach((reference) =>
                addCode(codes, reference.code || reference.path)
              )
              collectOperandReferences(cond.rightOperand).forEach((reference) =>
                addCode(codes, reference.code || reference.path)
              )
            })
          })
        })
      })
      const params = buildSampleParamsFromCodes(
        Array.from(codes),
        this.projectRefs
      )
      groups.forEach((group) => {
        const dimensions = group.dimensions || []
        dimensions.forEach((dim) => {
          const firstRule = (dim.rules || []).find(
            (rule) => rule && rule.conditions && rule.conditions.length
          )
          if (!firstRule) return
          firstRule.conditions.forEach((cond) => {
            if (cond.rightOperand && cond.rightOperand.kind !== 'LITERAL')
              return
            if (!cond.varCode || cond.value === undefined || cond.value === '')
              return
            const ref = this.projectRefs.find((r) => r.refCode === cond.varCode)
            const sampleValue = cond.rightOperand
              ? cond.rightOperand.value
              : cond.value
            params[cond.varCode] = coerceSampleValue(sampleValue, ref)
          })
        })
      })
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
  },
}
</script>

<style lang="scss" scoped>
.asc-designer {
  background: #f3f3f3;
  padding: 20px;
  min-height: 100%;
}
.asc-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-radius: 4px;
  padding: 14px 20px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 8px;
}
.asc-title-area {
  display: flex;
  align-items: center;
}
.asc-title-icon {
  font-size: 18px;
  color: #eb2f96;
  margin-right: 8px;
}
.asc-title {
  font-size: 16px;
  font-weight: bold;
  color: #282828;
}
.asc-toolbar {
  display: flex;
  align-items: center;
  gap: 6px;
}
.asc-card {
  background: #fff;
  border-radius: 4px;
  padding: 16px 20px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  margin-bottom: 16px;
}
.asc-card-title {
  font-size: 14px;
  font-weight: bold;
  color: #333;
  margin-bottom: 14px;
  display: flex;
  align-items: center;
  gap: 6px;
  i {
    color: #1890ff;
  }
}
.asc-card-title-row {
  justify-content: space-between;
}
.base-config-row {
  display: flex;
  gap: 24px;
  flex-wrap: wrap;
  align-items: center;
}
.base-config-item {
  display: flex;
  align-items: center;
  gap: 10px;
}
.base-config-label {
  font-size: 13px;
  color: #666;
  white-space: nowrap;
}
.result-var-picker {
  display: flex;
  align-items: center;
  gap: 4px;
}
.result-var-switch-btn {
  padding: 4px 8px;
  color: #909399;
  &:hover {
    color: #1890ff;
  }
}
.result-var-label {
  font-size: 12px;
  color: #909399;
  margin-left: 4px;
}
.asc-group {
  padding: 0;
  overflow: hidden;
}
.asc-group-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: #fafafa;
  border-bottom: 1px solid #f0f0f0;
  gap: 8px;
}
.asc-group-left {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  flex: 1;
  flex-wrap: wrap;
}
.asc-group-right {
  display: flex;
  align-items: center;
  gap: 4px;
}
.group-label-input {
  max-width: 300px;
}
.group-weight-summary {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-left: 8px;
}
.weight-label {
  font-size: 12px;
  color: #888;
  white-space: nowrap;
}
.asc-group-body {
  padding: 16px;
}
.asc-dimension {
  border: 1px solid #e8e8e8;
  border-radius: 6px;
  margin-bottom: 12px;
  overflow: hidden;
  transition: box-shadow 0.2s, border-color 0.2s;
  &:hover {
    box-shadow: 0 2px 8px rgba(24, 144, 255, 0.15);
    border-color: #91caff;
  }
}
.dim-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #f5f5f5;
  border-bottom: 1px solid #f0f0f0;
  flex-wrap: wrap;
}
.dim-index {
  width: 32px;
  height: 22px;
  border-radius: 11px;
  background: #1890ff;
  color: #fff;
  font-size: 11px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-weight: bold;
}
.dim-weight-area {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-left: auto;
}
.item-field-label {
  font-size: 12px;
  color: #888;
  white-space: nowrap;
}
.rule-table {
  width: 100%;
  border-collapse: collapse;
  th,
  td {
    border: 1px solid #f0f0f0;
    padding: 8px 10px;
    vertical-align: top;
  }
  th {
    background: #fafafa;
    font-size: 12px;
    color: #888;
    font-weight: 600;
    text-align: center;
  }
  .col-idx {
    width: 40px;
    text-align: center;
  }
  .col-conditions {
  }
  .col-score {
    width: 120px;
    text-align: center;
  }
  .col-action {
    width: 60px;
    text-align: center;
  }
}
.condition-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
  &:last-child {
    margin-bottom: 2px;
  }
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
.score-input {
  width: 100%;
}
.cond-and {
  font-size: 11px;
  color: #1890ff;
  font-weight: bold;
  margin: 0 2px;
  flex-shrink: 0;
}
.dim-empty,
.group-empty,
.asc-empty {
  text-align: center;
  padding: 20px;
  color: #bbb;
  font-size: 13px;
}
.asc-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}
.asc-formula {
  background: #fffbe6;
  border: 1px solid #ffe58f;
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
    color: #c41d7f;
  }
}
.op {
  color: #888;
  font-weight: bold;
  font-size: 13px;
}
.formula-group {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  background: #fff7e6;
  border: 1px solid #ffd591;
  border-radius: 4px;
  padding: 2px 8px;
}
.formula-group-label {
  color: #d46b08;
  font-size: 12px;
  font-weight: 600;
}
.formula-dims {
  font-size: 12px;
  color: #555;
}
.formula-term {
  color: #1890ff;
  font-weight: 500;
}
.weight-summary {
  display: flex;
  align-items: center;
  gap: 8px;
}
.weight-label-sm {
  font-size: 13px;
  color: #666;
  font-weight: normal;
}
.weight-value {
  font-weight: bold;
  font-size: 14px;
  min-width: 36px;
}
.weight-detail-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.weight-detail-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  background: #fafafa;
  border-radius: 4px;
  font-size: 12px;
  color: #555;
}
.weight-detail-name {
  font-weight: 600;
  color: #333;
  min-width: 120px;
}
.weight-detail-val {
  color: #1890ff;
  font-weight: 500;
}
.weight-detail-dims {
  color: #888;
}
.threshold-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.threshold-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  background: #fafafa;
}
.thresh-color-bar {
  width: 4px;
  height: 36px;
  border-radius: 2px;
  flex-shrink: 0;
}
.thresh-range {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}
.thresh-sep {
  font-size: 12px;
  color: #888;
  white-space: nowrap;
}
.thresh-result {
  flex: 1;
}
.thresh-badge {
  flex-shrink: 0;
  border-color: transparent !important;
}
.test-hint {
  font-size: 12px;
  color: #909399;
  margin-bottom: 8px;
}
.test-result {
  margin-top: 16px;
}
</style>
