const fs = require('node:fs')
const path = require('node:path')
const { createDocsApiData } = require('../../rule-engine-builder-ui/tests/e2e/support/docsFixtures.cjs')
const routes = createDocsApiData()
const slugs = ['table', 'tree', 'flow', 'ruleset', 'cross', 'score', 'cross-adv', 'score-adv', 'script']
const examples = slugs.map((slug, index) => {
  const id = 101 + index
  const definition = routes.get(`/api/rule/definition/${id}`)
  const model = JSON.parse(routes.get(`/api/rule/definition/content/${id}`).modelJson)
  const assign = (refId, code, type, value) => ({
    type: 'assign', target: code, _varId: refId, _refType: 'VARIABLE', targetOperand: { kind: 'REFERENCE', refId, refType: 'VARIABLE', value: code, code, valueType: type, resolved: true },
    valueOperand: { kind: 'LITERAL', value, valueType: type }
  })
  if (slug === 'tree' || slug === 'flow') {
    model.nodes = [
      { id: 'start', type: 'start', name: '开始', x: 100, y: 270 },
      { id: 'check', type: 'decision', name: '活体与相似度判断', x: 330, y: 270 },
      { id: 'pass', type: 'task', name: '核验通过 · 低风险', x: 600, y: 160, actionData: [assign(4, 'verified', 'BOOLEAN', true), assign(5, 'riskLevel', 'STRING', 'LOW')] },
      { id: 'review', type: 'task', name: '转人工复核', x: 600, y: 410, actionData: [assign(4, 'verified', 'BOOLEAN', false), assign(5, 'riskLevel', 'STRING', 'REVIEW')] },
      { id: 'end', type: 'end', name: '结束', x: 860, y: 160, terminationScope: 'CURRENT_BRANCH' }
    ]
    model.edges = [
      { source: 'start', target: 'check' },
      { source: 'check', target: 'pass', conditionExpression: 'livenessScore >= 0.95 && faceSimilarity >= 0.90' },
      { source: 'check', target: 'review', conditionExpression: 'livenessScore < 0.95 || faceSimilarity < 0.90' },
      { source: 'pass', target: 'end' }
    ]
    if (slug === 'tree') {
      model.nodes.push({ id: 'end-review', type: 'end', name: '复核结束', x: 860, y: 410, terminationScope: 'CURRENT_BRANCH' })
      model.edges.push({ source: 'review', target: 'end-review' })
    } else {
      model.nodes.find(node => node.id === 'end').y = 270
      model.edges.push({ source: 'review', target: 'end' })
    }
  }
  if (slug === 'script') model.script += '\n_result = {"verified": verified, "riskLevel": riskLevel};\nreturn _result;'
  if (slug === 'score') {
    model.resultVar = { varCode: 'totalScore', varLabel: '核验总分', varType: 'NUMBER', _varId: 15, _refType: 'VARIABLE', operand: { kind: 'REFERENCE', refId: 15, refType: 'VARIABLE', value: 'totalScore', code: 'totalScore', valueType: 'NUMBER', resolved: true } }
  }
  // Keep a score output numeric and the 100-point boundary inside the LOW grade.
  if (slug === 'score-adv') {
    model.resultVar = JSON.parse(routes.get('/api/rule/definition/content/106').modelJson).resultVar
    model.thresholds[2].max = 101
  }
  const params = { livenessScore: slug === 'cross' ? 0.95 : 0.98 }
  if (['cross', 'cross-adv'].includes(slug)) params.riskLevel = 'LOW'
  else if (!['score', 'score-adv'].includes(slug)) params.faceSimilarity = 0.936
  const expected = {
    table: { verified: true, riskLevel: 'LOW' }, tree: { verified: true, riskLevel: 'LOW' }, flow: { verified: true, riskLevel: 'LOW' },
    ruleset: [{ ruleCode: 'FACE_LIVENESS_PASS', ruleName: '活体检测通过', priority: 10, order: 1 }],
    cross: { verified: true }, score: { totalScore: 60 }, 'cross-adv': { verified: true },
    'score-adv': { faceQualityScore: 100, riskLevel: 'LOW' }, script: { verified: true, riskLevel: 'LOW' }
  }
  return {
    ...definition, slug, modelJson: JSON.stringify(model),
    params, expectedOutput: expected[slug]
  }
})
const out = path.resolve(__dirname, '../../rule-engine-core/target/docs')
fs.mkdirSync(out, { recursive: true })
fs.writeFileSync(path.join(out, 'examples.json'), JSON.stringify(examples, null, 2))
