const { createManagementApiData } = require('./managementFixtures.cjs')

function createOperationsApiData() {
  const routes = createManagementApiData()

  routes.set('/api/rule/definition/project-list/1', {
    records: [
      {
        id: 101,
        projectId: 1,
        ruleCode: 'age_rule',
        ruleName: '年龄判断规则',
        modelType: 'TABLE',
        status: 1,
        inputFields: [
          {
            varId: 1,
            refType: 'VARIABLE',
            fieldName: 'age',
            scriptName: 'age',
            fieldType: 'INTEGER'
          }
        ],
        outputFields: []
      }
    ],
    total: 1
  })
  routes.set('/api/rule/variable/project/1', [
    {
      id: 1,
      projectId: 1,
      varCode: 'age',
      varLabel: '年龄',
      scriptName: 'age',
      varType: 'INTEGER',
      varSource: 'INPUT',
      status: 1
    }
  ])
  routes.set('/api/rule/dataobject/tree/1', [])
  routes.set('/api/rule/model/project/1/all', [])

  routes.set('/api/rule/function/list', {
    records: [
      {
        id: 51,
        projectId: 1,
        projectName: 'E2E 项目',
        scope: 'PROJECT',
        funcCode: 'calcRisk',
        funcName: '风险分计算',
        returnType: 'NUMBER',
        implType: 'SCRIPT',
        implScript: 'return amount * 2;',
        paramsJson: '[{"name":"amount","type":"NUMBER","example":10}]',
        status: 1,
        updateTime: '2026-07-23 12:00:00'
      }
    ],
    total: 1
  })
  routes.set('POST /api/rule/function/51/test', {
    success: true,
    result: 20,
    executeTimeMs: 2
  })
  routes.set('/api/rule/function/project/1/all', [
    {
      id: 51,
      funcCode: 'calcRisk',
      funcName: '风险分计算',
      returnType: 'NUMBER',
      paramsJson: '[{"name":"amount","type":"NUMBER"}]',
      status: 1
    }
  ])

  routes.set('/api/rule/definition/content/101', {
    definitionId: 101,
    modelJson: '{}',
    scriptMode: 'visual'
  })
  routes.set('/api/rule/definition/inputFields/101', [
    {
      id: 1001,
      definitionId: 101,
      varId: 1,
      refType: 'VARIABLE',
      fieldName: 'age',
      fieldLabel: '年龄',
      scriptName: 'age',
      fieldType: 'INTEGER',
      defaultValue: '18'
    }
  ])
  routes.set('POST /api/rule/definition/refreshFields/101', {})
  routes.set('POST /api/rule/test-schema', {
    inputs: [
      {
        refId: 1,
        refType: 'VARIABLE',
        code: 'age',
        label: '年龄',
        scriptName: 'age',
        valueType: 'INTEGER',
        defaultValue: 18
      }
    ],
    outputs: [],
    sampleParams: { age: 18 },
    diagnostics: []
  })
  routes.set('/api/rule/definition/101/api-scenarios', [])
  routes.set('POST /api/rule/definition/execute', {
    success: true,
    result: { approved: true, score: 88 },
    executeTimeMs: 6,
    traces: []
  })

  routes.set('/api/rule/lineage/options', [
    {
      id: 1,
      type: 'VARIABLE',
      code: 'age',
      label: '年龄',
      displayName: '年龄 (age)'
    }
  ])
  routes.set('/api/rule/lineage/graph', {
    startNode: {
      id: 'VARIABLE:1',
      refId: 1,
      type: 'VARIABLE',
      code: 'age',
      label: '年龄',
      hasUpstream: false,
      hasDownstream: true
    },
    nodes: [
      {
        id: 'VARIABLE:1',
        refId: 1,
        type: 'VARIABLE',
        code: 'age',
        label: '年龄',
        hasUpstream: false,
        hasDownstream: true
      },
      {
        id: 'RULE:101',
        refId: 101,
        type: 'RULE',
        code: 'age_rule',
        label: '年判断规则',
        hasUpstream: false,
        hasDownstream: false
      }
    ],
    edges: [
      { from: 'VARIABLE:1', to: 'RULE:101', label: '规则输入' }
    ]
  })

  routes.set('/api/rule/experiment/list', {
    records: [
      {
        id: 61,
        projectId: 1,
        projectCode: 'e2e_project',
        projectName: 'E2E 项目',
        experimentCode: 'risk_ab',
        experimentName: '风险策略实验',
        routingMode: 'RATIO',
        testRoutingMode: 'CONDITION',
        status: 1,
        groups: [
          {
            groupCode: 'champion',
            groupName: '冠军组',
            groupType: 'CHAMPION',
            trafficRatio: 80
          },
          {
            groupCode: 'challenger',
            groupName: '挑战组',
            groupType: 'CHALLENGER',
            trafficRatio: 20
          },
          {
            groupCode: 'shadow',
            groupName: '空跑组',
            groupType: 'TEST',
            trafficRatio: 0
          }
        ]
      }
    ],
    total: 1
  })

  routes.set('/api/rule/log/list', {
    records: [
      {
        id: 71,
        projectCode: 'e2e_project',
        ruleCode: 'age_rule',
        traceId: 'trace_rule_001',
        modelType: 'TABLE',
        source: 'SERVER',
        authType: 'API_KEY',
        authCode: 'API_MAIN',
        tokenCode: 'TOKEN_E2E',
        success: 1,
        executeTimeMs: 6,
        clientAppName: 'risk-service',
        requestParams: '{"age":18}',
        resultJson: '{"approved":true}',
        traceInfo: '',
        createTime: '2026-07-23 12:30:00'
      }
    ],
    total: 1
  })
  routes.set('/api/rule/log/rule-set-stats', {
    overview: {
      evaluationCount: 10,
      hitRate: 0.8,
      failureRate: 0.1,
      avgCostTimeMs: 5,
      p95CostTimeMs: 8,
      p99CostTimeMs: 10
    },
    ruleSets: [
      {
        ruleCode: 'risk_set',
        ruleName: '风险规则集',
        evaluationCount: 10,
        hitCount: 8,
        hitRate: 0.8,
        failureRate: 0.1,
        avgCostTimeMs: 5,
        p95CostTimeMs: 8,
        p99CostTimeMs: 10,
        items: []
      }
    ]
  })

  routes.set('/api/rule/billing/config/list', {
    records: [
      {
        id: 81,
        projectId: 1,
        projectName: 'E2E 项目',
        scope: 'PROJECT',
        billingCode: 'engine_call',
        billingName: '规则执行计费',
        billingTarget: 'ENGINE',
        targetRefId: 101,
        chargeType: 'COUNT',
        unitPrice: 0.01,
        currency: 'CNY',
        status: 1
      }
    ],
    total: 1
  })
  routes.set('/api/rule/billing/record/list', {
    records: [
      {
        id: 82,
        occurTime: '2026-07-23 12:30:00',
        projectCode: 'e2e_project',
        authCode: 'API_MAIN',
        authType: 'API_KEY',
        tokenCode: 'TOKEN_E2E',
        billingCode: 'engine_call',
        billingTarget: 'ENGINE',
        ruleCode: 'age_rule',
        success: 1,
        quantity: 1,
        amount: 0.01,
        currency: 'CNY',
        costTimeMs: 6
      }
    ],
    total: 1
  })
  routes.set('/api/rule/billing/summary/list', {
    records: [
      {
        id: 83,
        summaryDate: '2026-07-23',
        projectCode: 'e2e_project',
        authCode: 'API_MAIN',
        authType: 'API_KEY',
        billingCode: 'engine_call',
        billingTarget: 'ENGINE',
        totalCount: 10,
        successCount: 9,
        failCount: 1,
        totalQuantity: 10,
        totalAmount: 0.1,
        currency: 'CNY',
        avgCostTimeMs: 6
      }
    ],
    total: 1
  })

  return routes
}

module.exports = { createOperationsApiData }
