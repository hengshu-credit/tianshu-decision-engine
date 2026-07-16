<template>
  <div class="block-editor">
    <div v-for="(block, bi) in blocks" :key="bi" class="block-item" :class="'block-' + block.type">
      <div class="block-header">
        <span class="block-type-tag" :style="{ background: typeColor(block.type) }">{{ typeLabel(block.type) }}</span>
        <div class="block-header-actions">
          <el-button v-if="bi > 0" type="text" size="mini" icon="el-icon-top" @click="moveBlock(bi, -1)" />
          <el-button v-if="bi < blocks.length - 1" type="text" size="mini" icon="el-icon-bottom" @click="moveBlock(bi, 1)" />
          <el-button type="text" size="mini" icon="el-icon-delete" class="danger" @click="removeBlock(bi)" />
        </div>
      </div>

      <div class="block-body">
        <template v-if="block.type === 'assign'">
          <assignment-row :action="block" />
          <div class="rounding-row">
            <el-switch v-model="block.enableRounding" active-text="精度" @change="sync" />
            <template v-if="block.enableRounding">
              <span class="mini-label">小数位</span>
              <el-input-number v-model="block.decimalPlaces" :min="0" :max="10" size="mini" @change="sync" />
              <el-select v-model="block.roundingMode" size="mini" class="rounding-mode" placeholder="进位规则" @change="sync">
                <el-option label="四舍五入" value="HALF_UP" />
                <el-option label="向上取整" value="UP" />
                <el-option label="向下截断" value="DOWN" />
                <el-option label="正无穷方向" value="CEILING" />
                <el-option label="负无穷方向" value="FLOOR" />
              </el-select>
            </template>
          </div>
        </template>

        <template v-else-if="block.type === 'if-block'">
          <div v-for="(branch, branchIndex) in block.branches" :key="branchIndex" class="nested-card">
            <div class="nested-head">
              <span>{{ branch.type === 'if' ? 'IF' : branch.type === 'elseif' ? 'ELSE IF' : 'ELSE' }}</span>
              <el-button type="text" size="mini" class="danger" @click="removeBranch(block, branchIndex)">删除</el-button>
            </div>
            <div v-if="branch.type !== 'else'" class="condition-row">
              <operand-picker :value="branch.leftOperand" :vars="vars" :functions="functions" :list-options="listOptions" :selected-vars="selectedVars" :allowed-kinds="readKinds" placeholder="选择左操作数" size="mini" @input="value => setConditionLeftOperand(branch, value)" />
              <el-select v-model="branch.operator" size="mini" class="operator" @change="onConditionOperatorChange(branch)">
                <el-option v-for="option in operatorOptions(branch)" :key="option.value" :label="option.label" :value="option.value" />
              </el-select>
              <operand-picker v-if="operatorRequiresValue(branch)" :value="branch.rightOperand" :vars="vars" :functions="functions" :list-options="listOptions" :selected-vars="selectedVars" :allowed-kinds="rightAllowedKinds(branch)" :context="rightContext(branch)" :expected-type="rightExpectedType(branch)" placeholder="选择右操作数" size="mini" @input="value => setOperand(branch, 'rightOperand', value)" />
            </div>
            <div class="nested-body">
              <div v-for="(action, actionIndex) in branch.actions" :key="actionIndex" class="assignment-with-delete">
                <assignment-row :action="action" />
                <el-button v-if="branch.actions.length > 1" type="text" size="mini" icon="el-icon-delete" class="danger" @click="removeAssignment(branch.actions, actionIndex)" />
              </div>
              <el-button size="mini" icon="el-icon-plus" class="wide-button" @click="addAssignment(branch.actions)">添加赋值</el-button>
            </div>
          </div>
          <div class="button-row">
            <el-button v-if="!hasElse(block)" size="mini" @click="addBranch(block, 'elseif')">+ ELSE IF</el-button>
            <el-button v-if="!hasElse(block)" size="mini" @click="addBranch(block, 'else')">+ ELSE</el-button>
          </div>
        </template>

        <template v-else-if="block.type === 'switch-block'">
          <div class="inline-row">
            <span class="mini-label">匹配</span>
            <operand-picker :value="block.matchOperand" :vars="vars" :functions="functions" :selected-vars="selectedVars" :allowed-kinds="readKinds" placeholder="选择匹配操作数" size="mini" @input="value => setOperand(block, 'matchOperand', value)" />
          </div>
          <div v-for="(item, caseIndex) in block.cases" :key="caseIndex" class="nested-card">
            <div class="nested-head">
              <span>CASE</span>
              <operand-picker :value="item.valueOperand" :vars="vars" :functions="functions" :selected-vars="selectedVars" :allowed-kinds="valueKinds" :expected-type="operandType(block.matchOperand)" placeholder="选择匹配值" size="mini" @input="value => setOperand(item, 'valueOperand', value)" />
              <el-button type="text" size="mini" class="danger" @click="removeCase(block, caseIndex)">删除</el-button>
            </div>
            <div class="nested-body">
              <assignment-row v-for="(action, actionIndex) in item.actions" :key="actionIndex" :action="action" />
              <el-button size="mini" icon="el-icon-plus" class="wide-button" @click="addAssignment(item.actions)">添加赋值</el-button>
            </div>
          </div>
          <div class="nested-card default-card">
            <div class="nested-head"><span>DEFAULT</span></div>
            <div class="nested-body">
              <assignment-row v-for="(action, actionIndex) in block.defaultActions" :key="actionIndex" :action="action" />
              <el-button size="mini" icon="el-icon-plus" class="wide-button" @click="addAssignment(block.defaultActions)">添加赋值</el-button>
            </div>
          </div>
          <el-button size="mini" icon="el-icon-plus" class="wide-button" @click="addCase(block)">添加 Case</el-button>
        </template>

        <template v-else-if="block.type === 'func-call'">
          <div class="inline-row">
            <span class="mini-label">结果</span>
            <operand-picker :value="block.targetOperand" :vars="vars" :selected-vars="selectedVars" :allowed-kinds="writeKinds" writable-only placeholder="选择结果字段（可空）" size="mini" @input="value => setOperand(block, 'targetOperand', value)" />
          </div>
          <div class="inline-row">
            <span class="mini-label">函数</span>
            <el-select v-model="block.functionCode" size="mini" filterable placeholder="选择函数" class="grow" @change="onFunctionSelect(block, $event)">
              <el-option v-for="fn in functions" :key="fn.id || fn.funcCode" :label="functionLabel(fn)" :value="fn.funcCode || fn.functionCode" />
            </el-select>
          </div>
          <div v-for="(arg, argIndex) in block.args" :key="argIndex" class="inline-row">
            <span class="mini-label">参数 {{ argIndex + 1 }}</span>
            <operand-picker :value="arg" :vars="vars" :functions="functions" :selected-vars="selectedVars" :allowed-kinds="valueKinds" placeholder="选择参数" size="mini" @input="value => setArrayOperand(block.args, argIndex, value)" />
            <el-button v-if="block.args.length > 1" type="text" size="mini" icon="el-icon-delete" class="danger" @click="removeArrayOperand(block.args, argIndex)" />
          </div>
          <el-button size="mini" icon="el-icon-plus" class="wide-button" @click="addArrayOperand(block.args)">添加参数</el-button>
        </template>

        <template v-else-if="block.type === 'rule-call'">
          <rule-execution-selector
            :rule-id="block.ruleId"
            :rule-code="block.ruleCode"
            :rules="rules"
            :current-rule-id="currentRuleId"
            :current-rule-code="currentRuleCode"
            @visible-change="visible => rememberRuleCallSnapshot(block, visible)"
            @select="rule => onRuleSelect(block, rule)"
          />
          <div class="shared-context-hint">
            <i class="el-icon-info" /> 子规则全部输出会写入当前共享上下文，后续动作可直接引用最新值
          </div>
          <div class="mapping-toggle-row">
            <el-switch
              :value="hasRuleOutputMapping(block)"
              active-text="额外映射单个输出字段"
              @change="enabled => toggleRuleOutputMapping(block, enabled)"
            />
          </div>
          <div v-if="hasRuleOutputMapping(block)" class="rule-output-mapping">
            <div class="mapping-header">
              <span class="mini-label">单字段映射</span>
              <el-button type="text" size="mini" icon="el-icon-delete" class="danger" @click="clearRuleOutputMapping(block)">一键清空</el-button>
            </div>
            <div class="inline-row">
              <span class="mini-label">子规则输出</span>
              <el-select v-model="block.outputField" size="mini" filterable clearable placeholder="选择输出字段" class="grow" @change="sync">
                <el-option v-for="field in ruleOutputFields(block)" :key="field.id || field.scriptName || field.fieldName" :label="fieldLabel(field)" :value="field.scriptName || field.fieldName" />
              </el-select>
            </div>
            <div class="inline-row">
              <span class="mini-label">写入字段</span>
              <operand-picker :value="block.targetOperand" :vars="vars" :selected-vars="selectedVars" :allowed-kinds="writeKinds" writable-only placeholder="选择当前上下文字段" size="mini" @input="value => setOperand(block, 'targetOperand', value)" />
            </div>
          </div>
        </template>

        <template v-else-if="block.type === 'foreach'">
          <div class="inline-row">
            <span class="mini-label">循环变量</span>
            <el-input v-model="block.itemVar" size="mini" class="item-var" placeholder="item" @input="sync" />
            <span class="mini-label">列表</span>
            <operand-picker :value="block.listOperand" :vars="vars" :functions="functions" :selected-vars="selectedVars" :allowed-kinds="readKinds" placeholder="选择列表操作数" size="mini" @input="value => setOperand(block, 'listOperand', value)" />
          </div>
          <div class="nested-body">
            <assignment-row v-for="(action, actionIndex) in block.actions" :key="actionIndex" :action="action" />
            <el-button size="mini" icon="el-icon-plus" class="wide-button" @click="addAssignment(block.actions)">添加赋值</el-button>
          </div>
        </template>

        <template v-else-if="block.type === 'ternary'">
          <assignment-target :block="block" />
          <div class="condition-row">
            <operand-picker :value="block.leftOperand" :vars="vars" :functions="functions" :list-options="listOptions" :selected-vars="selectedVars" :allowed-kinds="readKinds" placeholder="选择左操作数" size="mini" @input="value => setConditionLeftOperand(block, value)" />
            <el-select v-model="block.operator" size="mini" class="operator" @change="onConditionOperatorChange(block)">
              <el-option v-for="option in operatorOptions(block)" :key="option.value" :label="option.label" :value="option.value" />
            </el-select>
            <operand-picker v-if="operatorRequiresValue(block)" :value="block.rightOperand" :vars="vars" :functions="functions" :list-options="listOptions" :selected-vars="selectedVars" :allowed-kinds="rightAllowedKinds(block)" :context="rightContext(block)" :expected-type="rightExpectedType(block)" placeholder="选择右操作数" size="mini" @input="value => setOperand(block, 'rightOperand', value)" />
          </div>
          <div class="inline-row">
            <span class="mini-label success">真</span>
            <operand-picker :value="block.trueOperand" :vars="vars" :functions="functions" :selected-vars="selectedVars" :allowed-kinds="valueKinds" placeholder="选择真值" size="mini" @input="value => setOperand(block, 'trueOperand', value)" />
            <span class="mini-label danger-text">假</span>
            <operand-picker :value="block.falseOperand" :vars="vars" :functions="functions" :selected-vars="selectedVars" :allowed-kinds="valueKinds" placeholder="选择假值" size="mini" @input="value => setOperand(block, 'falseOperand', value)" />
          </div>
        </template>

        <template v-else-if="block.type === 'in-check'">
          <assignment-target :block="block" />
          <div class="inline-row">
            <span class="mini-label">检测</span>
            <operand-picker :value="block.checkOperand" :vars="vars" :functions="functions" :selected-vars="selectedVars" :allowed-kinds="readKinds" placeholder="选择检测操作数" size="mini" @input="value => setOperand(block, 'checkOperand', value)" />
          </div>
          <div v-for="(operand, operandIndex) in block.inOperands" :key="operandIndex" class="inline-row">
            <span class="mini-label">候选 {{ operandIndex + 1 }}</span>
            <operand-picker :value="operand" :vars="vars" :functions="functions" :selected-vars="selectedVars" :allowed-kinds="valueKinds" placeholder="选择候选值" size="mini" @input="value => setArrayOperand(block.inOperands, operandIndex, value)" />
            <el-button type="text" size="mini" icon="el-icon-delete" class="danger" @click="removeArrayOperand(block.inOperands, operandIndex)" />
          </div>
          <el-button size="mini" icon="el-icon-plus" class="wide-button" @click="addArrayOperand(block.inOperands)">添加候选值</el-button>
          <div class="inline-row result-row">
            <span class="mini-label success">匹配</span>
            <operand-picker :value="block.trueOperand" :vars="vars" :functions="functions" :selected-vars="selectedVars" :allowed-kinds="valueKinds" placeholder="选择匹配值" size="mini" @input="value => setOperand(block, 'trueOperand', value)" />
            <span class="mini-label danger-text">不匹配</span>
            <operand-picker :value="block.falseOperand" :vars="vars" :functions="functions" :selected-vars="selectedVars" :allowed-kinds="valueKinds" placeholder="选择不匹配值" size="mini" @input="value => setOperand(block, 'falseOperand', value)" />
          </div>
        </template>

        <template v-else-if="block.type === 'template-str'">
          <assignment-target :block="block" />
          <div v-for="(part, partIndex) in block.parts" :key="partIndex" class="inline-row">
            <el-select v-model="part.type" size="mini" class="part-type" @change="resetTemplatePart(part)">
              <el-option label="文本" value="text" /><el-option label="表达式" value="expr" />
            </el-select>
            <operand-picker :value="part.operand" :vars="vars" :functions="functions" :selected-vars="selectedVars" :allowed-kinds="part.type === 'text' ? literalKinds : readKinds" :expected-type="part.type === 'text' ? 'STRING' : ''" :placeholder="part.type === 'text' ? '输入文本' : '选择表达式'" size="mini" @input="value => setOperand(part, 'operand', value)" />
            <el-button v-if="block.parts.length > 1" type="text" size="mini" icon="el-icon-delete" class="danger" @click="removeTemplatePart(block, partIndex)" />
          </div>
          <el-button size="mini" icon="el-icon-plus" class="wide-button" @click="addTemplatePart(block)">添加片段</el-button>
        </template>
      </div>
    </div>

    <el-dropdown trigger="click" class="add-block" @command="addBlock">
      <el-button size="mini" icon="el-icon-plus" class="wide-button">添加动作块</el-button>
      <el-dropdown-menu slot="dropdown">
        <el-dropdown-item v-for="type in blockTypes" :key="type.type" :command="type.type">
          <i :class="type.icon" :style="{ color: type.color }" /> {{ type.label }}
        </el-dropdown-item>
      </el-dropdown-menu>
    </el-dropdown>
  </div>
