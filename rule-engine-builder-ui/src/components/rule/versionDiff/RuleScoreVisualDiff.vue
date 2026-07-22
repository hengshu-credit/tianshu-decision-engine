<template>
  <div class="rule-score-visual-diff">
    <div class="rule-score-sheet">
      <section
        v-for="section in sections"
        :key="section.key"
        class="rule-score-section"
        :data-section="section.key"
      >
        <div class="rule-score-section-head">
          <span>{{
            section.key === 'thresholds'
              ? '档'
              : section.key === 'settings'
              ? '配'
              : '分'
          }}</span>
          <div>
            <strong>{{ section.title }}</strong>
            <small>{{ section.lanes.length }} 项</small>
          </div>
        </div>
        <div v-if="section.lanes.length" class="rule-score-lanes">
          <rule-condition-diff
            v-for="lane in section.lanes"
            :key="lane.key"
            :lane="lane"
          />
        </div>
        <div v-else class="rule-score-empty">暂无{{ section.title }}</div>
      </section>
    </div>
  </div>
</template>

<script>
import RuleConditionDiff from './RuleConditionDiff.vue'

export default {
  name: 'RuleScoreVisualDiff',
  components: { RuleConditionDiff },
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
}
</script>

<style scoped>
.rule-score-sheet {
  overflow-x: auto;
}
.rule-score-section + .rule-score-section {
  margin-top: 16px;
}
.rule-score-section-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.rule-score-section-head > span {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 4px;
  background: #f4f0ff;
  color: #6f42c1;
  font-size: 12px;
  font-weight: 700;
}
.rule-score-section-head > div {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.rule-score-section-head strong {
  color: #303133;
  font-size: 14px;
}
.rule-score-section-head small {
  color: #909399;
  font-size: 12px;
}
.rule-score-empty {
  min-width: 860px;
  padding: 16px;
  border: 1px dashed #dcdfe6;
  color: #909399;
  text-align: center;
  font-size: 13px;
}
</style>
