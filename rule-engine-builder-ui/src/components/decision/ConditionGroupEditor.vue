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
            :depth="depth + 1"
            :get-var-options-fn="getVarOptionsFn"
            :selected-vars="selectedVars"
            :allow-custom-var="allowCustomVar"
            @remove-group="removeChild(idx)"
          />
          <div v-else class="cg-leaf">
            <div class="cg-field cg-field--var-left">
              <var-picker
                :vars="vars"
                :value="child.varCode"
                placeholder="选择字段..."
                size="mini"
                width="100%"
                :allow-custom="allowCustomVar"
                :selected-vars="selectedVars"
                @select="v => onLeafLeftSelect(child, v)"
              />
            </div>
            <div class="cg-field cg-field--op">
              <el-select v-model="child.operator" size="mini" class="cg-sel-full" @change="onOpChange(child)">
                <el-option v-for="o in operatorOptions(child)" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </div>
            <div v-if="operatorRequiresValue(child)" class="cg-field cg-field--kind">
              <el-select v-model="child.valueKind" size="mini" class="cg-sel-full" @change="onValueKindChange(child)">
                <el-option label="常量" value="CONST" />
                <el-option label="变量" value="VAR" :disabled="!operatorAllowsVarValue(child)" />
              </el-select>
            </div>
            <template v-if="operatorRequiresValue(child)">
              <div v-if="child.valueKind === 'VAR'" class="cg-field cg-field--var-right">
                <var-picker
                  :vars="vars"
                  :value="child.value"
                  placeholder="右侧变量"
                  size="mini"
                  width="100%"
                  :allow-custom="false"
                  :selected-vars="selectedVars"
                  @select="v => onLeafRightSelect(child, v)"
                />
              </div>
              <div v-else class="cg-field cg-field--value">
                <el-select
                  v-if="child.varType === 'ENUM' && enumOpts(child).length && !isMultiValueOperator(child)"
                  v-model="child.value"
                  size="mini"
                  class="cg-sel-full"
                  clearable
                >
                  <el-option v-for="opt in enumOpts(child)" :key="opt" :label="opt" :value="opt" />
                </el-select>
                <el-select v-else-if="child.varType === 'BOOLEAN'" v-model="child.value" size="mini" class="cg-sel-full">
                  <el-option label="true" value="true" />
                  <el-option label="false" value="false" />
                </el-select>
                <el-input v-else v-model="child.value" size="mini" class="cg-input-full" :placeholder="valuePlaceholder(child)" />
              </div>
            </template>
            <span v-else class="cg-field cg-field--any">无需输入值</span>
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
import VarPicker from '@/components/common/VarPicker.vue'
import { createEmptyGroup, createEmptyLeaf, normalizeConditionLeafOperator } from '@/utils/decisionConditionTree'
import {
  conditionOperatorAllowsVarValue,
  conditionOperatorRequiresValue,
  conditionValuePlaceholder,
  getConditionOperatorOptions
} from '@/constants/conditionOperators'

