const { expect, test } = require('@playwright/test')
const { randomUUID, randomInt } = require('node:crypto')

const base = process.env.E2E_DEMO_BASE_URL?.replace(/\/$/, '')

test('供应商、授信和 KYC 通过真实控制台执行并按追踪号读取日志', async ({ page }) => {
  test.skip(!base, '设置 E2E_DEMO_BASE_URL 后在已配置三项目及模拟供应商的演示库检查')
  test.setTimeout(300000)
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  await page.goto(`${base}/#/login`)
  await page.getByPlaceholder('请输入用户名').fill(process.env.E2E_USERNAME)
  await page.getByPlaceholder('请输入密码').fill(process.env.E2E_PASSWORD)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await page.locator('.menu-label', { hasText: '规则测试' }).first().click()

  const image = await page.evaluate(() => {
    const canvas = document.createElement('canvas')
    canvas.width = canvas.height = 128
    const ctx = canvas.getContext('2d')
    ctx.fillStyle = '#8395a5'
    ctx.fillRect(0, 0, 128, 128)
    ctx.fillStyle = '#a78972'
    ctx.fillRect(25, 22, 77, 84)
    return canvas.toDataURL('image/png').split(',')[1]
  })
  const faces = [{
    bbox: { x: 20, y: 15, width: 90, height: 100 },
    landmarks: [[42, 49], [82, 49], [62, 70], [44, 89], [80, 89]]
  }]
  const inputs = {
    KYC_FACE_LANDMARKS: { image, modelName: 'face-detection-0100', livenessModelType: 'liveness.onnx', outputHeight: 112, outputWidth: 112, widthScale: 1, heightScale: 1 },
    KYC_FACE_LIVENESS: { image, faces, livenessModelType: 'anti-spoof-mn3.onnx' },
    KYC_FACE_CONSISTENCY: { image, documentImage: image, faces, documentFaces: faces, recognitionModelType: 'w600k_mbf.onnx' }
  }
  const runs = []
  for (const code of ['MOCK_COURT', 'CREDIT_ADMISSION', ...Object.keys(inputs)]) {
    const choice = page.locator('.el-form-item').filter({ has: page.locator('.el-form-item__label', { hasText: /^规则$/ }) }).locator('.el-select')
    await choice.click()
    await choice.locator('input').fill(code)
    await page.getByRole('option').filter({ hasText: new RegExp(`\\b${code}\\b`) }).first().click()
    const run = page.getByRole('button', { name: '执行测试', exact: true })
    await expect(run).toBeEnabled({ timeout: 90000 })
    if (inputs[code]) {
      await page.getByText('表单填写', { exact: true }).first().click()
      const rows = page.locator('.param-row')
      await rows.first().waitFor()
      const filled = []
      for (const row of await rows.all()) {
        const field = await row.locator('.param-key input').inputValue()
        const value = inputs[code][field]
        if (value === undefined) continue
        await row.locator('.param-value input').fill(typeof value === 'string' ? value : JSON.stringify(value))
        filled.push(field)
      }
      expect(filled.sort()).toEqual(Object.keys(inputs[code]).sort())
    } else {
      await page.getByRole('button', { name: '载入样例', exact: true }).click()
      await expect(page.getByText(/已载入样例：/).last()).toBeVisible()
    }
    const response = page.waitForResponse(r => r.request().method() === 'POST' && r.url().includes('/api-scenarios/execute-validated'), { timeout: 180000 })
    await run.click()
    const { data } = await (await response).json()
    expect(data.execution.success, data.execution.errorMessage).toBe(true)
    const result = data.execution.result
    if (code === 'CREDIT_ADMISSION') {
      expect(result).toMatchObject({ result: '0', credit_limit: 20000, credit_period: 12, priced_rate: 16 })
    } else if (code === 'KYC_FACE_CONSISTENCY') {
      expect(result.faceSimilarity).toBeCloseTo(1, 5)
    } else if (code === 'KYC_FACE_LANDMARKS') {
      expect(Array.isArray(result.faces)).toBe(true)
    } else if (code === 'KYC_FACE_LIVENESS') {
      expect(result.faceLivenessResults).toHaveLength(1)
      expect(result.livenessRoute).not.toBe('SKIP')
    } else {
      expect(result.supplier_payload).toBeDefined()
    }
    await expect(page.locator('.test-result-card').getByText('执行成功', { exact: true })).toBeVisible()
    runs.push({ code, trace: data.execution.traceId, result })
  }

  await page.locator('.menu-label', { hasText: '执行日志' }).first().click()
  for (const { code, trace, result } of runs) {
    await page.getByPlaceholder('完整 trace_id').fill(trace)
    const response = page.waitForResponse(r => r.url().includes('/rule/log/list') && r.url().includes(trace))
    await page.getByRole('button', { name: '查询', exact: true }).click()
    const { data } = await (await response).json()
    expect(data.total).toBe(1)
    const summary = data.records[0]
    expect(summary).toMatchObject({ traceId: trace, ruleCode: code, success: 1, traceAvailable: true })
    expect(summary.outputResult ?? null).toBeNull()
    expect(summary.traceInfo ?? null).toBeNull()
    const detail = page.waitForResponse(r => new URL(r.url()).pathname === `/api/rule/log/${summary.id}`)
    await page.getByRole('button', { name: '详情', exact: true }).first().click()
    const record = (await (await detail).json()).data
    expect(record.traceId).toBe(trace)
    expect(JSON.parse(record.outputResult)).toEqual(result)
    expect(JSON.parse(record.traceInfo)).toBeTruthy()
    const drawer = page.getByRole('dialog', { name: '日志详情' })
    await expect(drawer).toBeVisible()
    await drawer.locator('.el-drawer__close-btn').click()
    await expect(drawer).not.toBeVisible()
  }
  expect(errors).toEqual([])
})

