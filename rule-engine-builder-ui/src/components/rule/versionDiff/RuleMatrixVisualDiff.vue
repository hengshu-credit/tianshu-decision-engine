<template>
  <div class="rule-matrix-visual-diff">
    <div class="rule-matrix-board">
      <section
        v-for="section in sections"
        :key="section.key"
        class="rule-matrix-section"
        :class="'is-' + section.variant"
        :data-section="section.key"
      >
        <div class="rule-matrix-section-head">
          <span>{{ section.variant === 'matrix' ? '矩' : section.variant === 'dimensions' || section.variant === 'axis' ? '维' : '配' }}</span>
          <strong>{{ section.title }}</strong>
          <em>{{ section.lanes.length }} 项</em>
        </div>
        <div v-if="section.lanes.length" class="rule-matrix-lanes">
          <rule-condition-diff
            v-for="lane in section.lanes"
            :key="lane.key"
            :lane="lane"
          />
        </div>
        <div v-else class="rule-matrix-empty">暂无{{ section.title }}</div>
      </section>
    </div>
  </div>
</template>

<script>
import RuleConditionDiff from './RuleConditionDiff.vue'

export default {
  name: 'RuleMatrixVisualDiff',
  components: { RuleConditionDiff },
  props: {
    modelType: {
      type: String,
      required: true
    },
    sections: {
      type: Array,
      default: () => []
    }
  }
}
</script>

<style scoped>
.rule-matrix-board {
  overflow-x: auto;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  background: #fafbfc;
  padding: 12px;
}
.rule-matrix-section + .rule-matrix-section {
  margin-top: 16px;
}
.rule-matrix-section-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.rule-matrix-section-head > span {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 4px;
  background: #e6f7ff;
  color: #1677a6;
  font-size: 12px;
  font-weight: 700;
}
.rule-matrix-section-head strong {
  color: #303133;
  font-size: 14px;
}
.rule-matrix-section-head em {
  color: #909399;
  font-size: 12px;
  font-style: normal;
}
.rule-matrix-empty {
  min-width: 860px;
  padding: 16px;
  border: 1px dashed #dcdfe6;
  background: #fff;
  color: #909399;
  text-align: center;
  font-size: 13px;
}
</style>
