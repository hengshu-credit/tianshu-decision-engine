import { renderJavaIntegration } from '@/utils/apiDoc/javaIntegration'

describe('Java 服务接入说明', () => {
  test.each([
    ['BASIC', 'username: ${RULE_USERNAME}'],
    ['API_KEY', 'api-key: ${RULE_API_KEY}'],
    ['HMAC_SHA256', 'hmac-secret: ${RULE_HMAC_SECRET}'],
    ['LEGACY_TOKEN', 'token: ${PROJECT_ACCESS_TOKEN}']
  ])('按 %s 生成环境变量占位配置，不包含真实凭据', (authType, expected) => {
    const html = renderJavaIntegration({
      project: { id: 7, projectCode: 'Credit_Code' },
      authentications: [{ authType, parameterName: 'X-Credit-Key', placement: 'QUERY', password: 'actual-secret' }],
      rules: [{ ruleCode: 'My_Rule' }]
    })
    const element = document.createElement('div')
    element.innerHTML = html

    expect(element.textContent).toContain(expected)
    expect(element.textContent).toContain('project-id: 7')
    expect(element.textContent).toContain('project-code: "Credit_Code"')
    expect(element.textContent).toContain('ruleClient.execute("My_Rule", params)')
    expect(element.textContent).not.toContain('actual-secret')
    if (authType === 'API_KEY') {
      expect(element.textContent).toContain('api-key-placement: QUERY')
      expect(element.textContent).toContain('api-key-parameter-name: "X-Credit-Key"')
    }
  })

  test('规则与项目内容按字面转义，不可注入文档脚本', () => {
    const html = renderJavaIntegration({
      project: { projectCode: '</code><script>alert(1)</script>' },
      rules: [{ ruleCode: '规则"\\编码' }]
    })
    const element = document.createElement('div')
    element.innerHTML = html

    expect(element.querySelector('script')).toBeNull()
    expect(element.textContent).toContain('ruleClient.execute("规则\\"\\\\编码", params)')
  })
})
