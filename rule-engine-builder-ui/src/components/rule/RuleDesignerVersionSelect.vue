<template>
  <div class="rule-designer-version-select" data-testid="designer-version-control">
    <span>规则版本</span>
    <el-select
      :model-value="modelValue"
      :loading="loading"
      :disabled="disabled"
      size="small"
      aria-label="选择规则版本"
      data-testid="designer-version-select"
      placeholder="选择规则版本"
      @change="$emit('change', $event)"
    >
      <el-option-group v-if="versionOptions.length" label="已发布版本">
        <el-option v-for="item in versionOptions" :key="item.value" :label="item.label" :value="item.value" />
      </el-option-group>
      <el-option-group v-if="revisionOptions.length" label="暂存与评审记录">
        <el-option
          v-for="item in revisionOptions"
          :key="item.value"
          :label="item.label"
          :value="item.value"
        >
          <div class="version-option">
            <span class="version-option__label" :title="item.sourceLabel || item.label">{{ item.label }}</span>
            <button v-if="item.state === 'DRAFT'" v-permission="'rule:edit'" type="button" class="delete-draft" :aria-label="`删除${item.label}`" :disabled="disabled" @click.stop="$emit('delete', item)">删除</button>
          </div>
        </el-option>
      </el-option-group>
    </el-select>
  </div>
</template>

<script>
export default {
  name: 'RuleDesignerVersionSelect',
  props: {
    options: { type: Array, default: () => [] },
    modelValue: { type: String, default: '' },
    loading: { type: Boolean, default: false },
    disabled: { type: Boolean, default: false },
  },
  emits: ['change', 'delete'],
  computed: {
    revisionOptions() {
      return this.options.filter((item) => item.group === 'REVISION')
    },
    versionOptions() {
      return this.options.filter((item) => item.group === 'VERSION')
    },
  },
}
</script>

<style scoped>
.rule-designer-version-select {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--tianshu-text-secondary);
  font-size: 13px;
  white-space: nowrap;
}

.rule-designer-version-select .el-select {
  width: 184px;
}
.version-option { display: flex; align-items: center; justify-content: space-between; gap: 12px; height: 100%; }
.version-option__label { min-width: 0; overflow: hidden; text-overflow: ellipsis; }
.delete-draft { flex: none; padding: 2px 8px; line-height: 20px; color: var(--tianshu-text-primary); background: var(--tianshu-bg-surface); border: 1px solid var(--tianshu-border-subtle); border-radius: 4px; cursor: pointer; }
.delete-draft:focus-visible { outline: 2px solid var(--el-color-primary); }
</style>