export default {
  name: 'ConditionGroupEditor',
  components: { VarPicker },
  props: {
    group: { type: Object, required: true },
    vars: { type: Array, default: () => [] },
    selectedVars: { type: Array, default: () => [] },
    depth: { type: Number, default: 0 },
    getVarOptionsFn: { type: Function, default: null },
    allowCustomVar: { type: Boolean, default: false }
  },
  methods: {
    setGroupOp(op) {
      this.$set(this.group, 'op', op)
    },

    operatorOptions(leaf) {
      return getConditionOperatorOptions(leaf && leaf.varType)
    },

    operatorRequiresValue(leaf) {
      return conditionOperatorRequiresValue(leaf && leaf.operator, leaf && leaf.varType)
    },

    operatorAllowsVarValue(leaf) {
      return conditionOperatorAllowsVarValue(leaf && leaf.operator, leaf && leaf.varType)
    },

    valuePlaceholder(leaf) {
      return conditionValuePlaceholder(leaf && leaf.operator, leaf && leaf.varType)
    },

    isMultiValueOperator(leaf) {
      return ['in', 'not_in', 'between', 'not_between', 'contains_any', 'contains_all'].includes(leaf && leaf.operator)
    },

    enumOpts(leaf) {
      if (!leaf.enumOptions) return []
      return leaf.enumOptions.split(',').map(s => s.trim()).filter(Boolean)
    },

    onOpChange(leaf) {
      normalizeConditionLeafOperator(leaf)
      if (!this.operatorRequiresValue(leaf)) {
        this.$set(leaf, 'valueKind', 'CONST')
        this.$set(leaf, 'value', '')
        this.clearRightVarRef(leaf)
        return
      }
      if (!this.operatorAllowsVarValue(leaf) && leaf.valueKind === 'VAR') {
        this.$set(leaf, 'valueKind', 'CONST')
        this.$set(leaf, 'value', '')
        this.clearRightVarRef(leaf)
      }
    },

    onValueKindChange(leaf) {
      this.$set(leaf, 'value', '')
      this.clearRightVarRef(leaf)
    },

    onLeafLeftSelect(leaf, variable) {
      if (!variable) {
        this.$set(leaf, 'varCode', '')
        this.$set(leaf, 'varLabel', '')
        this.$set(leaf, 'varType', 'STRING')
        this.$set(leaf, 'enumOptions', '')
        this.$set(leaf, '_varId', undefined)
        this.$set(leaf, '_refType', undefined)
        normalizeConditionLeafOperator(leaf)
        return
      }
      const varLabel = variable.varLabel || variable.varCode
      const _varId = this.refIdOf(variable)
      this.$set(leaf, 'varCode', variable.varCode)
      this.$set(leaf, 'varLabel', varLabel)
      this.$set(leaf, 'varType', variable.varType || 'STRING')
      this.$set(leaf, '_varId', _varId)
      this.$set(leaf, '_refType', this.refTypeOf(variable) || undefined)
      if (variable.varType === 'ENUM' && this.getVarOptionsFn) {
        const opts = this.getVarOptionsFn(variable.varCode) || []
        this.$set(leaf, 'enumOptions', opts.map(o => o.value || o.optionValue).filter(Boolean).join(','))
      } else {
        this.$set(leaf, 'enumOptions', '')
      }
      normalizeConditionLeafOperator(leaf)
      this.onOpChange(leaf)
    },

    onLeafRightSelect(leaf, variable) {
      if (!variable) {
        this.$set(leaf, 'value', '')
        this.clearRightVarRef(leaf)
        return
      }
      const varLabel = variable.varLabel || variable.varCode
      const _varId = this.refIdOf(variable)
      this.$set(leaf, 'value', variable.varCode)
      this.$set(leaf, 'rightVarType', variable.varType || 'STRING')
      this.$set(leaf, 'rightVarLabel', varLabel)
      this.$set(leaf, '_rightVarId', _varId)
      this.$set(leaf, '_rightRefType', this.refTypeOf(variable) || undefined)
    },

    clearRightVarRef(leaf) {
      this.$set(leaf, 'rightVarType', '')
      this.$set(leaf, 'rightVarLabel', '')
      this.$set(leaf, '_rightVarId', undefined)
      this.$set(leaf, '_rightRefType', undefined)
    },

    refIdOf(variable) {
      if (!variable) return null
      if (variable._varId != null) return variable._varId
      if (variable.id != null) return variable.id
      if (variable.varObj && variable.varObj.id != null) return variable.varObj.id
      if (variable._ref && variable._ref.id != null) return variable._ref.id
      return null
    },

    refTypeOf(variable) {
      if (!variable) return ''
      return variable._refType || variable.refType || (variable.varObj && variable.varObj.refType) || (variable._ref && variable._ref.refType) || ''
    },

    addLeaf() {
      if (!Array.isArray(this.group.children)) this.$set(this.group, 'children', [])
      this.group.children.push(createEmptyLeaf())
    },

    addSubGroup() {
      if (!Array.isArray(this.group.children)) this.$set(this.group, 'children', [])
      const g = createEmptyGroup('AND')
      g.children.push(createEmptyLeaf())
      this.group.children.push(g)
    },

    removeChild(idx) {
      this.group.children.splice(idx, 1)
    }
  }
}
</script>

<style lang="scss" scoped>
.cg {
  font-size: 13px;
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
}
.cg--nested {
  margin-left: 0;
  padding-left: 8px;
  border-left: 2px solid #e8e8e8;
}
.cg-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}
.cg-title {
  font-weight: 600;
  color: #333;
  margin-right: 4px;
}
.cg-op ::v-deep .el-button--mini {
  border-radius: 4px;
}
.cg-remove-group {
  color: #f56c6c !important;
  margin-left: auto;
}
.cg-stem {
  display: flex;
  gap: 0;
  align-items: stretch;
  width: 100%;
  min-width: 0;
}
.cg-stem-line {
  width: 2px;
  flex-shrink: 0;
  background: #e0e0e0;
  border-radius: 1px;
  margin-right: 12px;
  min-height: 24px;
}
.cg-children {
  flex: 1 1 0;
  min-width: 0;
  max-width: 100%;
  box-sizing: border-box;
  overflow-x: hidden;
  overflow-y: visible;
}
.cg-row {
  margin-bottom: 10px;
  width: 100%;
  max-width: 100%;
  min-width: 0;
  box-sizing: border-box;
}
.cg-row > .cg {
  width: 100%;
  max-width: 100%;
}
.cg-leaf {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(96px, 122px) minmax(76px, 92px) minmax(0, 2fr) auto;
  align-items: center;
  gap: 8px;
  width: 100%;
  max-width: 100%;
  min-width: 0;
  box-sizing: border-box;
}
.cg-field {
  min-width: 0;
  display: flex;
  align-items: center;
}
.cg-field--op {
  width: 122px;
}
.cg-field--kind {
  width: 92px;
}
.cg-field--any {
  min-width: 0;
  color: #999;
  font-size: 12px;
}
.cg-field--actions {
  justify-content: flex-end;
}
.cg-sel-full,
.cg-input-full {
  width: 100%;
}
.cg-field ::v-deep .var-picker-wrap {
  width: 100% !important;
  max-width: 100%;
}
.cg-field ::v-deep .el-select {
  width: 100%;
  display: block;
}
.cg-field ::v-deep .el-select > .el-input,
.cg-field ::v-deep .el-input {
  width: 100%;
}
.cg-del {
  color: #f56c6c !important;
  flex-shrink: 0;
}
.cg-footer-btns {
  display: flex;
  gap: 8px;
  margin-top: 4px;
}
@media (max-width: 768px) {
  .cg--nested {
    margin-left: 0;
    padding-left: 6px;
  }
  .cg-stem-line {
    margin-right: 6px;
  }
  .cg-field--var-left,
  .cg-field--value,
  .cg-field--var-right,
  .cg-field--actions {
    grid-column: 1 / -1;
    width: 100%;
  }
  .cg-field--actions {
    justify-content: flex-end;
  }
}
</style>
