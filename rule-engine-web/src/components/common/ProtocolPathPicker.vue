<template>
  <var-picker :value="value" :vars="fields" :visible-tags="['http-context']" :selectable-tags="['http-context']" :auto-switch-custom="false" edit-on-input :placeholder="placeholder" @input="$emit('update:value', $event)" />
</template>
<script>
import VarPicker from './VarPicker.vue'
import { apiProtocolFields } from '@/utils/apiContext'
export default {
  name: 'ProtocolPathPicker',
  components: { VarPicker },
  props: {
    value: { type: String, default: '' }, placeholder: { type: String, default: '选择或输入取值路径' },
    contextLabel: { type: String, default: '鉴权 API · HTTP 上下文' },
    apiId: { type: [String, Number], default: 'current-api' },
    paths: { type: Array, default: () => [{ value: 'body', label: '响应体', type: 'OBJECT' }, { value: 'body.access_token', label: 'Token', type: 'STRING' }, { value: 'body.expires_in', label: '过期秒数', type: 'NUMBER' }, { value: 'headers', label: '响应头', type: 'MAP' }, { value: 'headers.Authorization', label: 'Authorization', type: 'STRING' }] },
  },
  emits: ['update:value'],
  computed: {
    fields() { return apiProtocolFields(this.paths, this.contextLabel, this.apiId) },
  },
}
</script>
