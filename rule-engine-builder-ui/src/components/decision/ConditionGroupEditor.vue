<template>
  <div class="cg" :class="{ 'cg--nested': depth > 0 }">
    <div class="cg-head">
      <span v-if="depth === 0" class="cg-title">条件</span>
      <el-button-group class="cg-op">
        <el-button size="mini" :type="group.op === 'AND' ? 'primary' : 'default'" @click="setGroupOp('AND')">且</el-button>
        <el-button size="mini" :type="group.op === 'OR' ? 'primary' : 'default'" @click="setGroupOp('OR')">或</el-button>
      </el-button-group>
      <el-button v-if="depth > 0" type="text" size="mini" class="cg-remove-group" @click="$emit('remove-group')">删除组</el-button>
    </div>

    <div class="cg-stem">
      <div class="cg-stem-line" aria-hidden="true" />
      <div class="cg-children">
        <div v-for="(child, idx) in group.children" :key="'n-' + depth + '-' + idx" class="cg-row">
          <condition-group-editor
            v-if="child.type === 'group'"
            :group="child"
            :vars="vars"
            :functions="functions"
            :list-options="listOptions"
            :depth="depth + 1"
            :get-var-options-fn="getVarOptionsFn"
            :selected-vars="selectedVars"
            @remove-group="removeChild(idx)"
          />
          <div v-else class="cg-leaf">
            <div class="cg-field cg-field--operand">
              <operand-picker
                :value="child.leftOperand"
                :vars="vars"
                :functions="functions"
                :list-options="listOptions"
                :allowed-kinds="leftAllowedKinds"
                context="READ_EXPRESSION"
                :selected-vars="selectedVars"
                placeholder="选择左操作数..."
                size="mini"
                width="100%"
                @input="operand => onLeftOperandChange(child, operand)"
              />
            </div>
            <div class="cg-field cg-field--op">
              <el-select v-model="child.operator" size="mini" class="cg-sel-full" @change="onOpChange(child)">
                <el-option-group v-for="groupItem in operatorGroups(child)" :key="groupItem.label" :label="groupItem.label">
                  <el-option v-for="option in groupItem.options" :key="option.value" :label="option.label" :value="option.value" />
                </el-option-group>
              </el-select>
            </div>
            <div v-if="operatorRequiresValue(child)" class="cg-field cg-field--operand">
              <operand-picker
                :value="child.rightOperand"
                :vars="vars"
                :functions="functions"
                :list-options="listOptions"
                :allowed-kinds="rightAllowedKinds(child)"
                :context="rightContext(child)"
                :expected-type="rightExpectedType(child)"
                :selected-vars="selectedVars"
                placeholder="选择右操作数..."
                size="mini"
                width="100%"
                @input="operand => onRightOperandChange(child, operand)"
              />
            </div>
            <span v-else class="cg-field cg-field--any">无需右值</span>
            <div class="cg-field cg-field--actions">
              <el-button type="text" size="mini" class="cg-del" @click="removeChild(idx)">删除</el-button>
            </div>
          </div>
        </div>

        <div class="cg-footer-btns">
          <el-button size="mini" round @click="addLeaf">加条件</el-button>
          <el-button size="mini" round @click="addSubGroup">加条件组</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import OperandPicker from '@/components/common/OperandPicker.vue'
import { createEmptyGroup, createEmptyLeaf } from '@/utils/decisionConditionTree'
import {
  conditionOperatorAllowsVarValue,
  conditionOperatorRequiresValue,
  findConditionOperator,
  getConditionOperatorGroups,
  getConditionOperatorOptions,
  normalizeConditionOperator
} from '@/constants/conditionOperators'
import { getExpressionContext } from '@/constants/expressionContexts'
import { inferOperandType } from '@/utils/operand'

const READ_EXPRESSION_KINDS = getExpressionContext('READ_EXPRESSION').allowedKinds

