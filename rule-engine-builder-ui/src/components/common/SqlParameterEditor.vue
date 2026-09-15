<template>
  <section class="sql-params" aria-label="SQL 参数配置">
    <div class="sql-params-heading">
      <span>SQL 需要 {{ expectedCount }} 个参数，已配置 {{ rows.length }} 个</span>
      <el-radio-group v-model="mode" size="small">
        <el-radio-button value="FORM">表单</el-radio-button>
        <el-radio-button value="JSON">JSON</el-radio-button>
      </el-radio-group>
    </div>
    <el-alert v-if="error" :title="error" type="warning" :closable="false" />
    <monaco-editor v-if="mode === 'JSON'" :value="value" language="json" height="130px" @update:value="$emit('update:value', $event)" />
    <template v-else>
      <div v-for="(row, index) in rows" :key="index" class="sql-param-row">
        <span>{{ index + 1 }}</span>
        <el-select :model-value="typeOf(row)" :aria-label="`参数 ${index + 1} 类型`" @update:model-value="changeType(index, $event)">
          <el-option label="字符串" value="STRING" />
          <el-option label="数值" value="NUMBER" />
          <el-option label="布尔" value="BOOLEAN" />
          <el-option label="空值" value="NULL" />
          <el-option label="业务字段" value="REFERENCE" />
          <el-option v-if="typeOf(row) === 'LEGACY'" label="原有表达式" value="LEGACY" />
        </el-select>
        <el-select v-if="typeOf(row) === 'REFERENCE'" :model-value="referenceKey(row)" filterable :aria-label="`参数 ${index + 1} 字段`" @update:model-value="selectReference(index, $event)">
          <el-option v-for="option in fieldOptions" :key="optionKey(option)" :label="option.displayName || option.refName || option.varLabel || option.refCode || option.varCode" :value="optionKey(option)" />
        </el-select>
        <el-select v-else-if="typeOf(row) === 'BOOLEAN'" :model-value="rowValue(row)" :aria-label="`参数 ${index + 1} 值`" @update:model-value="changeValue(index, 'BOOLEAN', $event)">
          <el-option label="是（true）" :value="true" />
          <el-option label="否（false）" :value="false" />
        </el-select>
        <el-input v-else :model-value="displayValue(row)" :aria-label="`参数 ${index + 1} 值`" :disabled="['NULL', 'LEGACY'].includes(typeOf(row))" @update:model-value="changeValue(index, typeOf(row), $event)" />
        <el-button link type="danger" :aria-label="`删除参数 ${index + 1}`" @click="remove(index)">删除</el-button>
      </div>
      <div class="sql-params-actions">
        <el-button :disabled="!!parseError" @click="append">添加参数</el-button>
        <el-button v-if="rows.length < expectedCount" :disabled="!!parseError" @click="fillMissing">补齐参数</el-button>
      </div>
      <p>按 SQL 中 ? 的顺序填写；引号和注释内的 ? 不计入。业务字段按 ID 绑定。</p>
      <p v-if="rows.some(row => typeOf(row) === 'LEGACY')">原有表达式按原配置保留，可切换 JSON 查看；选择新类型时才转换该项。</p>
    </template>
  </section>
</template>

<script>
import MonacoEditor from '@/components/MonacoEditor.vue'
import { createReferenceOperand } from '@/utils/operand'
import { analyzeSqlQuery } from '@/utils/sqlQuery'
import { parseSqlParameters, sqlParameterType, validateSqlParameters } from '@/utils/sqlParameters'

export default {
  name: 'SqlParameterEditor',
  components: { MonacoEditor },
  props: {
    value: { type: String, default: '[]' },
    sql: { type: String, default: '' },
    variables: { type: Array, default: () => [] },
  },
  emits: ['update:value'],
  data() { return { mode: 'FORM' } },
  computed: {
    rows() { try { return parseSqlParameters(this.value) } catch (e) { return [] } },
    parseError() { try { parseSqlParameters(this.value); return '' } catch (e) { return 'SQL 参数 JSON 无法解析，请切换 JSON 修正；原内容已保留' } },
    expectedCount() { return analyzeSqlQuery(this.sql).placeholderCount },
    error() { return this.parseError || validateSqlParameters(this.sql, this.rows) },
    fieldOptions() { return this.variables.filter(option => ['VARIABLE', 'CONSTANT'].includes(option._refType)) },
  },
  methods: {
    typeOf: sqlParameterType,
    optionKey(option) { return `${option._refType}:${option._varId}` },
    referenceKey(row) { return row.refId ? `${row.refType}:${row.refId}` : '' },
    rowValue(row) { return row && row.kind === 'LITERAL' ? row.value : row },
    displayValue(row) { const value = this.rowValue(row); return value == null ? '' : typeof value === 'object' ? JSON.stringify(value) : String(value) },
    write(rows) { this.$emit('update:value', JSON.stringify(rows, null, 2)) },
    patch(index, value) { const rows = this.rows.slice(); rows[index] = value; this.write(rows) },
    changeType(index, type) {
      this.patch(index, type === 'REFERENCE' ? { kind: 'REFERENCE', refType: 'VARIABLE', refId: null } :
        { kind: 'LITERAL', valueType: type, value: type === 'NULL' ? null : type === 'BOOLEAN' ? false : '' })
    },
    changeValue(index, type, value) {
      // 数值由后端按声明类型解析，保留输入文本，避免 JavaScript 浮点转换损失精度。
      this.patch(index, { kind: 'LITERAL', valueType: type, value })
    },
    selectReference(index, key) { const option = this.fieldOptions.find(item => this.optionKey(item) === key); if (option) this.patch(index, createReferenceOperand(option)) },
    append() { this.write([...this.rows, { kind: 'LITERAL', valueType: 'STRING', value: '' }]) },
    fillMissing() { const rows = this.rows.slice(); while (rows.length < this.expectedCount) rows.push({ kind: 'LITERAL', valueType: 'STRING', value: '' }); this.write(rows) },
    remove(index) { this.write(this.rows.filter((_, i) => i !== index)) },
  },
}
</script>

<style scoped>
.sql-params { width: 100%; min-width: 0; }
.sql-params-heading, .sql-params-actions { display: flex; justify-content: space-between; align-items: center; gap: 8px; margin-bottom: 8px; flex-wrap: wrap; }
.sql-param-row { display: grid; grid-template-columns: 24px 120px minmax(0, 1fr) 40px; gap: 8px; align-items: center; margin-bottom: 8px; }
.sql-params-actions { justify-content: flex-start; margin-top: 8px; }
.sql-params p { margin: 4px 0; color: var(--tianshu-text-secondary); font-size: 12px; line-height: 1.5; }
</style>
