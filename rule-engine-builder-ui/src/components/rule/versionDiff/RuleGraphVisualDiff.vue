<template>
  <div class="rule-graph-visual-diff">
    <section
      v-for="section in sections"
      :key="section.key"
      class="rule-graph-section"
      :data-section="section.key"
    >
      <div class="rule-graph-section-head">
        <span class="rule-graph-section-icon">{{
          section.key === 'nodes'
            ? modelType === 'TREE'
              ? '树'
              : '流'
            : section.key === 'edges'
            ? '线'
            : '配'
        }}</span>
        <div>
          <strong>{{ section.title }}</strong>
          <span>{{ section.lanes.length }} 项</span>
        </div>
      </div>
      <div v-if="section.lanes.length" class="rule-graph-steps">
        <div
          v-for="(lane, index) in section.lanes"
          :key="lane.key"
          class="rule-graph-step"
        >
          <div
            v-if="index > 0"
            class="rule-graph-connector"
            aria-hidden="true"
          />
          <div class="rule-graph-step-index">{{ index + 1 }}</div>
          <rule-condition-diff :lane="lane" />
        </div>
      </div>
      <div v-else class="rule-graph-empty">暂无{{ section.title }}</div>
    </section>
  </div>
</template>

<script>
import RuleConditionDiff from './RuleConditionDiff.vue'

export default {
  name: 'RuleGraphVisualDiff',
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
.rule-graph-section + .rule-graph-section {
  margin-top: 16px;
}
.rule-graph-section-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.rule-graph-section-head > div {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.rule-graph-section-head strong {
  color: #303133;
  font-size: 14px;
}
.rule-graph-section-head span:not(.rule-graph-section-icon) {
  color: #909399;
  font-size: 12px;
}
.rule-graph-section-icon,
.rule-graph-step-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: #eef0ff;
  color: #2639e9;
  font-size: 12px;
  font-weight: 700;
}
.rule-graph-step {
  position: relative;
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr);
  align-items: start;
}
.rule-graph-step + .rule-graph-step {
  margin-top: 12px;
}
.rule-graph-step-index {
  position: relative;
  z-index: 1;
  margin-top: 10px;
}
.rule-graph-connector {
  position: absolute;
  top: -22px;
  bottom: calc(100% - 10px);
  left: 11px;
  width: 2px;
  background: #dcdfe6;
}
.rule-graph-steps {
  overflow-x: auto;
  padding: 4px 0 8px;
}
.rule-graph-empty {
  padding: 16px;
  border: 1px dashed #dcdfe6;
  color: #909399;
  text-align: center;
  font-size: 13px;
}
</style>
