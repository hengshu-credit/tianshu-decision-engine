const { createDetailApiData } = require('./detailFixtures.cjs')
const { createDesignerApiData } = require('./designerFixtures.cjs')

function assignNode(target, value) {
  return {
    type: 'OPERATOR',
    token: '=',
    evaluated: true,
    value,
    children: [
      { type: 'VARIABLE', token: target, evaluated: true, value },
      { type: 'VALUE', token: String(value), evaluated: true, value }
    ]
  }
}

function referenceOperand(refId, code, label, valueType) {
  return {
    kind: 'REFERENCE',
    value: code,
    code,
    label,
    valueType,
    refId,
    refType: 'VARIABLE',
    resolved: true
  }
}

function literalOperand(value, valueType) {
  return {
    kind: 'LITERAL',
    value,
    code: String(value),
    label: String(value),
    valueType,
    resolved: true
  }
}

function conditionLeaf(leftOperand, operator, rightOperand) {
  return {
    id: `leaf-${leftOperand.refId}-${operator}`,
    type: 'leaf',
    leftOperand,
    operator,
    rightOperand
  }
}

function conditionRoot(...children) {
  return {
    id: `group-${children.length}-${children[0] && children[0].id}`,
    type: 'group',
    op: 'AND',
    children
  }
}

function createFaceTrace() {
  return {
    schemaVersion: 2,
    traceKind: 'RULE',
    traceId: 'FACE202607240930000000000000000001',
    ruleCode: 'face_identity_rule',
    ruleName: '人脸识别核验',
    modelType: 'FLOW',
    status: 'SUCCESS',
    durationMs: 128,
    revisionId: 2003,
    artifactDigest: 'sha256:face-demo-8f04b5c1',
    events: [
      {
        type: 'MODULE_CALL',
        moduleType: 'EXTERNAL_API',
        resourceCode: 'face_liveness_check',
        resourceName: '人脸活体检测',
        traceId: 'FACE-API-001',
        status: 'SUCCESS',
        durationMs: 46
      },
      {
        type: 'MODULE_CALL',
        moduleType: 'MODEL',
        resourceCode: 'face_embedding_v3',
        resourceName: '人脸特征比对模型',
        traceId: 'FACE-MODEL-001',
        status: 'SUCCESS',
        durationMs: 67
      }
    ],
    expressionTrace: [
      assignNode('livenessScore', 0.982),
      assignNode('faceSimilarity', 0.936),
      assignNode('verified', true),
      assignNode('riskLevel', 'LOW')
    ],
    children: []
  }
}