test('两台服务依次提交真实进件，下一节点能计算同设备和 GPS 历史阈值', async ({ request }) => {
  const nodes = [process.env.E2E_DEMO_SERVER_URL, process.env.E2E_DEMO_PEER_URL]
  const key = process.env.E2E_DEMO_CREDIT_API_KEY
  test.skip(nodes.some(url => !url) || !key, '需要两个演示 server 的地址与 CREDIT_HTTP 的 API Key')
  test.setTimeout(300000)
  const run = randomUUID()
  const latitude = randomInt(-5500, -2500) / 100
  const longitude = randomInt(6000, 11000) / 100
  const execute = async (node, params) => {
    const response = await request.post(`${node.replace(/\/$/, '')}/api/rule/sync/execute/CREDIT_ADMISSION`, {
      headers: { 'X-Auth-Code': 'CREDIT_HTTP', 'X-Rule-Api-Key': key },
      data: { clientAppName: 'history-demo', traceEnabled: true, params },
      timeout: 45000
    })
    const body = await response.json()
    expect(response.ok(), body.message).toBe(true)
    expect(body.data.success, body.data.errorMessage).toBe(true)
    expect(body.data.result.result).toBe('1')
    return body.data
  }
  for (const [code, prior] of [['QN0005', 3], ['QN0006', 11]]) {
    const common = {
      name: '合成申请人', idcard_no: '110105199002030718', mobile_no: '13862097542', idcard_expiry_date: '长期',
      gps_latitude: latitude, gps_longitude: code === 'QN0005' ? longitude : longitude + 15
    }
    for (let i = 0; i < prior; i++) {
      await execute(nodes[i % 2], {
        ...common, request_id: `ES-${run}-${code}-${i}`, contact: { mobile_no: '13900002104' },
        device_id: code === 'QN0005' ? run : `${run}-gps-${i}`
      })
    }
    const result = await execute(nodes[prior % 2], {
      ...common, request_id: `ES-${run}-${code}-check`, contact: { mobile_no: '13928046173' },
      device_id: code === 'QN0005' ? run : `${run}-gps-check`
    })
    const events = []
    const visit = node => {
      if (!node || typeof node !== 'object') return
      if (node.type === 'RULE_SET_ITEM' && node.evaluated && node.hit) events.push(node.ruleCode)
      Object.values(node).forEach(visit)
    }
    visit(result.traces)
    expect(events).toContain(code)
  }
})
