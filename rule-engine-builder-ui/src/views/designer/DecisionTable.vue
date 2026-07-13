<template>
  <div class="dt-designer">
    <!-- 顶部工具栏 -->
    <div class="dt-header">
      <div class="dt-title-area">
        <el-button type="text" icon="el-icon-back" @click="$router.back()" style="color:#606266;" />
        <i class="el-icon-s-grid dt-title-icon" />
        <span class="dt-title">决策表配置</span>
        <el-tag size="mini" type="info" style="margin-left:8px;">共 {{ model.rules.length }} 条规则</el-tag>
      </div>
      <div class="dt-toolbar">
        <el-button size="small" icon="el-icon-plus" @click="addRule">添加行</el-button>
        <el-divider direction="vertical" />
        <el-button size="small" icon="el-icon-document" @click="handleSave">临时保存配置</el-button>
        <el-button size="small" type="warning" icon="el-icon-cpu" @click="handleCompile">保存并编译</el-button>
        <el-button size="small" type="primary" icon="el-icon-video-play" @click="handleTest">编译后测试</el-button>
        <el-divider direction="vertical" />
        <span class="toolbar-label">命中策略</span>
        <el-select v-model="model.hitPolicy" size="small" style="width:110px;">
          <el-option label="首次命中" value="FIRST" />
          <el-option label="全部执行" value="ALL" />
          <el-option label="唯一命中" value="UNIQUE" />
        </el-select>
        <el-tooltip :content="hitPolicyDesc" placement="bottom" effect="light">
          <i class="el-icon-question tip-icon" />
        </el-tooltip>
      </div>
    </div>

    <!-- 变量加载状态 -->
    <div v-if="loadingVars || varPickerOptions.length" class="dt-var-status">
      <span v-if="loadingVars" style="font-size:12px;color:#999;"><i class="el-icon-loading" /> 加载变量库...</span>
      <span v-else style="font-size:12px;color:#52c41a;">
        <i class="el-icon-s-custom" /> 已加载 {{ varPickerOptions.length }} 个变量/常量/对象字段
      </span>
    </div>

    <!-- 规则列表：每条含条件树 + 动作 -->
    <div class="dt-rules-wrap">
      <template v-if="contentLoaded && model.rules.length > 0">
        <div
          v-for="(row, ri) in model.rules"
          :key="'rule-' + ri"
          class="dt-rule-card"
        >
          <div class="dt-rule-toolbar">
            <span class="dt-rule-no">#{{ ri + 1 }}</span>
            <el-button type="text" size="mini" @click="copyRule(ri)">复制</el-button>
            <el-button type="text" size="mini" class="btn-delete" @click="removeRule(ri)">删除</el-button>
          </div>
          <div class="dt-rule-grid">
            <div class="dt-cond-panel">
              <condition-group-editor
                v-if="row.conditionRoot"
                :group="row.conditionRoot"
                :vars="varPickerOptions"
                :functions="projectFunctions"
                :selected-vars="selectedVarPickerOptions"
                :get-var-options-fn="getVarOptions"
              />
            </div>
            <div class="dt-act-panel">
              <div class="dt-act-panel-head">
                <span class="dt-act-panel-title">动作 (THEN)</span>
                <span class="dt-act-panel-hint">本条规则独立配置，可与其它行不同</span>
                <el-button type="primary" size="mini" plain icon="el-icon-plus" @click="addRuleAction(ri)">添加动作</el-button>
              </div>
              <div class="dt-act-rows">
                <div
                  v-for="(act, ai) in row.actions"
                  :key="'r' + ri + '-act-' + ai"
                  class="dt-act-field"
                >
                  <div class="dt-act-head">
                    <span class="col-tag act-tag">THEN</span>
                    <span class="dt-act-title">{{ operandDisplay(act.targetOperand) || '未选目标字段' }}</span>
                    <span class="th-actions">
                      <i
                        class="el-icon-delete"
                        title="删除此动作"
                        @click.stop="removeRuleAction(ri, ai)"
                      />
                    </span>
                  </div>
                  <div class="dt-act-body">
                    <operand-picker
                      :value="act.targetOperand"
                      :vars="varPickerOptions"
                      :selected-vars="selectedVarPickerOptions"
                      :allowed-kinds="writeOperandKinds"
                      writable-only
                      placeholder="选择目标字段"
                      size="mini"
                      @input="operand => setActionOperand(act, 'targetOperand', operand)"
                    />
                    <span class="dt-act-eq">=</span>
                    <operand-picker
                      :value="act.valueOperand"
                      :vars="varPickerOptions"
                      :functions="projectFunctions"
                      :selected-vars="selectedVarPickerOptions"
                      :allowed-kinds="valueOperandKinds"
                      :expected-type="act.targetOperand && act.targetOperand.valueType"
                      placeholder="选择阈值、路径或字段"
                      size="mini"
                      @input="operand => setActionOperand(act, 'valueOperand', operand)"
                    />
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </template>

      <!-- 加载中 -->
      <div v-if="!contentLoaded" class="dt-loading">
        <i class="el-icon-loading" /> 加载决策表数据...
      </div>

      <!-- 空状态 -->
      <div v-else-if="contentLoaded && model.rules.length === 0" class="dt-empty">
        <i class="el-icon-s-grid dt-empty-icon" />
        <p>暂无规则行，请点击「添加行」；每条规则内可单独「添加动作」并选择要赋值的变量</p>
      </div>
    </div>

    <!-- 脚本预览/编辑面板 -->
    <script-panel
      v-if="definitionId"
      ref="scriptPanel"
      :definitionId="definitionId"
      :onBeforeCompile="handleSave"
      @mode-change="onScriptModeChange"
    />

    <!-- 脚本覆盖模式横幅 -->
    <div v-if="scriptMode === 'script'" class="script-override-banner">
      <i class="el-icon-warning" />
      <span>脚本覆盖模式已激活，可视化编辑暂停。如需恢复请在下方脚本面板切换回「可视化模式」。</span>
    </div>

    <!-- 测试执行弹窗 -->
        <designer-test-dialog
      :visible.sync="testVisible"
      :definition-id="definitionId"
      :project-id="projectIdForRefs"
      model-type="TABLE"
      :model-json="model"
      :params-template="testParamsTemplate"
    />
  </div>
