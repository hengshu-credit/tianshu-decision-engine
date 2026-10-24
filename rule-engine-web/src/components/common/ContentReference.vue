<template>
  <span class="content-ref" :class="{ 'content-ref--wrap': wrap }" :title="parts.map(part => part.title).join(' · ')">
    <span v-for="part in parts" :key="part.key" :class="partClass(part)" :title="part.title" :aria-label="part.title" :role="cellRole || undefined" :data-content-part="part.key"><slot v-if="part.key === 'version' && $slots.version" name="version" /><template v-else>{{ part.text }}</template></span>
    <span v-if="$slots.version && !parts.some(part => part.key === 'version')" class="content-ref__segment content-ref__version" :role="cellRole || undefined"><slot name="version" /></span>
  </span>
</template>
<script>
import { contentReferenceParts, contentPartClass } from '@/utils/contentReference'
export default {
  name: 'ContentReference',
  props: { value: { type: [Object, String, Number, Boolean, Array], default: null }, options: { type: Array, default: () => [] }, sensitive: Boolean, wrap: Boolean, cellRole: { type: String, default: '' }, mode: { type: String, default: 'configuration' } },
  computed: { parts() { return contentReferenceParts(this.value, this.options, this.sensitive, this.mode) } },
  methods: { partClass: contentPartClass },
}
</script>
