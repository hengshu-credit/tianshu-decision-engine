import {
  normalizeProjectId,
  readProjectContext,
  routeSupportsProjectContext,
  withProjectContext,
  writeProjectContext,
} from '@/utils/projectContext'

describe('projectContext', () => {
  test('只接受正整数项目 ID', () => {
    expect(normalizeProjectId('9')).toBe(9)
    expect(normalizeProjectId(3)).toBe(3)
    expect(normalizeProjectId('0')).toBeNull()
    expect(normalizeProjectId('bad')).toBeNull()
  })

  test('会话缓存只保留稳定项目标识和展示信息', () => {
    const storage = {
      value: null,
      setItem: vi.fn(function (key, value) {
        this.value = value
      }),
      getItem: vi.fn(function () {
        return this.value
      }),
      removeItem: vi.fn(function () {
        this.value = null
      }),
    }

    writeProjectContext(storage, {
      id: 9,
      projectCode: 'RISK',
      projectName: '风控项目',
      accessToken: 'never-cache-me',
    })

    expect(readProjectContext(storage)).toEqual({
      id: 9,
      projectCode: 'RISK',
      projectName: '风控项目',
      status: null,
    })
    expect(storage.value).not.toContain('accessToken')
  })

  test('损坏缓存安全回退且退出时删除缓存', () => {
    const storage = {
      getItem: vi.fn(() => '{bad'),
      setItem: vi.fn(),
      removeItem: vi.fn(),
    }
    expect(readProjectContext(storage)).toBeNull()
    writeProjectContext(storage, null)
    expect(storage.removeItem).toHaveBeenCalled()
  })

  test.each([
    ['/variable', true],
    ['/rule', true],
    ['/model', true],
    ['/datasource', true],
    ['/test', true],
    ['/approval', true],
    ['/log', true],
    ['/experiment', true],
    ['/experiment/new', true],
    ['/model/7', false],
    ['/approval/12', false],
    ['/datasource/api/7', false],
    ['/experiment/detail/12', false],
    ['/list', false],
    ['/database', true],
    ['/function', false],
    ['/lineage', false],
    ['/billing', false],
    ['/project', false],
    ['/project/9', false],
    ['/account', false],
  ])('%s 的项目上下文支持状态为 %s', (path, expected) => {
    expect(routeSupportsProjectContext(path)).toBe(expected)
  })

  test('为支持的模块显式合并 projectId 且保留原查询参数', () => {
    expect(withProjectContext('/datasource?tab=api', 9)).toEqual({
      path: '/datasource',
      query: { tab: 'api', projectId: 9 },
    })
    expect(withProjectContext('/project', 9)).toBe('/project')
    expect(withProjectContext('/model/7', 9)).toBe('/model/7')
    expect(withProjectContext('/rule', null)).toBe('/rule')
  })
})
