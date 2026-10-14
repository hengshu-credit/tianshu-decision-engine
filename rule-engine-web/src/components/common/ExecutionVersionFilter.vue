<template>
  <el-form-item label="版本范围" class="execution-version-filter-item">
    <el-popover
      v-model:visible="visible"
      placement="bottom-start"
      :width="560"
      trigger="click"
      popper-class="execution-version-filter-popper"
      @show="openMenu"
      @hide="closeMenu"
    >
      <template #reference>
        <el-input
          class="execution-version-trigger"
          :model-value="selectionLabel"
          readonly
          clearable
          aria-label="执行版本范围"
          placeholder="选择规则及版本"
          @clear="clearSelection"
        />
      </template>

      <div class="execution-version-menu">
        <div class="execution-version-search">
          <el-input v-model="query" clearable placeholder="输入规则编码或名称搜索" aria-label="搜索版本筛选规则" @update:model-value="loadRules" />
        </div>
        <div class="execution-version-columns">
          <div class="execution-rule-list" role="listbox" aria-label="规则列表">
            <button
              v-for="rule in rules"
              :key="rule.id"
              type="button"
              class="execution-rule-option"
              :class="{ 'is-active': String(rule.id) === String(activeRuleId) }"
              @mouseenter="activateRule(rule)"
              @focus="activateRule(rule)"
              @click="selectRule(rule.id)"
            >
              <span class="execution-rule-option__text">
                <strong>{{ rule.ruleName || rule.ruleCode || `规则 ${rule.id}` }}</strong>
                <small>{{ rule.ruleCode || '—' }}</small>
              </span>
              <span class="execution-rule-option__arrow" aria-hidden="true">›</span>
            </button>
            <div v-if="loadingRules" class="execution-version-empty">正在加载规则…</div>
            <div v-else-if="!rules.length" class="execution-version-empty">没有符合条件的规则</div>
          </div>

          <div v-if="activeRule" class="execution-version-submenu" role="listbox" aria-label="规则版本列表">
            <div class="execution-version-submenu__heading" :title="activeRuleLabel">
              <strong>{{ activeRule.ruleName || activeRule.ruleCode || `规则 ${activeRule.id}` }}</strong>
              <small>{{ activeRule.ruleCode || '选择执行版本' }}</small>
            </div>
            <button type="button" class="execution-version-option" @click="selectRuleVersion(activeRule, 'ALL')">
              <span>全部版本</span><small>不限制业务版本</small>
            </button>
            <button type="button" class="execution-version-option" @click="selectRuleVersion(activeRule, 'LATEST')">
              <span>最新版</span><small>当前生产版本</small>
            </button>
            <div v-if="loadingRuleId === activeRule.id" class="execution-version-empty">正在加载历史版本…</div>
            <template v-else>
              <div v-if="activeVersions.length" class="execution-version-group-label">历史版本</div>
              <button v-for="row in activeVersions" :key="row.bindingId" type="button" class="execution-version-option" @click="selectRuleVersion(activeRule, String(row.version))">
                <span>v{{ row.version }}</span><small>{{ row.revision ? `修订 ${row.revision}` : '已发布版本' }}</small>
              </button>
              <div v-if="!activeVersions.length" class="execution-version-empty">暂无可用历史版本</div>
            </template>
          </div>
          <div v-else class="execution-version-submenu execution-version-submenu--placeholder">悬停规则查看最新版和历史版本</div>
        </div>
      </div>
    </el-popover>
  </el-form-item>
</template>

<script>
import { listDefinitions, listVersions } from '@/api/definition'

