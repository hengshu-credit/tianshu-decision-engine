<template>
  <div class="rule-version-diff">
    <div class="rule-version-head-grid">
      <div class="rule-version-side rule-version-side--left">
        <span class="rule-version-side-label">基准版本</span>
        <strong>v{{ leftVersion.version }}</strong>
        <span>{{ formatVersionTime(leftVersion.publishTime) }}</span>
        <span>{{ leftVersion.publishBy || '-' }}</span>
        <p>{{ leftVersion.changeLog || '无变更说明' }}</p>
      </div>
      <div class="rule-version-side rule-version-side--right">
        <span class="rule-version-side-label">对比版本</span>
        <strong>v{{ rightVersion.version }}</strong>
        <span>{{ formatVersionTime(rightVersion.publishTime) }}</span>
        <span>{{ rightVersion.publishBy || '-' }}</span>
        <p>{{ rightVersion.changeLog || '无变更说明' }}</p>
      </div>
    </div>

    <div class="rule-version-diff-summary">
      <div class="rule-version-diff-title">
        <strong>{{ modelTypeLabel }}业务配置差异</strong>
        <span v-if="diff.summary.total === 0" class="rule-version-same"
          >两个版本的业务配置一致</span
        >
        <span v-else>共 {{ diff.summary.total }} 处变化</span>
      </div>
      <div class="rule-version-diff-counts" aria-label="差异数量">
        <span class="is-modified">~ 修改 {{ diff.summary.modified }}</span>
        <span class="is-added">+ 新增 {{ diff.summary.added }}</span>
        <span class="is-removed">- 删除 {{ diff.summary.removed }}</span>
      </div>
    </div>

    <div
      v-if="diff.errors.left || diff.errors.right"
      class="rule-version-error-grid"
    >
      <div
        class="rule-version-error rule-version-error--left"
        :class="{ 'is-empty': !diff.errors.left }"
      >
        {{ diff.errors.left || '基准版本内容可正常解析' }}
      </div>
      <div
        class="rule-version-error rule-version-error--right"
        :class="{ 'is-empty': !diff.errors.right }"
      >
        {{ diff.errors.right || '对比版本内容可正常解析' }}
      </div>
    </div>

    <component
      :is="visualComponent"
      v-if="visualComponent"
      :model-type="modelType"
      :sections="diff.sections"
    />

    <div v-else-if="modelType === 'SCRIPT'" class="rule-script-visual-diff">
      <div class="rule-script-diff-head">
        <strong>QL 脚本代码差异</strong>
        <span>红色表示基准版本删除，绿色表示对比版本新增</span>
      </div>
      <monaco-diff-editor
        :original="diff.script.leftScript"
        :modified="diff.script.rightScript"
        language="ql"
        height="480px"
      />
      <section v-if="diff.script.refLanes.length" class="rule-script-ref-diff">
        <div class="rule-script-ref-head">
          <strong>变量引用变化</strong>
          <span>引用关系按变量 ID 校验</span>
        </div>
        <rule-condition-diff
          v-for="lane in diff.script.refLanes"
          :key="lane.key"
          :lane="lane"
        />
      </section>
    </div>
  </div>
</template>

<script>
import { buildRuleVersionDiff } from '@/utils/ruleVersionDiff'
import RuleListVisualDiff from './RuleListVisualDiff.vue'
import RuleGraphVisualDiff from './RuleGraphVisualDiff.vue'
import RuleMatrixVisualDiff from './RuleMatrixVisualDiff.vue'
import RuleScoreVisualDiff from './RuleScoreVisualDiff.vue'
import MonacoDiffEditor from './MonacoDiffEditor.vue'
import RuleConditionDiff from './RuleConditionDiff.vue'

const MODEL_TYPE_LABELS = {
  TABLE: '决策表',
  TREE: '决策树',
  FLOW: '决策流',
  RULE_SET: '规则集',
  CROSS: '交叉表',
  SCORE: '评分卡',
  CROSS_ADV: '复杂交叉表',
  SCORE_ADV: '复杂评分卡',
  SCRIPT: 'QL 脚本',
}

