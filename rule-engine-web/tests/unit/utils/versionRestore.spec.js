vi.mock('element-plus', () => ({ ElMessageBox: vi.fn() }))
import { ElMessageBox } from 'element-plus'
import { chooseVersionRestore, versionDisplayLabel } from '@/utils/versionRestore'

beforeEach(() => vi.clearAllMocks())

test('恢复默认新增，选择复用后返回复用方式，允许继续编辑', async () => {
  ElMessageBox.mockImplementation(async options => {
    expect(options.confirmButtonText).toBe('恢复并编辑')
    const first = options.message()
    expect(first.props).toMatchObject({ version: 7, modelValue: 'NEW' })
    first.props['onUpdate:modelValue']('REUSE')
    expect(options.message().props.modelValue).toBe('REUSE')
  })
  expect(await chooseVersionRestore(7)).toBe('REUSE')
})

test('取消恢复不返回保存方式', async () => {
  ElMessageBox.mockRejectedValue('cancel')
  expect(await chooseVersionRestore(7)).toBeNull()
})

test('同版本号不同快照可以区分，保持真实记录编号', () => {
  expect(versionDisplayLabel({ version: 1 })).toBe('v1')
  expect(versionDisplayLabel({ version: 4, businessVersion: 1 })).toBe('v1 · 记录 4')
  expect(versionDisplayLabel({ versionNo: 1, revisionNo: 8 })).toBe('v1 · 记录 8')
})
