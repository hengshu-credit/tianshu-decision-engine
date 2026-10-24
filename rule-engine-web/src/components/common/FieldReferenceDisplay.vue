<template><content-reference :value="field" :wrap="!compact" class="field-reference-display" /></template>
<script>
import ContentReference from './ContentReference.vue'
export default { name: 'FieldReferenceDisplay', components: { ContentReference }, props: { field: { type: Object, required: true }, compact: Boolean } }
</script>
