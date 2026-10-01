<template>
  <div class="rule-score-visual-diff">
    <div class="rule-score-sheet">
      <div class="rule-score-columns">
        <div
          v-for="side in sides"
          :key="side.key"
          class="rule-score-column"
          :class="'is-' + side.key"
        >
          <rule-score-result-table
            :sections="sections"
            :side="side.key"
            :side-label="side.label"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import RuleScoreResultTable from './RuleScoreResultTable.vue'

export default {
  name: 'RuleScoreVisualDiff',
  components: { RuleScoreResultTable },
  props: {
    modelType: {
      type: String,
      required: true,
    },
    sections: {
      type: Array,
      default: () => [],
    },
  },
  computed: {
    sides() {
      return [
        { key: 'left', label: '基准版本' },
        { key: 'right', label: '对比版本' },
      ]
    },
  },
}
</script>

<style scoped>
.rule-score-sheet {
  overflow-x: auto;
}

.rule-score-columns {
  display: grid;
  grid-template-columns: minmax(420px, 1fr) minmax(420px, 1fr);
  gap: 12px;
  min-width: 860px;
}

.rule-score-column {
  min-width: 0;
  padding: 12px;
  border: 1px solid var(--tianshu-border-subtle);
  border-radius: 6px;
  background: var(--tianshu-bg-soft);
}

@media (max-width: 980px) {
  .rule-score-columns {
    grid-template-columns: 1fr;
    min-width: 0;
  }
}
</style>
