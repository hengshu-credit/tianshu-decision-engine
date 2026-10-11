<template>
  <div class="version-restore-options">
    <p>将恢复 V{{ version }} 的内容，两种方式都可以继续修改，发布时需要审批。</p>
    <el-radio-group :model-value="modelValue" aria-label="历史恢复方式" @update:model-value="$emit('update:modelValue', $event)">
      <el-radio value="NEW">生成新版本</el-radio>
      <el-radio value="REUSE">复用版本号 V{{ version }}</el-radio>
    </el-radio-group>
    <p class="version-restore-hint">{{ modelValue === 'REUSE' ? '审批通过后，原版本号使用修改后的内容。修改前的快照和审批记录仍可追溯。' : '审批通过后分配新的版本号，原版本保持可追溯。' }}</p>
  </div>
</template>

<script>
export default {
  name: 'VersionRestoreOptions',
  props: { version: { type: [String, Number], required: true }, modelValue: { type: String, default: 'NEW' } },
  emits: ['update:modelValue'],
}
</script>

<style scoped>
.version-restore-options { color: var(--tianshu-text-primary); }
.el-radio-group { display: flex; flex-wrap: wrap; gap: 8px 16px; }
.version-restore-hint { color: var(--tianshu-text-secondary); line-height: 1.6; }
</style>
