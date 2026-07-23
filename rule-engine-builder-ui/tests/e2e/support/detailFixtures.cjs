const { createOperationsApiData } = require('./operationsFixtures.cjs')

const project = {
  id: 1,
  projectCode: 'e2e_project',
  projectName: 'E2E 项目',
  description: '浏览器验收项目',
  status: 1,
  createTime: '2026-07-23 09:00:00',
  updateTime: '2026-07-23 12:00:00'
}

const definition = {
  id: 101,
  projectId: 1,
  projectCode: 'e2e_project',
  projectName: 'E2E 项目',
  ruleCode: 'age_rule',
  ruleName: '年龄判断规则',
  modelType: 'TABLE',
  scope: 'PROJECT',
  currentVersion: 2,
  publishedVersion: 1,
  status: 1,
  inputFields: [
    {
      id: 1001,
      varId: 1,
      refType: 'VARIABLE',
      fieldName: 'age',
      fieldLabel: '年龄',
      scriptName: 'age',
      fieldType: 'INTEGER'
    }
  ],
  outputFields: [
    {
      id: 1002,
      varId: 2,
      refType: 'VARIABLE',
      fieldName: 'approved',
      fieldLabel: '是否通过',
      scriptName: 'approved',
      fieldType: 'BOOLEAN'
    }
  ],
  createTime: '2026-07-23 09:30:00',
  updateTime: '2026-07-23 12:00:00'
}