export default {
  name: 'ConditionGroupEditor',
  components: { OperandPicker },
  props: {
    group: { type: Object, required: true },
    vars: { type: Array, default: () => [] },
    functions: { type: Array, default: () => [] },
    listOptions: { type: Array, default: () => [] },
    selectedVars: { type: Array, default: () => [] },
    depth: { type: Number, default: 0 },
    getVarOptionsFn: { type: Function, default: null }
  },
  computed: {
    leftAllowedKinds() {
      return READ_EXPRESSION_KINDS
    }
  },
  methods: {
    setGroupOp(op) {
      this.$set(this.group, 'op', op)
    },
    leftOperandType(leaf) {
      return inferOperandType(leaf && leaf.leftOperand) || 'STRING'
    },
    operatorOptions(leaf) {
      return getConditionOperatorOptions(this.leftOperandType(leaf), leaf && leaf.leftOperand)
    },
    operatorGroups(leaf) {
      return getConditionOperatorGroups(this.leftOperandType(leaf), leaf && leaf.leftOperand)
    },
    operatorRequiresValue(leaf) {
      return conditionOperatorRequiresValue(leaf && leaf.operator, this.leftOperandType(leaf), leaf && leaf.leftOperand)
    },
    rightAllowedKinds(leaf) {
      const context = this.rightContext(leaf)
      if (context === 'LIST_QUERY_CONFIG') return getExpressionContext(context).allowedKinds
      return conditionOperatorAllowsVarValue(leaf && leaf.operator, this.leftOperandType(leaf), leaf && leaf.leftOperand)
        ? getExpressionContext(context).allowedKinds
        : ['LITERAL']
    },
    rightContext(leaf) {
      const option = findConditionOperator(leaf && leaf.operator, this.leftOperandType(leaf), leaf && leaf.leftOperand)
      return (option && option.rightContext) || 'READ_EXPRESSION'
    },
    rightExpectedType(leaf) {
      const option = findConditionOperator(leaf && leaf.operator, this.leftOperandType(leaf), leaf && leaf.leftOperand)
      return (option && option.rightValueType) || this.leftOperandType(leaf)
    },
    onOpChange(leaf) {
      const type = this.leftOperandType(leaf)
      const operator = normalizeConditionOperator(leaf.operator || '==', type, leaf.leftOperand)
      if (operator !== leaf.operator) this.$set(leaf, 'operator', operator)
      if (!conditionOperatorRequiresValue(operator, type, leaf.leftOperand)) {
        this.$set(leaf, 'rightOperand', null)
        return
      }
      if (leaf.rightOperand && !this.rightAllowedKinds(leaf).includes(leaf.rightOperand.kind)) {
        this.$set(leaf, 'rightOperand', null)
      }
    },
    onLeftOperandChange(leaf, operand) {
      this.$set(leaf, 'leftOperand', operand || null)
      const type = this.leftOperandType(leaf)
      this.$set(leaf, 'operator', normalizeConditionOperator(leaf.operator || '==', type, operand))
      this.onOpChange(leaf)
    },
    onRightOperandChange(leaf, operand) {
      this.$set(leaf, 'rightOperand', operand || null)
    },
    addLeaf() {
      if (!Array.isArray(this.group.children)) this.$set(this.group, 'children', [])
      this.group.children.push(createEmptyLeaf())
    },
    addSubGroup() {
      if (!Array.isArray(this.group.children)) this.$set(this.group, 'children', [])
      const group = createEmptyGroup('AND')
      group.children.push(createEmptyLeaf())
      this.group.children.push(group)
    },
    removeChild(idx) {
      this.group.children.splice(idx, 1)
    }
  }
}
</script>

<style lang="scss" scoped>
.cg { font-size: 13px; width: 100%; max-width: 100%; box-sizing: border-box; }
.cg--nested { padding-left: 8px; border-left: 2px solid #e8e8e8; }
.cg-head { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; flex-wrap: wrap; }
.cg-title { font-weight: 600; color: #333; margin-right: 4px; }
.cg-remove-group { color: #f56c6c !important; margin-left: auto; }
.cg-stem { display: flex; align-items: stretch; width: 100%; min-width: 0; }
.cg-stem-line { width: 2px; flex-shrink: 0; background: #e0e0e0; border-radius: 1px; margin-right: 12px; min-height: 24px; }
.cg-children { flex: 1 1 0; min-width: 0; max-width: 100%; box-sizing: border-box; overflow-x: hidden; }
.cg-row { margin-bottom: 10px; width: 100%; min-width: 0; }
.cg-leaf {
  display: grid;
  grid-template-columns: minmax(160px, 2fr) 108px minmax(160px, 2fr) auto;
  align-items: center;
  gap: 8px;
  width: 100%;
  min-width: 0;
}
.cg-field { min-width: 0; display: flex; align-items: center; }
.cg-field--op { width: 108px; }
.cg-field--any { color: #999; font-size: 12px; }
.cg-field--actions { justify-content: flex-end; }
.cg-sel-full { width: 100%; }
.cg-field ::v-deep .operand-picker,
.cg-field ::v-deep .var-picker-wrap { width: 100% !important; max-width: 100%; }
.cg-del { color: #f56c6c !important; flex-shrink: 0; }
.cg-footer-btns { display: flex; gap: 8px; margin-top: 4px; }
@media (max-width: 768px) {
  .cg-leaf { grid-template-columns: 1fr; }
  .cg-field--op { width: 100%; }
  .cg-field--actions { justify-content: flex-end; }
}
</style>
