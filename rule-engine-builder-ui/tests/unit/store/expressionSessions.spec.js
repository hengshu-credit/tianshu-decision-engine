import Vue from 'vue'
import Vuex from 'vuex'
import expressionSessions from '@/store/modules/expressionSessions'

Vue.use(Vuex)

function createStore() {
  return new Vuex.Store({
    modules: { expressionSessions }
  })
}

function createSession(sessionId, ruleId, sourceKey, title = '决策表 · 右操作数') {
  return {
    sessionId,
    ruleId,
    sourceKey,
    title,
    draft: { kind: 'PATH', value: 'request.age' },
    vars: [],
    functions: []
  }
}

describe('expressionSessions store', () => {
  test('暂存草稿、编译结果并按修订号只消费一次', async() => {
    const store = createStore()
    await store.dispatch('expressionSessions/openSession', createSession('s1', 7, 'picker-1'))

    const draft = { kind: 'LITERAL', value: '10', valueType: 'NUMBER' }
    await store.dispatch('expressionSessions/saveDraft', { sessionId: 's1', draft })
    draft.value = '20'
    expect(store.getters['expressionSessions/sessionById']('s1').draft.value).toBe('10')

    const operand = { kind: 'LITERAL', value: '10', valueType: 'NUMBER' }
    await store.dispatch('expressionSessions/saveCompiled', {
      sessionId: 's1',
      operand,
      compiledScript: '10'
    })
    operand.value = '30'

    expect(store.getters['expressionSessions/pendingCompiledResult']('s1')).toEqual({
      operand: { kind: 'LITERAL', value: '10', valueType: 'NUMBER' },
      compiledScript: '10',
      revision: 1
    })

    await store.dispatch('expressionSessions/markApplied', { sessionId: 's1', revision: 1 })
    expect(store.getters['expressionSessions/pendingCompiledResult']('s1')).toBeNull()
  })

  test('同一规则可同时打开多个表达式会话并区分同名标题', async() => {
    const store = createStore()
    await store.dispatch('expressionSessions/openSession', createSession('s1', 7, 'picker-1'))
    await store.dispatch('expressionSessions/openSession', createSession('s-other', 8, 'picker-3'))
    await store.dispatch('expressionSessions/openSession', createSession('s2', 7, 'picker-2'))

    expect(store.getters['expressionSessions/sessionById']('s1').status).toBe('ACTIVE')
    expect(store.getters['expressionSessions/sessionById']('s1').draft).toBeDefined()
    expect(store.getters['expressionSessions/sessionById']('s2').status).toBe('ACTIVE')
    expect(store.getters['expressionSessions/sessionById']('s2').title).toBe('决策表 · 右操作数（2）')
    expect(store.getters['expressionSessions/sessionById']('s-other').status).toBe('ACTIVE')
  })

  test('多个会话的编译结果互不替换', async() => {
    const store = createStore()
    await store.dispatch('expressionSessions/openSession', createSession('s1', 7, 'picker-1'))
    await store.dispatch('expressionSessions/openSession', createSession('s2', 7, 'picker-2'))
    await store.dispatch('expressionSessions/saveCompiled', {
      sessionId: 's1',
      operand: { kind: 'LITERAL', value: '1', valueType: 'NUMBER' },
      compiledScript: '1'
    })

    expect(store.getters['expressionSessions/pendingCompiledResult']('s1')).toMatchObject({
      operand: { kind: 'LITERAL', value: '1', valueType: 'NUMBER' },
      revision: 1
    })
    expect(store.getters['expressionSessions/sessionById']('s2').compiledRevision).toBe(0)
  })

  test('重复打开同一入口时保留已有草稿和展示标题', async() => {
    const store = createStore()
    await store.dispatch('expressionSessions/openSession', createSession('s1', 7, 'picker-1'))
    await store.dispatch('expressionSessions/saveDraft', {
      sessionId: 's1',
      draft: { kind: 'LITERAL', value: '88', valueType: 'NUMBER' }
    })
    await store.dispatch('expressionSessions/openSession', createSession('s1', 7, 'picker-1', '新标题'))

    expect(store.getters['expressionSessions/sessionById']('s1')).toMatchObject({
      title: '决策表 · 右操作数',
      draft: { kind: 'LITERAL', value: '88', valueType: 'NUMBER' }
    })
  })
})