export default {
  name: 'RuleVersionDiff',
  components: {
    RuleListVisualDiff,
    RuleGraphVisualDiff,
    RuleMatrixVisualDiff,
    RuleScoreVisualDiff,
    MonacoDiffEditor,
    RuleConditionDiff,
  },
  props: {
    modelType: {
      type: String,
      required: true,
    },
    leftVersion: {
      type: Object,
      required: true,
    },
    rightVersion: {
      type: Object,
      required: true,
    },
  },
  computed: {
    diff() {
      return buildRuleVersionDiff({
        modelType: this.modelType,
        leftModelJson: this.leftVersion.modelJson,
        rightModelJson: this.rightVersion.modelJson,
      })
    },
    visualComponent() {
      if (['TABLE', 'RULE_SET'].includes(this.modelType))
        return RuleListVisualDiff
      if (['TREE', 'FLOW'].includes(this.modelType)) return RuleGraphVisualDiff
      if (['CROSS', 'CROSS_ADV'].includes(this.modelType))
        return RuleMatrixVisualDiff
      if (['SCORE', 'SCORE_ADV'].includes(this.modelType))
        return RuleScoreVisualDiff
      return null
    },
    modelTypeLabel() {
      return MODEL_TYPE_LABELS[this.modelType] || '规则'
    },
  },
  methods: {
    formatVersionTime(value) {
      return value ? String(value).replace('T', ' ') : '-'
    },
  },
}
</script>

<style scoped>
.rule-version-diff {
  margin-top: 12px;
}
.rule-version-head-grid,
.rule-version-error-grid {
  display: grid;
  grid-template-columns: minmax(420px, 1fr) minmax(420px, 1fr);
  gap: 12px;
  min-width: 860px;
}
.rule-version-head-grid {
  position: sticky;
  top: 0;
  z-index: 2;
  padding-bottom: 8px;
  background: #fff;
}
.rule-version-side {
  display: grid;
  grid-template-columns: auto auto 1fr auto;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  background: #f8f9fb;
  color: #606266;
  font-size: 12px;
}
.rule-version-side strong {
  color: #303133;
  font-size: 16px;
}
.rule-version-side p {
  grid-column: 1 / -1;
  margin: 0;
  color: #303133;
  font-size: 13px;
}
.rule-version-side-label {
  padding: 2px 6px;
  border-radius: 4px;
  background: #eef0ff;
  color: #2639e9;
  font-weight: 600;
}
.rule-version-diff-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-width: 860px;
  margin: 4px 0 12px;
  padding: 12px 16px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
}
.rule-version-diff-title {
  display: flex;
  align-items: baseline;
  gap: 8px;
  color: #64748b;
  font-size: 12px;
}
.rule-version-diff-title strong {
  color: #303133;
  font-size: 14px;
}
.rule-version-same {
  color: #529b2e;
}
.rule-version-diff-counts {
  display: flex;
  gap: 8px;
}
.rule-version-diff-counts span {
  padding: 3px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
}
.rule-version-diff-counts .is-modified {
  background: #fdf6ec;
  color: #b88230;
}
.rule-version-diff-counts .is-added {
  background: #f0f9eb;
  color: #529b2e;
}
.rule-version-diff-counts .is-removed {
  background: #fef0f0;
  color: #c45656;
}
.rule-version-error-grid {
  margin-bottom: 12px;
}
.rule-version-error {
  padding: 8px 12px;
  border: 1px solid #fbc4c4;
  border-radius: 4px;
  background: #fef0f0;
  color: #c45656;
  font-size: 13px;
}
.rule-version-error.is-empty {
  border-color: #c2e7b0;
  background: #f0f9eb;
  color: #529b2e;
}
.rule-script-diff-head,
.rule-script-ref-head {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 8px;
}
.rule-script-diff-head strong,
.rule-script-ref-head strong {
  color: #303133;
  font-size: 14px;
}
.rule-script-diff-head span,
.rule-script-ref-head span {
  color: #64748b;
  font-size: 12px;
}
.rule-script-ref-diff {
  margin-top: 16px;
  overflow-x: auto;
}
</style>
