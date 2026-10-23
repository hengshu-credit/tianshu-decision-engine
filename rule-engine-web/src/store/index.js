import { createStore } from 'vuex'
import expressionSessions from './modules/expressionSessions'
import workspaceTabs from './modules/workspaceTabs'
import {
  projectContextStorage,
  readProjectContext,
  writeProjectContext,
} from '@/utils/projectContext'

const store = createStore({
  state: {
    currentProject: readProjectContext(projectContextStorage()),
    projectContextGeneration: 0,
  },
  mutations: {
    INVALIDATE_PROJECT_CONTEXT_REQUESTS(state) {
      state.projectContextGeneration += 1
    },
    SET_CURRENT_PROJECT(state, project) {
      state.projectContextGeneration += 1
      state.currentProject = project
      writeProjectContext(projectContextStorage(), project)
    }
  },
  actions: {},
  modules: { expressionSessions, workspaceTabs }
})

if (typeof window !== 'undefined') window.addEventListener('tianshu:identity-change', () => {
  store.commit('workspaceTabs/CLEAR')
  store.commit('expressionSessions/CLEAR')
  store.commit('SET_CURRENT_PROJECT', null)
})

export default store
