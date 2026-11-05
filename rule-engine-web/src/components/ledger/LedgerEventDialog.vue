<template>
  <el-dialog :model-value="modelValue" :title="form.id ? '编辑事件' : '新建事件'" width="560px" class="ledger-dialog" :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy" :before-close="close" @closed="$emit('closed')">
    <el-form label-width="90px" size="small" :disabled="busy">
      <el-form-item label="事件编码" required><el-input v-model="form.code" maxlength="128" aria-label="事件编码" placeholder="请输入唯一编码" /></el-form-item>
      <el-form-item label="事件名称" required><el-input v-model="form.title" maxlength="200" aria-label="事件名称" /></el-form-item>
      <el-form-item label="事件类型"><el-select v-model="form.typeId" clearable placeholder="未分类" aria-label="事件类型"><el-option v-for="type in types" :key="type.id" :value="String(type.id)" :label="type.name" /></el-select></el-form-item>
      <scope-project-fields v-model:scope="form.scope" v-model:project-id="form.projectId" />
      <el-form-item label="状态枚举" required>
        <div class="ledger-status-options">
          <div v-for="(option, index) in form.statusOptions" :key="index" class="ledger-status-option">
            <el-input v-model="option.value" :aria-label="`状态值 ${index + 1}`" maxlength="24" placeholder="状态值，如待处理" />
            <el-input v-model="option.label" :aria-label="`状态名称 ${index + 1}`" maxlength="80" placeholder="显示名称（可选）" />
            <el-button link type="danger" :aria-label="`删除状态 ${index + 1}`" @click="form.statusOptions.splice(index, 1)">删除</el-button>
          </div>
          <el-button size="small" :disabled="busy || form.statusOptions?.length >= 50" @click="form.statusOptions.push({ value: '', label: '' })">添加状态</el-button>
          <span class="ledger-help">为本事件配置可选状态；状态值必须唯一，显示名称留空时直接显示状态值。</span>
        </div>
      </el-form-item>
      <el-form-item label="当前状态" required :error="invalidCurrentStatus ? '请从本事件的状态枚举中重新选择' : ''"><el-select v-model="form.status" placeholder="请选择当前状态"><el-option v-for="item in statuses" :key="item.value" :value="item.value" :label="item.label || item.value" /></el-select></el-form-item>
    </el-form>
    <template #footer><el-button :disabled="busy" @click="close()">取消</el-button><el-button type="primary" :loading="busy" @click="save">保存</el-button></template>
  </el-dialog>
</template>
<script>
import ScopeProjectFields from '@/components/common/ScopeProjectFields.vue'
import { saveLedgerEvent } from '@/api/ledger'
import { LEDGER_EVENT_STATUSES } from '@/utils/ledger'
export default {
  name: 'LedgerEventDialog', components: { ScopeProjectFields },
  props: { modelValue: Boolean, event: { type: Object, default: null }, types: { type: Array, default: () => [] }, scope: { type: String, default: '' }, projectId: { type: [Number, String], default: null } },
  emits: ['update:modelValue', 'saved', 'closed'],
  data: () => ({ form: {}, baseline: '', busy: false, generation: 0 }),
  computed: {
    statuses() { return (this.form.statusOptions || []).filter(item => item.value?.trim()) },
    invalidCurrentStatus() { return this.form.status && !this.statuses.some(item => item.value === this.form.status) },
  },
  watch: { modelValue: { immediate: true, handler(open) {
    this.generation++
    if (open) {
      this.form = this.event ? { ...this.event } : { code: '', title: '', status: '', scope: this.scope, projectId: this.projectId }
      this.form.statusOptions = this.event ? (this.event.statusOptions || LEDGER_EVENT_STATUSES).map(item => ({ ...item })) : [{ value: '', label: '' }]
      if (this.event && !this.event.statusOptions && this.form.status && !this.form.statusOptions.some(item => item.value === this.form.status)) this.form.statusOptions.push({ value: this.form.status, label: this.form.status })
      this.form.typeId = this.form.typeId == null ? null : String(this.form.typeId)
      this.baseline = JSON.stringify(this.form)
    }
  } } },
  beforeUnmount() { this.generation++ },
  methods: {
    async close(done) {
      if (this.busy) return
      if (JSON.stringify(this.form) !== this.baseline) {
        try { await this.$confirm('事件有未保存修改，关闭后将丢失。', '未保存提醒', { confirmButtonText: '放弃修改', cancelButtonText: '继续编辑', type: 'warning' }) }
        catch { return }
      }
      if (typeof done === 'function') done()
      this.$emit('update:modelValue', false)
    },
    async save() {
      if (this.busy) return
      if (!this.form.code?.trim()) { this.$message.warning('请填写唯一的事件编码'); return }
      if (!this.form.title?.trim() || !this.form.scope || (this.form.scope === 'PROJECT' && !this.form.projectId)) { this.$message.warning('请填写事件名称和归属范围'); return }
      if (!this.statuses.length || this.statuses.length !== this.form.statusOptions.length || this.statuses.length > 50) { this.$message.warning('请配置 1 至 50 个非空状态值'); return }
      if (new Set(this.statuses.map(item => item.value)).size !== this.statuses.length) { this.$message.warning('状态枚举值不能重复'); return }
      if (!this.form.status || this.invalidCurrentStatus) { this.$message.warning('请从本事件的状态枚举中选择当前状态'); return }
      this.busy = true
      const generation = this.generation
      try { const response = await saveLedgerEvent(this.form.id, { ...this.form, projectId: this.form.scope === 'PROJECT' ? this.form.projectId : null }); if (generation !== this.generation) return; this.$emit('saved', response.data); this.$emit('update:modelValue', false) }
      catch { /* request 层已显示具体错误。 */ } finally { this.busy = false }
    },
  },
}
</script>
