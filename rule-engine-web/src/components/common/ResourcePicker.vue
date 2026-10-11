<template>
  <var-picker :value="value" :vars="options" :version-selection="versionSelection" :visible-tags="[category]" :selectable-tags="[category]" value-key="id" :allow-custom="false" :placeholder="placeholder" :disabled="disabled" @input="change">
    <template #version="{ item }"><field-version-select v-if="versionSelection && item" :item="item" :selection="staged[String(item.id)] || (String(item.id) === String(value) ? versionSelection : {})" @change="selectVersion(item, $event)" @loading="pending[String(item.id)] = $event" /><span v-else>{{ (item?.resource?.currentVersion ?? item?.resource?.version) != null ? `最新 · v${item.resource.currentVersion ?? item.resource.version}` : '—' }}</span></template>
  </var-picker>
</template>
<script>
import VarPicker from './VarPicker.vue'
import FieldVersionSelect from './FieldVersionSelect.vue'
export default {
  name: 'ResourcePicker',
  components: { VarPicker, FieldVersionSelect },
  data: () => ({ staged: {}, pending: {} }),
  props: { value: { type: [String, Number], default: null }, resources: { type: Array, default: () => [] }, category: { type: String, default: 'external' }, placeholder: { type: String, default: '选择配置' }, disabled: Boolean, versionSelection: { type: Object, default: null } },
  emits: ['update:value', 'select'],
  computed: {
    options() { return this.resources.map(item => ({ id: item.id, disabled: !!this.pending[String(item.id)], _varId: item.id, _refType: this.category.toUpperCase(), _resource: true, _ref: { category: this.category }, varCode: item.apiCode || item.modelCode || item.functionCode || item.funcCode || item.objectCode || item.code || '', varLabel: item.apiName || item.modelName || item.functionLabel || item.funcName || item.functionName || item.objectName || item.objectLabel || item.name || '', varType: ({ model: 'MODEL', function: 'FUNCTION', object: 'OBJECT' })[this.category] || 'API', resource: item })) },
  },
  methods: {
    change(id) {
      this.$emit('update:value', id === '' ? null : id)
      const item = this.resources.find(item => String(item.id) === String(id))
      const version = this.staged[String(id)] || (String(id) === String(this.value) ? this.versionSelection : null)
      this.$emit('select', item ? { ...item, ...version } : null)
    },
    selectVersion(item, version) { this.staged[String(item.id)] = version; if (String(item.id) === String(this.value)) this.change(item.id) },
  },
}
</script>
