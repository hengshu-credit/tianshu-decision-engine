function cloneValue(value) {
  if (value == null) return value
  return JSON.parse(JSON.stringify(value))
}

function activeSession(payload, current) {
  const retained = current && current.status === 'ACTIVE' ? current : null
  return {
    ...cloneValue(payload),
    status: 'ACTIVE',
    draft: retained ? retained.draft : cloneValue(payload.draft),
    savedAt: retained ? retained.savedAt : null,
    compiledOperand: retained ? retained.compiledOperand : null,
    compiledScript: retained ? retained.compiledScript : '',
    compiledRevision: retained ? retained.compiledRevision : 0,
    appliedRevision: retained ? retained.appliedRevision : 0
  }
}

function replacedSession(session) {
  return {
    sessionId: session.sessionId,
    ruleId: session.ruleId,
    sourceKey: session.sourceKey,
    status: 'REPLACED',
    compiledRevision: 0,
    appliedRevision: 0
  }
}

const state = () => ({
  sessions: {},
  activeByRule: {}
})

const getters = {
  sessionById: state => sessionId => state.sessions[sessionId] || null,
  pendingCompiledResult: state => sessionId => {
    const session = state.sessions[sessionId]
    if (!session || session.status !== 'ACTIVE') return null
    if (!session.compiledRevision || session.compiledRevision <= session.appliedRevision) return null
    return {
      operand: cloneValue(session.compiledOperand),
      compiledScript: session.compiledScript,
      revision: session.compiledRevision
    }
  }
}

const mutations = {
  OPEN_SESSION(state, payload) {
    const ruleKey = String(payload.ruleId)
    const previousId = state.activeByRule[ruleKey]
    const nextSessions = { ...state.sessions }
    if (previousId && previousId !== payload.sessionId && nextSessions[previousId]) {
      nextSessions[previousId] = replacedSession(nextSessions[previousId])
    }
    nextSessions[payload.sessionId] = activeSession(payload, nextSessions[payload.sessionId])
    state.sessions = nextSessions
    state.activeByRule = { ...state.activeByRule, [ruleKey]: payload.sessionId }
  },
  SAVE_DRAFT(state, payload) {
    const session = state.sessions[payload.sessionId]
    if (!session || session.status !== 'ACTIVE') return
    state.sessions = {
      ...state.sessions,
      [payload.sessionId]: {
        ...session,
        draft: cloneValue(payload.draft),
        savedAt: payload.savedAt || Date.now()
      }
    }
  },
  SAVE_COMPILED(state, payload) {
    const session = state.sessions[payload.sessionId]
    if (!session || session.status !== 'ACTIVE') return
    state.sessions = {
      ...state.sessions,
      [payload.sessionId]: {
        ...session,
        draft: cloneValue(payload.operand),
        compiledOperand: cloneValue(payload.operand),
        compiledScript: payload.compiledScript || '',
        compiledRevision: session.compiledRevision + 1
      }
    }
  },
  MARK_APPLIED(state, payload) {
    const session = state.sessions[payload.sessionId]
    if (!session || session.status !== 'ACTIVE') return
    const revision = Math.min(Number(payload.revision) || 0, session.compiledRevision)
    if (revision <= session.appliedRevision) return
    state.sessions = {
      ...state.sessions,
      [payload.sessionId]: { ...session, appliedRevision: revision }
    }
  }
}

const actions = {
  openSession({ commit }, payload) { commit('OPEN_SESSION', payload) },
  saveDraft({ commit }, payload) { commit('SAVE_DRAFT', payload) },
  saveCompiled({ commit }, payload) { commit('SAVE_COMPILED', payload) },
  markApplied({ commit }, payload) { commit('MARK_APPLIED', payload) }
}

export default {
  namespaced: true,
  state,
  getters,
  mutations,
  actions
}
