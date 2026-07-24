<template>
  <div class="rule-list-visual-diff">
    <section
      v-for="section in sections"
      :key="section.key"
      class="rule-visual-section"
      :class="'is-' + section.variant"
      :data-section="section.key"
    >
      <div class="rule-visual-section-head">
        <span class="rule-visual-section-icon">{{
          section.key === 'rules' ? '规' : '配'
        }}</span>
        <div>
          <strong>{{ section.title }}</strong>
          <span>{{ section.lanes.length }} 项</span>
        </div>
      </div>
      <div v-if="section.lanes.length" class="rule-visual-lanes">
        <rule-condition-diff
          v-for="lane in section.lanes"
          :key="lane.key"
          :lane="lane"
        />
      </div>
      <div v-else class="rule-visual-empty">
        此版本区间没有{{ section.title }}
      </div>
    </section>
  </div>
</template>

<script>
import RuleConditionDiff from './RuleConditionDiff.vue'

export default {
  name: 'RuleListVisualDiff',
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
.rule-visual-section + .rule-visual-section {
  margin-top: 16px;
}
.rule-visual-section-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  color: #303133;
}
.rule-visual-section-head > div {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.rule-visual-section-head strong {
  font-size: 14px;
}
.rule-visual-section-head span:not(.rule-visual-section-icon) {
  color: #64748b;
  font-size: 12px;
}
.rule-visual-section-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 4px;
  background: #eef0ff;
  color: #2639e9;
  font-size: 12px;
  font-weight: 700;
}
.rule-visual-lanes {
  overflow-x: auto;
  padding: 4px 0 8px;
}
.rule-visual-empty {
  padding: 16px;
  border: 1px dashed #dcdfe6;
  color: #64748b;
  text-align: center;
  font-size: 13px;
}
</style>
