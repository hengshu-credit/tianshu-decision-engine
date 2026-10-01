<template>
  <div class="managed-dialog-footer">
    <el-button size="small" :disabled="disabled || loading" @click="$emit('cancel')">
      {{ cancelText }}
    </el-button>
    <el-button
      size="small"
      type="primary"
      :loading="loading"
      :disabled="disabled"
      @click="$emit('submit')"
    >
      {{ submitText }}
    </el-button>
  </div>
</template>

<script>
export default {
  name: 'ManagedDialogFooter',
  props: {
    loading: { type: Boolean, default: false },
    disabled: { type: Boolean, default: false },
    cancelText: { type: String, default: '取消' },
    submitText: { type: String, default: '生成审批草稿' },
  },
  emits: ['cancel', 'submit'],
}
</script>

<style scoped>
.managed-dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
