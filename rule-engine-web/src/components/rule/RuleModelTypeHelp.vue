<template>
  <div class="model-type-help" aria-live="polite">
    <template v-if="guide">
      <strong>{{ guide.purpose }}</strong>
      <p>{{ guide.example }}</p>
      <p>{{ checks[modelType] }}</p>
    </template>
    <p v-else>先按业务目标选择类型：条件判断可从决策表开始；累计打分选评分卡；多步骤编排选决策流。</p>
    <span>创建后继续配置条件和结果，保存并测试，通过校验和审批后再发布。</span>
  </div>
</template>

<script>
const GUIDES = {
  TABLE: { purpose: '逐行配置“满足哪些条件，执行哪些结果”。', example: '例如：年龄不足 18 岁时拒绝，满足准入条件时通过。请确认多行同时命中时的执行方式。' },
  TREE: { purpose: '按判断分支逐层展开决策路径。', example: '例如：先判断是否老客，再分别判断收入或历史逾期情况。' },
  FLOW: { purpose: '编排多个判断、动作和规则调用步骤。', example: '例如：准入检查 → 调用评分规则 → 按评分分支处理 → 输出结论。' },
  RULE_SET: { purpose: '集中管理多条条件与动作，并设置命中策略。', example: '例如：多个拒绝条件集中维护；按业务需要选择命中即停或继续执行。' },
  CROSS: { purpose: '用行、列两个维度交叉确定结果。', example: '例如：客户等级 × 逾期次数，交叉格中配置授信额度。' },
  SCORE: { purpose: '将各评分项的得分按权重累加，并按总分划分等级。', example: '例如：年龄、收入、负债分别加减分，再根据总分确定风险等级。' },
  CROSS_ADV: { purpose: '为交叉矩阵配置多层行列条件和结果。', example: '适合单一行、列条件不足以表达的额度或定价矩阵；先明确各维度及其分段。' },
  SCORE_ADV: { purpose: '组合多个评分维度和条件分段计算结果。', example: '适合需要更复杂维度组合的评分逻辑；先明确每个维度的条件、得分与结果字段。' },
  SCRIPT: { purpose: '直接编写 QL 脚本表达决策逻辑。', example: '适合熟悉脚本的配置人员。通过变量选择器插入引用，并用测试覆盖分支和异常情况。' },
}

const CHECKS = {
  TABLE: '建议测试：一行命中、多行同时命中、全部不命中，以及阈值恰好相等的情况。',
  TREE: '建议测试：每条分支、没有分支命中、缺失输入；在追踪中核对最终到达的叶子。',
  FLOW: '建议测试：分支、汇合、提前终止、子规则失败；按追踪确认动作顺序和最终输出。',
  RULE_SET: '建议测试：首条命中、多条命中和无命中，确认是否继续执行后续规则。',
  CROSS: '建议测试：行列区间的端点、重叠、空白格和区间外输入，确认默认结果。',
  SCORE: '建议测试：每项得分、权重、总分和等级边界，核对缺失输入是否影响累计结果。',
  CROSS_ADV: '建议测试：每个维度的边界与组合、未覆盖组合和默认结果，核对写入字段。',
  SCORE_ADV: '建议测试：各维度分段、缺失值、总分与等级边界，逐项解释分数贡献。',
  SCRIPT: '建议测试：正常、边界与异常路径；动态拼接的引用需要额外检查，不能只依赖静态血缘。',
}

export default {
  name: 'RuleModelTypeHelp',
  props: { modelType: { type: String, default: '' } },
  data() { return { checks: CHECKS } },
  computed: {
    guide() { return GUIDES[this.modelType] || null },
  },
}
</script>

<style scoped>
.model-type-help {
  width: 100%;
  margin-top: 8px;
  padding: 12px;
  box-sizing: border-box;
  border-radius: 4px;
  background: var(--tianshu-bg-soft);
  color: var(--tianshu-text-secondary);
  font-size: 12px;
  line-height: 1.6;
  overflow-wrap: anywhere;
}
.model-type-help strong { color: var(--tianshu-text-primary); font-weight: 600; }
.model-type-help p { margin: 4px 0 8px; }
.model-type-help span { color: var(--tianshu-text-tertiary); }
</style>