</template>

<script>
import OperandPicker from '@/components/common/OperandPicker.vue'
import RuleExecutionSelector from '@/components/common/RuleExecutionSelector.vue'
import AssignmentRow from './ActionAssignmentRow.vue'
import AssignmentTarget from './ActionAssignmentTarget.vue'
import { actionDataToBlocks, BLOCK_TYPES, blocksToActionData, generateScript, newBlock } from '@/utils/actionDataCodegen'
import { getExpressionContext } from '@/constants/expressionContexts'
import {
  conditionOperatorAllowsVarValue,
  conditionOperatorRequiresValue,
  findConditionOperator,
  getConditionOperatorOptions,
  normalizeConditionOperator
} from '@/constants/conditionOperators'
import { inferOperandType } from '@/utils/operand'
import { isRuleOutputMappingEnabled } from '@/utils/ruleCallConfig'

export default {
  name: 'ActionBlockEditor',
  components: { AssignmentRow, AssignmentTarget, OperandPicker, RuleExecutionSelector },
  provide() { return { actionEditor: this } },
  props: {
    actionData: { type: Array, default: () => [] },
    vars: { type: Array, default: () => [] },
    selectedVars: { type: Array, default: () => [] },
    functions: { type: Array, default: () => [] },
    listOptions: { type: Array, default: () => [] },
    rules: { type: Array, default: () => [] },
    currentRuleId: { type: [String, Number], default: null },
    currentRuleCode: { type: String, default: '' },
    validateRuleCallCycle: { type: Function, default: null }
  },
  data() {
    return {
      blocks: [],
      blockTypes: BLOCK_TYPES,
      literalKinds: ['LITERAL'],
      readKinds: getExpressionContext('READ_EXPRESSION').allowedKinds,
      valueKinds: getExpressionContext('READ_EXPRESSION').allowedKinds,
      writeKinds: getExpressionContext('WRITE_TARGET').allowedKinds
    }
  },
  computed: {
    scriptPreview() { return generateScript(blocksToActionData(this.blocks)) }
  },
  watch: {
    actionData: {
      handler(value) { this.blocks = actionDataToBlocks(value || []) },
      immediate: true,
      deep: false
    }
  },
  methods: {
    sync() { this.$emit('update', blocksToActionData(this.blocks)) },
    setOperand(holder, field, value) {
      this.$set(holder, field, value || null)
      this.sync()
    },
    setArrayOperand(values, index, value) {
      this.$set(values, index, value || null)
      this.sync()
    },
    addArrayOperand(values) { values.push(null); this.sync() },
    removeArrayOperand(values, index) { values.splice(index, 1); this.sync() },
    operandType(operand) { return inferOperandType(operand) || 'STRING' },
    operatorOptions(holder) { return getConditionOperatorOptions(this.operandType(holder && holder.leftOperand)) },
    operatorRequiresValue(holder) { return conditionOperatorRequiresValue(holder && holder.operator, this.operandType(holder && holder.leftOperand)) },
    rightContext(holder) {
      const option = findConditionOperator(holder && holder.operator, this.operandType(holder && holder.leftOperand))
      return (option && option.rightContext) || 'READ_EXPRESSION'
    },
    rightExpectedType(holder) {
      const option = findConditionOperator(holder && holder.operator, this.operandType(holder && holder.leftOperand))
      return (option && option.rightValueType) || this.operandType(holder && holder.leftOperand)
    },
    rightAllowedKinds(holder) {
      const context = this.rightContext(holder)
      if (context === 'LIST_QUERY_CONFIG') return getExpressionContext(context).allowedKinds
      return conditionOperatorAllowsVarValue(holder && holder.operator, this.operandType(holder && holder.leftOperand))
        ? getExpressionContext(context).allowedKinds
        : ['LITERAL']
    },
    setConditionLeftOperand(holder, value) {
      this.$set(holder, 'leftOperand', value || null)
      this.$set(holder, 'operator', normalizeConditionOperator(holder.operator || '==', this.operandType(value)))
      this.onConditionOperatorChange(holder)
    },
    onConditionOperatorChange(holder) {
      const operator = normalizeConditionOperator(holder.operator || '==', this.operandType(holder.leftOperand))
      this.$set(holder, 'operator', operator)
      if (!conditionOperatorRequiresValue(operator, this.operandType(holder.leftOperand))) {
        this.$set(holder, 'rightOperand', null)
      } else if (holder.rightOperand && !this.rightAllowedKinds(holder).includes(holder.rightOperand.kind)) {
        this.$set(holder, 'rightOperand', null)
      }
      this.sync()
    },
    addBlock(type) { this.blocks.push(newBlock(type)); this.sync() },
    removeBlock(index) { this.blocks.splice(index, 1); this.sync() },
    moveBlock(index, direction) {
      const target = index + direction
      if (target < 0 || target >= this.blocks.length) return
      const current = this.blocks[index]
      this.$set(this.blocks, index, this.blocks[target])
      this.$set(this.blocks, target, current)
      this.sync()
    },
    newAssignment() { return newBlock('assign') },
    addAssignment(actions) { actions.push(this.newAssignment()); this.sync() },
    removeAssignment(actions, index) { actions.splice(index, 1); this.sync() },
    hasElse(block) { return (block.branches || []).some(branch => branch.type === 'else') },
    addBranch(block, type) {
      block.branches.push({ type, leftOperand: null, operator: '==', rightOperand: null, actions: [this.newAssignment()] })
      this.sync()
    },
    removeBranch(block, index) {
      block.branches.splice(index, 1)
      if (block.branches.length && block.branches[0].type !== 'if') block.branches[0].type = 'if'
      this.sync()
    },
    addCase(block) { block.cases.push({ valueOperand: null, actions: [this.newAssignment()] }); this.sync() },
    removeCase(block, index) { block.cases.splice(index, 1); this.sync() },
    addTemplatePart(block) { block.parts.push({ type: 'text', operand: null }); this.sync() },
    removeTemplatePart(block, index) { block.parts.splice(index, 1); this.sync() },
    resetTemplatePart(part) { this.$set(part, 'operand', null); this.sync() },
    onFunctionSelect(block, functionCode) {
      const fn = this.functions.find(item => (item.funcCode || item.functionCode) === functionCode)
      this.$set(block, 'functionId', fn && fn.id != null ? fn.id : null)
      if (fn && fn.paramsJson) {
        try {
          const params = JSON.parse(fn.paramsJson)
          this.$set(block, 'args', params.map(() => null))
        } catch (e) { /* 保留用户当前参数 */ }
      }
      this.sync()
    },
    functionLabel(fn) {
      const code = fn.funcCode || fn.functionCode || ''
      const name = fn.funcName || fn.functionName || code
      return code && code !== name ? name + ' (' + code + ')' : name
    },
    rememberRuleCallSnapshot(block, visible) {
      if (!visible || !block) return
      Object.defineProperty(block, '__ruleCallSnapshot', {
        value: {
          ruleId: block.ruleId == null ? null : block.ruleId,
          ruleCode: block.ruleCode || '',
          ruleName: block.ruleName || '',
          modelType: block.modelType || '',
          enableOutputMapping: isRuleOutputMappingEnabled(block),
          outputField: block.outputField || '',
          targetOperand: block.targetOperand ? JSON.parse(JSON.stringify(block.targetOperand)) : null
        },
        enumerable: false, configurable: true, writable: true
      })
    },
    restoreRuleCallSnapshot(block) {
      const snapshot = block.__ruleCallSnapshot || { ruleId: null, ruleCode: '', ruleName: '', modelType: '', outputField: '', targetOperand: null }
      Object.keys(snapshot).forEach(key => this.$set(block, key, snapshot[key]))
      this.clearRuleCallSnapshot(block)
    },
    clearRuleCallSnapshot(block) {
      if (block && Object.prototype.hasOwnProperty.call(block, '__ruleCallSnapshot')) delete block.__ruleCallSnapshot
    },
    async validateSelectedRuleCall(block) {
      if (typeof this.validateRuleCallCycle !== 'function') return true
      try {
        const result = await this.validateRuleCallCycle(block)
        return result === false ? '规则调用存在环路' : (result || true)
      } catch (error) {
        return (error && error.message) || '规则调用环校验失败'
      }
    },
    hasRuleOutputMapping(block) {
      return isRuleOutputMappingEnabled(block)
    },
    toggleRuleOutputMapping(block, enabled) {
      this.$set(block, 'enableOutputMapping', !!enabled)
      this.sync()
    },
    clearRuleOutputMapping(block) {
      this.$set(block, 'outputField', '')
      this.$set(block, 'targetOperand', null)
      this.sync()
    },
    async onRuleSelect(block, selectedRule) {
      const rule = selectedRule && typeof selectedRule === 'object'
        ? selectedRule
        : (this.rules || []).find(item => String(item.id) === String(selectedRule) || String(item.ruleCode) === String(selectedRule))
      if (!rule) {
        this.$set(block, 'ruleId', null); this.$set(block, 'ruleCode', ''); this.$set(block, 'ruleName', ''); this.$set(block, 'modelType', '')
        this.$set(block, 'outputField', ''); this.$set(block, 'targetOperand', null)
        this.clearRuleCallSnapshot(block); this.sync(); return
      }
      if (this.isCurrentRule(rule)) {
        if (this.$message) this.$message.warning('不能调用当前规则自身，会形成规则调用环')
        this.restoreRuleCallSnapshot(block); this.sync(); return
      }
      this.$set(block, 'ruleId', rule.id || null)
      this.$set(block, 'ruleCode', rule.ruleCode || '')
      this.$set(block, 'ruleName', rule.ruleName || '')
      this.$set(block, 'modelType', rule.modelType || '')
      if (!this.ruleOutputFields(block).some(field => (field.scriptName || field.fieldName) === block.outputField)) {
        this.$set(block, 'outputField', '')
        this.$set(block, 'targetOperand', null)
      }
      this.sync()
      const validation = await this.validateSelectedRuleCall(block)
      if (validation !== true) {
        if (this.$message) this.$message.warning(validation)
        this.restoreRuleCallSnapshot(block); this.sync(); return
      }
      this.clearRuleCallSnapshot(block)
    },
    isCurrentRule(rule) {
      if (this.currentRuleId != null && String(rule.id) === String(this.currentRuleId)) return true
      return !!this.currentRuleCode && String(rule.ruleCode) === String(this.currentRuleCode)
    },
    ruleLabel(rule) {
      const name = rule.ruleName || rule.ruleCode || ''
      const code = rule.ruleCode && rule.ruleCode !== name ? ' (' + rule.ruleCode + ')' : ''
      return name + code + (rule.modelType ? ' - ' + rule.modelType : '')
    },
    ruleOutputFields(block) {
      const rule = (this.rules || []).find(item =>
        (block.ruleId != null && String(item.id) === String(block.ruleId)) ||
        (block.ruleId == null && String(item.ruleCode) === String(block.ruleCode)))
      return rule ? (rule.outputFields || rule.outputFieldsJson || []) : []
    },
    fieldLabel(field) {
      const name = field.fieldLabel || field.fieldName || field.scriptName || ''
      const code = field.scriptName || field.fieldName || ''
      return code && code !== name ? name + ' (' + code + ')' : name
    },
    typeLabel(type) { const item = BLOCK_TYPES.find(value => value.type === type); return item ? item.label : type },
    typeColor(type) { const item = BLOCK_TYPES.find(value => value.type === type); return item ? item.color : '#999' }
  }
}
</script>

