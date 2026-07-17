import {
  buildEndpointScenarios,
  renderAuthentication,
  renderResponseContract
} from '@/utils/apiDoc/sections'

describe('API 文档内容区块', () => {
  test('鉴权 Tab 使用已配置的 API Key 位置且不包含凭据', () => {
    const html = renderAuthentication({
      authentications: [{
        authName: '合作方 Key',
        authType: 'API_KEY',
        placement: 'HEADER',
        parameterName: 'X-Partner-Key',
        tokenTtlSeconds: 7200,
        tokenGraceSeconds: 600
      }]
    })

    expect(html).toContain('X-Partner-Key')
    expect(html).toContain('&lt;API_KEY&gt;')
    expect(html).toContain('仅为示例')
    expect(html).not.toContain('secretMasked')
  })

  test('平台场景自动生成，业务场景保持显式配置', () => {
    const scenarios = buildEndpointScenarios({ ruleCode: 'RISK', scenarios: [] })

    expect(scenarios.map(item => item.title)).toContain('200 / 执行成功')
    expect(scenarios.map(item => item.title)).toContain('200 / 规则执行失败')
    expect(scenarios.map(item => item.title)).toContain('401 / 鉴权失败')
    expect(scenarios.map(item => item.title)).toContain('404 / 规则不存在')
    expect(scenarios.some(item => item.kind === 'BUSINESS')).toBe(false)
  })

  test('用户选择的业务场景按已保存内容追加', () => {
    const scenarios = buildEndpointScenarios({
      scenarios: [{
        scenarioName: '风险拒绝',
        outerCode: 200,
        businessCode: 'REJECT',
        requestJson: '{"params":{"age":17}}',
        responseJson: '{"code":200,"data":{"result":{"code":"REJECT"}}}'
      }]
    })

    const business = scenarios.find(item => item.kind === 'BUSINESS')
    expect(business.title).toBe('200 / 风险拒绝 / REJECT')
    expect(business.requestJson).toBe('{"params":{"age":17}}')
    expect(business.responseJson).toBe('{"code":200,"data":{"result":{"code":"REJECT"}}}')
  })

  test('未配置业务 code 时场景标题不虚构 code', () => {
    const scenarios = buildEndpointScenarios({
      scenarios: [{
        scenarioName: '人工复核',
        outerCode: 200,
        requestJson: '{}',
        responseJson: '{}'
      }]
    })

    expect(scenarios.find(item => item.kind === 'BUSINESS').title).toBe('200 / 人工复核')
  })

  test('通用响应约定提供 code 与 success 两级含义', () => {
    const html = renderResponseContract()

    expect(html).toContain('通用响应约定与码表')
    expect(html).toContain('401')
    expect(html).toContain('data.success')
    expect(html).toContain('不是业务决策 code')
  })
})