function createDetailApiData() {
  const routes = createOperationsApiData()

  routes.set('/api/rule/project/1', project)
  routes.set('/api/rule/definition/project-list/1', {
    records: [definition],
    total: 1
  })

  routes.set('/api/rule/definition/detail/101', {
    ...definition,
    inputFieldsJson: [
      {
        id: 1001,
        definitionId: 101,
        varId: 1,
        refType: 'VARIABLE',
        fieldName: 'age',
        fieldLabel: '年龄',
        scriptName: 'age',
        fieldType: 'INTEGER',
        defaultValue: '18',
        validationRuleIds: '[]',
        updateTime: '2026-07-23 12:00:00'
      }
    ],
    outputFieldsJson: [
      {
        id: 1002,
        definitionId: 101,
        varId: 2,
        refType: 'VARIABLE',
        fieldName: 'approved',
        fieldLabel: '是否通过',
        scriptName: 'approved',
        fieldType: 'BOOLEAN',
        updateTime: '2026-07-23 12:00:00'
      }
    ]
  })
  routes.set('/api/rule/field-validation/available', [])
  routes.set('/api/rule/definition/101/revisions', [
    {
      id: 2001,
      definitionId: 101,
      revisionNo: 2,
      state: 'DRAFT',
      createTime: '2026-07-23 12:00:00'
    }
  ])
  routes.set('/api/rule/definition/101/revisions/timeline', [])
  routes.set('/api/rule/dataobject/tree/1', [])
  routes.set('/api/rule/dataobject/tree/0', [])
  routes.set('/api/rule/dataobject/project/1', [])
  routes.set('/api/rule/model/project/1/all', [])
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
    },
    {
      id: 2,
      projectId: 1,
      varCode: 'approved',
      varLabel: '是否通过',
      scriptName: 'approved',
      varType: 'BOOLEAN',
      varSource: 'INPUT',
      status: 1
    }
  ])

  routes.set('/api/rule/list/library/9', {
    id: 9,
    projectId: 1,
    projectCode: 'e2e_project',
    projectName: 'E2E 项目',
    listCode: 'mobile_black',
    listName: '手机号黑名单',
    listType: 'BLACK',
    status: 1,
    recordCount: 1
  })
  routes.set('/api/rule/list/9/record', {
    records: [
      {
        id: 901,
        listId: 9,
        itemContent: '13800138000',
        itemType: 'PHONE',
        status: 1,
        reason: '浏览器验收数据',
        createTime: '2026-07-23 12:00:00'
      }
    ],
    total: 1
  })
  routes.set('/api/rule/list/9/log', {
    records: [
      {
        id: 902,
        listId: 9,
        itemContent: '13800138000',
        itemType: 'PHONE',
        operation: 'INSERT',
        operator: 'e2e',
        createTime: '2026-07-23 12:00:00'
      }
    ],
    total: 1
  })

  routes.set('/api/rule/datasource/21', {
    id: 21,
    projectId: 1,
    projectName: 'E2E 项目',
    scope: 'PROJECT',
    datasourceCode: 'credit_vendor',
    datasourceName: '征信供应商',
    providerName: 'E2E Vendor',
    authType: 'API_KEY',
    baseUrl: 'https://api.example.test',
    protocol: 'HTTPS',
    tokenCacheSeconds: 300,
    status: 1
  })
  routes.set('/api/rule/datasource/api-config/22', {
    id: 22,
    datasourceId: 21,
    datasourceCode: 'credit_vendor',
    datasourceName: '征信供应商',
    apiCode: 'credit_query',
    apiName: '征信查询',
    requestMethod: 'POST',
    endpointUrl: '/v1/credit/query',
    contentType: 'application/json',
    requestMode: 'SYNC',
    authMode: 'INHERIT',
    timeoutMs: 3000,
    retryCount: 1,
    status: 1
  })

  routes.set('/api/rule/database/31', {
    id: 31,
    projectId: 1,
    projectName: 'E2E 项目',
    scope: 'PROJECT',
    datasourceCode: 'risk_mysql',
    datasourceName: '风控只读库',
    dbType: 'MYSQL',
    driverClassName: 'com.mysql.cj.jdbc.Driver',
    connectionMode: 'DIRECT',
    host: 'mysql.example.test',
    port: 3306,
    databaseName: 'risk',
    jdbcUrl: 'jdbc:mysql://mysql.example.test:3306/risk',
    username: 'readonly',
    minIdle: 1,
    maxPoolSize: 5,
    validationQuery: 'SELECT 1',
    status: 1
  })

  routes.set('/api/rule/model/41', {
    id: 41,
    projectId: 1,
    projectName: 'E2E 项目',
    scope: 'PROJECT',
    modelCode: 'credit_score',
    modelName: '信用评分模型',
    modelType: 'XGBOOST',
    modelFormat: 'PMML',
    fileName: 'credit_score.pmml',
    fileSize: 2048,
    fileDigest: 'sha256:e2e',
    currentVersion: 2,
    publishedVersion: 1,
    executionTimeoutMs: 120000,
    status: 1,
    inputFields: [
      {
        id: 4101,
        modelId: 41,
        fieldName: 'age',
        fieldLabel: '年龄',
        fieldType: 'INTEGER',
        varId: 1,
        refType: 'VARIABLE',
        scriptName: 'age'
      }
    ],
    outputFields: [
      {
        id: 4102,
        modelId: 41,
        fieldName: 'riskScore',
        fieldLabel: '风险分',
        fieldType: 'NUMBER',
        varId: 2,
        refType: 'VARIABLE',
        scriptName: 'riskScore'
      }
    ]
  })

  routes.set('/api/rule/experiment/61', {
    id: 61,
    projectId: 1,
    projectCode: 'e2e_project',
    projectName: 'E2E 项目',
    experimentCode: 'risk_ab',
    experimentName: '风险策略实验',
    description: '验证挑战规则效果',
    routingMode: 'RATIO',
    testRoutingMode: 'CONDITION',
    status: 1,
    groups: [
      {
        id: 6101,
        groupCode: 'champion',
        groupName: '冠军组',
        groupType: 'CHAMPION',
        trafficRatio: 80,
        actionType: 'RULE',
        ruleId: 101
      },
      {
        id: 6102,
        groupCode: 'challenger',
        groupName: '挑战组',
        groupType: 'CHALLENGER',
        trafficRatio: 20,
        actionType: 'RULE',
        ruleId: 101
      }
    ]
  })
  routes.set('/api/rule/experiment/logs', { records: [], total: 0 })

  return routes
}

module.exports = { createDetailApiData }
