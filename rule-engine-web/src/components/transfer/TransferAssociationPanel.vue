<template>
  <section v-if="associations.length" class="transfer-association-panel">
    <header class="association-heading">
      <div>
        <strong>关联未随包携带的内容</strong>
        <p>同编码候选会标记为推荐；没有候选时请搜索并手工选择。数据对象引用还需要选择目标字段。</p>
      </div>
      <el-tag :type="unresolvedCount ? 'warning' : 'success'">
        {{ unresolvedCount ? `${unresolvedCount} 项待关联` : '关联已完成' }}
      </el-tag>
    </header>
    <div v-for="association in associations" :key="association.referenceKey" class="association-row">
      <div class="association-source">
        <strong>{{ association.targetName || association.targetCode || association.targetKey }}</strong>
        <span>{{ association.targetResourceType }} · {{ association.path }}</span>
        <el-tag v-if="association.suggestedTargetId" size="small" type="success">同编码推荐</el-tag>
      </div>
      <div class="association-target">
        <el-select
          v-if="association.candidates && association.candidates.length"
          :model-value="resourceBindings[association.targetKey] || association.selectedTargetId || ''"
          filterable
          remote
          clearable
          class="association-select"
          placeholder="选择目标内容"
          @visible-change="openCandidates(association, $event)"
          @change="selectResource(association, $event)"
          @clear="selectResource(association, null)"
        >
          <el-option
            v-for="candidate in association.candidates || []"
            :key="candidate.id"
            :label="candidateLabel(candidate, association)"
            :value="candidate.id"
          />
        </el-select>
        <el-input-number
          v-else
          :model-value="resourceBindings[association.targetKey] || association.selectedTargetId || null"
          :min="1"
          data-testid="binding-id"
          placeholder="填写目标 ID"
          @change="selectResource(association, $event)"
        />
        <el-button link type="primary" @click="$emit('search', association)">搜索更多</el-button>
        <el-select
          v-if="association.childPath"
          :model-value="fieldBindings[association.referenceKey] || association.selectedFieldId || ''"
          filterable
          clearable
          class="association-field-select"
          placeholder="选择目标字段"
          @change="selectField(association, $event)"
          @clear="selectField(association, null)"
        >
          <el-option
            v-for="field in fieldsFor(association)"
            :key="field.id"
            :label="fieldLabel(field)"
            :value="field.id"
          />
        </el-select>
      </div>
    </div>
  </section>
</template>

<script>
export default {
  name: 'TransferAssociationPanel',
  props: {
    associations: { type: Array, default: () => [] },
    resourceBindings: { type: Object, default: () => ({}) },
    fieldBindings: { type: Object, default: () => ({}) },
  },
  emits: ['update:resourceBindings', 'update:fieldBindings', 'search'],
  computed: {
    unresolvedCount() {
      return this.associations.filter((item) => {
        const resourceId = this.resourceBindings[item.targetKey] || item.selectedTargetId
        const fieldId = item.childPath
          ? this.fieldBindings[item.referenceKey] || item.selectedFieldId
          : true
        return !resourceId || !fieldId
      }).length
    },
  },
  methods: {
    candidateLabel(candidate, association) {
      const code = candidate.code ? ` · ${candidate.code}` : ''
      const scope = candidate.projectId ? ` · 项目 ${candidate.projectId}` : ''
      const recommendation = Number(candidate.id) === Number(association.suggestedTargetId) ? '（推荐）' : ''
      return `${candidate.name || candidate.code || candidate.id}${code}${scope}${recommendation}`
    },
    fieldLabel(field) {
      return `${field.name || field.code || field.id}${field.code ? ` · ${field.code}` : ''}${field.type ? ` · ${field.type}` : ''}`
    },
    fieldsFor(association) {
      const selected = Number(this.resourceBindings[association.targetKey] || association.selectedTargetId)
      const candidate = (association.candidates || []).find((item) => Number(item.id) === selected)
      return (candidate && candidate.fields) || association.fieldCandidates || []
    },
    openCandidates(association, open) {
      if (open && !(association.candidates || []).length) this.$emit('search', association)
    },
    selectResource(association, value) {
      const next = { ...this.resourceBindings }
      if (value == null || value === '') delete next[association.targetKey]
      else next[association.targetKey] = Number(value)
      this.$emit('update:resourceBindings', next)
      this.$emit('search', { ...association, targetResourceId: value == null ? null : Number(value) })
      if (association.childPath) {
        const fields = { ...this.fieldBindings }
        delete fields[association.referenceKey]
        this.$emit('update:fieldBindings', fields)
      }
    },
    selectField(association, value) {
      const next = { ...this.fieldBindings }
      if (value == null || value === '') delete next[association.referenceKey]
      else next[association.referenceKey] = Number(value)
      this.$emit('update:fieldBindings', next)
    },
  },
}
</script>

<style scoped>
.transfer-association-panel { margin-top: 18px; padding: 16px; border: 1px solid var(--el-border-color-light); border-radius: 10px; background: var(--el-fill-color-extra-light); }
.association-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 12px; }
.association-heading p { margin: 5px 0 0; color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.5; }
.association-row { display: grid; grid-template-columns: minmax(220px, .8fr) minmax(300px, 1.2fr); gap: 14px; align-items: center; padding: 10px 0; border-top: 1px solid var(--el-border-color-lighter); }
.association-source { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; min-width: 0; }
.association-source span { width: 100%; color: var(--el-text-color-secondary); font-size: 12px; }
.association-target { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 8px; align-items: center; }
.association-select, .association-field-select { width: 100%; }
.association-field-select { grid-column: 1 / -1; }
@media (max-width: 760px) { .association-row { grid-template-columns: 1fr; } }
</style>
