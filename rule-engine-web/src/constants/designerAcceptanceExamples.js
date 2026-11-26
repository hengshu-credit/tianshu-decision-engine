// 输入和预期对应每种设计器说明中的配置，复制到当前规则后可独立编辑。
const examples = {
  TABLE: ['按行从上到下首条命中。age < 18 输出 decision=拒绝，age < 25 输出 decision=复核，其他输出 decision=通过。', '检查命中行、比较符和首条命中设置', [
    ['重复命中取第一行', { age: 17 }, { decision: '拒绝' }], ['成年边界', { age: 18 }, { decision: '复核' }], ['两行均未命中', { age: 25 }, { decision: '通过' }],
  ]],
  TREE: ['从年龄判断开始，未成年拒绝，成年后按 income >= 5000 判断通过或复核。只有通过叶子输出 decision=通过。', '在追踪树核对年龄分支、收入分支和叶子动作', [
    ['未成年提前结束', { age: 17, income: 6000 }, { decision: '拒绝' }], ['收入临界值', { age: 18, income: 5000 }, { decision: '通过' }], ['低收入叶子', { age: 20, income: 4999 }, { decision: '复核' }],
  ]],
  FLOW: ['先判断 blocked，命中黑名单时输出 decision=拒绝并终止。其余调用评分子规则，按 score >= 600 通过，否则复核。', '检查终止范围、子规则版本、返回值及汇合后的动作', [
    ['命中名单提前终止', { blocked: true, score: 700 }, { decision: '拒绝' }], ['调用评分后通过', { blocked: false, score: 600 }, { decision: '通过' }], ['子规则评分不足', { blocked: false, score: 599 }, { decision: '复核' }],
  ]],
  RULE_SET: ['按顺序检查 overdue > 0 和 score < 600。首个命中分别输出 decision=拒绝、复核，其余通过。', '核对规则集命中统计、顺序与终止动作', [
    ['两条命中取优先项', { overdue: 1, score: 550 }, { decision: '拒绝' }], ['仅评分命中', { overdue: 0, score: 599 }, { decision: '复核' }], ['都未命中', { overdue: 0, score: 600 }, { decision: '通过' }],
  ]],
  CROSS: ['行按 grade 的 A/B 两级，列按 months 的 6/12 期，四格 limit 分别为 8000、6000、5000、3000。', '核对行列维度及交点的输出字段', [
    ['A 级六期', { grade: 'A', months: 6 }, { limit: 8000 }], ['B 级十二期', { grade: 'B', months: 12 }, { limit: 3000 }],
  ]],
  CROSS_ADV: ['行维度 score 达到 600 为高档，列维度 income 达到 5000 为高档。双高、仅行高和其他组合的结果分别为 limit=10000、5000、2000。', '检查维度区间的开闭及命中结果格', [
    ['两维同时触边', { score: 600, income: 5000 }, { limit: 10000 }], ['列在下界外', { score: 600, income: 4999 }, { limit: 5000 }], ['行在下界外', { score: 599, income: 5000 }, { limit: 2000 }],
  ]],
  SCORE: ['score 初始分为 500。income >= 5000 时加 50 分，权重为 2。', '核对初始分、评分项命中及加权贡献', [
    ['恰好达到阈值', { income: 5000 }, { score: 600 }], ['未命中取初始分', { income: 4999 }, { score: 500 }],
  ]],
  SCORE_ADV: ['score 初始为 300。收入维度 income >= 5000 加 40 分、权重 2，历史维度 overdue = 0 加 20 分、权重 1。', '分别核对收入和历史维度贡献，再核对总分', [
    ['两项贡献', { income: 5000, overdue: 0 }, { score: 400 }], ['仅历史贡献', { income: 4999, overdue: 0 }, { score: 320 }], ['两项均未命中', { income: 4999, overdue: 1 }, { score: 300 }],
  ]],
  SCRIPT: ['通过字段选择器插入 amount。在 QL 中返回 amount >= 1000 的布尔结果。', '核对字段引用 ID、实际入参及 QL 返回值类型', [
    ['达到额度', { amount: 1000 }, true], ['低于额度', { amount: 999 }, false],
  ]],
}
export default examples
