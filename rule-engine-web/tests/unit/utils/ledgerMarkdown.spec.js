import { describe, expect, test, vi } from 'vitest'
vi.mock('vditor', () => ({ default: {} }))
import { ledgerAssetId, sanitizeLedgerHtml } from '@/utils/ledgerMarkdown'

describe('台账 Markdown 安全渲染', () => {
  test('只接受本站素材内容地址，拒绝外链、凭据、查询串和脚本地址', () => {
    expect(ledgerAssetId('/api/rule/ledger/assets/12/content')).toBe('12')
    expect(ledgerAssetId('https://untrusted.example/api/rule/ledger/assets/12/content')).toBeNull()
    expect(ledgerAssetId('/api/rule/ledger/assets/12/content?token=other')).toBeNull()
    expect(ledgerAssetId('javascript:alert(1)')).toBeNull()
    expect(ledgerAssetId('data:image/png;base64,AAA')).toBeNull()
  })
  test('保留表格、代码和只读任务状态，过滤主动内容及任意输入控件', () => {
    const html = sanitizeLedgerHtml('<h2>验收</h2><script>alert(1)</script><iframe src="/private"></iframe><form><input value="secret"></form><ul><li class="vditor-task"><input type="checkbox" checked>已完成</li></ul><table><tr><td>结果</td></tr></table><pre><code>safe</code></pre>')
    const element = document.createElement('div'); element.innerHTML = html
    expect(element.querySelectorAll('script,iframe,form')).toHaveLength(0)
    expect(element.querySelectorAll('input')).toHaveLength(1)
    expect(element.querySelector('input').checked).toBe(true)
    expect(element.querySelector('input').disabled).toBe(true)
    expect(element.querySelector('td').textContent).toBe('结果')
    expect(element.querySelector('code').textContent).toBe('safe')
  })
  test('外部图片被替换，内部素材仍需服务端鉴权且不能注入额外资源', () => {
    const html = sanitizeLedgerHtml('<img src="https://untrusted.example/tracker" onerror="alert(1)"><img src="/api/rule/ledger/assets/8/content" style="background:url(https://untrusted.example)" srcset="https://untrusted.example/2 2x">')
    const element = document.createElement('div'); element.innerHTML = html
    expect(element.querySelectorAll('img')).toHaveLength(1)
    const image = element.querySelector('img')
    expect(image.getAttribute('src')).toBe('/api/rule/ledger/assets/8/content')
    expect(image.hasAttribute('srcset')).toBe(false)
    expect(image.hasAttribute('style')).toBe(false)
    expect(image.hasAttribute('onerror')).toBe(false)
    expect(element.textContent).toContain('图片需先上传')
  })
})
