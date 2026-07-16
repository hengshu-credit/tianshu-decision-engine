import Vue from 'vue'
import Vuex from 'vuex'
import expressionSessions from '@/store/modules/expressionSessions'

Vue.use(Vuex)

function createStore() {
  return new Vuex.Store({
    modules: { expressionSessions }
  })
}

function createSession(sessionId, ruleId, sourceKey) {
  return {
    sessionId,
    ruleId,
    sourceKey,
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

  test('同一规则打开其他表达式会替换旧会话，其他规则不受影响', async() => {
    const store = createStore()
    await store.dispatch('expressionSessions/openSession', createSession('s1', 7, 'picker-1'))
    await store.dispatch('expressionSessions/openSession', createSession('s-other', 8, 'picker-3'))
    await store.dispatch('expressionSessions/openSession', createSession('s2', 7, 'picker-2'))

    expect(store.getters['expressionSessions/sessionById']('s1').status).toBe('REPLACED')
    expect(store.getters['expressionSessions/sessionById']('s1').draft).toBeUndefined()
    expect(store.getters['expressionSessions/sessionById']('s1').vars).toBeUndefined()
    expect(store.getters['expressionSessions/sessionById']('s2').status).toBe('ACTIVE')
    expect(store.getters['expressionSessions/sessionById']('s-other').status).toBe('ACTIVE')
  })

  test('已替换会话不能继续暂存或生成待回填结果', async() => {
    const store = createStore()
    await store.dispatch('expressionSessions/openSession', createSession('s1', 7, 'picker-1'))
    await store.dispatch('expressionSessions/openSession', createSession('s2', 7, 'picker-2'))
    await store.dispatch('expressionSessions/saveCompiled', {
      sessionId: 's1',
      operand: { kind: 'LITERAL', value: '1', valueType: 'NUMBER' },
      compiledScript: '1'
    })

    expect(store.getters['expressionSessions/pendingCompiledResult']('s1')).toBeNull()
    expect(store.getters['expressionSessions/sessionById']('s1').compiledRevision).toBe(0)
  })
})