<style lang="scss" scoped>
.block-editor { padding: 0; }
.block-item { border: 1px solid #e0e0e0; border-radius: 4px; margin-bottom: 8px; background: #fafafa; overflow: hidden; }
.block-header, .nested-head, .inline-row, .condition-row, .button-row, .rounding-row { display: flex; align-items: center; gap: 6px; }
.block-header { justify-content: space-between; padding: 4px 8px; background: #f0f0f0; border-bottom: 1px solid #e0e0e0; }
.block-type-tag { font-size: 11px; font-weight: 600; color: #fff; padding: 2px 8px; border-radius: 3px; }
.block-body { padding: 8px; }
.inline-row, .condition-row { margin-bottom: 6px; }
.inline-row ::v-deep .var-picker-wrap, .condition-row ::v-deep .var-picker-wrap { flex: 1 1 0; min-width: 0; width: auto !important; }
.nested-card { border: 1px solid #dcdfe6; border-radius: 4px; margin-bottom: 8px; background: #fff; overflow: hidden; }
.nested-head { min-height: 30px; padding: 3px 8px; background: #f5f7fa; font-size: 11px; font-weight: 600; }
.nested-head ::v-deep .var-picker-wrap { flex: 1; margin-left: 8px; }
.nested-body, .condition-row { padding: 6px 8px; }
.default-card { background: #fcfcfc; }
.assignment-with-delete { display: flex; align-items: center; gap: 4px; }
.assignment-with-delete > :first-child { flex: 1; }
.eq { color: #909399; font-weight: 600; }
.mini-label { color: #606266; font-size: 12px; white-space: nowrap; }
.operator { width: 148px; flex: 0 0 148px; }
.grow { flex: 1; }
.wide-button { width: 100%; }
.button-row { margin-top: 6px; }
.add-block { width: 100%; margin-top: 6px; }
.danger { color: #f56c6c !important; }
.danger-text { color: #f56c6c; }
.success { color: #67c23a; }
.rounding-row { margin-top: 6px; }
.rounding-row ::v-deep .el-input-number { width: 100px; }
.rounding-mode { width: 120px; }
.item-var { width: 90px; flex: 0 0 90px; }
.part-type { width: 85px; flex: 0 0 85px; }
.result-row { margin-top: 8px; }
.shared-context-hint { margin-top: 8px; padding: 7px 9px; border-radius: 4px; color: #606266; background: #f0f7ff; font-size: 12px; line-height: 1.5; }
.shared-context-hint i { color: #409eff; }
.mapping-toggle-row { margin-top: 8px; }
.rule-output-mapping { margin-top: 8px; padding: 8px; border: 1px dashed #dcdfe6; border-radius: 4px; background: #fff; }
.mapping-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 4px; }
</style>
