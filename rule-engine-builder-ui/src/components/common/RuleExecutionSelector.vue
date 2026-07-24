<template>
  <div class="rule-execution-selector">
    <el-select
      :model-value="selectedId"
      filterable
      clearable
      size="small"
      class="rule-select"
      placeholder="选择要执行的规则"
      @visible-change="$emit('visible-change', $event)"
      @change="onChange"
    >
      <el-option-group
        v-for="group in ruleGroups"
        :key="group.key"
        :label="group.label"
        class="rule-scope-group"
      >
        <el-option
          v-for="rule in group.rules"
          :key="rule.id"
          :label="ruleLabel(rule)"
          :value="rule.id"
          :disabled="isRuleDisabled(rule)"
        />
      </el-option-group>
    </el-select>

    <el-alert
      v-if="hasMissingReference"
      type="warning"
      :closable="false"
      show-icon
      title="原规则引用已失效，请重新选择"
    />

    <div v-if="selectedRule" class="rule-summary">
      <div class="summary-head">
        <span class="rule-name">{{
          selectedRule.ruleName || selectedRule.ruleCode
        }}</span>
        <el-tag size="small" effect="plain">{{
          modelLabel(selectedRule.modelType)
        }}</el-tag>
        <el-tag size="small" type="info" effect="plain">{{
          selectedRule.scope === 'GLOBAL' ? '全局' : '项目'
        }}</el-tag>
      </div>
      <div class="field-summary">
        <span class="field-count"
          >输入 {{ selectedRule.inputFields.length }}</span
        >
        <span
          v-for="field in visibleFields(selectedRule.inputFields)"
          :key="'in-' + fieldKey(field)"
          class="field-chip"
          >{{ fieldName(field) }}</span
        >
        <span v-if="selectedRule.inputFields.length > 3" class="field-more"
          >+{{ selectedRule.inputFields.length - 3 }}</span
        >
      </div>
      <div class="field-summary">
        <span class="field-count"
          >输出 {{ selectedRule.outputFields.length }}</span
        >
        <span
          v-for="field in visibleFields(selectedRule.outputFields)"
          :key="'out-' + fieldKey(field)"
          class="field-chip output"
          >{{ fieldName(field) }}</span
        >
        <span v-if="selectedRule.outputFields.length > 3" class="field-more"
          >+{{ selectedRule.outputFields.length - 3 }}</span
        >
      </div>
    </div>
  </div>
</template>

<script>
import { $emit } from '../../utils/gogocodeTransfer'
import { RULE_MODEL_LABELS } from '@/utils/ruleCallConfig'

export default {
  name: 'RuleExecutionSelector',
  props: {
    ruleId: { type: [String, Number], default: null },
    ruleCode: { type: String, default: '' },
    rules: { type: Array, default: () => [] },
    currentRuleId: { type: [String, Number], default: null },
    currentRuleCode: { type: String, default: '' },
  },
  computed: {
    selectedRule() {
      if (this.ruleId != null) {
        const byId = this.rules.find(
          (rule) => String(rule.id) === String(this.ruleId)
        )
        if (byId) return byId
      }
      if (!this.ruleCode) return null
      const matches = this.rules.filter(
        (rule) => String(rule.ruleCode) === String(this.ruleCode)
      )
      return matches.length === 1 ? matches[0] : null
    },
    selectedId() {
      return this.selectedRule ? this.selectedRule.id : null
    },
    hasMissingReference() {
      return (this.ruleId != null || !!this.ruleCode) && !this.selectedRule
    },
    ruleGroups() {
      const projectRules = this.rules.filter((rule) => rule.scope !== 'GLOBAL')
      const globalRules = this.rules.filter((rule) => rule.scope === 'GLOBAL')
      return [
        { key: 'PROJECT', label: '项目规则', rules: projectRules },
        { key: 'GLOBAL', label: '已关联全局规则', rules: globalRules },
      ].filter((group) => group.rules.length)
    },
  },
  methods: {
    onChange(ruleId) {
      const rule =
        this.rules.find((item) => String(item.id) === String(ruleId)) || null
      $emit(this, 'update:value', rule ? rule.id : null)
      $emit(this, 'select', rule)
    },
    isRuleDisabled(rule) {
      const sameId =
        this.currentRuleId != null &&
        String(rule.id) === String(this.currentRuleId)
      const sameCode =
        this.currentRuleCode &&
        String(rule.ruleCode) === String(this.currentRuleCode)
      return (
        sameId || sameCode || (rule.status != null && Number(rule.status) !== 1)
      )
    },
    ruleLabel(rule) {
      const name = rule.ruleName || rule.ruleCode || ''
      const code =
        rule.ruleCode && rule.ruleCode !== name
          ? ' (' + rule.ruleCode + ')'
          : ''
      return name + code + ' · ' + this.modelLabel(rule.modelType)
    },
    modelLabel(modelType) {
      return RULE_MODEL_LABELS[modelType] || modelType || '未知类型'
    },
    visibleFields(fields) {
      return (fields || []).slice(0, 3)
    },
    fieldName(field) {
      return field.fieldLabel || field.scriptName || field.fieldName || '-'
    },
    fieldKey(field) {
      return field.id || field.scriptName || field.fieldName
    },
  },
  emits: ['input', 'visible-change', 'update:value', 'select'],
}
</script>

<style lang="scss" scoped>
.rule-execution-selector {
  width: 100%;
}
.rule-select {
  width: 100%;
}
.rule-summary {
  margin-top: 8px;
  padding: 8px 10px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  background: #fafafa;
}
.summary-head,
.field-summary {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}
.summary-head {
  margin-bottom: 6px;
}
.rule-name {
  color: #303133;
  font-size: 13px;
  font-weight: 600;
}
.field-summary {
  min-height: 22px;
  color: #606266;
  font-size: 12px;
}
.field-count {
  width: 46px;
  color: #64748b;
}
.field-chip {
  max-width: 160px;
  overflow: hidden;
  padding: 1px 6px;
  border-radius: 3px;
  color: #606266;
  background: #ebeef5;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.field-chip.output {
  color: #0f766e;
  background: #e6f6f3;
}
.field-more {
  color: #64748b;
}
.rule-summary + :deep(.el-alert),
:deep(.el-alert + .rule-summary) {
  margin-top: 8px;
}
</style>
