<template>
  <section class="api-object-value-editor" :aria-label="label">
    <div class="api-object-value-editor__help">{{ help }}</div>
    <p v-if="error" class="api-object-value-editor__error" role="alert">{{ error }}</p>
    <div v-for="(row, index) in rows" :key="row.id" class="api-object-value-editor__row">
      <el-input v-model="row.path" size="small" :placeholder="pathPlaceholder" aria-label="配置路径" @update:model-value="emitValue" />
      <api-object-value-editor v-if="isObject(row.value)" ref="children" :value="row.value" :vars="vars" :functions="functions" :modules="modules" :label="row.path" @update:value="setValue(row, $event)" />
      <api-value-editor v-else :value="row.value" :vars="vars" :functions="functions" :modules="modules" @update:value="setValue(row, $event)" />
      <el-button link size="small" type="danger" aria-label="删除配置项" @click="remove(index)">删除</el-button>
    </div>
    <div><el-button size="small" @click="add">添加配置项</el-button><el-button size="small" @click="addObject">添加嵌套对象</el-button></div>
  </section>
</template>

<script>
import ApiValueEditor from './ApiValueEditor.vue'
import { newApiId } from '@/utils/apiExecution'

const clone = value => value == null ? value : JSON.parse(JSON.stringify(value))

export default {
  name: 'ApiObjectValueEditor',
  components: { ApiValueEditor },
  props: {
    value: { type: Object, default: () => ({}) },
    vars: { type: Array, default: () => [] },
    functions: { type: Array, default: () => [] },
    modules: { type: Array, default: () => [] },
    label: { type: String, default: '对象值配置' },
    help: { type: String, default: '字段名原样保留；嵌套字段请添加对象。值支持字段、固定值和表达式。' },
    pathPlaceholder: { type: String, default: '字段名，如 client_id' },
  },
  emits: ['update:value'],
  data() { return { rows: [], emittedValue: '', sourceValue: '', error: '' } },
  watch: {
    value: { immediate: true, deep: true, handler(value) {
      const text = JSON.stringify(value)
      if (text === this.sourceValue) return
      this.sourceValue = text
      if (text === this.emittedValue) return
      this.rows = Object.entries(value).map(([path, content]) => ({ id: newApiId(), path, value: clone(content) }))
      this.error = ''
    } },
  },
  methods: {
    isObject(value) { return value && typeof value === 'object' && !Array.isArray(value) && typeof value.kind !== 'string' },
    validate() {
      const names = new Set()
      this.error = ''
      for (const row of this.rows) {
        if (!row.path && row.value == null) continue
        if (!String(row.path).trim()) { this.error = '请填写字段名'; break }
        if (names.has(row.path)) { this.error = '字段名重复：' + row.path; break }
        if (row.value == null) { this.error = '请为字段选择内容或固定值：' + row.path; break }
        names.add(row.path)
      }
      return !this.error && (this.$refs.children || []).every(child => child.validate())
    },
    emitValue() {
      if (!this.validate()) return
      const result = Object.fromEntries(this.rows.filter(row => row.path).map(row => [row.path, clone(row.value)]))
      this.emittedValue = JSON.stringify(result)
      this.$emit('update:value', result)
    },
    setValue(row, value) { row.value = value; this.emitValue() },
    add() { this.rows.push({ id: newApiId(), path: '', value: null }) },
    addObject() { this.rows.push({ id: newApiId(), path: '', value: {} }) },
    remove(index) { this.rows.splice(index, 1); if (!this.rows.length) this.add(); this.emitValue() },
  },
}
</script>

<style scoped>
.api-object-value-editor { display: grid; gap: 8px; min-width: 0; }
.api-object-value-editor__help { color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.5; }
.api-object-value-editor__error { color: var(--el-color-danger); margin: 0; }
.api-object-value-editor__row { display: grid; grid-template-columns: minmax(140px, .8fr) minmax(240px, 1.4fr) auto; gap: 8px; align-items: center; min-width: 0; }
.api-object-value-editor__row :deep(.api-value-editor) { min-width: 0; }
@media (max-width: 760px) { .api-object-value-editor__row { grid-template-columns: 1fr; } .api-object-value-editor__row > .el-button { justify-self: end; } }
</style>
