import { shallowMount } from '@test-utils'
import { flushPromises } from '@vue/test-utils'
import FontSettings from '@/views/system/FontSettings.vue'
import { DEFAULT_ENGINE_FONT } from '@/system/engineFont'
import * as api from '@/api/systemSettings'
import { previewEngineFont } from '@/system/engineFont'

vi.mock('@/api/systemSettings', () => ({ getSystemFontMetadata: vi.fn(), getSystemFontPreview: vi.fn(), uploadSystemFont: vi.fn() }))
vi.mock('@/system/engineFont', async importOriginal => ({ ...await importOriginal(), previewEngineFont: vi.fn() }))

describe('字体配置表单', () => {
  beforeEach(() => { vi.clearAllMocks() })
  test('浏览器不支持或拒绝本机字体权限时给出可操作提示', async () => {
    const wrapper = shallowMount(FontSettings, { props: { active: true } })
    await wrapper.vm.readLocalFonts()
    expect(wrapper.vm.error).toContain('直接输入字体名称')
    window.queryLocalFonts = vi.fn().mockRejectedValue(new Error('denied'))
    await wrapper.vm.readLocalFonts()
    expect(wrapper.vm.error).toContain('未获得')
    delete window.queryLocalFonts
    wrapper.unmount()
  })
  test('只读不预览，不完整上传与空本机字体不能保存', async () => {
    const wrapper = shallowMount(FontSettings, { props: { disabled: true, active: true, modelValue: { ...DEFAULT_ENGINE_FONT, source: 'LOCAL', family: 'Arial' } } })
    expect(previewEngineFont).toHaveBeenLastCalledWith(null)
    await wrapper.vm.upload({ name: 'font.ttf', raw: new Blob(['font']) })
    expect(api.uploadSystemFont).not.toHaveBeenCalled()
    await wrapper.setProps({ disabled: false, modelValue: { ...DEFAULT_ENGINE_FONT, source: 'UPLOAD' } })
    expect(wrapper.vm.valid).toBe(false)
    await wrapper.setProps({ modelValue: { ...DEFAULT_ENGINE_FONT, source: 'LOCAL' } })
    expect(wrapper.vm.valid).toBe(false)
    wrapper.unmount()
  })
  test('无效文件与上传失败保留之前选择，不能发布配置', async () => {
    const wrapper = shallowMount(FontSettings, { props: { active: true } })
    await wrapper.vm.upload({ name: 'bad.txt', raw: new Blob(['font']) })
    expect(api.uploadSystemFont).not.toHaveBeenCalled()
    api.uploadSystemFont.mockRejectedValueOnce(new Error('字体损坏'))
    await wrapper.vm.upload({ name: 'bad.ttf', raw: new Blob(['font']) })
    expect(wrapper.vm.error).toBe('字体损坏')
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    expect(wrapper.emitted('busy')).toEqual([[true], [false]])
    wrapper.unmount()
  })
  test('离开后的迟到字体下载不得恢复草稿预览', async () => {
    let release
    api.getSystemFontMetadata.mockResolvedValue({ data: { faces: [{ name: '字体', instances: [{ name: '默认', coordinates: {} }] }] } })
    api.getSystemFontPreview.mockImplementation(() => new Promise(resolve => { release = resolve }))
    const wrapper = shallowMount(FontSettings, { props: { active: true, modelValue: { ...DEFAULT_ENGINE_FONT, source: 'UPLOAD', assetId: 'asset' } } })
    await flushPromises()
    wrapper.unmount()
    release({ data: new Blob(['font']) }); await flushPromises()
    expect(previewEngineFont).toHaveBeenLastCalledWith(null)
    expect(previewEngineFont.mock.calls.every(([value]) => value === null)).toBe(true)
  })
})