</template>

<script>
import { saveContent, compileRule, executeRule, getContent, refreshFields } from '@/api/definition'
import varPickerMixin from '@/mixins/varPickerMixin'
import OperandPicker from '@/components/common/OperandPicker.vue'
import ScriptPanel from '@/components/common/ScriptPanel.vue'
import DesignerTestDialog from '@/components/common/DesignerTestDialog.vue'
import ConditionGroupEditor from '@/components/decision/ConditionGroupEditor.vue'
import {
  createEmptyLeaf,
  createEmptyActionItem,
  migrateRuleConditionsToTree,
  collectVarCodesFromConditionTree,
  walkConditionLeaves
} from '@/utils/decisionConditionTree'
import { collectOperandReferences, createLiteralOperand, operandDisplay, operandFromReferenceFields, syncOperandReference } from '@/utils/operand'
import { buildSampleParamsFromCodes, coerceSampleValue } from '@/utils/testSampleParams'
import { isSuccessResult, resultErrorMessage } from '@/utils/apiResponse'

export default {
  name: 'DecisionTable',
  components: { DesignerTestDialog, OperandPicker, ScriptPanel, ConditionGroupEditor },
  mixins: [varPickerMixin],
  data() {
    return {
      definitionId: null,
      model: {
        hitPolicy: 'FIRST',
        conditions: [],
        actions: [],
        rules: []
      },
      scriptMode: 'visual',
      testVisible: false,
      testParamsTemplate: {},
      testParams: {},
      testResult: null,
      contentLoaded: false,
      writeOperandKinds: ['PATH', 'REFERENCE'],
      valueOperandKinds: ['LITERAL', 'PATH', 'REFERENCE', 'FUNCTION']
    }
  },
  computed: {
    hitPolicyDesc() {
      const map = {
        FIRST: '首次命中：从上到下匹配规则，返回第一条满足条件的规则结果',
        ALL: '全部执行：匹配所有满足条件的规则并依次执行，结果为最后一条',
        UNIQUE: '唯一命中：期望有且仅有一条规则满足，否则报错'
      }
      return map[this.model.hitPolicy] || ''
    },
    /** 测试弹窗中需要录入的变量编码列表（条件树 DFS 去重） */
    testVarCodeList() {
      const s = new Set()
      ;(this.model.rules || []).forEach(r => {
        collectVarCodesFromConditionTree(r.conditionRoot, s)
      })
      return Array.from(s)
    },
    selectedVarPickerOptions() {
      const result = []
      const seen = new Set()
      const addItem = item => {
        const option = this.findVarPickerOptionByModelItem(item)
        if (!option) return
        const key = this.varPickerOptionKey(option)
        if (seen.has(key)) return
        seen.add(key)
        result.push(option)
      }
      const addOperand = operand => {
        collectOperandReferences(operand).forEach(reference => addItem({
          varCode: reference.code,
          _varId: reference.refId,
          _refType: reference.refType
        }))
      }

      ;(this.model.rules || []).forEach(rule => {
        walkConditionLeaves(rule.conditionRoot, leaf => {
          addOperand(leaf.leftOperand)
          addOperand(leaf.rightOperand)
        })
        ;(rule.actions || []).forEach(action => {
          addOperand(action.targetOperand)
          addOperand(action.valueOperand)
        })
      })
      return result
    }
  },
  created() {
    this.definitionId = this.$route.params.id
    this.loadProjectVars(this.definitionId)
    this.loadContent()
  },
  methods: {
    async loadContent() {
      try {
        const res = await getContent(this.definitionId)
        const content = res && res.data ? res.data : res
        if (content && content.modelJson && content.modelJson !== '{}') {
          this.model = JSON.parse(content.modelJson)
          this.normalizeModel()
        }
      } catch (e) {
        this.$message.error('加载内容失败: ' + (e.message || '未知错误'))
      } finally {
        this.contentLoaded = true
      }
    },

    /**
     * 同步条件叶与「变量比较」右侧引用到最新 projectRefs。
     */
    _syncModelVarRefs() {
      let changed = false
      const sync = (holder, field) => {
        const result = syncOperandReference(holder[field], this.varPickerOptions)
        if (!result.changed) return
        this.$set(holder, field, result.operand)
        changed = true
      }
      ;(this.model.rules || []).forEach(rule => {
        walkConditionLeaves(rule.conditionRoot, leaf => {
          sync(leaf, 'leftOperand')
          sync(leaf, 'rightOperand')
        })
        ;(rule.actions || []).forEach(action => {
          sync(action, 'targetOperand')
          sync(action, 'valueOperand')
        })
      })
      if (changed) this.$forceUpdate()
    },

    /**
     * 判断规则动作是否已是「每行自带 varCode」的新结构（旧数据仅为 { value }）。
     */
    ensureRuleActionsShape(rule, legacyGlobalActions) {
      const actions = rule.actions || []
      const normalized = (actions.length ? actions : [createEmptyActionItem()]).map((action, index) => {
        if (action.targetOperand !== undefined || action.valueOperand !== undefined) return action
        const definition = action.varCode ? action : ((legacyGlobalActions || [])[index] || {})
        return {
          targetOperand: operandFromReferenceFields(definition),
          valueOperand: createLiteralOperand(action.value == null ? '' : action.value, definition.varType || 'STRING')
        }
      })
      this.$set(rule, 'actions', normalized)
    },

    /**
     * 规范化模型：废弃顶层 conditions/actions 列定义；条件树 + 每规则独立动作列表。
     */
    normalizeModel() {
      this.model.rules = this.model.rules || []
      const legacyCols = Array.isArray(this.model.conditions) ? [...this.model.conditions] : []
      const legacyGlobalActions = Array.isArray(this.model.actions) && this.model.actions.length
        ? JSON.parse(JSON.stringify(this.model.actions))
        : null

      this.model.rules.forEach(r => {
        this.ensureRuleActionsShape(r, legacyGlobalActions)

        const hasTree = r.conditionRoot && r.conditionRoot.type === 'group' && Array.isArray(r.conditionRoot.children)
        if (!hasTree) {
          const migrated = migrateRuleConditionsToTree(r.conditions || [], legacyCols)
          this.$set(r, 'conditionRoot', migrated)
        }
        walkConditionLeaves(r.conditionRoot, leaf => {
          if (!leaf.leftOperand && leaf.varCode) this.$set(leaf, 'leftOperand', operandFromReferenceFields(leaf))
          if (!leaf.rightOperand && leaf.value !== undefined) {
            if (leaf.valueKind === 'VAR') {
              this.$set(leaf, 'rightOperand', operandFromReferenceFields({
                varCode: leaf.value,
                varLabel: leaf.rightVarLabel,
                varType: leaf.rightVarType,
                _varId: leaf._rightVarId,
                _refType: leaf._rightRefType
              }))
            } else {
              this.$set(leaf, 'rightOperand', createLiteralOperand(leaf.value, leaf.varType || 'STRING'))
            }
          }
        })
        if (r.conditions !== undefined) delete r.conditions
      })
      this.model.conditions = []
      this.model.actions = []
    },

    operandDisplay,

    /**
     * 测试弹窗：变量中文标签。
     */
    testVarLabel(code) {
      const ref = this.projectRefs.find(r => r.refCode === code)
      if (ref && ref.varObj && ref.varObj.varLabel) return ref.varObj.varLabel
      if (ref && ref.refLabel) return ref.refLabel
      return code
    },

    /**
     * 测试弹窗：从变量库解析类型与枚举串（用于表单控件）。
     */
    testVarMeta(code) {
      const ref = this.projectRefs.find(r => r.refCode === code)
      const vt = (ref && ref.varType) || 'STRING'
      let enumOptions = ''
      if (vt === 'ENUM' && ref && ref.varObj) {
        const opts = this.getVarOptions(code) || []
        enumOptions = opts.map(o => o.value || o.optionValue).filter(Boolean).join(',')
      }
      return { varType: vt, enumOptions }
    },

    /**
     * 测试弹窗：枚举选项列表。
     */
    testEnumOpts(code) {
      const m = this.testVarMeta(code)
      if (!m.enumOptions) return []
      return m.enumOptions.split(',').map(s => s.trim()).filter(Boolean)
    },

    setActionOperand(action, field, operand) {
      this.$set(action, field, operand || null)
    },

    varPickerOptionKey(option) {
      const id = option && (option._varId != null ? option._varId : (option.id != null ? option.id : (option.varObj && option.varObj.id)))
      const refType = option && (option._refType || option.refType || (option.varObj && option.varObj.refType) || (option._ref && option._ref.refType))
      if (id != null && id !== '') return (refType || 'REF') + ':' + id
      return (option && option.varCode) || ''
    },

    findVarPickerOptionByModelItem(item) {
      if (!item) return null
      const id = item._varId != null ? item._varId : (item.id != null ? item.id : (item.varObj && item.varObj.id))
      const refType = item._refType || item.refType || (item.varObj && item.varObj.refType)
      if (id != null && id !== '') {
        const byId = this.varPickerOptions.find(option => {
          const optionId = option._varId != null ? option._varId : (option.id != null ? option.id : (option.varObj && option.varObj.id))
          const optionType = option._refType || option.refType || (option.varObj && option.varObj.refType) || (option._ref && option._ref.refType)
          return String(optionId) === String(id) && (!refType || !optionType || optionType === refType)
        })
        if (byId) return byId
      }
      const code = item.varCode || item.refCode || ''
      if (!code) return null
      const exact = this.varPickerOptions.find(option => option.varCode === code)
      if (exact) return exact
      const leafMatches = this.varPickerOptions.filter(option => {
        if (!option._ref || option._ref.category !== 'object') return false
        const optionCode = option.varCode || ''
        return optionCode.substring(optionCode.lastIndexOf('.') + 1) === code
      })
      return leafMatches.length === 1 ? leafMatches[0] : null
    },

    /**
     * 在本条规则末尾增加一条可独立配置变量的动作。
     */
    addRuleAction(ruleIndex) {
      const r = this.model.rules[ruleIndex]
      if (!r) return
      if (!r.actions) this.$set(r, 'actions', [])
      r.actions.push(createEmptyActionItem())
    },

    /**
     * 删除本条规则内的一条动作（至少保留一条以免结构为空）。
     */
    removeRuleAction(ruleIndex, actionIndex) {
      const r = this.model.rules[ruleIndex]
      if (!r || !r.actions) return
      if (r.actions.length <= 1) {
        this.$message.warning('每条规则至少保留一条动作')
        return
      }
      this.$confirm('确认删除该动作？', '提示', { type: 'warning' }).then(() => {
        r.actions.splice(actionIndex, 1)
      }).catch(() => {})
    },

    /**
     * 新增规则行：默认条件树 + 一条空动作。
     */
    addRule() {
      this.model.rules.push({
        conditionRoot: { type: 'group', op: 'AND', children: [createEmptyLeaf()] },
        actions: [createEmptyActionItem()]
      })
    },

    copyRule(index) {
      const orig = this.model.rules[index]
      const copy = JSON.parse(JSON.stringify(orig))
      this.model.rules.splice(index + 1, 0, copy)
    },

    removeRule(index) {
      this.model.rules.splice(index, 1)
    },

    async handleSave() {
      try {
        this.normalizeModel()
        await saveContent({ definitionId: this.definitionId, modelJson: JSON.stringify(this.model) })
        await refreshFields(this.definitionId, JSON.stringify(this.model))
        this.refreshProjectRefs()
        this.$message.success('保存成功')
      } catch (e) {
        this.$message.error('保存失败: ' + (e && e.message ? e.message : '未知错误'))
        throw e
      }
    },

    async handleCompile() {
      await this.handleSave()
      const res = await compileRule(this.definitionId)
      if (isSuccessResult(res)) {
        this.$message.success('编译成功')
        await this.loadProjectVars(this.definitionId)
        if (this.$refs.scriptPanel) {
          this.$refs.scriptPanel.refresh()
        }
      } else {
        this.$message.error('编译失败: ' + resultErrorMessage(res))
      }
    },

    /**
     * 根据条件树涉及的变量构造测试默认值模板。
     */
    buildTestParamsTemplate() {
      const template = buildSampleParamsFromCodes(this.testVarCodeList, this.projectRefs)
      const firstRule = (this.model.rules || []).find(rule => rule && rule.conditions && rule.conditions.length)
      if (firstRule) {
        (this.model.conditions || []).forEach((condition, index) => {
          const code = condition && condition.varCode
          const ruleCondition = firstRule.conditions[index]
          if (!code || !Object.prototype.hasOwnProperty.call(template, code) || !ruleCondition || ruleCondition.value === undefined || ruleCondition.value === '') return
          const ref = this.projectRefs.find(r => r.refCode === code)
          template[code] = coerceSampleValue(ruleCondition.value, ref)
        })
      }
      const firstTreeRule = (this.model.rules || []).find(rule => rule && rule.conditionRoot)
      if (firstTreeRule) {
        walkConditionLeaves(firstTreeRule.conditionRoot, leaf => {
          const leftRefs = collectOperandReferences(leaf.leftOperand)
          const code = leftRefs.length ? (leftRefs[0].code || leftRefs[0].path) : ''
          if (!code || !Object.prototype.hasOwnProperty.call(template, code) || !leaf.rightOperand || leaf.rightOperand.kind !== 'LITERAL' || leaf.rightOperand.value === '') return
          const ref = this.projectRefs.find(r => r.refCode === code)
          template[code] = coerceSampleValue(leaf.rightOperand.value, ref)
        })
      }
      return template
    },

    handleTest() {
      const template = this.buildTestParamsTemplate()
      this.testParamsTemplate = template
      this.testParams = template
      this.testResult = null
      this.testVisible = true
    },

    async doTest() {
      const res = await executeRule({ definitionId: this.definitionId, params: this.testParams })
      this.testResult = res && res.data ? res.data : res
    },

    formatResult(val) {
      if (val === null || val === undefined) return '(空)'
      try {
        return JSON.stringify(typeof val === 'string' ? JSON.parse(val) : val, null, 2)
      } catch (e) {
        return String(val)
      }
    },

    onScriptModeChange(mode) {
      this.scriptMode = mode
    }
  }
}
</script>

