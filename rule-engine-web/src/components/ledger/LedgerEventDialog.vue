<template>
  <el-dialog :model-value="modelValue" :title="form.id ? '编辑事件' : '新建事件'" width="560px" class="ledger-dialog" :close-on-click-modal="false" @update:model-value="$emit('update:modelValue', $event)">
    <el-form label-width="90px" size="small">
      <el-form-item label="事件名称" required><el-input v-model="form.title" maxlength="200" aria-label="事件名称" /></el-form-item>
      <scope-project-fields v-model:scope="form.scope" v-model:project-id="form.projectId" />
      <el-form-item label="状态"><el-select v-model="form.status"><el-option v-for="item in statuses" :key="item.value" :value="item.value" :label="item.label" /></el-select></el-form-item>
    </el-form>
    <template #footer><el-button :disabled="busy" @click="$emit('update:modelValue', false)">取消</el-button><el-button type="primary" :loading="busy" @click="save">保存</el-button></template>
  </el-dialog>
</template>
<script>
import ScopeProjectFields from '@/components/common/ScopeProjectFields.vue'
import { saveLedgerEvent } from '@/api/ledger'
import { LEDGER_EVENT_STATUSES } from '@/utils/ledger'
export default {
  name: 'LedgerEventDialog', components: { ScopeProjectFields },
  props: { modelValue: Boolean, event: { type: Object, default: null }, scope: { type: String, default: '' }, projectId: { type: [Number, String], default: null } },
  emits: ['update:modelValue', 'saved'],
  data: () => ({ form: {}, busy: false, statuses: LEDGER_EVENT_STATUSES }),
  watch: { modelValue: { immediate: true, handler(open) { if (open) this.form = this.event ? { ...this.event } : { title: '', status: 'FOLLOWING', scope: this.scope, projectId: this.projectId } } } },
  methods: {
    async save() {
      if (!this.form.title?.trim() || !this.form.scope || (this.form.scope === 'PROJECT' && !this.form.projectId)) { this.$message.warning('请填写事件名称和归属范围'); return }
      this.busy = true
      try { const response = await saveLedgerEvent(this.form.id, { ...this.form, projectId: this.form.scope === 'PROJECT' ? this.form.projectId : null }); this.$emit('saved', response.data); this.$emit('update:modelValue', false) }
      catch { /* request 层已显示具体错误。 */ } finally { this.busy = false }
    },
  },
}
</script>
