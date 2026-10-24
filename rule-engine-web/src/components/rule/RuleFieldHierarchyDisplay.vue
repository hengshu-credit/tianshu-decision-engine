<template>
  <div class="rule-field-hierarchy">
    <content-reference v-if="field.isDataObject" :value="{ refType: 'DATA_OBJECT_ROOT', refId: field.objectId, code: field.objectCode, label: field.objectLabel, valueType: 'OBJECT', version: field.objectVersion }" />
    <content-reference :value="{ ...field, fieldCode: field.fieldPath, fieldName: field.fieldLabel, refType: field.refType || (field.isDataObject ? 'DATA_OBJECT' : 'VARIABLE') }" />
  </div>
</template>
<script>
import ContentReference from '@/components/common/ContentReference.vue'
export default { name: 'RuleFieldHierarchyDisplay', components: { ContentReference }, props: { field: { type: Object, required: true } } }
</script>
<style scoped>.rule-field-hierarchy { display:flex; flex-direction:column; align-items:flex-start; min-width:0; gap:4px; }</style>
