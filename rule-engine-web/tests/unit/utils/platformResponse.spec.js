import { readFileSync } from 'node:fs'
import { platformResponse } from '../../e2e/support/platformResponse.cjs'
import { PLATFORM_RESPONSE_CODES, PLATFORM_SUCCESS_CODE, acceptsExecutionFailure, platformResponseMessage } from '@/utils/platformResponse'

describe('平台响应契约', () => {
  test('测试页面保留入参、依赖和系统失败的执行诊断，拒绝未知码和成功数据', () => {
    for (const code of ['100001', '601001', '602001', '900001']) {
      expect(acceptsExecutionFailure({ acceptExecutionFailure: true }, { code, data: { success: false, traces: [] } })).toBe(true)
    }
    for (const body of [{ code: '299999', data: { success: false } }, { code: '200001', data: { success: true } }]) {
      expect(acceptsExecutionFailure({ acceptExecutionFailure: true }, body)).toBe(false)
    }
  })
  test('模拟接口和导出样例使用相同字典，业务数据原样保留', () => {
    const data = { code: 200, success: false }
    expect(platformResponse(data)).toMatchObject({ code: '000000', message: '成功', data })
    expect(platformResponse(null, 'SYSTEM_ERROR', '具体原因')).toMatchObject({
      code: '900001', message: '系统执行异常', success: false, detail: '具体原因',
    })
  })
  test('前端字典与 Java 公共模块逐项一致', () => {
    const java = readFileSync('../rule-engine-model/src/main/java/com/hengshucredit/rule/model/PlatformCode.java', 'utf8')
    const codes = [...java.matchAll(/^ {4}([A-Z_]+)\("(\d{6})", "([^"]+)", (\d+)\)/gm)]
      .map(([, name, code, message, status]) => ({ name, code, message, httpStatus: Number(status) }))
    expect(PLATFORM_RESPONSE_CODES).toEqual(codes)
    expect(PLATFORM_SUCCESS_CODE).toBe('000000')
  })

  test('只有显式的测试请求保留失败执行结果，鉴权失败始终拒绝', () => {
    const failure = { code: '200001', data: { success: false, traces: [] } }
    expect(acceptsExecutionFailure({}, failure)).toBe(false)
    expect(acceptsExecutionFailure({ acceptExecutionFailure: true }, failure)).toBe(true)
    expect(acceptsExecutionFailure({ acceptExecutionFailure: true }, { ...failure, code: '300001' })).toBe(false)
    expect(platformResponseMessage({ code: '100001', message: '入参校验失败', detail: '年龄缺失' })).toBe('入参校验失败：年龄缺失')
  })
})
