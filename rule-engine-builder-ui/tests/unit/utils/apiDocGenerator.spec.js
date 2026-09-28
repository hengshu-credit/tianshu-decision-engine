import { generateApiDocHtml, generateOpenApiDocument } from '@/utils/apiDoc'

describe('完整 API 文档生成器', () => {
  test('生成可导入的 OpenAPI 3.1 文档且不输出凭据', () => {
    const openApi = generateOpenApiDocument({
      project: { projectCode: 'credit', projectName: '授信决策' },
      authentications: [{ authType: 'API_KEY', placement: 'HEADER', parameterName: 'X-Partner-Key' }],
      rules: [{
        ruleCode: 'RISK', ruleName: '风险决策',
        inputVariables: [{ varCode: 'age', varType: 'INTEGER', varLabel: '年龄' }],
        outputVariables: [{ varCode: 'decision', varType: 'STRING', varLabel: '决策' }]
      }]
    })

    expect(openApi.openapi).toBe('3.1.0')
    expect(openApi.paths['/api/rule/sync/execute/RISK']).toBeTruthy()
    expect(openApi.components.securitySchemes.projectAuth1.name).toBe('X-Partner-Key')
    expect(openApi.paths['/api/rule/sync/execute/RISK'].post.requestBody.content['application/json'].schema.properties.params.properties.age.type).toBe('integer')
    expect(openApi.paths['/api/rule/sync/execute/RISK'].post.responses['200'].content['application/json'].schema.properties.data.properties.result.properties.decision.type).toBe('string')
    expect(JSON.stringify(openApi)).not.toContain('secretMasked')
  })

  test('HMAC 安全方案声明完整签名头和签名语义', () => {
    const openApi = generateOpenApiDocument({
      project: { projectCode: 'credit' },
      authentications: [{ authType: 'HMAC_SHA256' }],
      rules: []
    })

    const scheme = openApi.components.securitySchemes.projectAuth1
    expect(scheme.name).toBe('X-Rule-Access-Key')
    expect(scheme.description).toContain('X-Rule-Timestamp')
    expect(scheme.description).toContain('X-Rule-Signature')
  })

  test('已启用开放接口契约的规则导出 open 路径并标记契约状态', () => {
    const openApi = generateOpenApiDocument({
      project: { projectCode: 'credit' },
      rules: [{
        ruleCode: 'OPEN_RULE',
        openApiEnabled: true,
        schemaTrust: 'VERIFIED',
        openApiContract: {
          enabled: true,
          requestMappings: [{ sourceType: 'BODY', sourcePath: '$.customer.age', required: true, targetType: 'INTEGER' }],
          responseMappings: [{ sourceRefType: 'VARIABLE', sourceVarId: 7, targetField: 'decision' }]
        }
      }]
    })

    const operation = openApi.paths['/api/rule/open/execute/OPEN_RULE'].post
    expect(operation['x-open-api-contract-enabled']).toBe(true)
    expect(operation['x-rule-schema-trust']).toBe('VERIFIED')
    expect(operation['x-open-api-contract'].responseMappings[0].targetField).toBe('decision')
    expect(operation.requestBody.content['application/json'].schema.properties.customer.properties.age.type).toBe('integer')
    expect(operation.requestBody.content['application/json'].schema.properties.customer.required).toEqual(['age'])
  })
  const doc = {
    project: { id: 7, projectCode: 'credit', projectName: '授信决策', description: '测试项目' },
    authentications: [{
      authName: '合作方 Key',
      authType: 'API_KEY',
      placement: 'HEADER',
      parameterName: 'X-Partner-Key',
      tokenTtlSeconds: 7200,
      tokenGraceSeconds: 600
    }],
    dataObjects: [{ objectCode: 'internal-object' }],
    rules: [{
      id: 8,
      ruleCode: 'RISK',
      ruleName: '风险决策',
      description: '风险接口',
      modelJson: '{"internal":true}',
      inputVariables: [{ varCode: 'age', varType: 'INTEGER', varLabel: '年龄', exampleValue: '17' }],
      outputVariables: [{ varCode: 'decision', varType: 'STRING', varLabel: '决策结果', exampleValue: 'PASS' }],
      inputDataObjects: [],
      outputDataObjects: [],
      scenarios: [{
        scenarioName: '风险拒绝',
        outerCode: 200,
        businessCodePath: 'data.result.code',
        businessCode: 'REJECT',
        requestJson: '{"clientAppName":"demo","params":{"age":17}}',
        responseJson: '{"code":200,"message":"success","data":{"success":true,"result":{"code":"REJECT"}}}'
      }]
    }]
  }

  test('生成带三栏结构的离线单 HTML 文档', () => {
    const html = generateApiDocHtml(doc, { logoSvg: '<svg id="hengshucredit"></svg>' })

    expect(html.startsWith('<!DOCTYPE html>')).toBe(true)
    expect(html).toContain('<svg id="hengshucredit"></svg>')
    expect(html).toContain('认证鉴权')
    expect(html).toContain('通用响应约定与码表')
    expect(html).toContain('在线调用')
    expect(html).toContain('Shell')
    expect(html).toContain('JavaScript')
    expect(html).toContain('风险拒绝')
    expect(html).not.toContain('项目数据对象')
    expect(html).not.toContain('internal-object')
    expect(html).not.toContain('modelJson')
  })

  test('左侧品牌与管理端 header-title 保持相同结构并在下方显示项目信息', () => {
    const html = generateApiDocHtml(doc, { logoSvg: '<svg id="hengshucredit"></svg>' })

    expect(html).toContain('class="brand-header-title"')
    expect(html).toContain('class="brand-logo"')
    expect(html).toContain('class="brand-main-text">天枢决策引擎')
    expect(html).toContain('class="brand-sub-text">天工开物, 枢衡定策')
    expect(html).toContain('class="brand-project"')
    expect(html).toContain('授信决策')
    expect(html).toContain('credit')
  })

  test('包含 Java 服务接入、项目隔离和独立日志开关说明', () => {
    const html = generateApiDocHtml(doc, { logoSvg: '<svg></svg>' })

    expect(html).toContain('href="#java-integration"')
    expect(html).toContain('业务端 → Java 服务 → 决策引擎')
    expect(html).toContain('project-code: &quot;credit&quot;')
    expect(html).toContain('trace-enabled: false')
    expect(html).toContain('log-report-enabled: false')
    expect(html).toContain('已关联的全局规则')
    expect(html).toContain('服务端审计')
    expect(html).toContain('ruleClient.execute(')
    expect(html).toContain('traceEnabled')
  })

  test('文档数据中的 script 闭合标签不会突破内联数据脚本', () => {
    const malicious = JSON.parse(JSON.stringify(doc))
    malicious.project.description = '</script><script>alert(1)</script>'

    const html = generateApiDocHtml(malicious, { logoSvg: '<svg></svg>' })

    expect(html).not.toContain('</script><script>alert(1)</script>')
    expect(html).toContain('\\u003c/script\\u003e')
  })

  test('拒绝非 SVG Logo 内容', () => {
    expect(() => generateApiDocHtml(doc, { logoSvg: '<img src=x>' })).toThrow('hengshucredit Logo')
  })

  test('不配置业务场景时只输出平台样例', () => {
    const withoutBusiness = JSON.parse(JSON.stringify(doc))
    withoutBusiness.rules[0].scenarios = []

    const html = generateApiDocHtml(withoutBusiness, { logoSvg: '<svg></svg>' })

    expect(html).toContain('200 / 执行成功')
    expect(html).not.toContain('风险拒绝')
    expect(html).not.toContain('REJECT')
  })

  test('内置字段树折叠交互且不依赖外部脚本', () => {
    const html = generateApiDocHtml(doc, { logoSvg: '<svg></svg>' })

    expect(html).toContain('data-field-toggle')
    expect(html).toContain("closest('[data-field-toggle]')")
    expect(html).toContain('aria-expanded')
    expect(html).not.toContain('<script src=')
  })

  test('接口以单页 Tab 切换并包含两侧拖拽栏', () => {
    const multiple = JSON.parse(JSON.stringify(doc))
    multiple.rules.push({
      id: 9,
      ruleCode: 'SECOND',
      ruleName: '第二接口',
      requestFields: [],
      responseFields: [],
      scenarios: []
    })

    const html = generateApiDocHtml(multiple, { logoSvg: '<svg></svg>' })

    expect(html).toContain('data-resize="nav"')
    expect(html).toContain('data-resize="runner"')
    expect(html).toContain('data-endpoint-nav="8"')
    expect(html).toContain('data-endpoint-nav="9"')
    expect((html.match(/endpoint-panel active/g) || [])).toHaveLength(1)
    expect(html).toContain('scrollIntoView')
  })

  test('完整文档内联编辑器运行时且不加载外部资源', () => {
    const html = generateApiDocHtml(doc, { logoSvg: '<svg></svg>' })

    expect(html).toContain('window.ApiDocEditors')
    expect(html).toContain('data-code-editor="runner-body"')
    expect(html).toContain('new FormData()')
    expect(html).not.toContain('monaco')
    expect(html).not.toContain('<script src=')
  })
})
