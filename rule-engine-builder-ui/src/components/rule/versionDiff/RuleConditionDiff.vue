<template>
  <div class="rule-condition-diff">
    <rule-diff-lane :lane="lane" :depth="depth">
      <template #children="{ children }">
        <rule-condition-diff
          v-for="child in children"
          :key="child.key"
          :lane="child"
          :depth="depth + 1"
        />
      </template>
    </rule-diff-lane>
  </div>
</template>

<script>
import RuleDiffLane from './RuleDiffLane.vue'

export default {
  name: 'RuleConditionDiff',
  components: { RuleDiffLane },
  props: {
    lane: {
      type: Object,
      required: true
    },
    depth: {
      type: Number,
      default: 0
    }
  }
}
</script>