export default {
  name: 'ExecutionVersionFilter',
  props: { modelValue: { type: Object, required: true } },
  emits: ['update:modelValue'],
  data: () => ({
    rules: [],
    versions: [],
    versionsByRule: {},
    query: '',
    activeRuleId: null,
    loadingRules: false,
    loadingRuleId: null,
    visible: false,
    sequence: 0,
    ruleRequestId: 0,
  }),
  computed: {
    activeRule() {
      if (this.activeRuleId == null) return null
      return this.rules.find(rule => String(rule.id) === String(this.activeRuleId)) || null
    },
    activeRuleLabel() {
      const rule = this.activeRule
      return rule ? `${rule.ruleName || rule.ruleCode || `规则 ${rule.id}`} · ${rule.ruleCode || ''}` : ''
    },
    activeVersions() {
      return this.versionsByRule[String(this.activeRuleId)] || (String(this.activeRuleId) === String(this.modelValue.ruleId) ? this.versions : [])
    },
    selectedRule() {
      return this.rules.find(rule => String(rule.id) === String(this.modelValue.ruleId)) || null
    },
    selectionLabel() {
      if (!this.modelValue.ruleId) return '选择规则及版本'
      const rule = this.selectedRule
      const ruleLabel = rule?.ruleName || rule?.ruleCode || this.modelValue.ruleName || this.modelValue.ruleCode || `规则 ${this.modelValue.ruleId}`
      if (this.modelValue.versionMode === 'FIXED') return `${ruleLabel} · v${this.modelValue.ruleVersion}`
      if (this.modelValue.versionMode === 'LATEST') return `${ruleLabel} · 最新版`
      return `${ruleLabel} · 全部版本`
    },
    selected() { return this.modelValue.versionMode === 'FIXED' ? String(this.modelValue.ruleVersion) : this.modelValue.versionMode || 'ALL' },
  },
  watch: {
    'modelValue.ruleId'() {
      this.sequence++
      this.versions = []
      this.versionsByRule = {}
      this.loadingRuleId = null
      this.activeRuleId = null
    },
  },
  beforeUnmount() { this.sequence++ },
  methods: {
    async openMenu() {
      await this.loadRules(this.query)
      if (this.modelValue.ruleId) {
        const selected = this.rules.find(rule => String(rule.id) === String(this.modelValue.ruleId))
        if (selected) this.activateRule(selected)
      }
    },
    closeMenu() {
      this.activeRuleId = null
      this.query = ''
    },
    clearSelection() {
      this.$emit('update:modelValue', { ...this.modelValue, ruleId: null, versionMode: 'ALL', ruleVersion: null })
    },
    async loadRules(query = '') {
      const requestId = ++this.ruleRequestId
      this.loadingRules = true
      try {
        const response = await listDefinitions({ pageNum: 1, pageSize: 50, ruleName: query || undefined, ruleCode: query || undefined })
        if (requestId !== this.ruleRequestId) return
        const data = response.data || {}
        this.rules = Array.isArray(data) ? data : data.records || []
      } catch (error) {
        if (requestId === this.ruleRequestId) this.rules = []
      } finally {
        if (requestId === this.ruleRequestId) this.loadingRules = false
      }
    },
    async fetchRules({ query, pageNum = 1, pageSize = 20 }) {
      const response = await listDefinitions({ pageNum, pageSize, ruleName: query })
      return response.data
    },
    selectRule(ruleId) {
      const rule = this.rules.find(item => String(item.id) === String(ruleId))
      if (rule) this.activateRule(rule)
      this.$emit('update:modelValue', { ...this.modelValue, ruleId: ruleId || null, versionMode: 'ALL', ruleVersion: null })
    },
    selectRuleVersion(rule, value) {
      if (!rule) return
      this.$emit('update:modelValue', {
        ...this.modelValue,
        ruleId: rule.id,
        ruleCode: rule.ruleCode || this.modelValue.ruleCode || '',
        ruleName: rule.ruleName || this.modelValue.ruleName || '',
        versionMode: ['ALL', 'LATEST'].includes(value) ? value : 'FIXED',
        ruleVersion: ['ALL', 'LATEST'].includes(value) ? null : Number(value),
      })
      this.visible = false
    },
    activateRule(rule) {
      if (!rule) return
      this.activeRuleId = rule.id
      this.loadVersions(rule.id)
    },
    normalizeVersions(rows) {
      return [...new Set((rows || []).map(row => row.businessVersion ?? row.version))]
        .filter(version => Number(version) > 0)
        .sort((a, b) => Number(b) - Number(a))
        .map(version => {
          const source = (rows || []).find(row => String(row.businessVersion ?? row.version) === String(version)) || {}
          return { bindingId: String(version), version, revision: source.version }
        })
    },
    async loadVersions(ruleId) {
      if (!ruleId) return
      const key = String(ruleId)
      if (Object.prototype.hasOwnProperty.call(this.versionsByRule, key)) {
        this.versions = this.versionsByRule[key]
        return
      }
      const sequence = ++this.sequence
      this.loadingRuleId = ruleId
      try {
        const response = await listVersions(ruleId)
        if (sequence !== this.sequence) return
        const versions = this.normalizeVersions(response.data)
        this.versionsByRule = { ...this.versionsByRule, [key]: versions }
        this.versions = versions
      } catch (error) {
        if (sequence === this.sequence) this.versionsByRule = { ...this.versionsByRule, [key]: [] }
      } finally {
        if (sequence === this.sequence) this.loadingRuleId = null
      }
    },
    async load(open) {
      if (!open || !this.modelValue.ruleId) return
      await this.loadVersions(this.modelValue.ruleId)
    },
    selectVersion(value) {
      this.$emit('update:modelValue', { ...this.modelValue, versionMode: ['ALL', 'LATEST'].includes(value) ? value : 'FIXED', ruleVersion: ['ALL', 'LATEST'].includes(value) ? null : Number(value) })
    },
  },
}
</script>

