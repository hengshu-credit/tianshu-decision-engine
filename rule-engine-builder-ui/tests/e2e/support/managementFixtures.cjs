const projects = [
  {
    id: 1,
    projectCode: 'e2e_project',
    projectName: 'E2E 项目',
    description: '浏览器验收项目',
    status: 1,
    createTime: '2026-07-23 09:00:00'
  }
]

const variables = [
  {
    id: 1,
    varCode: 'age',
    varLabel: '年龄',
    varType: 'STRING',
    varSource: 'INPUT',
    scope: 'PROJECT',
    projectId: 1,
    projectName: 'E2E 项目',
    scriptName: 'age',
    status: 1,
    defaultValue: '18'
  },
  {
    id: 2,
    varCode: 'income',
    varLabel: '收入',
    varType: 'NUMBER',
    varSource: 'INPUT',
    scope: 'PROJECT',
    projectId: 1,
    projectName: 'E2E 项目',
    scriptName: 'income',
    status: 1
  }
]

const constants = [
  {
    id: 3,
    varCode: 'MAX_AGE',
    varLabel: '最大年龄',
    varType: 'NUMBER',
    varSource: 'CONSTANT',
    scope: 'PROJECT',
    projectId: 1,
    projectName: 'E2E 项目',
    scriptName: 'MAX_AGE',
    defaultValue: '65',
    status: 1
  }
]

