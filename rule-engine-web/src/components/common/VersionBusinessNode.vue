<template>
  <div class="business-node" :class="{ 'has-children': node.children?.length }">
    <strong>{{ node.title }}</strong>
    <span v-if="node.text !== undefined && node.text !== ''" class="business-node-value">{{ node.text }}</span>
    <div v-if="node.children?.length" class="business-node-children">
      <version-business-node v-for="(child, index) in node.children" :key="index" :node="child" />
    </div>
  </div>
</template>
<script>
export default { name: 'VersionBusinessNode', props: { node: { type: Object, required: true } } }
</script>
<style scoped>
.business-node { display: grid; grid-template-columns: minmax(88px, 1fr) minmax(0, 2fr); gap: 8px 16px; padding: 8px 0; font-size: 13px; line-height: 1.7; overflow-wrap: anywhere; }
.business-node > strong { color: var(--tianshu-text-secondary); font-weight: 500; }
.business-node-value { color: var(--tianshu-text-primary); white-space: pre-wrap; }
.business-node.has-children { grid-template-columns: minmax(0, 1fr); }
.has-children > strong { color: var(--tianshu-text-primary); font-weight: 600; }
.business-node-children { border-left: 2px solid var(--tianshu-border-subtle); padding-left: 12px; }
</style>
