<template>
  <div class="rule-execution-selector">
    <var-picker
      :value="selectedId"
      :vars="pickerRules" value-key="id" :allow-custom="false"
      :visible-tags="['rule']" :selectable-tags="['rule']"
      :version-selection="inputVersion"
      size="small"
      class="rule-select"
      placeholder="选择要执行的规则"
      @visible-change="$emit('visible-change', $event)"
      @input="onChange"
    >
      <template #version="{ item, active }">
        <rule-call-version-select v-if="item && active && versioned" compact :rule-id="item.id" :version-mode="versionFor(item.id).versionMode" :version-binding-id="versionFor(item.id).versionBindingId" :catalog-mode="catalogMode" :catalog-key="catalogKey" :frozen-versions="item.rule.executionVersions || []" @change="onVersionChange(item.id, $event)" @fields="onVersionFields" />
        <span v-else :title="versioned ? '单击该行选择版本，双击确认' : '使用规则当前发布版本'">{{ versionLabel(item) }}</span>
      </template>
    </var-picker>

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
        <el-tag
          v-for="field in visibleFields(selectedRule.inputFields)"
          :key="'in-' + fieldKey(field)"
          class="field-chip"
          type="info"
          size="small"
          effect="plain"
          >{{ fieldName(field) }}</el-tag
        >
        <span v-if="selectedRule.inputFields.length > 3" class="field-more"
          >+{{ selectedRule.inputFields.length - 3 }}</span
        >
      </div>
      <div class="field-summary">
        <span class="field-count"
          >输出 {{ selectedRule.outputFields.length }}</span
        >
        <el-tag
          v-for="field in visibleFields(selectedRule.outputFields)"
          :key="'out-' + fieldKey(field)"
          class="field-chip output"
          type="success"
          size="small"
          effect="plain"
          >{{ fieldName(field) }}</el-tag
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
import VarPicker from './VarPicker.vue'
import RuleCallVersionSelect from '@/components/flow/RuleCallVersionSelect.vue'

export default {
  name: 'RuleExecutionSelector',
  components: { VarPicker, RuleCallVersionSelect },
  data: () => ({ draftVersions: {}, knownVersions: {} }),
  props: {
    ruleId: { type: [String, Number], default: null },
    ruleCode: { type: String, default: '' },
    rules: { type: Array, default: () => [] },
    currentRuleId: { type: [String, Number], default: null },
    currentRuleCode: { type: String, default: '' },
    versioned: Boolean,
    versionMode: { type: String, default: 'LATEST' },
    versionBindingId: { type: [String, Number], default: null },
    catalogMode: { type: String, default: 'LIVE' },
    catalogKey: { type: String, default: '' },
  },
  computed: {
    inputVersion() {
      if (!this.versioned || !this.ruleId) return null
      const versions = this.knownVersions[String(this.ruleId)] || this.selectedRule?.executionVersions || []
      const selected = this.versionFor(this.ruleId)
      const version = selected.versionMode === 'FIXED' ? versions.find(item => String(item.bindingId) === String(selected.versionBindingId))?.version
        : versions.reduce((result, item) => Math.max(result, Number(item.version) || 0), 0) || this.selectedRule?.publishedVersion
      return { versionMode: selected.versionMode, version }
    },
    selectionContext() { return `${this.catalogMode}:${this.catalogKey}:${this.ruleId}:${this.versionMode}:${this.versionBindingId}` },
    pickerRules() { return this.rules.map(rule => ({ id: rule.id, _varId: rule.id, _refType: 'RULE', varCode: rule.ruleCode, varLabel: rule.ruleName, varType: 'RULE', _ref: { category: 'rule' }, disabled: this.isRuleDisabled(rule), rule })) },
    selectedRule() {
      if (this.ruleId != null) {
        const byId = this.rules.find(
          (rule) => String(rule.id) === String(this.ruleId)
        )
        return byId || null
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
  watch: { selectionContext() { this.draftVersions = {} } },
  methods: {
    onVersionFields(payload) { if (payload.versions) this.knownVersions[payload.ruleId] = payload.versions; this.$emit('fields', payload) },
    onVersionChange(id, version) { this.draftVersions[String(id)] = version; if (String(id) === String(this.ruleId)) this.onChange(id) },
    versionFor(id) { return this.draftVersions[String(id)] || (String(id) === String(this.ruleId) ? { versionMode: this.versionMode, versionBindingId: this.versionBindingId } : { versionMode: 'LATEST', versionBindingId: null }) },
    versionLabel(item) {
      if (!item) return '—'
      const versions = this.knownVersions[String(item.id)] || item.rule.executionVersions || []
      const selected = this.versionFor(item.id)
      if (selected.versionMode === 'FIXED') return `v${versions.find(version => String(version.bindingId) === String(selected.versionBindingId))?.version ?? '（指定）'}`
      const latest = versions.reduce((result, version) => Number(version.version) > Number(result?.version || 0) ? version : result, null)
      const current = latest?.version ?? item.rule.publishedVersion
      return current != null && Number(current) > 0 ? `最新 · v${current}` : '最新版'
    },
    onChange(ruleId) {
      const rule =
        this.rules.find((item) => String(item.id) === String(ruleId)) || null
      $emit(this, 'update:value', rule ? rule.id : null)
      $emit(this, 'select', rule && this.versioned ? { ...rule, pickerVersion: this.versionFor(rule.id) } : rule)
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
  emits: ['input', 'visible-change', 'update:value', 'select', 'fields'],
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
  border: 1px solid var(--tianshu-border-subtle);
  border-radius: 4px;
  background: var(--tianshu-bg-muted);
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
  color: var(--tianshu-text-primary);
  font-size: 13px;
  font-weight: 600;
}
.field-summary {
  min-height: 22px;
  color: var(--tianshu-text-secondary);
  font-size: 12px;
}
.field-count {
  width: 46px;
  color: var(--tianshu-text-tertiary);
}
.field-chip {
  min-width: 0;
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.field-more {
  color: var(--tianshu-text-tertiary);
}
.rule-summary + :deep(.el-alert),
:deep(.el-alert + .rule-summary) {
  margin-top: 8px;
}
</style>
