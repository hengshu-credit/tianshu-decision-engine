import { applySystemBrand, DEFAULT_BRAND, isBrandImageUrl, resolveBrandImages, systemBrand } from '@/system/systemBrand'

describe('系统品牌图片来源', () => {
  test('水印只在明确开启时生效，缺省及关闭均移除', () => {
    expect(DEFAULT_BRAND.watermarkEnabled).toBe(false)
    applySystemBrand({ ...DEFAULT_BRAND, watermarkEnabled: true })
    expect(systemBrand.watermarkEnabled).toBe(true)
    applySystemBrand({ ...DEFAULT_BRAND, watermarkEnabled: false })
    expect(systemBrand.watermarkEnabled).toBe(false)
    applySystemBrand({ systemName: '旧版品牌' })
    expect(systemBrand.watermarkEnabled).toBe(false)
  })
  afterEach(() => applySystemBrand({ ...DEFAULT_BRAND, logoUrl: '', faviconUrl: '' }))
  test('未设置浏览器图标时使用 Logo，单独图标优先', () => {
    const logoUrl = 'https://images.example.com/logo.svg'
    const oldIcon = document.createElement('link'); oldIcon.rel = 'icon'; oldIcon.type = 'image/svg+xml'; document.head.appendChild(oldIcon)
    applySystemBrand({ systemName: '品牌测试', subtitle: '', logoUrl })
    expect(systemBrand.faviconUrl).toBe(logoUrl)
    expect(document.querySelector('link[rel="icon"]').getAttribute('href')).toBe(logoUrl)
    expect(document.querySelectorAll('link[rel="icon"]')).toHaveLength(1)
    expect(document.querySelector('link[rel="icon"]').hasAttribute('type')).toBe(false)
    expect(resolveBrandImages({ logoId: 'image-id' }).faviconUrl).toBe('/api/auth/console/brand/assets/image-id')
    expect(resolveBrandImages({ logoUrl, faviconUrl: '/images/icon.webp' }).faviconUrl).toBe('/images/icon.webp')
    expect(resolveBrandImages().faviconUrl).toBe(DEFAULT_BRAND.logoUrl)
  })
  test('支持站内路径和 HTTP(S) 图片，拒绝脚本、内联数据和带凭据地址', () => {
    for (const url of ['/images/logo.svg', 'https://images.example.com/a.png?v=1', 'http://localhost:9090/logo.jpg']) expect(isBrandImageUrl(url)).toBe(true)
    for (const url of ['javascript:alert(1)', 'data:image/svg+xml,test', '//example.com/a.png', 'https://user:secret@example.com/a.png', 'https://example.com\\a.png']) expect(isBrandImageUrl(url)).toBe(false)
  })
})