<style lang="scss" scoped>
.dt-designer {
  background: #fff;
  border-radius: 4px;
  padding: 16px 20px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.08);
  min-height: 100%;
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
}

.dt-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  flex-wrap: wrap;
  gap: 8px;
}
.dt-title-area {
  display: flex;
  align-items: center;
}
.dt-title-icon {
  font-size: 18px;
  color: #1890ff;
  margin-right: 8px;
}
.dt-title {
  font-size: 16px;
  font-weight: bold;
  color: #282828;
}
.dt-toolbar {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.toolbar-label {
  font-size: 13px;
  color: #666;
}
.tip-icon {
  color: #999;
  cursor: pointer;
  font-size: 14px;
  &:hover { color: #1890ff; }
}

.dt-var-status {
  margin-bottom: 8px;
}

.dt-rules-wrap {
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
}

.dt-rule-card {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 12px 14px;
  background: #fafafa;
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
}
.dt-rule-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
.dt-rule-no {
  font-weight: 600;
  color: #666;
  margin-right: 8px;
}
.dt-rule-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
  align-items: flex-start;
  width: 100%;
  box-sizing: border-box;
}
.dt-cond-panel {
  flex: 1 1 100%;
  width: 100%;
  min-width: 0;
  background: #fff;
  border: 1px solid #e8e8e8;
  border-radius: 6px;
  padding: 12px 14px;
  box-sizing: border-box;
}
.dt-act-panel {
  flex: 1 1 100%;
  width: 100%;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
  box-sizing: border-box;
}
.dt-act-panel-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 12px;
}
.dt-act-panel-title {
  font-weight: 600;
  color: #333;
}
.dt-act-panel-hint {
  font-size: 12px;
  color: #999;
  flex: 1 1 auto;
  min-width: 160px;
}
.dt-act-rows {
  display: flex;
  flex-direction: column;
  gap: 12px;
  width: 100%;
}
.dt-act-field {
  background: #fafffe;
  border: 1px solid #d9f7f0;
  border-radius: 6px;
  padding: 10px 12px;
  width: 100%;
  box-sizing: border-box;
}
.dt-act-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}
.dt-act-title {
  font-size: 13px;
  font-weight: 600;
  color: #333;
  flex: 1;
  min-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.dt-act-body {
  width: 100%;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  ::v-deep .var-picker-wrap { flex: 1 1 0; width: auto !important; min-width: 0; }
}
.dt-act-eq { color: #909399; font-weight: 600; }
.dt-act-value-ctl {
  width: 100%;
  max-width: 100%;
}
@media (min-width: 1100px) {
  .dt-rule-grid {
    flex-wrap: nowrap;
    align-items: stretch;
  }
  /* 条件区占主要横向空间，避免控件挤在左侧 */
  .dt-cond-panel {
    flex: 3 1 0;
    min-width: 0;
    max-width: none;
  }
  .dt-act-panel {
    flex: 1 1 300px;
    min-width: 260px;
    max-width: 400px;
  }
}
.col-tag {
  display: inline-block;
  font-size: 10px;
  font-weight: bold;
  padding: 0 4px;
  border-radius: 2px;
  letter-spacing: 0.5px;
  line-height: 16px;
  flex-shrink: 0;
}
.act-tag {
  background: #e6fffb;
  color: #13c2c2;
}
.th-actions {
  display: inline-flex;
  gap: 6px;
  flex-shrink: 0;
  i {
    cursor: pointer;
    color: #c0c0c0;
    font-size: 13px;
    transition: color .2s;
    &:hover { color: #1890ff; }
    .action-delete {
      color: #F76E6C;
    }
  }
}

.dt-loading {
  text-align: center;
  padding: 40px;
  color: #999;
  font-size: 13px;
  i { margin-right: 4px; }
}

.dt-empty {
  text-align: center;
  padding: 30px;
  color: #999;
  font-size: 13px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
}
.dt-empty-icon {
  font-size: 36px;
  color: #ddd;
  display: block;
  margin-bottom: 6px;
}

.test-result {
  margin-top: 16px;
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

.script-override-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  background: #fff1f0;
  border: 1px solid #ffccc7;
  border-radius: 4px;
  margin-top: 12px;
  font-size: 12px;
  color: #cf1322;
  i { color: #f5222d; font-size: 14px; }
}
</style>
