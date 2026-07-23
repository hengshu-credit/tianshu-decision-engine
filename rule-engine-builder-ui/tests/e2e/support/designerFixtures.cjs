function createDesignerApiData() {
  const routes = new Map([
    ['/api/rule/definition/project-list/1', { records: [], total: 0 }],
    ['/api/rule/variable/project/1', [
      {
        id: 1,
        varCode: 'age',
        varLabel: '年龄',
        varType: 'Integer',
        varSource: 'INPUT',
        scriptName: 'age'
      },
      {
        id: 2,
        varCode: 'income',
        varLabel: '收入',
        varType: 'NUMBER',
        varSource: 'INPUT',
        scriptName: 'income'
      },
      {
        id: 3,
        varCode: 'MAX_AGE',
        varLabel: '最大年龄',
        varType: 'Integer',
        varSource: 'CONSTANT',
        scriptName: 'MAX_AGE'
      }
    ]],
    ['/api/rule/dataobject/tree/1', [
      {
        object: {
          id: 10,
          objectCode: 'TaxRequest',
          objectLabel: '税务请求',
          scriptName: 'TaxRequest'
        },
        variables: [
          {
            id: 4,
            varCode: 'amount',
            varLabel: '金额',
            varType: 'NUMBER',
            varSource: 'OBJECT',
            scriptName: 'amount',
            objectField: true
          }
        ]
      }
    ]],
    ['/api/rule/function/project/1/all', []],
    ['/api/rule/model/project/1/all', [
      {
        id: 20,
        modelCode: 'creditModel',
        modelName: '信用模型',
        modelType: 'LR',
        status: 1,
        outputFields: [
          {
            id: 201,
            modelId: 20,
            fieldName: 'score',
            scriptName: 'score',
            fieldLabel: '评分',
            fieldType: 'NUMBER'
          }
        ]
      }
    ]],
    ['/api/rule/list/library', { records: [], total: 0 }],
    ['POST /api/rule/expression/compile', {
      success: true,
      compiledScript: 'age'
    }],
    ['POST /api/rule/expression/schema', {
      inputs: [
        {
          refType: 'VARIABLE',
          refId: 1,
          scriptName: 'age',
          label: '年龄',
          valueType: 'INTEGER'
        }
      ],
      runtimeNodes: [],
      sampleParams: { age: 18 },
      diagnostics: []
    }],
    ['POST /api/rule/expression/test', {
      success: true,
      result: 18,
      executeTimeMs: 3
    }]
  ])

  const definitions = [
    { id: 101, modelType: 'TABLE', ruleCode: 'decision_table' },
    { id: 102, modelType: 'TREE', ruleCode: 'decision_tree' },
    { id: 103, modelType: 'FLOW', ruleCode: 'decision_flow' },
    { id: 104, modelType: 'RULE_SET', ruleCode: 'rule_set' },
    { id: 105, modelType: 'CROSS', ruleCode: 'cross_table' },
    { id: 106, modelType: 'SCORE', ruleCode: 'scorecard' },
    { id: 107, modelType: 'CROSS_ADV', ruleCode: 'advanced_cross_table' },
    { id: 108, modelType: 'SCORE_ADV', ruleCode: 'advanced_scorecard' },
    { id: 109, modelType: 'SCRIPT', ruleCode: 'ql_script' }
  ]
  for (const definition of definitions) {
    routes.set(`/api/rule/definition/${definition.id}`, {
      ...definition,
      projectId: 1,
      ruleName: `E2E ${definition.ruleCode}`,
      scope: 'PROJECT',
      status: 0
    })
    routes.set(`/api/rule/definition/content/${definition.id}`, {
      definitionId: definition.id,
      modelJson: '{}',
      scriptMode: definition.modelType === 'SCRIPT' ? 'script' : 'visual'
    })
  }

  return routes
}

module.exports = { createDesignerApiData }
