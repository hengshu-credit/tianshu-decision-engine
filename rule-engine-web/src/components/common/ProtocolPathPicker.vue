<template>
  <div class="protocol-path-picker">
    <var-picker :value="value" :vars="fields" :visible-tags="['http-context']" :selectable-tags="['http-context']" :auto-switch-custom="false" :allow-custom="allowCustom" :disabled="disabled" :edit-on-input="allowCustom" :placeholder="placeholder" @input="$emit('update:value', $event)" />
    <span v-if="value && !isKnownPath" class="protocol-path-picker__hint" role="status">{{ removed ? '所选路径已从当前结构移除，请核对或重新选择。' : '自定义路径：当前结构未声明，请用样例验证取值。' }}</span>
    <span v-else-if="typeMismatch" class="protocol-path-picker__hint" role="status">所选结果类型与目标字段类型不一致，请核对取值路径。</span>
    <span v-else-if="selectedLabel" class="protocol-path-picker__label">{{ selectedLabel }}</span>
  </div>
</template>
<script>
import VarPicker from './VarPicker.vue'
import { apiProtocolFields } from '@/utils/apiContext'
import { pickerTypeMatches } from '@/utils/fieldPicker'
export default {
  name: 'ProtocolPathPicker',
  components: { VarPicker },
  props: {
    value: { type: String, default: '' }, placeholder: { type: String, default: '选择或输入取值路径' },
    contextLabel: { type: String, default: '鉴权 API · HTTP 上下文' },
    apiId: { type: [String, Number], default: 'current-api' },
    allowCustom: { type: Boolean, default: true },
    disabled: Boolean,
    expectedType: { type: String, default: '' },
    paths: { type: Array, default: () => [{ value: 'body', label: '响应体', type: 'OBJECT' }, { value: 'body.access_token', label: 'Token', type: 'STRING' }, { value: 'body.expires_in', label: '过期秒数', type: 'NUMBER' }, { value: 'headers', label: '响应头', type: 'MAP' }, { value: 'headers.Authorization', label: 'Authorization', type: 'STRING' }] },
  },
  emits: ['update:value'],
  data: () => ({ removed: false }),
  watch: {
    paths(next, previous) { this.removed = previous.some(path => path.value === this.value) && !next.some(path => path.value === this.value) },
    value() { this.removed = false },
  },
  computed: {
    fields() { return apiProtocolFields(this.paths, this.contextLabel, this.apiId) },
    isKnownPath() { return this.paths.some(path => path?.value === this.value) },
    typeMismatch() { const path = this.paths.find(path => path.value === this.value); return path?.type && this.expectedType && !pickerTypeMatches(path.type, this.expectedType) },
    selectedLabel() { const path = this.paths.find(path => path.value === this.value); return path?.label && path.label !== path.value ? path.label : '' },
  },
}
</script>
<style scoped>
.protocol-path-picker { display: grid; gap: 4px; min-width: 0; }
.protocol-path-picker__hint { color: var(--el-color-warning); font-size: 12px; line-height: 1.4; }
.protocol-path-picker__label { color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.4; }
</style>
