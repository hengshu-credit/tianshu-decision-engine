import { generateApiDocHtml } from '@/utils/apiDoc'

describe('完整 API 文档生成器', () => {
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
})
