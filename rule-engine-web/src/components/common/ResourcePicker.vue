<template>
  <div class="resource-content-picker">
  <div v-if="multiple && value?.length" class="resource-content-tags"><el-tag v-for="id in value" :key="id" :closable="!disabled" @close="remove(id)"><content-reference :value="resourceOption(id)" /></el-tag></div>
  <var-picker v-bind="$attrs" :value="multiple ? null : value" :vars="options" :selected-vars="selectedOptions" :version-selection="versionSelection" :visible-tags="[category]" :selectable-tags="[category]" value-key="id" :allow-custom="false" :placeholder="placeholder" :disabled="disabled" @input="change">
    <template #version="{ item }"><field-version-select v-if="versionSelection && item" :item="item" :selection="staged[String(item.id)] || (String(item.id) === String(value) ? versionSelection : {})" @change="selectVersion(item, $event)" @loading="pending[String(item.id)] = $event" /><span v-else>{{ (item?.resource?.currentVersion ?? item?.resource?.version) != null ? `最新 · v${item.resource.currentVersion ?? item.resource.version}` : '—' }}</span></template>
  </var-picker>
  </div>
</template>
<script>
import VarPicker from './VarPicker.vue'
import ContentReference from './ContentReference.vue'
import FieldVersionSelect from './FieldVersionSelect.vue'
import { compactVersion } from '@/utils/resourceVersion'
export default {
  name: 'ResourcePicker',
  inheritAttrs: false,
  components: { VarPicker, FieldVersionSelect, ContentReference },
  data: () => ({ staged: {}, pending: {} }),
  props: { value: { type: [String, Number, Array], default: null }, multiple: Boolean, resources: { type: Array, default: () => [] }, category: { type: String, default: 'external' }, placeholder: { type: String, default: '选择配置' }, disabled: Boolean, versionSelection: { type: Object, default: null } },
  emits: ['update:value', 'select'],
  computed: {
    selectedOptions() { return this.multiple ? this.options.filter(item => (this.value || []).some(id => String(id) === String(item.id))) : [] },
    options() { return this.resources.map(item => ({ id: item.id, disabled: item.disabled || !!this.pending[String(item.id)], _varId: item.id,
      _refType: ({ external: 'EXTERNAL_API', object: 'DATA_OBJECT_ROOT', list: 'LIST_LIBRARY' })[this.category] || this.category.toUpperCase(), _resource: true, _ref: { category: this.category },
      varCode: item.apiCode || item.modelCode || item.functionCode || item.funcCode || item.objectCode || item.listCode || item.datasourceCode || item.ruleCode || item.code || '',
      varLabel: item.apiName || item.modelName || item.functionLabel || item.funcName || item.functionName || item.objectName || item.objectLabel || item.listName || item.datasourceName || item.ruleName || item.name || '',
      varType: ({ model: 'MODEL', function: item.returnType || 'FUNCTION', object: 'OBJECT', list: 'LIST', database: 'DATABASE', sftp: 'SFTP', rule: 'RULE' })[this.category] || 'API',
      versionMode: 'LATEST', version: item.currentVersion ?? item.version, resource: item })) },
  },
  methods: {
    change(id) {
      if (this.multiple) {
        const values = this.value || []
        if (id != null && id !== '' && !values.some(value => String(value) === String(id))) this.$emit('update:value', [...values, id])
        return
      }
      this.$emit('update:value', id === '' ? null : id)
      const item = this.resources.find(item => String(item.id) === String(id))
      const version = this.staged[String(id)] || (String(id) === String(this.value) ? this.versionSelection : null)
      this.$emit('select', item ? { ...item, ...version } : null)
    },
    selectVersion(item, version) { this.staged[String(item.id)] = version; if (String(item.id) === String(this.value)) this.change(item.id) },
    remove(id) { if (!this.disabled) this.$emit('update:value', (this.value || []).filter(value => String(value) !== String(id))) },
    resourceOption(id) { return this.options.find(item => String(item.id) === String(id)) },
    resourceLabel(id) { const item = this.resourceOption(id); return item ? [item.varLabel, item.varCode, compactVersion(item)].filter(Boolean).join(' ') : String(id) },
  },
}
</script>
<style scoped>
.resource-content-picker { min-width: 0; width: 100%; }
.resource-content-tags { display: flex; flex-wrap: wrap; gap: 4px; margin-bottom: 4px; }
.resource-content-tags :deep(.el-tag__content) { display: inline-flex; align-items: center; gap: 4px; }
</style>