function createDocsApiData() {
  const routes = createDetailApiData()
  for (const [key, value] of createDesignerApiData()) routes.set(key, value)

  const project = {
    id: 1,
    projectCode: 'face_risk_center',
    projectName: '人脸风控中心',
    description: '统一管理人脸活体、证件一致性与风险分层决策',
    status: 1,
    createTime: '2026-07-01 09:00:00',
    updateTime: '2026-07-24 09:30:00'
  }
  const inputFields = [
    {
      id: 1001,
      definitionId: 101,
      varId: 1,
      refType: 'VARIABLE',
      fieldName: 'faceImageUrl',
      fieldLabel: '人脸图片地址',
      scriptName: 'faceImageUrl',
      fieldType: 'STRING',
      defaultValue: 'https://example.test/face/demo.jpg',
      validationRuleIds: '[11]'
    },
    {
      id: 1002,
      definitionId: 101,
      varId: 2,
      refType: 'VARIABLE',
      fieldName: 'idCardNumber',
      fieldLabel: '证件号码',
      scriptName: 'idCardNumber',
      fieldType: 'STRING',
      defaultValue: '110101199001011234',
      validationRuleIds: '[12]'
    },
    {
      id: 1003,
      definitionId: 101,
      varId: 3,
      refType: 'VARIABLE',
      fieldName: 'livenessScore',
      fieldLabel: '活体检测分',
      scriptName: 'livenessScore',
      fieldType: 'NUMBER',
      defaultValue: '0.98',
      validationRuleIds: '[]'
    }
  ]
  const outputFields = [
    {
      id: 1004,
      definitionId: 101,
      varId: 4,
      refType: 'VARIABLE',
      fieldName: 'verified',
      fieldLabel: '核验是否通过',
      scriptName: 'verified',
      fieldType: 'BOOLEAN'
    },
    {
      id: 1005,
      definitionId: 101,
      varId: 5,
      refType: 'VARIABLE',
      fieldName: 'riskLevel',
      fieldLabel: '风险等级',
      scriptName: 'riskLevel',
      fieldType: 'STRING'
    }
  ]
  const definition = {
    id: 101,
    projectId: 1,
    projectCode: project.projectCode,
    projectName: project.projectName,
    ruleCode: 'face_identity_rule',
    ruleName: '人脸识别核验',
    description: '完成活体检测、人证一致性比对并输出统一风险等级',
    modelType: 'FLOW',
    scope: 'PROJECT',
    currentVersion: 4,
    publishedVersion: 3,
    status: 1,
    inputFields,
    outputFields,
    inputFieldsJson: inputFields,
    outputFieldsJson: outputFields,
    createTime: '2026-07-02 10:00:00',
    updateTime: '2026-07-24 09:30:00'
  }
  const variables = [
    {
      id: 1,
      projectId: 1,
      projectName: project.projectName,
      varCode: 'faceImageUrl',
      varLabel: '人脸图片地址',
      scriptName: 'faceImageUrl',
      varType: 'STRING',
      varSource: 'INPUT',
      scope: 'PROJECT',
      status: 1
    },
    {
      id: 2,
      projectId: 1,
      projectName: project.projectName,
      varCode: 'idCardNumber',
      varLabel: '证件号码',
      scriptName: 'idCardNumber',
      varType: 'STRING',
      varSource: 'INPUT',
      scope: 'PROJECT',
      status: 1
    },
    {
      id: 3,
      projectId: 1,
      projectName: project.projectName,
      varCode: 'livenessScore',
      varLabel: '活体检测分',
      scriptName: 'livenessScore',
      varType: 'NUMBER',
      varSource: 'API',
      scope: 'PROJECT',
      status: 1
    },
    {
      id: 4,
      projectId: 1,
      projectName: project.projectName,
      varCode: 'verified',
      varLabel: '核验是否通过',
      scriptName: 'verified',
      varType: 'BOOLEAN',
      varSource: 'COMPUTED',
      scope: 'PROJECT',
      status: 1
    },
    {
      id: 5,
      projectId: 1,
      projectName: project.projectName,
      varCode: 'riskLevel',
      varLabel: '风险等级',
      scriptName: 'riskLevel',
      varType: 'STRING',
      varSource: 'COMPUTED',
      scope: 'PROJECT',
      status: 1
    },
    {
      id: 13,
      projectId: 1,
      projectName: project.projectName,
      varCode: 'faceSimilarity',
      varLabel: '人脸相似度',
      scriptName: 'faceSimilarity',
      varType: 'NUMBER',
      varSource: 'MODEL',
      scope: 'PROJECT',
      status: 1
    },
    {
      id: 14,
      projectId: 1,
      projectName: project.projectName,
      varCode: 'faceQualityScore',
      varLabel: '图像质量分',
      scriptName: 'faceQualityScore',
      varType: 'NUMBER',
      varSource: 'MODEL',
      scope: 'PROJECT',
      status: 1
    }
  ]
  variables.forEach((variable) => {
    variable.updateTime = '2026-07-24 09:30:00'
  })
  const openApiContract = {
    enabled: true,
    recordTrace: true,
    returnTrace: false,
    requestMappings: [
      {
        targetRefType: 'VARIABLE',
        targetVarId: 1,
        sourceType: 'BODY',
        sourcePath: '$.faceImageUrl',
        required: true,
        defaultValue: null,
        targetType: 'STRING'
      },
      {
        targetRefType: 'VARIABLE',
        targetVarId: 2,
        sourceType: 'BODY',
        sourcePath: '$.idCardNumber',
        required: true,
        defaultValue: null,
        targetType: 'STRING'
      }
    ],
    responseMappings: [
      {
        sourceRefType: 'VARIABLE',
        sourceVarId: 4,
        targetField: 'verified'
      },
      {
        sourceRefType: 'VARIABLE',
        sourceVarId: 5,
        targetField: 'riskLevel'
      }
    ],
    envelopeTemplate: {
      success: '${status.success}',
      code: '${status.code}',
      message: '${status.message}',
      traceId: '${traceId}',
      data: '${data}'
    },
    dataPath: '$.data',
    successDataTemplate: '${response}',
    errorDataTemplate: {
      errorCode: '${status.code}',
      errorMessage: '${status.message}',
      field: '${error.field}'
    },
    responseHeaders: {
      'X-Decision-Trace-Id': '${traceId}'
    }
  }

  routes.set('/api/rule/project/list', { records: [project], total: 1 })
  routes.set('/api/rule/project/1', project)
  routes.set('/api/rule/definition/list', { records: [definition], total: 1 })
  routes.set('/api/rule/definition/project-list/1', {
    records: [definition],
    total: 1
  })
  routes.set('/api/rule/definition/detail/101', definition)
  routes.set('/api/rule/definition/content/101', {
    definitionId: 101,
    modelJson: JSON.stringify({ nodes: [], edges: [] }),
    scriptMode: 'visual',
    openApiConfigJson: JSON.stringify(openApiContract)
  })
  routes.set('/api/rule/definition/inputFields/101', inputFields)
  routes.set('/api/rule/definition/outputFields/101', outputFields)
  routes.set('/api/rule/variable/project/1', variables)
  routes.set('/api/rule/variable/list', ({ url }) => {
    const source = url.searchParams.get('varSource')
    const records = source === 'CONSTANT'
      ? [{
          id: 6,
          projectId: 1,
          projectName: project.projectName,
          varCode: 'FACE_PASS_SCORE',
          varLabel: '人脸通过阈值',
          scriptName: 'FACE_PASS_SCORE',
          varType: 'NUMBER',
          varSource: 'CONSTANT',
          scope: 'PROJECT',
          defaultValue: '0.90',
          status: 1
        }]
      : variables
    return { records, total: records.length }
  })
  routes.set('/api/rule/dataobject/tree', {
    tree: [{
      object: {
        id: 10,
        projectId: 1,
        projectName: project.projectName,
        objectCode: 'FaceVerifyRequest',
        objectLabel: '人脸核验请求',
        scriptName: 'FaceVerifyRequest',
        scope: 'PROJECT',
        objectType: 'INPUT',
        sourceType: 'MANUAL',
        status: 1
      },
      variables: [
        {
          id: 11,
          objectId: 10,
          projectId: 1,
          varCode: 'deviceId',
          varLabel: '设备编号',
          scriptName: 'deviceId',
          varType: 'STRING',
          status: 1,
          sortOrder: 0,
          objectField: true
        },
        {
          id: 12,
          objectId: 10,
          projectId: 1,
          varCode: 'channel',
          varLabel: '业务渠道',
          scriptName: 'channel',
          varType: 'STRING',
          status: 1,
          sortOrder: 1,
          objectField: true
        }
      ]
    }],
    objectIdMap: { 10: 'FaceVerifyRequest' }
  })
  routes.set('/api/rule/field-validation/list', {
    records: [
      {
        id: 11,
        scope: 'PROJECT',
        projectId: 1,
        validationCode: 'face_url_required',
        validationName: '人脸图片必填',
        validationType: 'REQUIRED',
        validationValue: '',
        errorMessage: '请上传人脸图片',
        status: 1,
        updateTime: '2026-07-24 09:25:00'
      },
      {
        id: 12,
        scope: 'PROJECT',
        projectId: 1,
        validationCode: 'id_card_format',
        validationName: '证件号码格式',
        validationType: 'REGEX',
        validationValue: '^\\d{17}[0-9Xx]$',
        errorMessage: '证件号码格式不正确',
        status: 1,
        updateTime: '2026-07-24 09:26:00'
      }
    ],
    total: 2
  })
  routes.set('/api/rule/field-validation/available', [
    {
      id: 11,
      validationCode: 'face_url_required',
      validationName: '人脸图片必填',
      validationType: 'REQUIRED'
    },
    {
      id: 12,
      validationCode: 'id_card_format',
      validationName: '证件号码格式',
      validationType: 'REGEX'
    }
  ])

  routes.set('/api/rule/project/1/auth', [
    {
      id: 301,
      authCode: 'FACE_API_MAIN',
      authName: '人脸核验生产调用方',
      authType: 'HMAC_SHA256',
      identifierMasked: 'face_ak_****8K2P',
      secretMasked: '**** **** **** 7M9Q',
      tokenTtlSeconds: 7200,
      tokenGraceSeconds: 600,
      asyncAccessLogEnabled: 1,
      accessPolicyJson: JSON.stringify({
        ipWhitelist: ['10.20.0.0/16'],
        hostWhitelist: ['face-api.example.com'],
        qps: 200,
        burst: 400,
        maxConcurrent: 80,
        requestTimeoutMs: 3000
      }),
      status: 1
    },
    {
      id: 302,
      authCode: 'FACE_API_UAT',
      authName: '联调验收环境',
      authType: 'API_KEY',
      identifierMasked: '',
      secretMasked: '**** **** **** UAT1',
      tokenTtlSeconds: 3600,
      tokenGraceSeconds: 300,
      asyncAccessLogEnabled: 1,
      accessPolicyJson: JSON.stringify({
        ipWhitelist: ['172.16.8.0/24'],
        hostWhitelist: [],
        qps: 30,
        burst: 60,
        maxConcurrent: 12,
        requestTimeoutMs: 5000
      }),
      status: 1
    }
  ])
  routes.set('/api/rule/project/1/auth/access-logs', {
    records: [{
      id: 3301,
      createTime: '2026-07-24 09:31:15',
      authCode: 'FACE_API_MAIN',
      authType: 'HMAC_SHA256',
      tokenCode: 'TOKEN_FACE_20260724',
      requestMethod: 'POST',
      requestUri: '/api/open/rule/face_identity_rule/execute',
      success: 1,
      failureReason: ''
    }],
    total: 1
  })
  routes.set('/api/rule/project/1/auth/301/tokens', {
    records: [{
      id: 3401,
      authId: 301,
      tokenCode: 'TOKEN_FACE_20260724',
      tokenMasked: 'eyJhbGciOiJIUzI1NiJ9.****.8K2P',
      issuedTime: '2026-07-24 08:00:00',
      expireTime: '2026-07-24 10:00:00',
      graceExpireTime: '2026-07-24 10:10:00',
      status: 1
    }],
    total: 1
  })

  routes.set('/api/rule/list/library', {
    records: [{
      id: 9,
      projectId: 1,
      projectName: project.projectName,
      listCode: 'face_device_blocklist',
      listName: '高风险设备名单',
      listType: 'BLACK',
      status: 1,
      recordCount: 128,
      createTime: '2026-07-12 10:00:00'
    }],
    total: 1
  })
  routes.set('/api/rule/datasource/list', {
    records: [{
      id: 21,
      projectId: 1,
      projectName: project.projectName,
      scope: 'PROJECT',
      datasourceCode: 'face_service_provider',
      datasourceName: '人脸识别服务商',
      providerName: '企业级生物识别平台',
      protocol: 'HTTPS',
      baseUrl: 'https://face.example.com',
      authType: 'OAUTH2',
      status: 1
    }],
    total: 1
  })
  routes.set('/api/rule/datasource/api-config/list', {
    records: [{
      id: 22,
      datasourceId: 21,
      datasourceCode: 'face_service_provider',
      apiCode: 'face_liveness_check',
      apiName: '人脸活体与一致性检测',
      requestMethod: 'POST',
      endpointUrl: '/v3/face/verify',
      requestMode: 'SYNC',
      authMode: 'INHERIT',
      timeoutMs: 5000,
      retryCount: 1,
      responseCacheSeconds: 0,
      status: 1
    }],
    total: 1
  })
  routes.set('/api/rule/datasource/21', {
    id: 21,
    projectId: 1,
    projectName: project.projectName,
    scope: 'PROJECT',
    datasourceCode: 'face_service_provider',
    datasourceName: '人脸识别服务商',
    providerName: '企业级生物识别平台',
    authType: 'OAUTH2',
    baseUrl: 'https://face.example.com',
    protocol: 'HTTPS',
    tokenCacheSeconds: 3300,
    authConfig: JSON.stringify({
      tokenUrl: 'https://face.example.com/oauth2/token',
      clientId: 'face-risk-console',
      clientSecret: '******',
      scope: 'face.verify'
    }),
    status: 1
  })
  routes.set('/api/rule/datasource/api-config/22', {
    id: 22,
    datasourceId: 21,
    datasourceCode: 'face_service_provider',
    datasourceName: '人脸识别服务商',
    apiCode: 'face_liveness_check',
    apiName: '人脸活体与一致性检测',
    description: '上传人脸图片并返回活体分与人证相似度',
    requestMethod: 'POST',
    endpointUrl: '/v3/face/verify',
    contentType: 'application/json',
    requestMode: 'SYNC',
    authMode: 'OAUTH2',
    authApiConfig: JSON.stringify({
      tokenUrl: 'https://face.example.com/oauth2/token',
      method: 'POST',
      contentType: 'application/json',
      headers: { 'X-Client-Channel': 'face-risk-center' },
      body: {
        grant_type: 'client_credentials',
        client_id: 'face-risk-console',
        client_secret: '******'
      },
      tokenPath: 'body.access_token',
      expiresInPath: 'body.expires_in',
      tokenPlacement: 'HEADER',
      tokenHeaderName: 'Authorization',
      tokenPrefix: 'Bearer '
    }),
    timeoutMs: 5000,
    retryCount: 1,
    responseCacheSeconds: 0,
    requestMapping: JSON.stringify({
      image_url: '$.faceImageUrl',
      id_number: '$.idCardNumber'
    }),
    responseMapping: JSON.stringify({
      livenessScore: 'body.data.liveness_score',
      faceSimilarity: 'body.data.similarity'
    }),
    status: 1
  })
  routes.set('POST /api/rule/datasource/api-config/22/request-preview', {
    method: 'POST',
    url: 'https://face.example.com/v3/face/verify',
    headers: {
      Authorization: 'Bearer ******',
      'Content-Type': 'application/json'
    },
    body: {
      image_url: 'https://example.test/face/demo.jpg',
      id_number: '110101199001011234'
    },
    note: '仅生成请求预览，未访问外部地址'
  })
  routes.set('/api/rule/runtime-log/list', ({ url }) => {
    const moduleType = url.searchParams.get('moduleType') || 'DATASOURCE'
    const targetCode = moduleType === 'MODEL'
      ? 'face_embedding_v3'
      : moduleType === 'DATABASE'
        ? 'face_audit_mysql'
        : 'face_liveness_check'
    return {
      records: [{
        id: 91,
        moduleType,
        actionType: moduleType === 'MODEL' ? 'MODEL_EXECUTE' : 'API_INVOKE',
        targetCode,
        targetName: moduleType === 'MODEL' ? '人脸特征比对模型' : '人脸活体与一致性检测',
        traceId: `trace_${moduleType.toLowerCase()}_face`,
        success: 1,
        costTimeMs: moduleType === 'MODEL' ? 67 : 46,
        createTime: '2026-07-24 09:31:15'
      }],
      total: 1
    }
  })
  routes.set('/api/rule/runtime-log/external-api-stats', {
    overview: {
      queryCount: 26840,
      cacheHitRate: 0,
      requestSuccessRate: 0.997,
      failureRate: 0.003,
      foundRate: 0.992,
      avgCostTimeMs: 46,
      p95CostTimeMs: 82,
      p99CostTimeMs: 126
    },
    providers: [{
      targetCode: 'face_liveness_check',
      targetName: '人脸活体与一致性检测',
      queryCount: 26840,
      cacheHitRate: 0,
      requestSuccessRate: 0.997,
      failureRate: 0.003,
      foundRate: 0.992,
      p95CostTimeMs: 82,
      p99CostTimeMs: 126
    }]
  })

  routes.set('/api/rule/database/list', {
    records: [{
      id: 31,
      projectId: 1,
      projectName: project.projectName,
      scope: 'PROJECT',
      datasourceCode: 'face_audit_mysql',
      datasourceName: '人脸核验审计只读库',
      dbType: 'MYSQL',
      connectionMode: 'DIRECT',
      host: 'face-audit-db.internal',
      port: 3306,
      databaseName: 'face_audit',
      jdbcUrl: 'jdbc:mysql://face-audit-db.internal:3306/face_audit',
      minIdle: 2,
      maxPoolSize: 12,
      status: 1
    }],
    total: 1
  })
  routes.set('/api/rule/model/list', {
    records: [{
      id: 41,
      projectId: 1,
      projectName: project.projectName,
      scope: 'PROJECT',
      modelCode: 'face_embedding_v3',
      modelName: '人脸特征比对模型 V3',
      modelType: 'NEURAL_NET',
      modelFormat: 'ONNX',
      currentVersion: 5,
      publishedVersion: 4,
      status: 1,
      inputFieldCount: 2,
      outputFieldCount: 2,
      executionTimeoutMs: 3000
    }],
    total: 1
  })
  routes.set('/api/rule/model/41', {
    id: 41,
    projectId: 1,
    projectName: project.projectName,
    scope: 'PROJECT',
    modelCode: 'face_embedding_v3',
    modelName: '人脸特征比对模型 V3',
    modelType: 'NEURAL_NET',
    modelFormat: 'ONNX',
    fileName: 'face_embedding_v3.onnx',
    fileSize: 48234496,
    fileDigest: 'sha256:face-model-399f8c2b',
    currentVersion: 5,
    publishedVersion: 4,
    executionTimeoutMs: 3000,
    status: 1,
    inputFields: [
      {
        id: 4101,
        modelId: 41,
        fieldName: 'faceImage',
        fieldLabel: '待核验人脸',
        fieldType: 'STRING',
        varId: 1,
        refType: 'VARIABLE',
        scriptName: 'faceImageUrl'
      },
      {
        id: 4102,
        modelId: 41,
        fieldName: 'idPhoto',
        fieldLabel: '证件照',
        fieldType: 'STRING',
        varId: 2,
        refType: 'VARIABLE',
        scriptName: 'idCardNumber'
      }
    ],
    outputFields: [
      {
        id: 4103,
        modelId: 41,
        fieldName: 'similarity',
        fieldLabel: '人脸相似度',
        fieldType: 'NUMBER',
        varId: 13,
        refType: 'VARIABLE',
        scriptName: 'faceSimilarity'
      },
      {
        id: 4104,
        modelId: 41,
        fieldName: 'qualityScore',
        fieldLabel: '图像质量分',
        fieldType: 'NUMBER',
        varId: 14,
        refType: 'VARIABLE',
        scriptName: 'faceQualityScore'
      }
    ]
  })
  routes.set('/api/rule/model/project/1/all', [{
    id: 41,
    modelCode: 'face_embedding_v3',
    modelName: '人脸特征比对模型 V3',
    modelType: 'NEURAL_NET',
    status: 1,
    outputFields: []
  }])
  routes.set('/api/rule/model/testParams/41', {
    faceImageUrl: 'https://example.test/face/demo.jpg',
    idCardNumber: '110101199001011234'
  })
  routes.set('POST /api/rule/model/execute/41', {
    success: true,
    result: {
      faceSimilarity: 0.936,
      faceQualityScore: 0.974,
      modelVersion: 'V3.4'
    },
    executeTimeMs: 67
  })
  routes.set('/api/rule/function/list', {
    records: [{
      id: 51,
      projectId: 1,
      projectName: project.projectName,
      scope: 'PROJECT',
      funcCode: 'normalizeFaceScore',
      funcName: '人脸分归一化',
      returnType: 'NUMBER',
      implType: 'SCRIPT',
      implScript: 'return Math.max(0, Math.min(1, score));',
      paramsJson: '[{"name":"score","type":"NUMBER","example":0.936}]',
      status: 1,
      updateTime: '2026-07-24 08:50:00'
    }],
    total: 1
  })

  const faceTrace = createFaceTrace()
  routes.set('POST /api/rule/test-schema', {
    inputs: inputFields.map((field) => ({
      refId: field.varId,
      refType: field.refType,
      code: field.fieldName,
      label: field.fieldLabel,
      scriptName: field.scriptName,
      valueType: field.fieldType,
      defaultValue: field.defaultValue
    })),
    outputs: outputFields,
    sampleParams: {
      faceImageUrl: 'https://example.test/face/demo.jpg',
      idCardNumber: '110101199001011234',
      livenessScore: 0.98
    },
    diagnostics: []
  })
  routes.set('POST /api/rule/definition/execute', {
    success: true,
    result: {
      verified: true,
      riskLevel: 'LOW',
      livenessScore: 0.982,
      faceSimilarity: 0.936
    },
    executeTimeMs: 128,
    traces: [faceTrace]
  })
  routes.set('POST /api/rule/expression/compile', {
    success: true,
    compiledScript: 'livenessScore >= 0.95'
  })
  routes.set('POST /api/rule/expression/schema', {
    inputs: [
      {
        refType: 'VARIABLE',
        refId: 3,
        scriptName: 'livenessScore',
        label: '活体检测分',
        valueType: 'NUMBER'
      }
    ],
    runtimeNodes: [],
    sampleParams: { livenessScore: 0.982 },
    diagnostics: []
  })
  routes.set('POST /api/rule/expression/test', {
    success: true,
    result: true,
    executeTimeMs: 2
  })
  routes.set('/api/rule/log/list', {
    records: [{
      id: 71,
      projectCode: project.projectCode,
      ruleCode: definition.ruleCode,
      traceId: faceTrace.traceId,
      modelType: 'FLOW',
      source: 'SERVER',
      authType: 'HMAC_SHA256',
      authCode: 'FACE_API_MAIN',
      tokenCode: 'TOKEN_FACE_20260724',
      success: 1,
      executeTimeMs: 128,
      clientAppName: 'identity-gateway',
      inputParams: JSON.stringify({
        faceImageUrl: 'https://example.test/face/demo.jpg',
        idCardNumber: '110101********1234',
        livenessScore: 0.982
      }),
      outputResult: JSON.stringify({
        verified: true,
        riskLevel: 'LOW',
        faceSimilarity: 0.936
      }),
      traceInfo: JSON.stringify([faceTrace]),
      revisionId: 2003,
      artifactDigest: 'sha256:face-demo-8f04b5c1',
      createTime: '2026-07-24 09:31:15'
    }],
    total: 1
  })

  routes.set('/api/rule/lineage/options', [{
    id: 1,
    type: 'VARIABLE',
    code: 'faceImageUrl',
    label: '人脸图片地址',
    displayName: '人脸图片地址 (faceImageUrl)'
  }])
  routes.set('/api/rule/lineage/graph', {
    startNode: {
      id: 'VARIABLE:1',
      refId: 1,
      type: 'VARIABLE',
      code: 'faceImageUrl',
      label: '人脸图片地址',
      hasUpstream: false,
      hasDownstream: true
    },
    nodes: [
      {
        id: 'VARIABLE:1',
        refId: 1,
        type: 'VARIABLE',
        code: 'faceImageUrl',
        label: '人脸图片地址',
        hasUpstream: false,
        hasDownstream: true
      },
      {
        id: 'API:22',
        refId: 22,
        type: 'API',
        code: 'face_liveness_check',
        label: '人脸活体与一致性检测',
        hasUpstream: true,
        hasDownstream: true
      },
      {
        id: 'RULE:101',
        refId: 101,
        type: 'RULE',
        code: definition.ruleCode,
        label: definition.ruleName,
        hasUpstream: true,
        hasDownstream: false
      }
    ],
    edges: [
      { from: 'VARIABLE:1', to: 'API:22', label: '请求映射' },
      { from: 'API:22', to: 'RULE:101', label: '规则输入' }
    ]
  })

  const experiment = {
    id: 61,
    projectId: 1,
    projectCode: project.projectCode,
    projectName: project.projectName,
    experimentCode: 'face_model_upgrade_ab',
    experimentName: '人脸模型 V3 灰度实验',
    description: '在生产流量中验证新模型相似度稳定性',
    routingMode: 'RATIO',
    testRoutingMode: 'CONDITION',
    status: 1,
    groups: [
      {
        id: 6101,
        groupCode: 'champion',
        groupName: 'V2 稳定策略',
        groupType: 'CHAMPION',
        trafficRatio: 90,
        actionType: 'RULE',
        ruleId: 101
      },
      {
        id: 6102,
        groupCode: 'challenger',
        groupName: 'V3 挑战策略',
        groupType: 'CHALLENGER',
        trafficRatio: 10,
        actionType: 'RULE',
        ruleId: 101
      }
    ]
  }
  routes.set('/api/rule/experiment/list', { records: [experiment], total: 1 })
  routes.set('/api/rule/experiment/61', experiment)

  routes.set('/api/rule/billing/config/list', {
    records: [
      {
        id: 81,
        projectId: 1,
        projectName: project.projectName,
        scope: 'PROJECT',
        billingCode: 'face_verify_call',
        billingName: '人脸核验调用',
        billingTarget: 'ENGINE',
        targetRefId: 101,
        chargeType: 'COUNT',
        unitPrice: 0.035,
        currency: 'CNY',
        status: 1
      },
      {
        id: 84,
        projectId: 1,
        projectName: project.projectName,
        scope: 'PROJECT',
        billingCode: 'face_provider_call',
        billingName: '人脸服务商调用',
        billingTarget: 'API',
        targetRefId: 22,
        chargeType: 'SUCCESS',
        unitPrice: 0.12,
        currency: 'CNY',
        status: 1
      }
    ],
    total: 2
  })
  routes.set('/api/rule/billing/record/list', {
    records: [{
      id: 82,
      occurTime: '2026-07-24 09:31:15',
      projectCode: project.projectCode,
      authCode: 'FACE_API_MAIN',
      authType: 'HMAC_SHA256',
      tokenCode: 'TOKEN_FACE_20260724',
      billingCode: 'face_verify_call',
      billingTarget: 'ENGINE',
      ruleCode: definition.ruleCode,
      success: 1,
      quantity: 1,
      amount: 0.035,
      currency: 'CNY',
      costTimeMs: 128
    }],
    total: 1
  })
  routes.set('/api/rule/billing/summary/list', {
    records: [{
      id: 83,
      summaryDate: '2026-07-24',
      projectCode: project.projectCode,
      authCode: 'FACE_API_MAIN',
      authType: 'HMAC_SHA256',
      billingCode: 'face_verify_call',
      billingTarget: 'ENGINE',
      totalCount: 26840,
      successCount: 26759,
      failCount: 81,
      totalQuantity: 26840,
      totalAmount: 939.4,
      currency: 'CNY',
      avgCostTimeMs: 128
    }],
    total: 1
  })

  let revisionState = 'DRAFT'
  const lifecycleEvents = [
    {
      id: 7001,
      action: 'PUBLISH',
      fromState: 'APPROVED',
      toState: 'PUBLISHED',
      actor: 'risk_admin',
      comment: '人脸模型 V2 稳定版上线',
      artifactDigest: 'sha256:face-demo-v3',
      createTime: '2026-07-18 16:20:00'
    },
    {
      id: 7002,
      action: 'CREATE_DRAFT',
      fromState: 'PUBLISHED',
      toState: 'DRAFT',
      actor: 'face_operator',
      comment: '调整活体阈值并接入人脸模型 V3',
      createTime: '2026-07-24 09:20:00'
    }
  ]
  const currentRevision = () => ({
    id: 2003,
    definitionId: 101,
    revisionNo: 4,
    state: revisionState,
    artifactId: ['APPROVED', 'PUBLISHED'].includes(revisionState) ? 9003 : null,
    artifactDigest: ['APPROVED', 'PUBLISHED'].includes(revisionState)
      ? 'sha256:face-demo-8f04b5c1'
      : '',
    createTime: '2026-07-24 09:20:00'
  })
  routes.set('/api/rule/definition/101/revisions', () => [
    currentRevision(),
    {
      id: 2002,
      definitionId: 101,
      revisionNo: 3,
      state: 'PUBLISHED',
      artifactId: 9002,
      artifactDigest: 'sha256:face-demo-v3',
      createTime: '2026-07-18 16:20:00'
    }
  ])
  routes.set('/api/rule/definition/101/revisions/timeline', () => lifecycleEvents)
  routes.set('POST /api/rule/definition/101/revisions/2003/preflight', {
    valid: true,
    breakingSchemaChange: false,
    errors: [],
    warnings: [
      {
        code: 'MODEL_VERSION_UPDATED',
        message: '人脸特征比对模型由 V2 升级为 V3，已完成字段兼容校验'
      }
    ],
    dependencyCount: 6,
    schemaChangeCount: 0
  })
  routes.set('POST /api/rule/definition/101/revisions/2003/submit', () => {
    lifecycleEvents.push({
      id: 7003,
      action: 'SUBMIT',
      fromState: 'DRAFT',
      toState: 'REVIEW',
      actor: 'face_operator',
      comment: '提交人脸模型 V3 灰度发布评审',
      createTime: '2026-07-24 09:35:00'
    })
    revisionState = 'REVIEW'
    return currentRevision()
  })
  routes.set('POST /api/rule/definition/101/revisions/2003/approve', () => {
    lifecycleEvents.push({
      id: 7004,
      action: 'APPROVE',
      fromState: 'REVIEW',
      toState: 'APPROVED',
      actor: 'risk_reviewer',
      comment: '字段契约和阈值回归验证通过',
      artifactDigest: 'sha256:face-demo-8f04b5c1',
      createTime: '2026-07-24 09:42:00'
    })
    revisionState = 'APPROVED'
    return currentRevision()
  })
  routes.set('POST /api/rule/definition/101/revisions/2003/publish', () => {
    lifecycleEvents.push({
      id: 7005,
      action: 'PUBLISH',
      fromState: 'APPROVED',
      toState: 'PUBLISHED',
      actor: 'risk_admin',
      comment: '人脸识别核验 V4 发布完成',
      artifactDigest: 'sha256:face-demo-8f04b5c1',
      createTime: '2026-07-24 09:45:00'
    })
    revisionState = 'PUBLISHED'
    return currentRevision()
  })
  routes.set('/api/rule/definition/101/api-scenarios', [{
    id: 8101,
    definitionId: 101,
    scenarioName: '活体通过且人证一致',
    description: '活体分 0.982、相似度 0.936，预期低风险通过',
    requestJson: JSON.stringify({
      faceImageUrl: 'https://example.test/face/demo.jpg',
      idCardNumber: '110101199001011234'
    }),
    responseJson: JSON.stringify({
      success: true,
      code: '000000',
      data: { verified: true, riskLevel: 'LOW' }
    }),
    outerCode: '000000',
    businessCode: 'LOW',
    responseSource: 'EXECUTED',
    includeInDoc: 1,
    ruleVersion: 4,
    status: 1,
    sortOrder: 1
  }])

  const designerNames = [
    [101, 'TABLE', 'face_threshold_table', '人脸阈值决策表'],
    [102, 'TREE', 'face_review_tree', '人脸复核决策树'],
    [103, 'FLOW', 'face_identity_flow', '人脸识别决策流'],
    [104, 'RULE_SET', 'face_risk_rules', '人脸风险规则集'],
    [105, 'CROSS', 'face_channel_matrix', '渠道风险交叉表'],
    [106, 'SCORE', 'face_quality_scorecard', '人脸质量评分卡'],
    [107, 'CROSS_ADV', 'face_device_matrix', '设备风险复杂交叉表'],
    [108, 'SCORE_ADV', 'face_fraud_scorecard', '人脸欺诈复杂评分卡'],
    [109, 'SCRIPT', 'face_fallback_script', '人脸兜底脚本']
  ]
  for (const [id, modelType, ruleCode, ruleName] of designerNames) {
    routes.set(`/api/rule/definition/${id}`, {
      id,
      projectId: 1,
      projectName: project.projectName,
      modelType,
      ruleCode,
      ruleName,
      scope: 'PROJECT',
      status: 0
    })
  }

  const livenessRef = referenceOperand(3, 'livenessScore', '活体检测分 livenessScore', 'NUMBER')
  const similarityRef = referenceOperand(13, 'faceSimilarity', '人脸相似度 faceSimilarity', 'NUMBER')
  const verifiedRef = referenceOperand(4, 'verified', '核验是否通过 verified', 'BOOLEAN')
  const riskLevelRef = referenceOperand(5, 'riskLevel', '风险等级 riskLevel', 'STRING')
  const qualityRef = referenceOperand(14, 'faceQualityScore', '图像质量分 faceQualityScore', 'NUMBER')

  routes.set('/api/rule/definition/content/101', {
    definitionId: 101,
    modelJson: JSON.stringify({
      name: '人脸阈值决策表',
      hitPolicy: 'FIRST',
      rules: [
        {
          id: 'face-pass',
          conditionRoot: conditionRoot(
            conditionLeaf(livenessRef, '>=', literalOperand(0.95, 'NUMBER')),
            conditionLeaf(similarityRef, '>=', literalOperand(0.9, 'NUMBER'))
          ),
          actions: [
            {
              id: 'set-verified',
              targetOperand: verifiedRef,
              valueOperand: literalOperand(true, 'BOOLEAN')
            },
            {
              id: 'set-risk',
              targetOperand: riskLevelRef,
              valueOperand: literalOperand('LOW', 'STRING')
            }
          ]
        }
      ]
    }),
    scriptMode: 'visual',
    openApiConfigJson: JSON.stringify(openApiContract)
  })

  const reviewTree = {
    defaultEdgeLineType: 'polyline',
    nodes: [
      { id: 'tree-start', type: 'start', name: '开始', x: 150, y: 280 },
      { id: 'tree-check', type: 'decision', name: '活体分判断', x: 430, y: 280 },
      { id: 'tree-review', type: 'task', name: '进入人工复核', x: 710, y: 150, actionData: [] },
      { id: 'tree-pass', type: 'end', name: '核验通过', x: 990, y: 150, terminationScope: 'CURRENT_BRANCH' },
      { id: 'tree-reject', type: 'end', name: '拒绝交易', x: 710, y: 430, terminationScope: 'CURRENT_BRANCH' }
    ],
    edges: [
      { source: 'tree-start', target: 'tree-check', conditionExpression: '' },
      { source: 'tree-check', target: 'tree-review', conditionExpression: 'livenessScore >= 0.95' },
      { source: 'tree-review', target: 'tree-pass', conditionExpression: 'faceSimilarity >= 0.90' },
      { source: 'tree-check', target: 'tree-reject', conditionExpression: 'livenessScore < 0.95' }
    ]
  }
  routes.set('/api/rule/definition/content/102', {
    definitionId: 102,
    modelJson: JSON.stringify(reviewTree),
    scriptMode: 'visual'
  })

  const identityFlow = {
    defaultEdgeLineType: 'polyline',
    nodes: [
      { id: 'flow-start', type: 'start', name: '开始', x: 130, y: 280 },
      { id: 'flow-api', type: 'task', name: '调用活体检测', x: 360, y: 280, actionData: [] },
      { id: 'flow-model', type: 'task', name: '执行人脸模型', x: 590, y: 280, actionData: [] },
      { id: 'flow-check', type: 'decision', name: '相似度判断', x: 820, y: 280 },
      { id: 'flow-pass', type: 'end', name: '通过', x: 1060, y: 160, terminationScope: 'CURRENT_BRANCH' },
      { id: 'flow-reject', type: 'end', name: '拒绝', x: 1060, y: 400, terminationScope: 'CURRENT_BRANCH' }
    ],
    edges: [
      { source: 'flow-start', target: 'flow-api', conditionExpression: '' },
      { source: 'flow-api', target: 'flow-model', conditionExpression: '' },
      { source: 'flow-model', target: 'flow-check', conditionExpression: '' },
      { source: 'flow-check', target: 'flow-pass', conditionExpression: 'faceSimilarity >= 0.90' },
      { source: 'flow-check', target: 'flow-reject', conditionExpression: 'faceSimilarity < 0.90' }
    ]
  }
  routes.set('/api/rule/definition/content/103', {
    definitionId: 103,
    modelJson: JSON.stringify(identityFlow),
    scriptMode: 'visual'
  })

  routes.set('/api/rule/definition/content/104', {
    definitionId: 104,
    modelJson: JSON.stringify({
      executionMode: 'SERIAL',
      rules: [
        {
          uid: 'face-rule-1',
          ruleCode: 'FACE_LIVENESS_PASS',
          ruleName: '活体检测通过',
          priority: 10,
          enabled: true,
          conditionRoot: conditionRoot(
            conditionLeaf(livenessRef, '>=', literalOperand(0.95, 'NUMBER'))
          ),
          actionData: [
            {
              type: 'assign',
              targetOperand: verifiedRef,
              valueOperand: literalOperand(true, 'BOOLEAN')
            }
          ]
        },
        {
          uid: 'face-rule-2',
          ruleCode: 'FACE_SIMILARITY_REVIEW',
          ruleName: '相似度不足转复核',
          priority: 20,
          enabled: true,
          conditionRoot: conditionRoot(
            conditionLeaf(similarityRef, '<', literalOperand(0.9, 'NUMBER'))
          ),
          actionData: [
            {
              type: 'assign',
              targetOperand: riskLevelRef,
              valueOperand: literalOperand('REVIEW', 'STRING')
            }
          ]
        }
      ]
    }),
    scriptMode: 'visual'
  })

  routes.set('/api/rule/definition/content/105', {
    definitionId: 105,
    modelJson: JSON.stringify({
      rowVar: { varCode: 'livenessScore', varLabel: '活体检测分', varType: 'NUMBER', _varId: 3, _refType: 'VARIABLE', operand: livenessRef },
      colVar: { varCode: 'riskLevel', varLabel: '风险等级', varType: 'STRING', _varId: 5, _refType: 'VARIABLE', operand: riskLevelRef },
      resultVar: { varCode: 'verified', varLabel: '核验是否通过', varType: 'BOOLEAN', _varId: 4, _refType: 'VARIABLE', operand: verifiedRef },
      rowHeaders: ['达标', '待复核'],
      colHeaders: ['LOW', 'REVIEW'],
      cells: [[true, false], [false, false]],
      rowHeaderOperands: [literalOperand(0.95, 'NUMBER'), literalOperand(0.8, 'NUMBER')],
      colHeaderOperands: [literalOperand('LOW', 'STRING'), literalOperand('REVIEW', 'STRING')],
      cellOperands: [
        [literalOperand(true, 'BOOLEAN'), literalOperand(false, 'BOOLEAN')],
        [literalOperand(false, 'BOOLEAN'), literalOperand(false, 'BOOLEAN')]
      ]
    }),
    scriptMode: 'visual'
  })

  routes.set('/api/rule/definition/content/106', {
    definitionId: 106,
    modelJson: JSON.stringify({
      initialScore: 0,
      resultVar: { varCode: 'faceQualityScore', varLabel: '图像质量分', varType: 'NUMBER', _varId: 14, _refType: 'VARIABLE', operand: qualityRef },
      scoreItems: [
        {
          leftOperand: livenessRef,
          rightOperand: literalOperand(0.95, 'NUMBER'),
          condVar: 'livenessScore',
          condOperator: '>=',
          condValue: '0.95',
          condVarType: 'NUMBER',
          conditionLabel: '活体检测达标',
          score: 60,
          weight: 1
        }
      ],
      thresholds: []
    }),
    scriptMode: 'visual'
  })

  routes.set('/api/rule/definition/content/107', {
    definitionId: 107,
    modelJson: JSON.stringify({
      rowDimensions: [
        {
          varCode: 'livenessScore',
          varLabel: '活体检测分',
          varType: 'NUMBER',
          _varId: 3,
          _refType: 'VARIABLE',
          operand: livenessRef,
          segments: [
            { label: '达标', operator: '>=', value: 0.95, valueOperand: literalOperand(0.95, 'NUMBER') },
            { label: '待复核', operator: '<', value: 0.95, valueOperand: literalOperand(0.95, 'NUMBER') }
          ]
        }
      ],
      colDimensions: [
        {
          varCode: 'riskLevel',
          varLabel: '风险等级',
          varType: 'STRING',
          _varId: 5,
          _refType: 'VARIABLE',
          operand: riskLevelRef,
          segments: [
            { label: '低风险', operator: '==', value: 'LOW', valueOperand: literalOperand('LOW', 'STRING') },
            { label: '需复核', operator: '==', value: 'REVIEW', valueOperand: literalOperand('REVIEW', 'STRING') }
          ]
        }
      ],
      resultVar: { varCode: 'verified', varLabel: '核验是否通过', varType: 'BOOLEAN', _varId: 4, _refType: 'VARIABLE', operand: verifiedRef },
      cells: [
        [literalOperand(true, 'BOOLEAN'), literalOperand(false, 'BOOLEAN')],
        [literalOperand(false, 'BOOLEAN'), literalOperand(false, 'BOOLEAN')]
      ]
    }),
    scriptMode: 'visual'
  })

  routes.set('/api/rule/definition/content/108', {
    definitionId: 108,
    modelJson: JSON.stringify({
      initialScore: 0,
      resultVar: { varCode: 'riskLevel', varLabel: '风险等级', varType: 'STRING', _varId: 5, _refType: 'VARIABLE', operand: riskLevelRef },
      dimensionGroups: [
        {
          groupLabel: '生物识别质量',
          weight: 1,
          _collapsed: false,
          dimensions: [
            {
              varCode: 'livenessScore',
              varLabel: '活体检测分',
              varType: 'NUMBER',
              _varId: 3,
              _refType: 'VARIABLE',
              operand: livenessRef,
              weight: 1,
              rules: [
                {
                  conditions: [
                    {
                      leftOperand: livenessRef,
                      operator: '>=',
                      rightOperand: literalOperand(0.95, 'NUMBER'),
                      varCode: 'livenessScore',
                      varType: 'NUMBER',
                      value: 0.95
                    }
                  ],
                  score: 100
                }
              ]
            }
          ]
        }
      ],
      thresholds: [
        { min: 0, max: 60, result: 'HIGH', resultOperand: literalOperand('HIGH', 'STRING') },
        { min: 60, max: 90, result: 'REVIEW', resultOperand: literalOperand('REVIEW', 'STRING') },
        { min: 90, max: 100, result: 'LOW', resultOperand: literalOperand('LOW', 'STRING') }
      ]
    }),
    scriptMode: 'visual'
  })

  routes.set('/api/rule/definition/content/109', {
    definitionId: 109,
    modelJson: JSON.stringify({
      script: 'if (livenessScore >= 0.95 && faceSimilarity >= 0.90) {\n  verified = true;\n  riskLevel = "LOW";\n} else {\n  verified = false;\n  riskLevel = "REVIEW";\n}',
      scriptVarRefs: [
        { varId: 3, refType: 'VARIABLE', refCode: 'livenessScore' },
        { varId: 13, refType: 'VARIABLE', refCode: 'faceSimilarity' },
        { varId: 4, refType: 'VARIABLE', refCode: 'verified' },
        { varId: 5, refType: 'VARIABLE', refCode: 'riskLevel' }
      ]
    }),
    scriptMode: 'script'
  })

  return routes
}

module.exports = { createDocsApiData }