<style scoped>
.execution-version-filter-item :deep(.el-form-item__content) { min-width: 0; }
.execution-version-trigger { width: 320px; max-width: 100%; cursor: pointer; }
.execution-version-trigger :deep(.el-input__inner) { cursor: pointer; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.execution-version-menu { width: 100%; min-width: 0; }
.execution-version-search { padding-bottom: 10px; }
.execution-version-columns { display: grid; grid-template-columns: minmax(220px, 1fr) minmax(220px, 1fr); min-height: 220px; border: 1px solid var(--tianshu-border-subtle); }
.execution-rule-list { min-width: 0; max-height: 320px; overflow: auto; border-right: 1px solid var(--tianshu-border-subtle); }
.execution-rule-option, .execution-version-option { display: flex; width: 100%; align-items: center; justify-content: space-between; gap: 8px; padding: 9px 10px; border: 0; background: transparent; color: var(--tianshu-text-primary); text-align: left; cursor: pointer; }
.execution-rule-option:hover, .execution-rule-option:focus-visible, .execution-rule-option.is-active, .execution-version-option:hover, .execution-version-option:focus-visible { background: var(--tianshu-info-bg); outline: none; }
.execution-rule-option__text, .execution-version-option { min-width: 0; }
.execution-rule-option__text { display: grid; gap: 2px; overflow: hidden; }
.execution-rule-option__text strong, .execution-rule-option__text small, .execution-version-submenu__heading strong, .execution-version-submenu__heading small, .execution-version-option small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.execution-rule-option__text strong, .execution-version-submenu__heading strong { font-size: 13px; font-weight: 500; }
.execution-rule-option__text small, .execution-version-submenu__heading small, .execution-version-option small { color: var(--tianshu-text-tertiary); font-size: 11px; }
.execution-rule-option__arrow { flex: none; color: var(--tianshu-text-tertiary); font-size: 18px; }
.execution-version-submenu { min-width: 0; max-height: 320px; overflow: auto; padding: 4px 0; }
.execution-version-submenu__heading { display: grid; gap: 2px; padding: 7px 10px; border-bottom: 1px solid var(--tianshu-border-subtle); }
.execution-version-group-label { padding: 8px 10px 4px; color: var(--tianshu-text-tertiary); font-size: 11px; }
.execution-version-option { justify-content: space-between; }
.execution-version-option span { min-width: 0; font-size: 13px; }
.execution-version-empty { padding: 20px 10px; color: var(--tianshu-text-tertiary); font-size: 12px; text-align: center; }
.execution-version-submenu--placeholder { display: grid; place-items: center; padding: 20px; color: var(--tianshu-text-tertiary); font-size: 12px; text-align: center; }
@media (max-width: 680px) {
  .execution-version-trigger { width: 100%; }
  .execution-version-columns { grid-template-columns: minmax(150px, 1fr) minmax(150px, 1fr); }
}
</style>