function createManagementApiData() {
  return new Map([
    ['/api/rule/project/list', { records: projects, total: projects.length }],
    ['/api/rule/definition/list', {
      records: [
        {
          id: 101,
          ruleName: '年龄判断规则',
          ruleCode: 'age_rule',
          modelType: 'TABLE',
          scope: 'PROJECT',
          projectId: 1,
          projectName: 'E2E 项目',
          status: 1,
          currentVersion: 2,
          publishedVersion: 1,
          updateTime: '2026-07-23 09:30:00'
        }
      ],
      total: 1
    }],
    ['/api/rule/variable/list', ({ url }) => {
      const rows = url.searchParams.get('varSource') === 'CONSTANT'
        ? constants
        : variables
      return { records: rows, total: rows.length }
    }],
    ['/api/rule/dataobject/tree', {
      tree: [
        {
          object: {
            id: 10,
            projectId: 1,
            projectName: 'E2E 项目',
            objectCode: 'TaxRequest',
            objectLabel: '税务请求',
            scriptName: 'TaxRequest',
            scope: 'PROJECT',
            objectType: 'INPUT',
            sourceType: 'MANUAL',
            status: 1
          },
          variables: [
            {
              id: 4,
              objectId: 10,
              projectId: 1,
              varCode: 'amount',
              varLabel: '金额',
              scriptName: 'amount',
              varType: 'NUMBER',
              status: 1,
              sortOrder: 0,
              objectField: true
            }
          ]
        }
      ],
      objectIdMap: { 10: 'TaxRequest' }
    }],
    ['/api/rule/field-validation/list', {
      records: [
        {
          id: 11,
          scope: 'GLOBAL',
          projectId: 0,
          validationCode: 'mobile_required',
          validationName: '手机号必填',
          validationType: 'REQUIRED',
          validationValue: '',
          errorMessage: '手机号不能为空',
          status: 1
        }
      ],
      total: 1
    }],
    ['/api/rule/list/library', {
      records: [
        {
          id: 9,
          projectId: 1,
          projectName: 'E2E 项目',
          listCode: 'mobile_black',
          listName: '手机号黑名单',
          listType: 'BLACK',
          status: 1,
          recordCount: 2,
          createTime: '2026-07-23 10:00:00'
        }
      ],
      total: 1
    }],
    ['/api/rule/datasource/list', {
      records: [
        {
          id: 21,
          projectId: 1,
          projectName: 'E2E 项目',
          scope: 'PROJECT',
          datasourceCode: 'credit_vendor',
          datasourceName: '征信供应商',
          providerName: 'E2E Vendor',
          protocol: 'HTTPS',
          baseUrl: 'https://api.example.test',
          authType: 'API_KEY',
          status: 1
        }
      ],
      total: 1
    }],
    ['/api/rule/datasource/api-config/list', {
      records: [
        {
          id: 22,
          datasourceId: 21,
          datasourceCode: 'credit_vendor',
          apiCode: 'credit_query',
          apiName: '征信查询',
          requestMethod: 'POST',
          endpointUrl: '/v1/credit/query',
          requestMode: 'SYNC',
          authMode: 'INHERIT',
          timeoutMs: 3000,
          retryCount: 1,
          responseCacheSeconds: 60,
          status: 1
        }
      ],
      total: 1
    }],
    ['/api/rule/dataobject/project/0', []],
    ['/api/rule/database/list', {
      records: [
        {
          id: 31,
          projectId: 1,
          projectName: 'E2E 项目',
          scope: 'PROJECT',
          datasourceCode: 'risk_mysql',
          datasourceName: '风控只读库',
          dbType: 'MYSQL',
          connectionMode: 'DIRECT',
          host: 'mysql.example.test',
          port: 3306,
          databaseName: 'risk',
          jdbcUrl: 'jdbc:mysql://mysql.example.test:3306/risk',
          minIdle: 1,
          maxPoolSize: 5,
          status: 1
        }
      ],
      total: 1
    }],
    ['/api/rule/model/list', {
      records: [
        {
          id: 41,
          projectId: 1,
          projectName: 'E2E 项目',
          scope: 'PROJECT',
          modelCode: 'credit_score',
          modelName: '信用评分模型',
          modelType: 'XGBOOST',
          modelFormat: 'PMML',
          currentVersion: 2,
          publishedVersion: 1,
          status: 1,
          inputFieldCount: 5,
          outputFieldCount: 2,
          executionTimeoutMs: 120000
        }
      ],
      total: 1
    }],
    ['/api/rule/model/health', { healthy: true }],
    ['/api/rule/model/runtimeCapabilities', {
      onnxRuntimeVersion: '1.26.0',
      availableProviders: ['CPUExecutionProvider'],
      cudaAvailable: false,
      cudaError: null
    }],
    ['/api/rule/runtime-log/list', ({ url }) => {
      const moduleType = url.searchParams.get('moduleType') || 'DATASOURCE'
      const profiles = {
        DATASOURCE: {
          actionType: 'API_INVOKE',
          targetCode: 'credit_query',
          targetName: '征信查询',
          requestMethod: 'POST',
          requestUrl: '/v1/credit/query'
        },
        DATABASE: {
          actionType: 'QUERY',
          targetCode: 'risk_mysql',
          targetName: '风控只读库',
          requestMethod: 'SELECT'
        },
        MODEL: {
          actionType: 'MODEL_EXECUTE',
          targetCode: 'credit_score',
          targetName: '信用评分模型',
          projectCode: 'e2e_project'
        }
      }
      const profile = profiles[moduleType] || profiles.DATASOURCE
      return {
        records: [{
          id: 91,
          moduleType,
          ...profile,
          traceId: `trace_${moduleType.toLowerCase()}`,
          success: 1,
          costTimeMs: 18,
          createTime: '2026-07-23 11:00:00'
        }],
        total: 1
      }
    }],
    ['/api/rule/runtime-log/external-api-stats', {
      overview: {
        queryCount: 10,
        cacheHitRate: 0.4,
        requestSuccessRate: 0.9,
        failureRate: 0.1,
        foundRate: 0.8,
        avgCostTimeMs: 18,
        p95CostTimeMs: 35,
        p99CostTimeMs: 50
      },
      providers: [{
        targetCode: 'credit_query',
        targetName: '征信查询',
        queryCount: 10,
        cacheHitRate: 0.4,
        requestSuccessRate: 0.9,
        failureRate: 0.1,
        foundRate: 0.8,
        p95CostTimeMs: 35,
        p99CostTimeMs: 50
      }]
    }]
  ])
}

module.exports = { createManagementApiData }
